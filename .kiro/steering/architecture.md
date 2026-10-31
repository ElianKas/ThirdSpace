---
inclusion: always
---

# Architecture & Native iOS Conventions

This document records the long-lived architecture, module boundaries, and native-UI conventions for the ThirdSpace project.

## Architecture principle

- iOS UI is fully native SwiftUI (in `iosApp/iosApp/`). There is no Compose-Multiplatform UI.
- The `shared` module is Kotlin Multiplatform LOGIC/DATA ONLY — no Compose dependency. The Android target remains, but `androidApp` is currently a non-functional placeholder; a native Android UI comes in a later phase. Business/shared logic stays in the shared module so Android can adopt it later.

## Module & package boundaries

- Shared logic/data lives in package `com.example.thirdspace.data` (commonMain): `Models.kt`, `SampleData.kt`, `Formatting.kt`, `SpotCategory.kt`, `SpotRepository.kt`.
- The Swift↔Kotlin boundary goes through `FeedBridge` (shared `iosMain`). Only flat, ObjC-friendly types cross it: String / Int / Bool / Double (e.g. spot `lat`/`lng`); colors as `#RRGGBB` strings; Kotlin enums exposed as String; NO nullables and NO Long across the boundary. The generated framework is named `Shared`.

## Native UI conventions

- No external UI library; use built-in SwiftUI controls, custom components, and SF Symbols.
- Design tokens are centralized in Swift in the `AppColor` enum (mirrored from the shared color values), together with a `Color(hex:)` initializer. Do not hardcode hex color literals inside views — reference `AppColor`.
- Reusable building blocks currently in place: `PostCardView`, `AvatarView`, `VoteControlView`, `FilterChipView`, `MapboxMapView`, `AddSpotSheetView`, and the `ScrollOffsetTracking` helper.
- The feed uses a `List` with native `.refreshable` pull-to-refresh. The `ScrollOffsetTracking` helper is reserved for a future auto-hide header step.
- Sheet pattern: present sheets via `.sheet(item:)` + `.presentationDetents` + a visible drag indicator (reference implementation: `SpotSheetView`). AddSpot (`AddSpotSheetView`) already follows this pattern; remaining future sheets (e.g. CreatePost, Comments) follow it too rather than inventing their own.
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

## Map (Mapbox)

- The map engine is the Mapbox Maps SDK for iOS (native), integrated via Swift Package Manager and pinned to an exact v11 version (currently 11.19.0). The engine lives in Swift (`iosApp/iosApp/`), NOT in the shared module.
- `MapboxMapView` wraps the SDK's native SwiftUI `Map` and renders one category-colored pin per spot via `CircleAnnotationGroup` (pin color resolved shared-side from `SpotCategory`; no category hex hardcoded in Swift).
- Interaction uses the v11 `Interaction` API inside the `Map { }` content builder: `TapInteraction(.standardPoi)` adopts a tapped built-in POI (prefills name + category); a generic `TapInteraction`/`LongPressInteraction` handles taps on empty map to create a spot at that coordinate. POI-tap takes PRECEDENCE over the generic tap (featureset interactions resolve first; the POI handler returns `true` to stop propagation). Do NOT use the deprecated `.onMapTapGesture`/`.onMapLongPressGesture` modifiers.
- Creating a spot (empty-map tap, the FAB at map center, or POI adoption) opens `AddSpotSheetView` following the standard sheet pattern.
- Tokens are never hardcoded: the public `pk.` runtime token comes from a gitignored `iosApp/Mapbox.xcconfig` (seeded from the committed `Mapbox.xcconfig.template`) surfaced into Info.plist as `MBXAccessToken`; a secret `sk.` download token, if required by the SDK fetch, goes in `~/.netrc` per the committed `netrc.template`.
- Route planning and search/geocoding are deliberately NOT implemented yet (planned later); Google Places is not used (single-vendor Mapbox).

## Spots & offline seam

- `Spot` (commonMain) now carries `lat`/`lng` (Double). It remains extensible for later fields (e.g. `description`, photo).
- `SpotCategory` (commonMain) is the single source of truth for the fixed category list, each category's map-marker color (`#RRGGBB`), and the Mapbox maki/POI-class → category mapping. Category LABELS are dynamic content and are NOT part of the i18n `Strings` layer (consistent with the existing not-translated convention for spot categories).
- `SpotRepository` (commonMain) is the offline-ready seam for spots: currently an in-memory implementation (`InMemorySpotRepository`) seeded from `SampleData`, with documented extension points for local persistence + backend sync later. Spots created/adopted at runtime are in-memory only (do NOT survive relaunch yet). Offline is a shared/cross-platform concern, so this lives in the shared module (Android can adopt it later).

## Deliberate exclusions (current state)

- No backend/Supabase yet — the UI runs on `SampleData` dummy data.
- The spot model still lacks `description` and a photo strip (both intentionally absent for now); `SpotSheetView` uses placeholder content for those fields. Coordinates (`lat`/`lng`) do now exist.

## Verification

Automated/technical verification is governed by `verification-strategy.md`. iOS simulator Debug builds ARE runnable in this environment via `xcodebuild` and should be used to verify iOS/Swift changes; visual/layout QA remains the developer's responsibility.
