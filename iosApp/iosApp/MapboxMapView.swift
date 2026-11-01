import SwiftUI
import MapboxMaps

/// Thin SwiftUI wrapper around the Mapbox Maps SDK (v11) base map.
///
/// Phase 3 scope: a REAL base map with one colored pin per spot PLUS
/// tap-to-create. Spots come from the shared module via `FeedData.spots()`
/// (which bridges `FeedBridge.shared.spots()`); each pin is placed at the
/// spot's lat/lng and colored by the spot's category color (`UISpot.colorHex`,
/// resolved shared-side from `SpotCategory`). A tap (or long-press) on an EMPTY
/// part of the map yields the tapped geographic coordinate via the `onMapTap`
/// callback so the parent can start a create-spot flow at that point. All
/// existing map chrome stays layered on top.
///
/// Phase 4 scope: ADOPT a built-in Mapbox POI by tapping it. A tap on one of
/// the base style's points of interest resolves the POI's name, maki icon id
/// and symbol class and yields them (with the POI's coordinate) via the
/// `onPoiTap` callback so the parent can open the create-spot sheet PREFILLED.
/// A POI tap takes PRECEDENCE over the empty-map tap (see POI-query note).
///
/// DEFERRED (to control scope): the legacy zoom-variant marker behavior (dot at
/// far zoom / pin mid / card near) is intentionally SKIPPED here — a single,
/// simple pin style renders at all zoom levels.
///
/// Annotation API choice: this uses the Mapbox v11 SwiftUI DATA-DRIVEN
/// annotations API — `CircleAnnotationGroup` over the spots — rather than view
/// annotations or the imperative annotation managers. A `CircleAnnotation` reads
/// like a map pin (a filled, outlined dot), needs NO registered image asset, and
/// takes its fill color directly from the shared `colorHex` via `Color(hex:)`.
/// The group form is declarative and re-renders when the spot list changes,
/// which fits the SwiftUI `Map` content builder cleanly. If a later phase needs
/// richer pins (custom glyphs, selection), it can switch to `MapViewAnnotation`
/// / `PointAnnotation` with registered images then.
///
/// Empty-map tap API choice: this uses the Mapbox v11 `Interaction` API —
/// `TapInteraction { context in … }` and `LongPressInteraction { context in … }`
/// — placed INSIDE the `Map { }` content builder (both conform to `MapContent`,
/// exactly like the featureset `TapInteraction(.standardPoi)` above them). This
/// is the non-deprecated replacement for the old `.onMapTapGesture` /
/// `.onMapLongPressGesture` view modifiers (deprecated in 11.19.0). Each
/// delivers an `InteractionContext` whose `.coordinate` is already the geographic
/// `CLLocationCoordinate2D` of the touched point — no manual
/// screen-point→coordinate conversion (no `MapReader`/`MapProxy` plumbing) is
/// needed, which is why this API was chosen over a `MapReader` + proxy. These
/// fire for taps on EMPTY map only (see the precedence note below).
///
/// POI-query API choice (Phase 4): the default base style is **Mapbox
/// Standard** (`Map`'s default `styleURI`). In the Standard style the internal
/// symbol layers are NOT individually addressable by a raw layer id such as
/// "poi-label" (Standard ships as an imported style whose child layers are not
/// directly query-/tap-targetable). Standard instead exposes a typed
/// **featureset** for its points of interest: `StandardPoiFeature` via the
/// `FeaturesetDescriptor.standardPoi` descriptor (featureset id "poi"). So the
/// POI tap is wired as a v11 `TapInteraction(.standardPoi) { feature, context in … }`
/// placed INSIDE the `Map { }` content builder (the SDK's SwiftUI way to add
/// interactions — `TapInteraction` conforms to `MapContent`). `StandardPoiFeature`
/// gives the POI's `name`, `maki`, `class` and `coordinate` directly as flat
/// optionals, so no manual `queryRenderedFeatures` + JSON digging is needed.
/// (A raw `onLayerTapGesture("poi-label")` exists but is BOTH deprecated in
/// 11.19.0 AND would not match the Standard style's non-addressable POI layer,
/// so the featureset `TapInteraction` is the correct choice here.)
///
/// POI-tap PRECEDENCE over empty-map tap: precedence is preserved by interaction
/// ORDERING plus the featureset handler's return value. The featureset
/// `TapInteraction(.standardPoi)` is added BEFORE the generic empty-map
/// `TapInteraction`, and by the SDK contract featureset interactions are resolved
/// first; the generic map `TapInteraction` "will be called last, when no other
/// interactions targeted to a featureset handled it." Because the `.standardPoi`
/// closure returns `true` when it handles a POI tap, propagation STOPS and the
/// generic map tap does NOT also fire. So a tap that hits a POI runs only the
/// adopt-prefill flow; a tap that hits no POI falls through to the generic
/// `TapInteraction` and keeps the Phase 3 empty-create behavior. The generic
/// `TapInteraction`/`LongPressInteraction` closures return `true` to mark the
/// generic gesture handled.
///
/// FAB coordinate: the parent's FAB needs a sensible default coordinate even
/// without a tap. We keep a `@State` camera viewport bound to the `Map`; its
/// `camera?.center` is the CURRENT map center, exposed to the parent via the
/// `currentCenter` closure. If the center isn't readable yet, the parent falls
/// back to Berlin center (see `MapBaseView`).
///
/// Approach: this uses the Mapbox SDK's NATIVE SwiftUI `Map` view (v11), not a
/// `UIViewRepresentable` wrapper — the SDK's recommended SwiftUI entry point.
///
/// The public access token is NOT hardcoded here: the SDK reads `MBXAccessToken`
/// from Info.plist automatically, wired from the gitignored `Mapbox.xcconfig`
/// (see Mapbox.xcconfig.template). No token literal lives in Swift.
struct MapboxMapView: View {
    /// Berlin city center — the default camera target.
    static let berlin = CLLocationCoordinate2D(latitude: 52.52, longitude: 13.405)
    private static let defaultZoom: CGFloat = 11

