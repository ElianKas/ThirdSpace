import SwiftUI

/// The pending create-spot request: a coordinate the user tapped (or the map
/// center, via the FAB) at which a new spot will be created. `Identifiable` so
/// it can drive the project's `.sheet(item:)` presentation pattern from
/// `MapBaseView` — presenting the sheet whenever this is non-nil.
///
/// Phase 4 adds optional PREFILL for the "adopt a Mapbox POI" flow:
/// `prefillName` / `prefillCategory` seed the sheet's name + selected category
/// when the pending spot came from tapping a built-in POI. An EMPTY string
/// means "no prefill" — this keeps the model flat and non-nullable (it's a
/// Swift-internal model, it never crosses the `FeedBridge` boundary), and the
/// empty-map/FAB path (Phase 3) simply constructs it without prefill so the
/// sheet behaves exactly as before.
struct PendingSpot: Identifiable {
    let id = UUID()
    let coordinate: (lat: Double, lng: Double)
    /// Adopted POI name to prefill the name field; empty = no prefill.
    let prefillName: String
    /// Resolved category label (from `FeedData.categoryForMaki`) to preselect;
    /// empty = no preselection.
    let prefillCategory: String

    /// Phase 3 path: a plain pending spot at a coordinate, no prefill.
    init(coordinate: (lat: Double, lng: Double)) {
        self.coordinate = coordinate
        self.prefillName = ""
        self.prefillCategory = ""
    }

    /// Phase 4 path: a pending spot adopted from a tapped POI, carrying the
    /// POI's name and resolved category to prefill the sheet.
    init(coordinate: (lat: Double, lng: Double), prefillName: String, prefillCategory: String) {
        self.coordinate = coordinate
        self.prefillName = prefillName
        self.prefillCategory = prefillCategory
    }
}

/// Native SwiftUI "create spot" sheet.
///
/// Presented from `MapBaseView` via the project sheet pattern
/// (`.sheet(item: $pendingSpot)` + `.presentationDetents([.medium, .large])` +
/// `.presentationDragIndicator(.visible)`), driven by an `Identifiable`
/// `PendingSpot` carrying the tapped/center coordinate.
///
/// Contents, top to bottom (via `SheetScaffold`):
/// - Title + subtitle (localized static chrome).
/// - An editable NAME text field.
/// - A CATEGORY selector: selectable pills built from the FIXED shared category
///   list (`FeedData.categories()` → the 8 `SpotCategory` labels). The list and
///   each selected pill's color come from the shared module (no hardcoded
///   category list or hex Swift-side). Category labels are DYNAMIC (not i18n).
/// - A subtle read-only location line showing the pending coordinate.
/// - A primary "Spot erstellen" button, DISABLED until a non-empty name.
/// - A cancel/close affordance via the scaffold's title-row close button.
///
/// On confirm it builds and persists a new spot through the shared repository
/// (`FeedData.addSpot`) with a Swift-generated `UUID` id, then calls
/// `onCreated` so the presenter re-reads `FeedData.spots()` and the new pin
/// renders in-session. New spots are in-memory only (no relaunch persistence).
///
/// Phase 4: when the `pending` spot was ADOPTED from a tapped Mapbox POI it
/// carries a `prefillName` / `prefillCategory`; the sheet seeds its `@State`
/// `name` / `selectedCategory` from them (in `init`, so the seed takes effect
/// before first render). The user can still edit the prefilled name and change
/// the category before confirming. From the empty-map tap or FAB (Phase 3) the
/// prefill is empty and the sheet opens blank exactly as before.
///
/// Styling uses only `AppColor`/`AppDimens`/`AppTypography` tokens and reuses
/// the hoisted `SheetComponents` building blocks.
struct AddSpotSheetView: View {
    @Environment(Localization.self) private var loc

    /// The coordinate the new spot will be created at (tap point / map center),
    /// plus any adopt-prefill (Phase 4).
    let pending: PendingSpot
    /// Called with the created spot's id after it is persisted, so the presenter
    /// can refresh its spot list and dismiss.
    let onCreated: (String) -> Void
    /// Cancel/close without creating anything.
    let onClose: () -> Void

    /// Fixed category labels from the shared bridge (single source of truth).
    private let categories: [String] = FeedData.categories()

    @State private var name: String
    @State private var selectedCategory: String?

    /// Seeds the editable `@State` from the pending spot's prefill (Phase 4).
    /// An empty `prefillName` leaves the name field blank; a `prefillCategory`
    /// that isn't one of the shared categories (defensive) is ignored. Done in
    /// `init` via `_state = State(initialValue:)` so the seed is in place before
    /// the first render (no `onAppear` flicker).
    init(
        pending: PendingSpot,
        onCreated: @escaping (String) -> Void,
        onClose: @escaping () -> Void
    ) {
        self.pending = pending
        self.onCreated = onCreated
        self.onClose = onClose
        _name = State(initialValue: pending.prefillName)
        let prefillCategory = pending.prefillCategory
        let seededCategory = FeedData.categories().contains(prefillCategory) ? prefillCategory : nil
        _selectedCategory = State(initialValue: seededCategory)
    }

