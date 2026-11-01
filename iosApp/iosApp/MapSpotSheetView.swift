import SwiftUI

/// Lean native SwiftUI spot detail sheet for a map pin tap (Phase C5).
///
/// Presented from `MapBaseView` when one of the app's OWN spot pins is tapped
/// on the Mapbox map (`MapboxMapView.onSpotTap` → `selectedSpot` →
/// `.sheet(item:)`). It renders a single `UISpot`'s detail, top to bottom: a
/// colored placeholder photo area, a category badge (+ verified seal), the spot
/// title, a neighborhood meta row, a placeholder description, and ONE prominent
/// "In Sammlung speichern" action that opens the nested
/// `CollectionPickerSheetView` to assign/unassign the spot to the user's
/// collections.
///
/// SCOPE — this is DELIBERATELY lean. Unlike the post-bound `SpotSheetView`
/// (which takes a `UIPost`), there is NO vote control, comment button, report
/// button, share control, or "posts about this spot" section here: those are
/// post-bound concerns and live in `SpotSheetView`. This sheet is bound to a
/// bare `UISpot` (a map pin), so its only action is saving the spot into a
/// collection. `SpotSheetView` is intentionally NOT generalized/reused.
///
/// It follows the project sheet pattern — the call site (`MapBaseView`) sets
/// `.presentationDetents([.medium, .large])` + `.presentationDragIndicator`.
/// The nested picker follows the same pattern. Styling reuses `CategoryPill` /
/// `SheetWideButton` from `SheetComponents.swift` and only `AppColor` /
/// `AppDimens` / `AppTypography` tokens (no hardcoded hex / magic numbers). All
/// static text comes from `loc.strings.*`; the spot name / category /
/// neighborhood are dynamic content (shown verbatim, not translated).
struct MapSpotSheetView: View {
    @Environment(Localization.self) private var loc
    let spot: UISpot

    /// Drives the nested collection picker sheet. Presenting a sheet from within
    /// a sheet is intended here — the "save to collection" button opens the
    /// picker, mirroring how `SpotSheetView` opens its comments sub-sheet.
    @State private var showPicker = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: AppDimens.spacingLG) {
                photo
                badgeRow
                title
                metaRow
                description
                saveButton
            }
            .padding(.horizontal, AppDimens.spacingLG)
            // Top padding so the content sits below the sheet's drag indicator.
            .padding(.top, AppDimens.spacingXL)
            .padding(.bottom, AppDimens.spacingXL)
        }
        .background(AppColor.background)
        // Nested collection picker. Follows the project sheet pattern: the
        // presenter sets detents + drag indicator.
        .sheet(isPresented: $showPicker) {
            CollectionPickerSheetView(spotId: spot.id)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
    }

    // MARK: 1. Placeholder photo

    /// Colored placeholder photo area. The fill is the spot's shared category
    /// color (`colorHex` via `Color(hex:)`) — no category hex literal is
    /// hardcoded Swift-side. The spot model has no photo strip yet.
    private var photo: some View {
        Color(hex: spot.colorHex)
            .aspectRatio(4.0 / 3.0, contentMode: .fit)
            .frame(maxWidth: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous))
    }

    // MARK: 2. Badge row

    private var badgeRow: some View {
        HStack(spacing: AppDimens.spacingSM) {
            CategoryPill(label: spot.category)
            if spot.verified {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: AppTypography.title))
                    .foregroundColor(AppColor.secondary)
            }
            Spacer(minLength: 0)
        }
    }

    // MARK: 3. Title

    private var title: some View {
        HStack(spacing: AppDimens.spacingSM) {
            Text(spot.name)
                .font(.system(size: AppTypography.titleLarge, weight: .bold))
                .foregroundColor(AppColor.foreground)
                .fixedSize(horizontal: false, vertical: true)
            if spot.verified {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: AppTypography.body))
                    .foregroundColor(AppColor.secondary)
            }
        }
    }

    // MARK: 4. Meta row (neighborhood)

    private var metaRow: some View {
        HStack(spacing: AppDimens.spacingSM) {
            Image(systemName: "mappin")
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.brand)
            Text(spot.neighborhood)
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.mutedForeground)
            Spacer(minLength: 0)
        }
    }

    // MARK: 5. Description (placeholder)

    // Placeholder: UISpot has no description field yet.
    private var description: some View {
        Text(loc.strings.spotNoDescription)
            .font(.system(size: AppTypography.body))
            .foregroundColor(AppColor.mutedForeground)
            .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: 6. Save-to-collection action

    /// The ONE prominent action on this lean sheet: open the collection picker.
    private var saveButton: some View {
        SheetWideButton(
            icon: "bookmark",
            label: loc.strings.saveToCollection,
            action: { showPicker = true }
        )
    }
}
