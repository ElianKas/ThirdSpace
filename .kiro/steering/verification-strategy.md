---
inclusion: always
---

# Verification Strategy

## Purpose

This document defines the binding, technical/automated verification strategy for
the ThirdSpace Kotlin Multiplatform project. It covers **automated technical
correctness only**: compilation, builds, and unit tests.

**Visual QA is explicitly out of scope and stays with the developer.** No part of
this strategy asserts that a screen looks correct, is laid out properly, or matches
a design. Automated checks prove code compiles, apps assemble/launch, and pure
logic behaves — nothing about visual appearance.

## Proportional Binding Matrix

This matrix is **binding but proportional**: for any given change, run **only the
checks relevant to that change type**. Do not run full cross-platform builds or the
entire test suite when the change does not warrant it. When a change spans multiple
rows, run the union of the relevant checks.

| Change type | Required checks |
|---|---|
| **Shared common Kotlin** (`shared/src/commonMain`) | Run `commonTest` unit tests, AND verify compilation/build on **BOTH** Android and iOS (simulator target). Common code ships to both platforms, so both must in principle be verified. |
| **Shared Android-specific** (`shared/src/androidMain`) or **`androidApp`** code | Android unit tests where present, AND `./gradlew :androidApp:assembleDebug`. |
| **Shared iOS-specific** (`shared/src/iosMain`) or **`iosApp`** Swift | iOS simulator Debug build via `xcodebuild`, plus an optional simulator boot/launch smoke check. **No automated iOS unit/UI tests exist** (see Known Limitations). |
| **Cross-cutting / build-config** (Gradle files, version catalog, project config) | Builds for **both** platforms, AND **all** existing unit tests. |
| **Docs-only** (Markdown, comments, steering) | No build or test required. |

## Principles

- **Proportional:** run only the checks relevant to the change. Avoid unnecessary
  full builds. Prefer the smallest task that proves the thing you need to prove.
- **Reuse-first:** use only the tooling already provided by the toolchain and
  version catalog — `kotlin.test`, JUnit4, Gradle, `xcodebuild`, `simctl`. **Do not
  add new test frameworks or tooling without explicit approval.**
- **Shared code is dual-platform:** changes to `commonMain` must in principle be
  verified on both Android and iOS, because the same code compiles to both.

## Reuse-First Tooling

| Tool | Use |
|---|---|
| `kotlin.test` (wired into `commonTest`) | Multiplatform unit tests for shared logic. |
| JUnit4 (`junit`, `androidx-testExt-junit`) | Android host/instrumented tests where present. |
| Gradle (`./gradlew`) | Build and run all JVM/Android/Native unit tests. |
| `xcodebuild` | iOS simulator Debug builds of `iosApp`. |
| `simctl` | Boot a simulator and launch the app for a smoke check. |

## Verified Commands

These task names were discovered via `./gradlew :shared:tasks --all` and are
confirmed against the KMP AndroidLibrary plugin in use:

| Purpose | Command | Status |
|---|---|---|
| Discover task names | `./gradlew :shared:tasks --all` | Use this; do not hardcode guesses. |
| Run shared unit tests on the host (JVM) — executes `commonTest` + android host test | `./gradlew :shared:testAndroidHostTest` | **Verified running** (4 tests, 0 failures). |
| Run shared unit tests on iOS simulator target — executes `commonTest` as native | `./gradlew :shared:iosSimulatorArm64Test` | Discovered; run to verify iOS side. |
| Run all shared unit tests across targets | `./gradlew :shared:allTests` | Discovered aggregate task. |
| Build Android app | `./gradlew :androidApp:assembleDebug` | Standard AGP task. |
| Build iOS app (simulator Debug) | `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator build` | Standard; adjust scheme/project to match. |

> The `:shared` unit-test task under the KMP AndroidLibrary plugin is
> `testAndroidHostTest` (NOT the classic `testDebugUnitTest`). If task names change,
> re-discover via `./gradlew :shared:tasks --all` rather than guessing.

## Reporting Rules

Always distinguish these three outcomes explicitly; they are **not**
interchangeable:

1. **BUILD success** — code compiled and the artifact assembled. Says nothing about
   runtime behavior.
2. **APP LAUNCH success** — the app started on a device/simulator without crashing.
   Says nothing about whether features work.
3. **TESTED functionality** — a test with real assertions executed and passed.

Additional rules:

- A "BUILD SUCCESSFUL" line alone does **not** prove tests ran. Confirm actual test
  counts from the test report (e.g. `shared/build/test-results/.../*.xml`).
- If a check **cannot run** (no Android emulator, no iOS simulator, missing tests),
  report it as **NOT verified** with the reason. Never imply something ran if it did
  not.
- For the Reviewer: unavailable simulators/emulators or missing tests mean the
  relevant criterion is **BLOCKED**, not **PASS**.

## Known Limitations

- **No iOS XCTest target exists — but the iOS build IS runnable here.** There are
  no automated iOS unit or UI tests. However, the iOS simulator Debug build via
  `xcodebuild` DOES run in this environment and is the **required BUILD gate** for
  any `iosApp`/`iosMain` change. iOS-specific verification therefore consists of
  that simulator Debug build (BUILD) plus an optional boot/launch smoke check (APP
  LAUNCH). iOS feature behavior is **not** automatically TESTED (no XCTest target);
  the absence of iOS tests does NOT mean the iOS build is unavailable.
- **Android instrumented / Compose UI tests require a device or emulator.** The
  `androidDeviceTest` source set and `connectedAndroidDeviceTest` task exist, but
  their execution depends on an available emulator/device, which is
  environment-dependent. Report as NOT verified when none is available.
- **Shared unit tests cover the shared logic/data + i18n layer.** The shared module
  is Compose-free (UI is native SwiftUI); current commonTest suites (e.g. score
  formatting, comments, report reasons, i18n strings, profile) run on both the
  Android host and the iOS simulator target. UI behavior itself is not unit-tested.
