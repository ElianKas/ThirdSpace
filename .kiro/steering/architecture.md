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
- Borderless pages (those using `.ignoresSafeArea(edges: .top)`) place interactive top chrome via the reusable `.topChrome` modifier / `AppLayout.topChromeSpacing` token (in `AppLayout.swift`) rather than reading `safeAreaInsets` manually inside the ignored subtree (which returns 0). `AppLayout` is the central iOS layout-token location, analogous to `AppColor`.

## Internationalization (i18n)

- Static UI text lives in a shared i18n layer (package `com.example.thirdspace.i18n`): an `AppLanguage` enum (DE default, EN), a `Strings` interface with `De`/`En` implementations, and `AppStrings` as the single resolution point. The shared module is the single source of truth for all static strings (Android inherits them later).
- The Swift↔Kotlin boundary stays flat: `FeedBridge` exposes a flat `IosStrings` snapshot (simple keys as String properties; pluralized/interpolated strings as functions). The `AppLanguage` enum never crosses the boundary — the language code crosses as a String ("de"/"en").
- Swift reads strings through an `@Observable Localization` wrapper injected into the environment; views use `@Environment(Localization.self) private var loc` and `loc.strings.*`. Changing the language live (via `Localization.setLanguage`) re-renders observing views.
- Adding a language = one `AppLanguage` enum case + its values; the typed `Strings` interface forces every key to be provided (compile-time completeness). A commonTest asserts every key is non-empty in both languages.
- The in-app language picker is the `globe` button at the top-right of the Profile main page; the chosen language is in-memory only (NOT persisted across relaunch), default DE.
- Language display names are endonyms ("Deutsch"/"English") and are NOT translated. Dynamic/user content is NOT translated either: spot names, categories, neighborhoods, post/comment text, author names, date labels, the current email, etc.

## Navigation

- The app shell is a native SwiftUI `TabView` with four tabs: Posts (feed), Map, Discover (inert placeholder), Profile.
- Only the Profile tab is wrapped in a `NavigationStack`, so the profile can PUSH sub-pages (e.g. the "Profil bearbeiten" edit page). Other tabs are not wrapped.
- Modal surfaces use the sheet pattern (`.sheet(...)` + `.presentationDetents` + `.presentationDragIndicator(.visible)` set at the call site). Push vs. sheet: detail/compose surfaces are sheets; the profile edit page is a push sub-page.

## Deliberate exclusions (current state)

- No real map engine (MapKit/Mapbox) yet — `MapBaseView` renders only the chrome (search bar, filter chips, placeholder, controls, FAB).
- No backend/Supabase yet — the UI runs on `SampleData` dummy data.
- The spot model is intentionally minimal (no `description`, no photo strip); `SpotSheetView` uses placeholder content for fields that do not exist yet.

## Verification

Automated/technical verification is governed by `verification-strategy.md`. iOS simulator Debug builds ARE runnable in this environment via `xcodebuild` and should be used to verify iOS/Swift changes; visual/layout QA remains the developer's responsibility.
