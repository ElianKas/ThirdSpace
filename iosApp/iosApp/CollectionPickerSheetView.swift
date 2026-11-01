import SwiftUI

/// Native SwiftUI "save to collection" picker (Phase C5).
///
/// Presented as a NESTED sheet from `MapSpotSheetView` (which is itself the map
/// pin-tap sheet). It lets the user assign/unassign the tapped spot to their
/// collections, and create a new collection inline.
///
/// STATE — reads the app-wide `CollectionsStore` from the environment
/// (`@Environment(CollectionsStore.self)`), the single owner of the collection
/// list + the observable membership map. On appear it triggers an explicit
/// `loadCollections()` + `loadMembership(spotId:)` (both seed synchronously from
/// the pure bridge cache, then refresh in a main-thread completion). Row
/// checkmarks read `store.isSpotInCollection(...)`, which reads the OBSERVABLE
/// membership map, so a toggle re-renders the checkmark live (optimistic).
///
/// TOGGLE — tapping a collection row toggles membership via the store's
/// optimistic `addSpot` / `removeSpot`; the observable membership drives the
/// checkmark, so no local mirror state is needed. Live PERSISTENCE is
/// BLOCKED-until-session (needs the user's keys + the collections migration + a
/// login); offline/unauth keeps the optimistic state but the bridge reports
/// `false` (no durable queue) — the picker does not surface that here.
///
/// CREATE — an inline "new collection" affordance (text field + button) calls
/// `store.create(name:)`. On success the new collection appears in the list (via
/// `observeCollections`), ready to be TAPPED to add the spot. Auto-adding to the
/// freshly created collection would be racy (its server id isn't known until the
/// list refresh resolves), so we keep it simple + correct: create the
/// collection, clear the field, and let the user tap the new row to add the
/// spot.
///
/// Styling uses only `AppColor` / `AppDimens` / `AppTypography` tokens (no
/// hardcoded hex / magic numbers). All static text comes from `loc.strings.*`;
/// collection names are dynamic content (shown verbatim, not translated).
struct CollectionPickerSheetView: View {
    @Environment(CollectionsStore.self) private var store
    @Environment(Localization.self) private var loc

    /// The spot being assigned/unassigned. Flat String id crossing nothing else.
    let spotId: String

    /// Whether this spot can actually be saved — i.e. its id is a server uuid.
    /// A demo/offline-seed spot (or any not-yet-persisted local spot) has a
    /// non-uuid id, which the shared `CollectionRepository.addSpot` guard
    /// rejects as a no-op (it could never be a legal `collection_spots.spot_id`
    /// uuid FK). We mirror that shared rule via the flat
    /// `FeedData.isSavableSpotId(spotId:)` helper (single source of truth) so
    /// the picker can explain why, rather than letting a tap silently fail.
    private var isSavable: Bool { FeedData.isSavableSpotId(spotId: spotId) }

    /// Inline new-collection name field.
    @State private var newName = ""

