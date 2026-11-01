---
inclusion: always
---

# Native UI conventions

> Split out of the former `architecture.md`. The reusable UI-component convention lives in the sibling `35-ui-components.md`.

- No external UI library; use built-in SwiftUI controls, custom components, and SF Symbols.
- Design tokens are centralized in Swift in the `AppColor` enum (mirrored from the shared color values), together with a `Color(hex:)` initializer. Do not hardcode hex color literals inside views — reference `AppColor`.
- Reusable building blocks currently in place: `PostCardView`, `AvatarView`, `VoteControlView`, `FilterChipView`, `MapboxMapView`, `AddSpotSheetView`, `CreatePostView`, `CommentsSheetView`, `ProfileEditView`, `OtherProfileView`, `FollowListView`, `MapSpotSheetView`, `CollectionPickerSheetView`, `CollectionDetailView`, the `ScrollOffsetTracking` helper, and the auth screens `LoginView` / `SignUpView` / `AuthComponents`. Collections are backed by a `CollectionsStore` `@Observable` env wrapper (mirrors the `Session`/`Localization` pattern) with a `UISpotCollection` Swift mirror of the flat `IosSpotCollection`.
- Auth state is held in an `@Observable` Swift `Session` wrapper (mirrors the `Localization` pattern, injected into the environment) which drives the AUTH GATE in `ContentView`: auth screens when logged out, `RootTabView` when logged in.
- The feed uses a `List` with native `.refreshable` pull-to-refresh. The `ScrollOffsetTracking` helper is reserved for a future auto-hide header step.
- Sheet pattern: present sheets via `.sheet(item:)` + `.presentationDetents` + a visible drag indicator (reference implementation: `SpotSheetView`). AddSpot (`AddSpotSheetView`), CreatePost (`CreatePostView`), and Comments (`CommentsSheetView`) all follow this pattern rather than inventing their own.
- Borderless pages (those using `.ignoresSafeArea(edges: .top)`) place interactive top chrome via the reusable `.topChrome` modifier / `AppLayout.topChromeSpacing` token (in `AppLayout.swift`) rather than reading `safeAreaInsets` manually inside the ignored subtree (which returns 0). `AppLayout` is the central iOS layout-token location, analogous to `AppColor`.
