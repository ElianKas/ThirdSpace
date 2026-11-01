---
inclusion: always
---

# Navigation

> Split out of the former `architecture.md`.

- The app shell is a native SwiftUI `TabView` with four tabs: Posts (feed), Map, Discover (inert placeholder), Profile.
- Only the Profile tab is wrapped in a `NavigationStack`, so the profile can PUSH sub-pages (e.g. the "Profil bearbeiten" edit page). Other tabs are not wrapped.
- Modal surfaces use the sheet pattern (`.sheet(...)` + `.presentationDetents` + `.presentationDragIndicator(.visible)` set at the call site). Push vs. sheet: detail/compose surfaces are sheets; the profile edit page is a push sub-page.
- Another user's profile is viewable via `OtherProfileView` (a follow/unfollow button + follower/following counts) and searchable follower/following lists via `FollowListView`. These are reached by PUSH inside the Profile tab's `NavigationStack` (from the profile stat columns) and by a `NavigationStack`-wrapped SHEET from the feed/comments author taps (which are not in a `NavigationStack`).
- The Profile segmented control now has a THIRD tab, "Sammlungen" (collections), listing the user's collections; tapping one PUSHES `CollectionDetailView` (manage: remove spot, rename, delete) inside the Profile tab's `NavigationStack`, and a new collection is created inline. The collection picker (`CollectionPickerSheetView`) is presented as a nested SHEET from the map spot sheet.
