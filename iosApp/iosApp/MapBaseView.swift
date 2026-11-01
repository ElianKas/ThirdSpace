import SwiftUI
import CoreLocation

/// Native SwiftUI map BASE. The map area is a REAL Mapbox base map
/// (`MapboxMapView`); the chrome below is layered on top of it.
///
/// Layout:
/// - Real Mapbox base map centered on Berlin, filling the area the former
///   "Karte folgt" placeholder occupied, with one category-colored pin per
///   shared spot (Phase 2).
/// - Top frosted search bar: magnifyingglass + placeholder + round avatar.
/// - Horizontally scrollable filter chips sourced from the shared bridge
///   (`FeedBridge.shared.mapFilters()`), "Meine Pins" active.
/// - Bottom-left vertical control stack (locate + users SF Symbols) — no-ops.
/// - Bottom-right FAB (plus) — starts a create-spot flow at the current map
///   center (Phase 3).
///
/// Create-spot (Phase 3): a tap/long-press on empty map, OR the FAB, opens the
/// native `AddSpotSheetView` at the chosen coordinate. Confirming persists the
/// new spot through the shared repository and refreshes the pins in-session.
/// The two control-stack buttons remain no-ops (UI only).
struct MapBaseView: View {
    @Environment(Localization.self) private var loc
    // Computed (not a stored `let`) so the chip labels re-resolve from the
    // shared bridge on every render. A language change re-renders this view
    // (its body reads `loc.strings.*`), so the chips pick up the new language
    // live instead of staying frozen at their first-resolved values.
    private var filters: [UIFilterChip] { FeedData.mapFilters() }

    /// Spots rendered as pins. Held in `@State` and seeded from the shared
    /// bridge so confirming a new spot can re-read `FeedData.spots()` and have
    /// the `CircleAnnotationGroup` render the new pin in-session.
    @State private var spots: [UISpot] = FeedData.spots()

    /// Drives the create-spot sheet via the project `.sheet(item:)` pattern.
    /// Non-nil carries the pending coordinate (from a tap or the FAB).
    @State private var pendingSpot: PendingSpot?

    /// Phase C5: drives the lean `MapSpotSheetView` via the project
    /// `.sheet(item:)` pattern. Set when one of the app's OWN spot pins is
    /// tapped (`MapboxMapView.onSpotTap`). A pin tap opens this detail sheet and
    /// NEVER the create flow (pin-tap precedence is enforced inside
    /// `MapboxMapView` by returning `true` from the pin `.onTapGesture`, which
    /// stops the empty-map `TapInteraction` from firing). This is a SEPARATE
    /// `@State` item from `pendingSpot`, so the create sheet and the detail
    /// sheet are never requested at the same time (the two triggers are mutually
    /// exclusive by construction — empty map vs. pin).
    @State private var selectedSpot: UISpot?

    /// Getter handed up from `MapboxMapView` to read the CURRENT map center for
    /// the FAB's default coordinate. Nil until the map has appeared.
    @State private var mapCenter: (() -> CLLocationCoordinate2D?)?

    /// Phase 3 post-prompt. After a spot is created, a native `.alert` asks
    /// "also share this spot as a post?". Non-nil `postPromptSpotId` drives the
    /// alert's presentation; `postPromptSpotName` carries the name for the
    /// interpolated body (looked up from the refreshed `spots` by id, so
    /// `AddSpotSheetView`'s `(String) -> Void` callback signature is unchanged).
    @State private var postPromptSpotId: String?
    @State private var postPromptSpotName: String = ""

    /// Drives the preselected create-post sheet opened from the prompt's
    /// "Posten" button. `createPostSpotId` is the spot to preselect.
    @State private var showCreatePost = false
    @State private var createPostSpotId: String?

