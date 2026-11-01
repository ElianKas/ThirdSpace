import SwiftUI

/// Native SwiftUI "create post" sheet ("Ich war hier").
///
/// Presented from the feed header's compose button (the presenter sets
/// `.presentationDetents([.large])` + `.presentationDragIndicator(.visible)`).
///
/// Sections, top to bottom (via `SheetScaffold`):
/// - Title "Ich war hier" + subtitle.
/// - Spot selector: a selectable list sourced from the REAL spots
///   (`FeedData.spots()`), each carrying the real spot `id` so creation passes
///   a real `spot_id`. Selecting sets local `@State`; the selection can be
///   cleared. A simple search field filters it by name.
/// - Caption `TextEditor` (local state, with a placeholder).
/// - A photo PLACEHOLDER tile ("Foto hinzufügen") — no-op, no real PHPicker.
/// - A visibility toggle (Öffentlich / Freunde) — local, visual only.
/// - "Posten" button — disabled until a spot is selected (empty caption IS
///   allowed, a pure check-in); on tap it persists a real post via
///   `FeedData.createPost` and then dismisses.
///
/// Post creation goes through the flat `FeedData.createPost` hook (optimistic
/// write-through + offline queue shared-side). The photo placeholder and the
/// visibility toggle remain visual-only/inert — the shared `createPost` takes
/// only `spotId` + `text` (the posts table has no photo/visibility column).
/// Styling uses only `AppColor` tokens and the hoisted shared components.
struct CreatePostView: View {
    @Environment(Localization.self) private var loc
    /// Optional close handler from the presenter. Falls back to the environment
    /// dismiss so the view also works when presented without an explicit one.
    var onClose: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss

    // The REAL spots (with real ids) from the shared bridge, used as the
    // picker source so creation can pass a real spot_id.
    private let spots: [SpotOption] = CreatePostView.realSpots()

    @State private var selectedSpot: SpotOption?

    /// Two presentations:
    /// - Feed header compose button: `CreatePostView()` — no preselect, starts
    ///   with the spot picker shown.
    /// - Map "Posten" prompt (Phase 3): `CreatePostView(preselectedSpotId:)` —
    ///   seeds `selectedSpot` with the just-created spot so the sheet opens with
    ///   that spot already chosen.
    ///
    /// Mirrors `AddSpotSheetView`'s `_state = State(initialValue:)` pattern:
    /// because `selectedSpot` is a `@State` with no inline initializer, this
    /// custom init seeds its backing store. `onClose` is folded in with a
    /// default so the no-arg feed-header call site stays identical.
    init(onClose: (() -> Void)? = nil, preselectedSpotId: String? = nil) {
        self.onClose = onClose
        if let preselectedSpotId {
            let preselected = CreatePostView.realSpots().first { $0.id == preselectedSpotId }
            _selectedSpot = State(initialValue: preselected)
        } else {
            _selectedSpot = State(initialValue: nil)
        }
    }

    @State private var searchText: String = ""
    @State private var caption: String = ""
    @State private var visibility: Visibility = .publicEveryone
    /// Brief working state while the shared write-through runs, so a double-tap
    /// cannot fire two creates. The sheet dismisses in the completion.
    @State private var isPosting = false

    var body: some View {
        ScrollView {
            SheetScaffold(title: loc.strings.createTitle, onClose: closeAction) {
                VStack(alignment: .leading, spacing: AppDimens.spacingXL) {
                    subtitle
                    spotSection
                    captionSection
                    photoPlaceholder
                    visibilitySection
                    postButton
                }
            }
        }
        .background(AppColor.background)
    }

    // MARK: Subtitle

    private var subtitle: some View {
        Text(loc.strings.createSubtitle)
            .font(.system(size: AppTypography.body))
            .foregroundColor(AppColor.mutedForeground)
            .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: Spot selector

    private var filteredSpots: [SpotOption] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !query.isEmpty else { return spots }
        return spots.filter { $0.name.lowercased().contains(query) }
    }

