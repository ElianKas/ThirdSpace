import SwiftUI

/// Native SwiftUI collection-detail page — a PUSHED sub-page to manage ONE
/// saved-spot collection (Phase C6).
///
/// Navigation: this is a PUSH destination (not a sheet). It is reached from the
/// collection rows in `ProfileView`'s Collections tab, inside the Profile tab's
/// `NavigationStack`. Deleting the collection pops back to the profile via the
/// injected `\.dismiss`.
///
/// Data flows entirely through the shared collections seam via the
/// `@Observable` `CollectionsStore`: on appear it calls
/// `loadSpots(inCollection:)` for this `collectionId` and renders the store's
/// `spotsInViewedCollection` array. The store owns the remote refresh + the
/// live re-read via `observeCollections`, so this view just reads + renders and
/// forwards the three management actions:
///
///   * REMOVE a spot — a destructive swipe action per row calls
///     `store.removeSpot(collectionId:spotId:)` (optimistic; the observable
///     spots list drops the row).
///   * RENAME the collection — a toolbar affordance presents an `.alert` with a
///     `TextField` seeded with the current name → `store.rename(...)`.
///   * DELETE the collection — a toolbar affordance presents a
///     `.confirmationDialog` → `store.delete(...)`; on completion we `dismiss()`
///     to pop back to the profile tab (optimistic: the store removed it
///     locally regardless of the remote outcome).
///
/// `store.spotsInViewedCollection` carries the spots of the VIEWED collection
/// (the bridge's `loadSpotsInCollection` / `spotsInCollection` pure read). Live
/// collection data is BLOCKED-UNTIL-SESSION: the list is empty offline /
/// pre-login (the shared refresh is a no-op without the user's keys + the
/// collections migration + a live session), and all writes report `false`
/// (optimistic state kept, NO durable queue). The UI then shows the
/// `emptyCollectionSpots` state without crashing.
///
/// Styling uses only `AppColor` / `AppDimens` / `AppTypography` tokens (no
/// hardcoded hex / magic numbers). All static UI text comes from
/// `loc.strings.*`; the collection name + spot name/category/neighborhood are
/// dynamic content (shown verbatim, not translated).
struct CollectionDetailView: View {
    @Environment(CollectionsStore.self) private var store
    @Environment(Localization.self) private var loc
    @Environment(\.dismiss) private var dismiss

    /// The collection being managed. Flat String id crossing nothing else.
    let collectionId: String

    /// The collection's current name, used as the (dynamic, verbatim) nav title
    /// and to seed the rename field.
    let collectionName: String

    /// Rename alert presentation + its draft text (seeded with `collectionName`).
    @State private var isRenaming = false
    @State private var draftName = ""

    /// Delete confirmation presentation.
    @State private var showDeleteConfirm = false

    /// The spots of this collection, read live from the observable store so a
    /// background refresh / optimistic remove re-renders the list.
    private var spots: [UISpot] { store.spotsInViewedCollection }

    var body: some View {
        Group {
            if spots.isEmpty {
                emptyState
            } else {
                list
            }
        }
        .background(AppColor.background)
        .navigationTitle(collectionName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { toolbarContent }
        .onAppear { store.loadSpots(inCollection: collectionId) }
        // Rename: an alert with a text field seeded with the current name.
        .alert(loc.strings.renameCollection, isPresented: $isRenaming) {
            TextField(loc.strings.collectionNamePlaceholder, text: $draftName)
            Button(loc.strings.save, action: performRename)
            // No explicit cancel button: SwiftUI auto-inserts an OS-localized
            // Cancel for `.alert`/`.confirmationDialog` when none is provided,
            // so there is no hardcoded Swift "Cancel" string (and no untranslated
            // key — there is no generic `cancel` key in the shared i18n table).
        }
        // Delete: a destructive confirmation dialog; on confirm we dismiss to
        // pop back to the profile tab (optimistic local removal in the store).
        // The system adds its own OS-localized Cancel button automatically.
        .confirmationDialog(
            loc.strings.deleteCollection,
            isPresented: $showDeleteConfirm,
            titleVisibility: .visible
        ) {
            Button(loc.strings.deleteCollection, role: .destructive, action: performDelete)
        }
    }

    // MARK: Toolbar (rename + delete)

    /// Two trailing toolbar buttons — SF Symbols + `loc.strings` labels, tokens
    /// only. Rename opens the alert (seeding the draft with the current name);
    /// delete opens the confirmation dialog.
    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItemGroup(placement: .topBarTrailing) {
            Button {
                draftName = collectionName
                isRenaming = true
            } label: {
                Label(loc.strings.renameCollection, systemImage: "pencil")
            }

            Button(role: .destructive) {
                showDeleteConfirm = true
            } label: {
                Label(loc.strings.deleteCollection, systemImage: "trash")
            }
        }
    }

    // MARK: List

    private var list: some View {
        List {
            ForEach(spots) { spot in
                CollectionSpotRow(spot: spot)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(AppColor.background)
                    .listRowSeparatorTint(AppColor.hairline)
                    .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                        Button(role: .destructive) {
                            remove(spot)
                        } label: {
                            Label(loc.strings.removeFromCollection, systemImage: "trash")
                        }
                    }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(AppColor.background)
    }

    // MARK: Empty state

    private var emptyState: some View {
        VStack(spacing: AppDimens.spacingMD) {
            Image(systemName: "bookmark")
                .font(.system(size: AppTypography.headline))
                .foregroundColor(AppColor.textFaint)
            Text(loc.strings.emptyCollectionSpots)
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.mutedForeground)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, AppDimens.spacingXXL)
        .padding(.top, AppDimens.spacingXXL)
    }

    // MARK: Actions

    /// Remove `spot` from this collection (optimistic; the observable spots list
    /// drops the row). Live persistence is BLOCKED-until-session.
    private func remove(_ spot: UISpot) {
        store.removeSpot(collectionId: collectionId, spotId: spot.id) { _ in }
    }

    /// Rename this collection to the trimmed draft (optimistic + best-effort).
    private func performRename() {
        let name = draftName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        store.rename(collectionId: collectionId, name: name) { _ in }
    }

    /// Delete this collection and pop back to the profile tab. The store removes
    /// it locally optimistically regardless of the remote outcome, so we dismiss
    /// as soon as the completion fires.
    private func performDelete() {
        store.delete(collectionId: collectionId) { _ in
            dismiss()
        }
    }
}

// MARK: - Private subviews

/// A simple spot row for the collection-detail list: color swatch + name +
/// "category · neighborhood". A LOCAL equivalent of `ProfileView`'s file-private
/// `ProfileSpotRow` (intentionally not widened); tokens only. Name / category /
/// neighborhood are dynamic content, interpolated verbatim (NOT translated).
private struct CollectionSpotRow: View {
    let spot: UISpot

    var body: some View {
        HStack(spacing: AppDimens.spacingMD) {
            RoundedRectangle(cornerRadius: AppDimens.radiusSM)
                .fill(Color(hex: spot.colorHex))
                .frame(width: 44, height: 44)

            VStack(alignment: .leading, spacing: AppDimens.spacingXS) {
                Text(spot.name)
                    .font(.system(size: AppTypography.body, weight: .semibold))
                    .foregroundColor(AppColor.foreground)
                Text("\(spot.category) · \(spot.neighborhood)")
                    .font(.system(size: AppTypography.footnote))
                    .foregroundColor(AppColor.mutedForeground)
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, AppDimens.spacingXL)
        .padding(.vertical, AppDimens.spacingMD)
        .contentShape(Rectangle())
    }
}
