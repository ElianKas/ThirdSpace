import SwiftUI

/// Shared building blocks for the native sheets (SpotSheet, Comments, CreatePost).
///
/// These were originally private to `SpotSheetView`; they are hoisted here as
/// internal components so the Comments and CreatePost sheets can reuse the exact
/// same styling. Styling is unchanged from the original SpotSheet versions and
/// uses only `AppColor` tokens (no hardcoded hex literals).

// MARK: - Category pill

/// A small rounded badge showing the spot category. Subtle surface fill with a
/// hairline border and foreground text, consistent with the app's card look.
///
/// As of slice B3 this is a THIN WRAPPER over the shared `AppChip(.badge)`, so
/// its call sites (SpotSheetView / CreatePostView) keep working while the look
/// is unified with the map/AddSpot chips. The public name/signature are
/// unchanged.
struct CategoryPill: View {
    let label: String

    var body: some View {
        AppChip(label: label, style: .badge)
    }
}

// MARK: - Action row button

/// A compact action-row button. `.bordered` renders a surface fill with a
/// hairline border; `.primary` renders a filled brand button with white text.
///
/// As of slice B2 this is a THIN WRAPPER over the shared `AppButton`
/// (`.primary` → `AppButton(.primary)`, `.bordered` → `AppButton(.secondary)`),
/// so its many call sites keep working while the styling + the ONE disabled
/// convention are now unified with every other app button. The public
/// name/signature are unchanged.
struct SheetActionButton: View {
    enum Style { case bordered, primary }

    let icon: String
    let label: String
    let style: Style
    let action: () -> Void

    var body: some View {
        AppButton(
            label: label,
            style: style == .primary ? .primary : .secondary,
            systemImage: icon,
            action: action
        )
    }
}

// MARK: - Full-width bordered button

/// A full-width bordered button used for the comment and share actions.
///
/// As of slice B2 this is a THIN WRAPPER over `AppButton(.secondary)` with its
/// leading icon+label layout (full-width, so the content centers like the other
/// buttons). The public name/signature are unchanged.
struct SheetWideButton: View {
    let icon: String
    let label: String
    let action: () -> Void

    var body: some View {
        AppButton(
            label: label,
            style: .secondary,
            systemImage: icon,
            action: action
        )
    }
}

// MARK: - Comment row

/// A single comment: round avatar + "Name · time" header + comment text.
/// Reuses `AvatarView` from `PostCardView`. Styling matches the app's
/// attribution look and uses only `AppColor` tokens.
struct CommentRow: View {
    let comment: UIComment
    /// Phase F5: optional author-tap callback. `CommentsSheetView` passes a
    /// closure that presents `OtherProfileView` for the comment's author.
    /// Defaults to `nil` so any display-only call site / preview compiles
    /// unchanged; the author then renders as plain, non-tappable text. The
    /// avatar + name become a `Button` only when this is non-nil AND the author
    /// is tappable (known id, not self) — see `authorTappable`.
    var onAuthorTap: (() -> Void)? = nil

    /// Whether the author (avatar + name) is tappable. Suppressed for an unknown
    /// author id, the current user's own comment, or when no callback is wired.
    private var authorTappable: Bool {
        onAuthorTap != nil
            && !comment.authorUserId.isEmpty
            && comment.authorUserId != FeedData.currentUserId()
    }

    var body: some View {
        HStack(alignment: .top, spacing: AppDimens.spacingMD) {
            if authorTappable {
                Button(action: { onAuthorTap?() }) {
                    authorIdentity
                }
                .buttonStyle(.plain)
            } else {
                authorIdentity
            }

            Spacer(minLength: 0)
        }
    }

    /// Avatar + "Name · time" header + comment text. Shared between the tappable
    /// (Button) and non-tappable rendering so the look is identical.
    private var authorIdentity: some View {
        HStack(alignment: .top, spacing: AppDimens.spacingMD) {
            AvatarView(name: comment.authorName, size: 32)

            VStack(alignment: .leading, spacing: AppDimens.spacingXS) {
                HStack(spacing: 0) {
                    Text(comment.authorName)
                        .font(.system(size: AppTypography.footnote, weight: .semibold))
                        .foregroundColor(AppColor.foreground)
                    Text(" · \(comment.timeLabel)")
                        .font(.system(size: AppTypography.footnote))
                        .foregroundColor(AppColor.textFaint)
                }
                Text(comment.text)
                    .font(.system(size: AppTypography.body))
                    .foregroundColor(AppColor.bodyText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}