    private var spotSection: some View {
        VStack(alignment: .leading, spacing: AppDimens.spacingMD) {
            Text(loc.strings.createSpotLabel)
                .font(.system(size: AppTypography.footnote, weight: .semibold))
                .foregroundColor(AppColor.foreground)

            if let selectedSpot {
                selectedSpotRow(selectedSpot)
            } else {
                searchField
                VStack(spacing: 0) {
                    ForEach(filteredSpots) { spot in
                        Button {
                            self.selectedSpot = spot
                        } label: {
                            spotPickRow(spot)
                        }
                        .buttonStyle(.plain)

                        if spot.id != filteredSpots.last?.id {
                            Rectangle()
                                .fill(AppColor.hairline)
                                .frame(height: AppDimens.hairline)
                        }
                    }
                }
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
    }

    private var searchField: some View {
        HStack(spacing: AppDimens.spacingSM) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.textFaint)
            TextField(loc.strings.createSpotSearch, text: $searchText)
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.foreground)
        }
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

    private func spotPickRow(_ spot: SpotOption) -> some View {
        HStack(spacing: AppDimens.spacingMD) {
            VStack(alignment: .leading, spacing: AppDimens.spacingXS) {
                Text(spot.name)
                    .font(.system(size: AppTypography.body, weight: .medium))
                    .foregroundColor(AppColor.foreground)
                Text(spot.neighborhood)
                    .font(.system(size: AppTypography.caption))
                    .foregroundColor(AppColor.mutedForeground)
            }
            Spacer(minLength: AppDimens.spacingSM)
            CategoryPill(label: spot.category)
        }
        .padding(.horizontal, AppDimens.spacingMD)
        .padding(.vertical, AppDimens.spacingMD)
        .contentShape(Rectangle())
    }

