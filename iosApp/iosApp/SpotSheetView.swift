import SwiftUI

/// Native SwiftUI Spot detail sheet (V1).
///
/// This is the first interactive surface in the native feed: tapping a
/// `PostCardView` in `NativeFeedView` presents this view as a sheet. It renders
/// a single spot's detail, top to bottom: a placeholder photo area, a category
/// badge (+ verified seal), the spot title, a meta + vote row, a placeholder
/// description, an action row (Route / Speichern / Ich war hier), and full-width
/// comment and share buttons.
///
/// REUSABLE SHEET PATTERN — all future detail/compose surfaces should follow the
/// same approach used by the call site in `NativeFeedView`:
///
///     .sheet(item: $selectedPost) { post in
///         SpotSheetView(post: post)
///             .presentationDetents([.medium, .large])
///             .presentationDragIndicator(.visible)
///     }
///
/// i.e. drive presentation from an `Identifiable` `@State` item, offer
/// `.medium` + `.large` detents, and show the system drag indicator. Future
/// sheets (CreatePost, Comments, AddSpot) should reuse this `.sheet(item:)` +
/// `.presentationDetents` pattern rather than inventing their own.
///
/// V1 is intentionally visual only: every action is a no-op (`action: {}`).
/// There is no vote/save/comment/route/share logic, no sub-sheets, no photo
/// strip, and no "posts about this spot" section.
struct SpotSheetView: View {
    let post: UIPost

    /// Drives the nested Comments sheet. Presenting a sheet from within a sheet
    /// is intended here — the comment button opens the comments list.
    @State private var showComments = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                photo
                badgeRow
                title
                metaAndVoteRow
                description
                actionRow
                commentButton
                shareButton
            }
            .padding(.horizontal, 16)
            // Top padding so the content sits below the sheet's drag indicator.
            .padding(.top, 20)
            .padding(.bottom, 24)
        }
        .background(AppColor.background)
        // Nested Comments sheet. Follows the project sheet pattern: the
        // presenter sets detents + drag indicator. `.large` fits a comment list.
        .sheet(isPresented: $showComments) {
            CommentsSheetView(post: post)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
    }

    // MARK: 1. Placeholder photo

    private var photo: some View {
        Color(hex: post.colorHex)
            .aspectRatio(4.0 / 3.0, contentMode: .fit)
            .frame(maxWidth: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    // MARK: 2. Badge row

    private var badgeRow: some View {
        HStack(spacing: 8) {
            CategoryPill(label: post.category)
            if post.verified {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 16))
                    .foregroundColor(AppColor.secondary)
            }
            Spacer(minLength: 0)
        }
    }

    // MARK: 3. Title

    private var title: some View {
        HStack(spacing: 6) {
            Text(post.spotName)
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(AppColor.foreground)
                .fixedSize(horizontal: false, vertical: true)
            if post.verified {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 15))
                    .foregroundColor(AppColor.secondary)
            }
        }
    }

    // MARK: 4. Meta + vote row

    private var metaAndVoteRow: some View {
        HStack(spacing: 6) {
            Image(systemName: "mappin")
                .font(.system(size: 15))
                .foregroundColor(AppColor.brand)
            Text(post.neighborhood)
                .font(.system(size: 14))
                .foregroundColor(AppColor.mutedForeground)

            Spacer()

            VoteControlView(score: post.score, voteState: post.voteState)
        }
    }

    // MARK: 5. Description (placeholder)

    // Placeholder: UIPost has no description field yet (V1).
    private var description: some View {
        Text("Noch keine Beschreibung vorhanden.")
            .font(.system(size: 14))
            .foregroundColor(AppColor.mutedForeground)
            .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: 6. Action row

    private var actionRow: some View {
        HStack(spacing: 10) {
            SheetActionButton(
                icon: "arrow.triangle.turn.up.right.diamond",
                label: "Route",
                style: .bordered,
                action: {}
            )
            SheetActionButton(
                icon: "bookmark",
                label: "Speichern",
                style: .bordered,
                action: {}
            )
            SheetActionButton(
                icon: "checkmark",
                label: "Ich war hier",
                style: .primary,
                action: {}
            )
            .layoutPriority(1) // primary gets a little extra width
        }
    }

    // MARK: 7. Comment button

    private var commentButton: some View {
        SheetWideButton(
            icon: "bubble.left",
            label: post.commentCount > 0
                ? "Alle \(post.commentCount) Kommentare anzeigen"
                : "Kommentar schreiben",
            action: { showComments = true }
        )
    }

    // MARK: 8. Share button

    private var shareButton: some View {
        SheetWideButton(
            icon: "square.and.arrow.up",
            label: "Teilen",
            action: {}
        )
    }
}

// The reusable sheet building blocks (CategoryPill, SheetActionButton,
// SheetWideButton) previously defined here have been hoisted to
// SheetComponents.swift so the Comments and CreatePost sheets can reuse them.
// SpotSheetView's visible appearance is unchanged — it now references the
// shared (internal) versions with identical styling.
