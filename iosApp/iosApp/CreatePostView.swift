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
                VStack(alignment: .leading, spacing: 20) {
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
            .font(.system(size: 14))
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
        VStack(alignment: .leading, spacing: 10) {
            Text(loc.strings.createSpotLabel)
                .font(.system(size: 13, weight: .semibold))
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
                                .frame(height: 1)
                        }
                    }
                }
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(AppColor.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(AppColor.border, lineWidth: 1)
                )
            }
        }
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 14))
                .foregroundColor(AppColor.textFaint)
            TextField(loc.strings.createSpotSearch, text: $searchText)
                .font(.system(size: 14))
                .foregroundColor(AppColor.foreground)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(AppColor.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .stroke(AppColor.border, lineWidth: 1)
        )
    }

    private func spotPickRow(_ spot: SpotOption) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 4) {
                Text(spot.name)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(AppColor.foreground)
                Text(spot.neighborhood)
                    .font(.system(size: 12))
                    .foregroundColor(AppColor.mutedForeground)
            }
            Spacer(minLength: 8)
            CategoryPill(label: spot.category)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    private func selectedSpotRow(_ spot: SpotOption) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "mappin.circle.fill")
                .font(.system(size: 18))
                .foregroundColor(AppColor.brand)
            VStack(alignment: .leading, spacing: 4) {
                Text(spot.name)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(AppColor.foreground)
                Text("\(spot.category) · \(spot.neighborhood)")
                    .font(.system(size: 12))
                    .foregroundColor(AppColor.mutedForeground)
            }
            Spacer(minLength: 8)
            Button {
                selectedSpot = nil
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 18))
                    .foregroundColor(AppColor.textFaint)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(AppColor.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(AppColor.border, lineWidth: 1)
        )
    }

    // MARK: Caption

    private var captionSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(loc.strings.createTextLabel)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(AppColor.foreground)

            ZStack(alignment: .topLeading) {
                if caption.isEmpty {
                    Text(loc.strings.createCaptionPlaceholder)
                        .font(.system(size: 14))
                        .foregroundColor(AppColor.textFaint)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                }
                TextEditor(text: $caption)
                    .font(.system(size: 14))
                    .foregroundColor(AppColor.foreground)
                    .scrollContentBackground(.hidden)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .frame(minHeight: 100)
            }
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(AppColor.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(AppColor.border, lineWidth: 1)
            )
        }
    }

    // MARK: Photo placeholder (no-op)

    private var photoPlaceholder: some View {
        Button(action: {}) {
            HStack(spacing: 10) {
                Image(systemName: "photo.badge.plus")
                    .font(.system(size: 18))
                    .foregroundColor(AppColor.mutedForeground)
                Text(loc.strings.createAddPhoto)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(AppColor.mutedForeground)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 20)
            .padding(.horizontal, 14)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(AppColor.page)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(
                        AppColor.border,
                        style: StrokeStyle(lineWidth: 1, dash: [5])
                    )
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: Visibility toggle (local, visual)

    private var visibilitySection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(loc.strings.createVisibility)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(AppColor.foreground)

            HStack(spacing: 10) {
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
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .semibold))
                Text(label)
                    .font(.system(size: 13, weight: .semibold))
                if active {
                    Image(systemName: "checkmark")
                        .font(.system(size: 12, weight: .bold))
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .foregroundColor(active ? .white : AppColor.foreground)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(active ? AppColor.brand : AppColor.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(active ? Color.clear : AppColor.border, lineWidth: 1)
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