    private func selectedSpotRow(_ spot: SpotOption) -> some View {
        HStack(spacing: AppDimens.spacingMD) {
            Image(systemName: "mappin.circle.fill")
                .font(.system(size: AppTypography.title))
                .foregroundColor(AppColor.brand)
            VStack(alignment: .leading, spacing: AppDimens.spacingXS) {
                Text(spot.name)
                    .font(.system(size: AppTypography.body, weight: .semibold))
                    .foregroundColor(AppColor.foreground)
                Text("\(spot.category) · \(spot.neighborhood)")
                    .font(.system(size: AppTypography.caption))
                    .foregroundColor(AppColor.mutedForeground)
            }
            Spacer(minLength: AppDimens.spacingSM)
            Button {
                selectedSpot = nil
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: AppTypography.title))
                    .foregroundColor(AppColor.textFaint)
            }
            .buttonStyle(.plain)
        }
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

    // MARK: Caption

    private var captionSection: some View {
        VStack(alignment: .leading, spacing: AppDimens.spacingMD) {
            Text(loc.strings.createTextLabel)
                .font(.system(size: AppTypography.footnote, weight: .semibold))
                .foregroundColor(AppColor.foreground)

            ZStack(alignment: .topLeading) {
                if caption.isEmpty {
                    Text(loc.strings.createCaptionPlaceholder)
                        .font(.system(size: AppTypography.body))
                        .foregroundColor(AppColor.textFaint)
                        .padding(.horizontal, AppDimens.spacingLG)
                        .padding(.vertical, AppDimens.spacingMD)
                }
                TextEditor(text: $caption)
                    .font(.system(size: AppTypography.body))
                    .foregroundColor(AppColor.foreground)
                    .scrollContentBackground(.hidden)
                    .padding(.horizontal, AppDimens.spacingMD)
                    .padding(.vertical, AppDimens.spacingSM)
                    .frame(minHeight: 100)
            }
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

    // MARK: Photo placeholder (no-op)

    private var photoPlaceholder: some View {
        Button(action: {}) {
            HStack(spacing: AppDimens.spacingMD) {
                Image(systemName: "photo.badge.plus")
                    .font(.system(size: AppTypography.title))
                    .foregroundColor(AppColor.mutedForeground)
                Text(loc.strings.createAddPhoto)
                    .font(.system(size: AppTypography.body, weight: .medium))
                    .foregroundColor(AppColor.mutedForeground)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppDimens.spacingXL)
            .padding(.horizontal, AppDimens.spacingLG)
            .background(
                RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                    .fill(AppColor.page)
            )
            .overlay(
                RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                    .strokeBorder(
                        AppColor.border,
                        style: StrokeStyle(lineWidth: AppDimens.hairline, dash: [5])
                    )
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: Visibility toggle (local, visual)

    private var visibilitySection: some View {
        VStack(alignment: .leading, spacing: AppDimens.spacingMD) {
            Text(loc.strings.createVisibility)
                .font(.system(size: AppTypography.footnote, weight: .semibold))
                .foregroundColor(AppColor.foreground)

            HStack(spacing: AppDimens.spacingMD) {
                visibilityOption(.publicEveryone, icon: "globe", label: loc.strings.visibilityPublic)
                visibilityOption(.friends, icon: "person.2", label: loc.strings.visibilityFriends)
            }
        }
    }

    private func visibilityOption(_ value: Visibility, icon: String, label: String) -> some View {
        let active = visibility == value
        return Button {
            visibility = value
        } label: {
            HStack(spacing: AppDimens.spacingSM) {
                Image(systemName: icon)
                    .font(.system(size: AppTypography.body, weight: .semibold))
                Text(label)
                    .font(.system(size: AppTypography.footnote, weight: .semibold))
                if active {
                    Image(systemName: "checkmark")
                        .font(.system(size: AppTypography.caption, weight: .bold))
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppDimens.spacingMD)
            .foregroundColor(active ? .white : AppColor.foreground)
            .background(
                RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                    .fill(active ? AppColor.brand : AppColor.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: AppDimens.radiusMD, style: .continuous)
                    .stroke(active ? Color.clear : AppColor.border, lineWidth: AppDimens.hairline)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: Post button

    private var postButton: some View {
        // Disabled until a spot is selected (empty caption is allowed) and
        // while a create is already in flight.
        let disabled = selectedSpot == nil || isPosting
        return SheetActionButton(
            icon: "paperplane.fill",
            label: loc.strings.createPost,
            style: .primary,
            action: postAction
        )
        .opacity(disabled ? 0.5 : 1)
        .disabled(disabled)
    }

    // MARK: Helpers

    /// Persist a real post for the selected spot, then dismiss. The caption may
    /// be empty (pure check-in). Goes through the flat `FeedData.createPost`
    /// hook; on completion (main thread) we nudge the feed with `loadFeed` so
    /// the just-created optimistic post is picked up, then close. The feed's own
    /// `observeFeed` listener also re-reads, so there is no duplicate post.
    private func postAction() {
        guard let spot = selectedSpot, !isPosting else { return }
        isPosting = true
        let text = caption.trimmingCharacters(in: .whitespacesAndNewlines)
        FeedData.createPost(spotId: spot.id, text: text) {
            FeedData.loadFeed {
                closeAction()
            }
        }
    }

    private func closeAction() {
        if let onClose {
            onClose()
        } else {
            dismiss()
        }
    }

    /// Spot options sourced from the REAL shared spots (`FeedData.spots()`),
    /// carrying the real spot `id` so creation passes a real `spot_id`.
    private static func realSpots() -> [SpotOption] {
        FeedData.spots().map { spot in
            SpotOption(
                id: spot.id,
                name: spot.name,
                category: spot.category,
                neighborhood: spot.neighborhood
            )
        }
    }
}

// MARK: - Supporting types

extension CreatePostView {
    /// Local visibility choice (visual only).
    enum Visibility { case publicEveryone, friends }

    /// A selectable spot sourced from a real shared `UISpot`. `id` is the REAL
    /// spot id passed to `FeedData.createPost` as `spot_id`.
    struct SpotOption: Identifiable, Equatable {
        let id: String
        let name: String
        let category: String
        let neighborhood: String
    }
}