    private var canCreate: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        ScrollView {
            SheetScaffold(title: loc.strings.addSpotTitle, onClose: onClose) {
                VStack(alignment: .leading, spacing: AppDimens.spacingXL) {
                    subtitle
                    nameSection
                    categorySection
                    locationSection
                    createButton
                }
            }
        }
        .background(AppColor.background)
    }

    // MARK: Subtitle

    private var subtitle: some View {
        Text(loc.strings.addSpotSubtitle)
            .font(.system(size: AppTypography.body))
            .foregroundColor(AppColor.mutedForeground)
            .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: Name field

    private var nameSection: some View {
        VStack(alignment: .leading, spacing: AppDimens.spacingMD) {
            Text(loc.strings.addSpotNameLabel)
                .font(.system(size: AppTypography.footnote, weight: .semibold))
                .foregroundColor(AppColor.foreground)

            TextField(loc.strings.addSpotNamePlaceholder, text: $name)
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.foreground)
                .padding(.horizontal, AppDimens.spacingMD)
                .padding(.vertical, AppDimens.spacingMD)
                .background(
                    RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                        .fill(AppColor.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                        .stroke(AppColor.border, lineWidth: AppDimens.hairline)
                )
        }
    }

    // MARK: Category selector

    private var categorySection: some View {
        VStack(alignment: .leading, spacing: AppDimens.spacingMD) {
            Text(loc.strings.addSpotCategoryLabel)
                .font(.system(size: AppTypography.footnote, weight: .semibold))
                .foregroundColor(AppColor.foreground)

            // Wrapping rows of selectable pills. The pill labels + the selected
            // pill's fill color come from the shared module, not Swift.
            FlowLayout(spacing: AppDimens.spacingSM) {
                ForEach(categories, id: \.self) { category in
                    categoryPill(category)
                }
            }
        }
    }

    private func categoryPill(_ category: String) -> some View {
        let active = selectedCategory == category
        // Selected pills are tinted with the category's own shared marker color
        // (resolved shared-side); unselected pills use the frosted surface look
        // from `FilterChipView`.
        let activeColor = Color(hex: FeedData.categoryColorHex(category))
        return Button {
            selectedCategory = active ? nil : category
        } label: {
            Text(category)
                .font(.system(size: AppTypography.footnote, weight: .medium))
                .foregroundColor(active ? .white : AppColor.foreground)
                .padding(.horizontal, AppDimens.spacingLG)
                .frame(height: 30)
                .background(
                    Capsule().fill(active ? activeColor : AppColor.frost)
                )
                .overlay(
                    Capsule().stroke(active ? Color.clear : AppColor.border, lineWidth: AppDimens.hairline)
                )
        }
        .buttonStyle(.plain)
    }

    // MARK: Location (read-only)

    private var locationSection: some View {
        VStack(alignment: .leading, spacing: AppDimens.spacingXS) {
            Text(loc.strings.addSpotLocationLabel)
                .font(.system(size: AppTypography.footnote, weight: .semibold))
                .foregroundColor(AppColor.foreground)

            HStack(spacing: AppDimens.spacingSM) {
                Image(systemName: "mappin.and.ellipse")
                    .font(.system(size: AppTypography.body))
                    .foregroundColor(AppColor.brand)
                Text(coordinateLabel)
                    .font(.system(size: AppTypography.caption))
                    .foregroundColor(AppColor.mutedForeground)
            }
        }
    }

    /// Pending coordinate formatted to ~5 decimals as "lat, lng".
    private var coordinateLabel: String {
        let lat = String(format: "%.5f", pending.coordinate.lat)
        let lng = String(format: "%.5f", pending.coordinate.lng)
        return "\(lat), \(lng)"
    }

    // MARK: Create button

    private var createButton: some View {
        SheetActionButton(
            icon: "mappin.circle.fill",
            label: loc.strings.addSpotCreate,
            style: .primary,
            action: create
        )
        .opacity(canCreate ? 1 : 0.5)
        .disabled(!canCreate)
    }

    // MARK: Actions

    private func create() {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        // Swift-side id generation (documented): a UUID string keeps id creation
        // in one place and avoids a shared-side generator. Default the category
        // to the first shared category when none was picked (neighborhood stays
        // empty — no reverse geocoding yet).
        let id = "local-spot-\(UUID().uuidString)"
        let category = selectedCategory ?? categories.first ?? ""
        FeedData.addSpot(
            id: id,
            name: trimmed,
            category: category,
            neighborhood: "",
            lat: pending.coordinate.lat,
            lng: pending.coordinate.lng
        )
        onCreated(id)
    }
}

// MARK: - Flow layout

/// A minimal wrapping layout: lays children left-to-right, wrapping to the next
/// line when the current one is full. Used for the category pill grid so pills
/// reflow instead of overflowing. Pure layout (iOS 16+ `Layout`); no tokens
/// baked in beyond the caller-supplied `spacing`.
struct FlowLayout: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var rowWidth: CGFloat = 0
        var rowHeight: CGFloat = 0
        var totalHeight: CGFloat = 0
        var totalWidth: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if rowWidth + size.width > maxWidth, rowWidth > 0 {
                totalHeight += rowHeight + spacing
                totalWidth = max(totalWidth, rowWidth - spacing)
                rowWidth = 0
                rowHeight = 0
            }
            rowWidth += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        totalHeight += rowHeight
        totalWidth = max(totalWidth, rowWidth - spacing)
        return CGSize(width: min(totalWidth, maxWidth), height: totalHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        let maxWidth = bounds.width
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > bounds.minX + maxWidth, x > bounds.minX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), anchor: .topLeading, proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