    var body: some View {
        ZStack {
            // Real Mapbox base map centered on Berlin with category-colored
            // pins. All chrome below stays layered ON TOP of it. A tap/long-
            // press yields a coordinate (`onMapTap`) that opens the create-spot
            // sheet; `currentCenter` hands up a getter for the FAB path. POI
            // tapping is still Phase 4 and is NOT handled here.
            MapboxMapView(
                spots: spots,
                onMapTap: { coordinate in
                    pendingSpot = PendingSpot(
                        coordinate: (lat: coordinate.latitude, lng: coordinate.longitude)
                    )
                },
                onSpotTap: { spot in
                    // Phase C5: a tap on one of the app's own spot pins opens
                    // the lean MapSpotSheetView for that spot. The tapped UISpot
                    // is resolved inside MapboxMapView's CircleAnnotationGroup
                    // closure, so we just present it. This path took PRECEDENCE
                    // over the empty-map create tap (handled in MapboxMapView),
                    // so a pin tap never starts a create flow.
                    selectedSpot = spot
                },
                onPoiTap: { coordinate, name, maki, clazz in
                    // Phase 4: adopt a tapped Mapbox POI. Resolve the category
                    // shared-side from the POI's maki/class (single source of
                    // truth, flat String in/out), then open the SAME create-spot
                    // sheet PREFILLED with the POI name + resolved category at
                    // the POI coordinate. A POI tap already took precedence over
                    // the empty-map tap inside MapboxMapView.
                    let category = FeedData.categoryForMaki(maki: maki, clazz: clazz)
                    pendingSpot = PendingSpot(
                        coordinate: (lat: coordinate.latitude, lng: coordinate.longitude),
                        prefillName: name,
                        prefillCategory: category
                    )
                },
                currentCenter: { getter in
                    mapCenter = getter
                }
            )

            // Bottom-left vertical control stack
            VStack {
                Spacer()
                HStack {
                    controlStack
                    Spacer()
                }
            }
            .padding(.leading, AppDimens.spacingLG)
            .padding(.bottom, AppDimens.spacingXXL)

            // Bottom-right FAB
            VStack {
                Spacer()
                HStack {
                    Spacer()
                    fab
                }
            }
            .padding(.trailing, AppDimens.spacingLG)
            .padding(.bottom, AppDimens.spacingXXL)
        }
        // Top controls (search bar + filter chips) placed BELOW the real top
        // safe-area inset via `.topChrome` so they clear the status bar /
        // Dynamic Island, while the `MapboxMapView` below uses `.ignoresSafeArea()`
        // to keep the map borderless to the top. The map uses the larger
        // `AppLayout.mapTopChromeSpacing` token so the search + filter chips
        // sit lower, clear of the Dynamic Island; other `topChrome` callers
        // (e.g. the Profile language pill) keep the default `topChromeSpacing`.
        .topChrome(alignment: .leading, spacing: AppLayout.mapTopChromeSpacing) {
            VStack(spacing: AppDimens.spacingSM) {
                searchBar
                filterChips
            }
            .padding(.horizontal, AppDimens.spacingLG)
        }
        // Create-spot sheet. Follows the project sheet pattern: driven by an
        // Identifiable @State item (`pendingSpot`) carrying the pending
        // coordinate, with `.medium`+`.large` detents and the drag indicator.
        .sheet(item: $pendingSpot) { pending in
            AddSpotSheetView(
                pending: pending,
                onCreated: { id in
                    // Re-read the shared repository so the new pin renders.
                    spots = FeedData.spots()
                    pendingSpot = nil
                    // Phase 3: prompt to also post about the new spot. Resolve
                    // the name from the refreshed spots by id (keeps the
                    // AddSpotSheetView callback signature as (String) -> Void).
                    let name = spots.first { $0.id == id }?.name ?? ""
                    postPromptSpotName = name
                    postPromptSpotId = id
                },
                onClose: { pendingSpot = nil }
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        // Phase 3 post-prompt: after creating a spot, ask whether to also share
        // it as a post. "Nicht jetzt" just dismisses (no post); "Posten" opens
        // the create-post sheet PRESELECTED to the new spot. The
        // `presenting:`/actions/message alert form is valid for iOS 15+.
        .alert(
            loc.strings.postPromptTitle,
            isPresented: Binding(
                get: { postPromptSpotId != nil },
                set: { if !$0 { postPromptSpotId = nil } }
            ),
            presenting: postPromptSpotId
        ) { id in
            Button(loc.strings.postPromptNotNow, role: .cancel) {
                postPromptSpotId = nil
            }
            Button(loc.strings.postPromptPost) {
                createPostSpotId = id
                postPromptSpotId = nil
                showCreatePost = true
            }
        } message: { _ in
            Text(loc.strings.postPromptBody(spotName: postPromptSpotName))
        }
        // Preselected create-post sheet opened from the prompt's "Posten"
        // button. Follows the project sheet pattern (large detent + drag
        // indicator). On dismiss, re-read spots (trivial) so any state stays
        // fresh; the feed refresh after posting is handled inside CreatePostView.
        .sheet(isPresented: $showCreatePost, onDismiss: { spots = FeedData.spots() }) {
            CreatePostView(preselectedSpotId: createPostSpotId)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        // Phase C5: lean spot-detail sheet opened by tapping an own spot pin.
        // Follows the project sheet pattern (Identifiable @State item +
        // .medium/.large detents + drag indicator). Driven by a SEPARATE
        // @State item (`selectedSpot`) from the create flow's `pendingSpot`,
        // so the detail sheet and the create sheet are never requested at
        // once (pin tap vs. empty-map tap are mutually exclusive). From here
        // the user opens the nested CollectionPickerSheetView.
        .sheet(item: $selectedSpot) { spot in
            MapSpotSheetView(spot: spot)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
    }

    // MARK: Frosted search bar

    private var searchBar: some View {
        HStack(spacing: AppDimens.spacingSM) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: AppTypography.title))
                .foregroundColor(AppColor.mutedForeground)
            Text(loc.strings.mapSearchPlaceholder)
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.textFaint)
            Spacer(minLength: AppDimens.spacingSM)
            AvatarView(name: "Du", size: 24)
        }
        .padding(.horizontal, AppDimens.spacingMD)
        .frame(height: 44)
        .background(
            RoundedRectangle(cornerRadius: AppDimens.radiusMD)
                .fill(AppColor.frost)
        )
        .overlay(
            RoundedRectangle(cornerRadius: AppDimens.radiusMD)
                .stroke(AppColor.border, lineWidth: AppDimens.hairline)
        )
    }