    /// Pin geometry tokens (map-marker sizing; not part of the SwiftUI layout
    /// token sets, which don't cover Mapbox annotation radii).
    private static let pinRadius: CGFloat = 7
    private static let pinStrokeWidth: CGFloat = 2

    /// Spots to pin, sourced from the shared module (single source of truth).
    /// Passed from the parent as `@State` so adding a spot and re-reading
    /// `FeedData.spots()` re-renders the `CircleAnnotationGroup` with the new pin.
    private let spots: [UISpot]

    /// Called with the geographic coordinate of a tap/long-press on empty map.
    private let onMapTap: (CLLocationCoordinate2D) -> Void

    /// Called when one of the app's OWN spot pins is tapped (Phase C5). The
    /// tapped `UISpot` is already in the `CircleAnnotationGroup` closure scope,
    /// so the handler resolves exactly which spot was tapped and the parent can
    /// open `MapSpotSheetView` for it. A pin tap takes PRECEDENCE over the
    /// empty-map create tap (see the `.onTapGesture` precedence note below).
    private let onSpotTap: (UISpot) -> Void

    /// Called when a built-in Mapbox POI is tapped (Phase 4). Carries ONLY flat
    /// values: the POI's coordinate plus its `name`, `maki` icon id and symbol
    /// `clazz` (empty strings when the style omits a field). The parent resolves
    /// the category shared-side (`FeedData.categoryForMaki`) and opens the
    /// create-spot sheet prefilled.
    private let onPoiTap: (_ coordinate: CLLocationCoordinate2D, _ name: String, _ maki: String, _ clazz: String) -> Void

    /// Called once on appear with a getter the parent can invoke to read the
    /// CURRENT map center (for the FAB's default create coordinate).
    private let currentCenter: (@escaping () -> CLLocationCoordinate2D?) -> Void