    /// Guards the create button against re-entrancy while a create is in flight.
    @State private var creating = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: AppDimens.spacingLG) {
                header
                if !isSavable {
                    notSaveableNote
                }
                createRow
                if store.collections.isEmpty {
                    emptyState
                } else {
                    collectionList
                }
            }
            .padding(.horizontal, AppDimens.spacingLG)
            .padding(.top, AppDimens.spacingXL)
            .padding(.bottom, AppDimens.spacingXL)
        }
        .background(AppColor.background)
        .onAppear {
            store.loadCollections()
            store.loadMembership(spotId: spotId)
        }
    }

    // MARK: Header

    private var header: some View {
        Text(loc.strings.saveToCollection)
            .font(.system(size: AppTypography.titleLarge, weight: .bold))
            .foregroundColor(AppColor.foreground)
    }

    // MARK: Not-saveable note

    /// Shown when the spot's id is not a server uuid (a demo/offline-seed or a
    /// not-yet-persisted local spot). Explains why saving is unavailable instead
    /// of letting a tap silently no-op. The collection rows + create row are
    /// disabled while this is shown.
    private var notSaveableNote: some View {
        HStack(spacing: AppDimens.spacingSM) {
            Image(systemName: "info.circle")
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.mutedForeground)
            Text(loc.strings.collectionSpotNotSaveable)
                .font(.system(size: AppTypography.footnote))
                .foregroundColor(AppColor.mutedForeground)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, AppDimens.spacingMD)
        .padding(.vertical, AppDimens.spacingSM)
        .background(
            RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                .fill(AppColor.surface)
        )
    }

    // MARK: Inline create row

    /// A text field (`collectionNamePlaceholder`) + a create button
    /// (`createCollection`). The button is disabled while empty/whitespace or a
    /// create is in flight. On success the field is cleared.
    private var createRow: some View {
        HStack(spacing: AppDimens.spacingSM) {
            AppTextField(placeholder: loc.strings.collectionNamePlaceholder, text: $newName)
                .textInputAutocapitalization(.words)
                .autocorrectionDisabled()

            // Shared primary button (hugging, not full-width) — the old
            // fixed `height: 44` + fill-swap-to-`mutedForeground` drift is gone;
            // it now matches the field height via the standard vertical padding
            // and dims via the ONE disabled convention when `!canCreate`.
            AppButton(
                label: loc.strings.createCollection,
                style: .primary,
                fullWidth: false,
                isEnabled: canCreate,
                action: create
            )
        }
    }

    /// Whether the create button is enabled: a non-blank name and no in-flight
    /// create.
    private var canCreate: Bool {
        !creating && !newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func create() {
        let name = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, !creating else { return }
        creating = true
        store.create(name: name) { _ in
            // The list already updated optimistically via observeCollections /
            // the store's re-read. Clear the field regardless of the remote
            // outcome (the optimistic row is kept), and let the user tap the new
            // row to add the spot.
            newName = ""
            creating = false
        }
    }

    // MARK: Collection list

    private var collectionList: some View {
        VStack(spacing: AppDimens.spacingSM) {
            ForEach(store.collections) { collection in
                collectionRow(collection)
            }
        }
    }

    /// A single tappable collection row: name + localized spot count on the
    /// left, a trailing checkmark when the spot is already in it. Tapping
    /// toggles membership via the store's optimistic add/remove.
    private func collectionRow(_ collection: UISpotCollection) -> some View {
        let isIn = store.isSpotInCollection(collectionId: collection.id, spotId: spotId)
        return Button(action: { toggle(collection, isIn: isIn) }) {
            HStack(spacing: AppDimens.spacingMD) {
                VStack(alignment: .leading, spacing: AppDimens.spacingXS) {
                    Text(collection.name)
                        .font(.system(size: AppTypography.body, weight: .semibold))
                        .foregroundColor(AppColor.foreground)
                    Text(loc.strings.collectionSpotCount(count: Int32(collection.spotCount)))
                        .font(.system(size: AppTypography.footnote))
                        .foregroundColor(AppColor.mutedForeground)
                }

                Spacer(minLength: 0)

                Image(systemName: isIn ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: AppTypography.title))
                    .foregroundColor(isIn ? AppColor.brand : AppColor.mutedForeground)
            }
            .padding(.horizontal, AppDimens.spacingLG)
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
        .buttonStyle(.plain)
        // A non-saveable spot (non-uuid id) can't be added to a collection — the
        // shared guard would reject it — so disable + dim the toggle and let the
        // `notSaveableNote` explain why.
        .disabled(!isSavable)
        .opacity(isSavable ? 1 : 0.5)
    }

    /// Toggle the spot's membership in `collection` — optimistic; the observable
    /// membership map drives the checkmark re-render, so no local state is kept.
    private func toggle(_ collection: UISpotCollection, isIn: Bool) {
        if isIn {
            store.removeSpot(collectionId: collection.id, spotId: spotId) { _ in }
        } else {
            store.addSpot(collectionId: collection.id, spotId: spotId) { _ in }
        }
    }

    // MARK: Empty state

    private var emptyState: some View {
        EmptyStateView(systemImage: "folder", text: loc.strings.emptyCollections)
    }
}
