---
inclusion: always
---

# Architecture & Native iOS Conventions

This document records the long-lived architecture, module boundaries, and native-UI conventions for the ThirdSpace project.

## Architecture principle

- iOS UI is fully native SwiftUI (in `iosApp/iosApp/`). There is no Compose-Multiplatform UI.
- The `shared` module is Kotlin Multiplatform LOGIC/DATA ONLY — no Compose dependency. The Android target remains, but `androidApp` is currently a non-functional placeholder; a native Android UI comes in a later phase. Business/shared logic stays in the shared module so Android can adopt it later.

## Module & package boundaries

- Shared logic/data lives in package `com.example.thirdspace.data` (commonMain): `Models.kt`, `SampleData.kt`, `Formatting.kt`.
- The Swift↔Kotlin boundary goes through `FeedBridge` (shared `iosMain`). Only flat, ObjC-friendly types cross it: String / Int / Bool; colors as `#RRGGBB` strings; Kotlin enums exposed as String; NO nullables and NO Long across the boundary. The generated framework is named `Shared`.

## Native UI conventions

- No external UI library; use built-in SwiftUI controls, custom components, and SF Symbols.
- Design tokens are centralized in Swift in the `AppColor` enum (mirrored from the shared color values), together with a `Color(hex:)` initializer. Do not hardcode hex color literals inside views — reference `AppColor`.
- Reusable building blocks currently in place: `PostCardView`, `AvatarView`, `VoteControlView`, `FilterChipView`, and the `ScrollOffsetTracking` helper.
- The feed uses a `List` with native `.refreshable` pull-to-refresh. The `ScrollOffsetTracking` helper is reserved for a future auto-hide header step.
- Sheet pattern: present sheets via `.sheet(item:)` + `.presentationDetents` + a visible drag indicator (reference implementation: `SpotSheetView`). Future sheets (e.g. CreatePost, Comments, AddSpot) follow this same pattern rather than inventing their own.

## Deliberate exclusions (current state)

- No real map engine (MapKit/Mapbox) yet — `MapBaseView` renders only the chrome (search bar, filter chips, placeholder, controls, FAB).
- No backend/Supabase yet — the UI runs on `SampleData` dummy data.
- The spot model is intentionally minimal (no `description`, no photo strip); `SpotSheetView` uses placeholder content for fields that do not exist yet.

## Verification

Automated/technical verification is governed by `verification-strategy.md`. In short, iOS builds are verified by the developer in Xcode, and this environment has no automated iOS build.