    /// The live camera viewport, bound into the `Map`. Reading
    /// `viewport.camera?.center` gives the current map center for the FAB path.
    @State private var viewport: Viewport = .camera(
        center: MapboxMapView.berlin,
        zoom: MapboxMapView.defaultZoom
    )

    init(
        spots: [UISpot] = FeedData.spots(),
        onMapTap: @escaping (CLLocationCoordinate2D) -> Void = { _ in },
        onSpotTap: @escaping (UISpot) -> Void = { _ in },
        onPoiTap: @escaping (CLLocationCoordinate2D, String, String, String) -> Void = { _, _, _, _ in },
        currentCenter: @escaping (@escaping () -> CLLocationCoordinate2D?) -> Void = { _ in }
    ) {
        self.spots = spots
        self.onMapTap = onMapTap
        self.onSpotTap = onSpotTap
        self.onPoiTap = onPoiTap
        self.currentCenter = currentCenter
    }

    var body: some View {
        Map(viewport: $viewport) {
            // One colored pin per spot. The fill color comes from the shared
            // category color (`colorHex`) via `Color(hex:)` — no category hex
            // literal is hardcoded Swift-side.
            CircleAnnotationGroup(spots) { spot in
                CircleAnnotation(
                    centerCoordinate: CLLocationCoordinate2D(
                        latitude: spot.lat,
                        longitude: spot.lng
                    )
                )
                .circleRadius(Self.pinRadius)
                .circleColor(StyleColor(Color(hex: spot.colorHex)))
                .circleStrokeColor(StyleColor(.white))
                .circleStrokeWidth(Self.pinStrokeWidth)
                // Phase C5: tapping one of the app's OWN spot pins opens the
                // lean MapSpotSheetView for it. API used (PRIMARY, confirmed to
                // compile against the pinned SDK 11.19.0): the v11 data-driven
                // annotation builder `CircleAnnotation.onTapGesture { context in
                // -> Bool }` — a single-argument closure receiving the tap
                // `InteractionContext` and returning a `Bool` (a zero-arg
                // closure does NOT compile: the context arg "cannot be
                // implicitly ignored"). `spot` is in closure scope, so the
                // handler resolves exactly which spot was tapped without any
                // featureset/query plumbing. Returning `true` marks the tap
                // HANDLED so it does NOT fall through to the generic empty-map
                // `TapInteraction` below → a pin tap opens the sheet and NEVER
                // starts the create flow (pin-tap precedence over empty-map tap).
                .onTapGesture { _ in
                    onSpotTap(spot)
                    return true
                }
            }

            // Phase 4: adopt a built-in Standard-style POI by tapping it. The
            // typed `StandardPoiFeature` exposes the POI's name/maki/class and
            // coordinate as flat optionals; we forward them (empty string for a
            // missing field) to `onPoiTap`. Returning `true` marks the tap
            // HANDLED, which stops propagation so the generic empty-map
            // `TapInteraction` below does NOT also fire → POI precedence.
            TapInteraction(.standardPoi) { poi, context in
                onPoiTap(
                    poi.coordinate,
                    poi.name ?? "",
                    poi.maki ?? "",
                    poi.`class` ?? ""
                )
                return true
            }

            // A tap or long-press on EMPTY map yields the geographic coordinate
            // directly (v11 `InteractionContext.coordinate`). Both are wired so
            // creation is reachable by a quick tap or a deliberate long-press.
            // These are placed AFTER the `.standardPoi` featureset interaction:
            // featureset interactions are resolved first and, when the POI tap
            // returns `true`, propagation stops, so these generic map handlers
            // fire only for taps/long-presses that hit NO POI (empty map).
            // Returning `true` marks the generic gesture handled.
            TapInteraction { context in
                onMapTap(context.coordinate)
                return true
            }

            LongPressInteraction { context in
                onMapTap(context.coordinate)
                return true
            }
        }
        .ignoresSafeArea()
        .onAppear {
            // Hand the parent a getter for the current map center so the FAB can
            // create a spot at the center without a tap.
            currentCenter { viewport.camera?.center }
        }
    }
}
