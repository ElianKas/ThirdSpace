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
    /// Optional vote callback, mirroring `onTap`. The argument is the tapped
    /// direction (`1` = up, `-1` = down). Defaults to `nil` so display-only
    /// call sites / previews compile unchanged; `NativeFeedView` passes a
    /// closure that calls `FeedData.vote` and re-reads `posts`.
    var onVote: ((Int) -> Void)? = nil
    /// Phase F5: optional author-tap callback, mirroring `onTap`/`onVote`.
    /// `NativeFeedView` passes a closure that opens `OtherProfileView` for the
    /// post's author. Defaults to `nil` so display-only call sites (the profile
    /// tabs, previews) compile unchanged — and the author then renders as plain,
    /// non-tappable text. The avatar + name become a `Button` only when this is
    /// non-nil AND the author is tappable (see `authorTappable`).
    var onAuthorTap: (() -> Void)? = nil
    /// Phase 4: when `true`, this card renders a BARE SPOT (a spot with no
    /// post). It suppresses the post-only chrome that makes no sense for a
    /// spot: the caption (there is none) and the vote control + comment button
    /// in the action row (nothing to vote/comment on). The spot header, colored
    /// placeholder, attribution (owner + created date), and the map-pin /
    /// neighborhood are KEPT. Defaults to `false` so existing call sites and
    /// previews (regular posts) are unchanged.
    var isBareSpot: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            spotHeader
                .padding(.horizontal, AppDimens.spacingLG)
                .padding(.top, AppDimens.spacingLG)

            placeholder
                .padding(.top, AppDimens.spacingMD)

            attributionRow
                .padding(.horizontal, AppDimens.spacingLG)
                .padding(.vertical, AppDimens.spacingMD)

            actionRow
                .padding(.horizontal, AppDimens.spacingLG)
        }
        .padding(.bottom, AppDimens.spacingXL)
        .contentShape(Rectangle())
        .onTapGesture { onTap?() }
    }

    // MARK: 1. Spot header

    private var spotHeader: some View {
        HStack(alignment: .top, spacing: AppDimens.spacingSM) {
            VStack(alignment: .leading, spacing: AppDimens.spacingXS) {
                HStack(spacing: AppDimens.spacingXS) {
                    Text(post.spotName)
                        .font(.system(size: AppTypography.title, weight: .bold))
                        .foregroundColor(AppColor.foreground)
                        .fixedSize(horizontal: false, vertical: true)
                    if post.verified {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: AppTypography.footnote))
                            .foregroundColor(AppColor.secondary)
                    }
                }
                Text("\(post.category) · \(post.neighborhood) · \(post.checkInCount) \(loc.strings.checkInLabel(count: Int32(post.checkInCount)))")
                    .font(.system(size: AppTypography.caption))
                    .foregroundColor(AppColor.mutedForeground)
            }

            Spacer(minLength: AppDimens.spacingSM)

            HStack(spacing: AppDimens.spacingMD) {
                Image(systemName: "bookmark")
                    .font(.system(size: AppTypography.title))
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

    /// Whether the author (avatar + name) is tappable to open their profile.
    /// Suppressed for a bare spot, an unknown author id, the current user's own
    /// posts, or when no `onAuthorTap` was wired (display-only call sites).
    private var authorTappable: Bool {
        onAuthorTap != nil
            && !isBareSpot
            && !post.authorUserId.isEmpty
            && post.authorUserId != FeedData.currentUserId()
    }

    private var attributionRow: some View {
        HStack(alignment: .top, spacing: AppDimens.spacingSM) {
            if authorTappable {
                // Only the avatar + name are the tap target (a plain Button), so
                // the post caption below stays a normal, selectable text block.
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

    /// Avatar + "Name · date" + optional caption. Shared between the tappable
    /// (Button) and non-tappable rendering so the look is identical.
    private var authorIdentity: some View {
        HStack(alignment: .top, spacing: AppDimens.spacingSM) {
            AvatarView(name: post.authorName, size: 28)

            VStack(alignment: .leading, spacing: AppDimens.spacingXS) {
                HStack(spacing: 0) {
                    Text(post.authorName)
                        .font(.system(size: AppTypography.footnote, weight: .medium))
                        .foregroundColor(AppColor.foreground)
                    Text(" · \(post.dateLabel)")
                        .font(.system(size: AppTypography.footnote))
                        .foregroundColor(AppColor.textFaint)
                }
                // A bare spot has no caption; only posts render text.
                if !isBareSpot && post.hasText {
                    Text("„\(post.text)\u{201C}")
                        .font(.system(size: AppTypography.body))
                        .foregroundColor(AppColor.bodyText)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    // MARK: 4. Action row

    private var actionRow: some View {
        HStack(spacing: 0) {
            // A bare spot has no post to vote on or comment on, so the vote
            // control + comment button are suppressed. The right-aligned
            // map-pin / neighborhood is kept for both kinds.
            if !isBareSpot {
                VoteControlView(
                    score: post.score,
                    voteState: post.voteState,
                    onUp: { onVote?(1) },
                    onDown: { onVote?(-1) }
                )

                Spacer().frame(width: 20)

                HStack(spacing: AppDimens.spacingXS) {
                    Image(systemName: "bubble.left")
                        .font(.system(size: AppTypography.title))
                        .foregroundColor(AppColor.mutedForeground)
                    if post.commentCount > 0 {
                        Text("\(post.commentCount)")
                            .font(.system(size: AppTypography.footnote, weight: .semibold))
                            .foregroundColor(AppColor.mutedForeground)
                    }
                }
            }

            Spacer()

            HStack(spacing: AppDimens.spacingXS) {
                Image(systemName: "mappin")
                    .font(.system(size: AppTypography.body))
                    .foregroundColor(AppColor.brand)
                Text(post.neighborhood)
                    .font(.system(size: AppTypography.footnote))
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
    /// Up-arrow tap. Defaults to a no-op so previews / display-only call sites
    /// compile unchanged. Wired call sites forward to `FeedData.vote(…, +1)`.
    var onUp: () -> Void = {}
    /// Down-arrow tap. Defaults to a no-op. Forwards to `FeedData.vote(…, -1)`.
    var onDown: () -> Void = {}

    private var upActive: Bool { voteState == "UP" }
    private var downActive: Bool { voteState == "DOWN" }

    var body: some View {
        HStack(spacing: AppDimens.spacingSM) {
            Button(action: onUp) {
                Image(systemName: "arrow.up")
                    .font(.system(size: AppTypography.body, weight: .semibold))
                    .foregroundColor(upActive ? AppColor.brand : AppColor.mutedForeground)
            }
            .buttonStyle(.plain)
            Text("\(score)")
                .font(.system(size: AppTypography.footnote, weight: .semibold))
                .foregroundColor(AppColor.foreground)
            Button(action: onDown) {
                Image(systemName: "arrow.down")
                    .font(.system(size: AppTypography.body, weight: .semibold))
                    .foregroundColor(downActive ? AppColor.secondary : AppColor.mutedForeground)
            }
            .buttonStyle(.plain)
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
