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
struct CategoryPill: View {
    let label: String

    var body: some View {
        Text(label)
            .font(.system(size: 12, weight: .medium))
            .foregroundColor(AppColor.foreground)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(
                Capsule().fill(AppColor.surface)
            )
            .overlay(
                Capsule().stroke(AppColor.border, lineWidth: 1)
            )
    }
}

// MARK: - Action row button

/// A compact action-row button. `.bordered` renders a surface fill with a
/// hairline border; `.primary` renders a filled brand button with white text.
struct SheetActionButton: View {
    enum Style { case bordered, primary }

    let icon: String
    let label: String
    let style: Style
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .semibold))
                Text(label)
                    .font(.system(size: 13, weight: .semibold))
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .foregroundColor(style == .primary ? .white : AppColor.foreground)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(style == .primary ? AppColor.brand : AppColor.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(style == .primary ? Color.clear : AppColor.border, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Full-width bordered button

/// A full-width bordered button used for the comment and share actions.
struct SheetWideButton: View {
    let icon: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 15, weight: .semibold))
                Text(label)
                    .font(.system(size: 14, weight: .semibold))
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .padding(.horizontal, 14)
            .foregroundColor(AppColor.foreground)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(AppColor.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(AppColor.border, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Comment row

/// A single comment: round avatar + "Name · time" header + comment text.
/// Reuses `AvatarView` from `PostCardView`. Styling matches the app's
/// attribution look and uses only `AppColor` tokens.
struct CommentRow: View {
    let comment: UIComment

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            AvatarView(name: comment.authorName, size: 32)

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 0) {
                    Text(comment.authorName)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(AppColor.foreground)
                    Text(" · \(comment.timeLabel)")
                        .font(.system(size: 13))
                        .foregroundColor(AppColor.textFaint)
                }
                Text(comment.text)
                    .font(.system(size: 14))
                    .foregroundColor(AppColor.bodyText)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 0)
        }
    }
}
