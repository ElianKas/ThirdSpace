---
inclusion: always
---

# Map (Mapbox)

> Split out of the former `architecture.md`.

- The map engine is the Mapbox Maps SDK for iOS (native), integrated via Swift Package Manager and pinned to an exact v11 version (currently 11.19.0). The engine lives in Swift (`iosApp/iosApp/`), NOT in the shared module.
- `MapboxMapView` wraps the SDK's native SwiftUI `Map` and renders one category-colored pin per spot via `CircleAnnotationGroup` (pin color resolved shared-side from `SpotCategory`; no category hex hardcoded in Swift).
- Interaction uses the v11 `Interaction` API inside the `Map { }` content builder: `TapInteraction(.standardPoi)` adopts a tapped built-in POI (prefills name + category); a generic `TapInteraction`/`LongPressInteraction` handles taps on empty map to create a spot at that coordinate. POI-tap takes PRECEDENCE over the generic tap (featureset interactions resolve first; the POI handler returns `true` to stop propagation). Do NOT use the deprecated `.onMapTapGesture`/`.onMapLongPressGesture` modifiers.
- The app's OWN spot pins are now TAPPABLE: a tap on a spot pin (`CircleAnnotation.onTapGesture` returning handled) opens the lean `MapSpotSheetView` for that spot (whose action assigns it to a collection), and this takes PRECEDENCE over the empty-map create tap (the pin handler returns `true` to stop propagation) so a pin tap never starts a create flow. The POI-adopt + empty-map/long-press create flows are unchanged.
- Creating a spot (empty-map tap, the FAB at map center, or POI adoption) opens `AddSpotSheetView` following the standard sheet pattern. After a spot is created, the AddSpot flow prompts the user via a native SwiftUI `.alert` whether to also create a post for it; choosing to post opens `CreatePostView` with that spot preselected (the legacy spot→post flow).
- Tokens are never hardcoded: the public `pk.` runtime token comes from a gitignored `iosApp/Mapbox.xcconfig` (seeded from the committed `Mapbox.xcconfig.template`) surfaced into Info.plist as `MBXAccessToken`; a secret `sk.` download token, if required by the SDK fetch, goes in `~/.netrc` per the committed `netrc.template`.
- Route planning and search/geocoding are deliberately NOT implemented yet (planned later); Google Places is not used (single-vendor Mapbox).
