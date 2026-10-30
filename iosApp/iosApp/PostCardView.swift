import SwiftUI

/// Native SwiftUI replication of the Compose `PostCard` layout.
///
/// Sections (top to bottom):
/// 1. Spot header: bold spot name + optional purple verified check, meta line,
///    right-side bookmark icon.
/// 2. Square 1:1 colored placeholder (no images).
/// 3. Attribution: round avatar with initial (orange bg) + "Name · date",
///    optional post text wrapped in „…".
/// 4. Action row: vote control, comment icon + count, right-aligned map pin +
///    neighborhood.
///
/// Visual parity with the Compose card is approximate (fonts/spacing).
struct PostCardView: View {
    @Environment(Localization.self) private var loc
    let post: UIPost
    /// Optional tap callback. Defaults to `nil` so existing call sites and
    /// previews compile unchanged; `NativeFeedView` passes a closure to present
    /// the spot detail sheet.
    var onTap: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            spotHeader
                .padding(.horizontal, 16)
                .padding(.top, 16)

            placeholder
                .padding(.top, 12)

            attributionRow
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

            actionRow
                .padding(.horizontal, 16)
        }
        .padding(.bottom, 24)
        .contentShape(Rectangle())
        .onTapGesture { onTap?() }
    }

    // MARK: 1. Spot header

    private var spotHeader: some View {
        HStack(alignment: .top, spacing: 8) {
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 4) {
                    Text(post.spotName)
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(AppColor.foreground)
                        .fixedSize(horizontal: false, vertical: true)
                    if post.verified {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 13))
                            .foregroundColor(AppColor.secondary)
                    }
                }
                Text("\(post.category) · \(post.neighborhood) · \(post.checkInCount) \(loc.strings.checkInLabel(count: Int32(post.checkInCount)))")
                    .font(.system(size: 12))
                    .foregroundColor(AppColor.mutedForeground)
            }

            Spacer(minLength: 8)

            HStack(spacing: 12) {
                Image(systemName: "bookmark")
                    .font(.system(size: 17))
                    .foregroundColor(AppColor.foreground)
            }
        }
    }

    // MARK: 2. Square placeholder

    private var placeholder: some View {
        Color(hex: post.colorHex)
            .aspectRatio(1, contentMode: .fit)
            .frame(maxWidth: .infinity)
    }

    // MARK: 3. Attribution

    private var attributionRow: some View {
        HStack(alignment: .top, spacing: 8) {
            AvatarView(name: post.authorName, size: 28)

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 0) {
                    Text(post.authorName)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(AppColor.foreground)
                    Text(" · \(post.dateLabel)")
                        .font(.system(size: 13))
                        .foregroundColor(AppColor.textFaint)
                }
                if post.hasText {
                    Text("„\(post.text)\u{201C}")
                        .font(.system(size: 14))
                        .foregroundColor(AppColor.bodyText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            Spacer(minLength: 0)
        }
    }

    // MARK: 4. Action row

    private var actionRow: some View {
        HStack(spacing: 0) {
            VoteControlView(score: post.score, voteState: post.voteState)

            Spacer().frame(width: 20)

            HStack(spacing: 4) {
                Image(systemName: "bubble.left")
                    .font(.system(size: 17))
                    .foregroundColor(AppColor.mutedForeground)
                if post.commentCount > 0 {
                    Text("\(post.commentCount)")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(AppColor.mutedForeground)
                }
            }

            Spacer()

            HStack(spacing: 4) {
                Image(systemName: "mappin")
                    .font(.system(size: 15))
                    .foregroundColor(AppColor.brand)
                Text(post.neighborhood)
                    .font(.system(size: 13))
                    .foregroundColor(AppColor.mutedForeground)
            }
        }
    }
}

// MARK: - Vote control

/// up-arrow / score / down-arrow, matching the Compose VoteControl.
/// Highlights the active direction with the brand color.
struct VoteControlView: View {
    let score: Int
    let voteState: String

    private var upActive: Bool { voteState == "UP" }
    private var downActive: Bool { voteState == "DOWN" }

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "arrow.up")
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(upActive ? AppColor.brand : AppColor.mutedForeground)
            Text("\(score)")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(AppColor.foreground)
            Image(systemName: "arrow.down")
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(downActive ? AppColor.secondary : AppColor.mutedForeground)
        }
    }
}

// MARK: - Avatar

/// Round avatar showing the first initial of the name on an orange (brand) bg,
/// mirroring the Compose `Avatar` component.
struct AvatarView: View {
    let name: String
    let size: CGFloat

    private var initial: String {
        guard let first = name.trimmingCharacters(in: .whitespaces).first else { return "?" }
        return String(first).uppercased()
    }

    var body: some View {
        Circle()
            .fill(AppColor.brand)
            .frame(width: size, height: size)
            .overlay(
                Text(initial)
                    .font(.system(size: size * 0.42, weight: .semibold))
                    .foregroundColor(.white)
            )
    }
}
