import SwiftUI

/// Native SwiftUI "create post" sheet ("Ich war hier").
///
/// Presented from the feed header's compose button (the presenter sets
/// `.presentationDetents([.large])` + `.presentationDragIndicator(.visible)`).
///
/// Sections, top to bottom (via `SheetScaffold`):
/// - Title "Ich war hier" + subtitle.
/// - Spot selector: a selectable list derived from the existing posts
///   (distinct by spot name — NO new bridge method). Selecting sets local
///   `@State`; the selection can be cleared. A simple search field filters it.
/// - Caption `TextEditor` (local state, with a placeholder).
/// - A photo PLACEHOLDER tile ("Foto hinzufügen") — no-op, no real PHPicker.
/// - A visibility toggle (Öffentlich / Freunde) — local, visual only.
/// - "Posten" button — disabled until a spot is selected; action dismisses.
///
/// Everything is visual/local: no upload, no persistence, no bridge write.
/// Styling uses only `AppColor` tokens and the hoisted shared components.
struct CreatePostView: View {
    @Environment(Localization.self) private var loc
    /// Optional close handler from the presenter. Falls back to the environment
    /// dismiss so the view also works when presented without an explicit one.
    var onClose: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss

    // Derived, distinct spot options (by name) from the existing feed posts.
    private let spots: [SpotOption] = CreatePostView.distinctSpots()

    @State private var selectedSpot: SpotOption?
    @State private var searchText: String = ""
    @State private var caption: String = ""
    @State private var visibility: Visibility = .publicEveryone

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
        SheetActionButton(
            icon: "paperplane.fill",
            label: loc.strings.createPost,
            style: .primary,
            action: closeAction
        )
        .opacity(selectedSpot == nil ? 0.5 : 1)
        .disabled(selectedSpot == nil)
    }

    // MARK: Helpers

    private func closeAction() {
        if let onClose {
            onClose()
        } else {
            dismiss()
        }
    }

    /// Distinct spot options derived from the existing feed posts (by name).
    /// No new bridge method — derived purely from `UIPost` fields.
    private static func distinctSpots() -> [SpotOption] {
        var seen = Set<String>()
        var result: [SpotOption] = []
        for post in FeedData.posts() where seen.insert(post.spotName).inserted {
            result.append(
                SpotOption(
                    name: post.spotName,
                    category: post.category,
                    neighborhood: post.neighborhood
                )
            )
        }
        return result
    }
}

// MARK: - Supporting types

extension CreatePostView {
    /// Local visibility choice (visual only).
    enum Visibility { case publicEveryone, friends }

    /// A selectable spot derived from an existing post's spot fields.
    struct SpotOption: Identifiable, Equatable {
        let name: String
        let category: String
        let neighborhood: String
        var id: String { name }
    }
}