    // MARK: Filter chips (from shared bridge)

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: AppDimens.spacingSM) {
                ForEach(filters) { chip in
                    FilterChipView(label: chip.label, active: chip.active)
                }
            }
        }
    }

    // MARK: Bottom-left control stack

    private var controlStack: some View {
        VStack(spacing: 0) {
            mapControl(systemImage: "location")
            Rectangle()
                .fill(AppColor.hairline)
                .frame(width: 44, height: AppDimens.hairline)
            mapControl(systemImage: "person.2")
        }
        .background(
            RoundedRectangle(cornerRadius: AppDimens.radiusMD)
                .fill(AppColor.frost)
        )
        .overlay(
            RoundedRectangle(cornerRadius: AppDimens.radiusMD)
                .stroke(AppColor.border, lineWidth: AppDimens.hairline)
        )
        .clipShape(RoundedRectangle(cornerRadius: AppDimens.radiusMD))
    }

    private func mapControl(systemImage: String) -> some View {
        Button(action: { /* no-op */ }) {
            Image(systemName: systemImage)
                .font(.system(size: AppTypography.title))
                .foregroundColor(AppColor.foreground)
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
    }

    // MARK: Bottom-right FAB

    private var fab: some View {
        // Shared icon button — a brand circle (size 52).
        AppIconButton(
            systemImage: "plus",
            shape: .circle,
            size: 52,
            action: startCreateAtCenter
        )
    }

    /// FAB path: start a create-spot flow at the CURRENT map center. The center
    /// is read from the getter `MapboxMapView` handed up (`mapCenter`), i.e. the
    /// live camera center. If it isn't readable yet (map not appeared), fall
    /// back to Berlin center — the same default the camera starts at.
    private func startCreateAtCenter() {
        let center = mapCenter?() ?? MapboxMapView.berlin
        pendingSpot = PendingSpot(
            coordinate: (lat: center.latitude, lng: center.longitude)
        )
    }
}

// MARK: - Filter chip

/// Native replication of the Compose `FilterChip`: a pill with a label. Active
/// chips use the brand fill with white text; inactive chips use a frosted
/// surface with a hairline border.
///
/// As of slice B3 this is a THIN WRAPPER over the shared
/// `AppChip(.selectable(active:))` (brand accent), so its MapBaseView call sites
/// keep working while the look is unified with the AddSpot category pill. The
/// public name/signature are unchanged.
struct FilterChipView: View {
    let label: String
    let active: Bool

    var body: some View {
        AppChip(label: label, style: .selectable(active: active))
    }
}
