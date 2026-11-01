import SwiftUI

/// Native SwiftUI OTHER-user profile — a PUSHED sub-page showing ANOTHER
/// user's profile (Phase F5).
///
/// Reached by tapping an author in the feed / comments (presented as a sheet,
/// wrapped in a `NavigationStack` by the presenter) or a row in a
/// `FollowListView` (pushed inside the Profile tab's stack). Either way it
/// lives inside a `NavigationStack`, so it shows its own nav bar + back button
/// (analogous to `ProfileEditView`) and can PUSH the viewed user's own
/// follower / following `FollowListView`s.
///
/// Data: the shared bridge's `otherProfile(userId:)` carries the row + REAL
/// spot/post/follower/following COUNTS only — NOT the user's post/spot lists.
/// So this V1 shows the banner / avatar / identity / stats row + a follow
/// button, and deliberately OMITS the own-posts / own-spots tabs that
/// `ProfileView` has (the bridge doesn't carry those lists for other users).
/// The counts in the stats row are enough for V1; the tabs stay out of scope.
///
/// Reactivity: `profile` is seeded synchronously from `FeedData.otherProfile`
/// (a neutral snapshot pre-fetch) and refreshed on appear via
/// `loadOtherProfile` + `loadFollowCounts`; the follow button state is driven
/// by the observable `FollowStore.isFollowing(targetId:)`, refreshed via
/// `loadFollowState`. All static UI text is `loc.strings.*`; name / username /
/// city / bio / monthYear are dynamic content, interpolated verbatim.
///
/// The follow button is HIDDEN when `userId` is empty or equals the current
/// user's own id (so you never see a follow button on yourself).
///
/// BLOCKED-UNTIL-SESSION: offline / pre-login the fetch is a no-op (neutral
/// snapshot, zero counts, no follow state) and follow/unfollow report `false`;
/// the real data requires the user's keys + migrations + a live session.
struct OtherProfileView: View {
    @Environment(FollowStore.self) private var followStore
    @Environment(Localization.self) private var loc

    let userId: String

    /// The viewed user's profile, seeded from the pure bridge read and refreshed
    /// on appear. Local `@State` (not a store) since other profiles have no
    /// durable shared cache.
    @State private var profile: UIProfile
    /// In-flight guard for the follow/unfollow toggle so a double-tap can't fire
    /// two concurrent writes.
    @State private var isToggling = false

    init(userId: String) {
        self.userId = userId
        _profile = State(initialValue: FeedData.otherProfile(userId: userId))
    }

    /// Whether to show the follow button: never on yourself or an empty id.
    private var showFollowButton: Bool {
        !userId.isEmpty && userId != FeedData.currentUserId()
    }

    private var isFollowing: Bool {
        followStore.isFollowing(targetId: userId)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                banner

                avatar
                    .padding(.leading, AppDimens.spacingXL)
                    .offset(y: -35)
                    .padding(.bottom, -35)

                identity
                    .padding(.horizontal, AppDimens.spacingXL)
                    .padding(.top, AppDimens.spacingMD)

                statsRow
                    .padding(.horizontal, AppDimens.spacingXL)
                    .padding(.top, AppDimens.spacingLG)

                if showFollowButton {
                    followButton
                        .padding(.horizontal, AppDimens.spacingXL)
                        .padding(.top, AppDimens.spacingLG)
                }
            }
            .padding(.bottom, AppDimens.spacingXXL)
        }
        .background(AppColor.background)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: load)
    }

    // MARK: Load

    /// Refresh the viewed user's profile (row + counts) and the follow state.
    /// Both complete on the main thread; re-read `otherProfile` in the profile
    /// completion. The follow state is driven by the observable store.
    private func load() {
        FeedData.loadOtherProfile(userId: userId) {
            profile = FeedData.otherProfile(userId: userId)
        }
        FeedData.loadFollowCounts(userId: userId) {
            profile = FeedData.otherProfile(userId: userId)
        }
        followStore.loadFollowState(targetId: userId)
    }

    // MARK: Banner (mirrors ProfileView.banner; tokens only)

    private var banner: some View {
        LinearGradient(
            colors: [
                AppColor.brand.opacity(0.85),
                AppColor.secondary.opacity(0.65),
                AppColor.page,
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .frame(height: 140)
        .clipShape(
            .rect(
                bottomLeadingRadius: AppDimens.radiusLG,
                bottomTrailingRadius: AppDimens.radiusLG
            )
        )
    }

    // MARK: Avatar

    private var avatar: some View {
        AvatarView(name: profile.name, size: 88)
            .overlay(
                Circle()
                    .stroke(AppColor.background, lineWidth: 3)
            )
    }

    // MARK: Identity (name / @username / location+joined / bio)

    private var identity: some View {
        VStack(alignment: .leading, spacing: AppDimens.spacingSM) {
            Text(profile.name)
                .font(.system(size: AppTypography.titleLarge, weight: .bold))
                .foregroundColor(AppColor.foreground)

            Text("@\(profile.username)")
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.mutedForeground)

            HStack(spacing: AppDimens.spacingXS) {
                Image(systemName: "mappin")
                    .font(.system(size: AppTypography.footnote))
                    .foregroundColor(AppColor.brand)
                Text("\(profile.city) · \(loc.strings.joinedSince(monthYear: profile.monthYear))")
                    .font(.system(size: AppTypography.footnote))
                    .foregroundColor(AppColor.mutedForeground)
            }
            .padding(.top, AppDimens.spacingXS)

            if !profile.bio.isEmpty {
                Text(profile.bio)
                    .font(.system(size: AppTypography.body))
                    .foregroundColor(AppColor.bodyText)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, AppDimens.spacingXS)
            }
        }
    }

    // MARK: Stats row (followers/following tappable -> that user's lists)

    private var statsRow: some View {
        HStack(spacing: 0) {
            OtherStatColumn(value: profile.spotCount, label: loc.strings.profileStatSpots)
            OtherStatColumn(value: profile.postCount, label: loc.strings.profileStatPosts)
            NavigationLink {
                FollowListView(userId: userId, mode: .followers)
            } label: {
                OtherStatColumn(value: profile.followerCount, label: loc.strings.profileStatFollowers)
            }
            .buttonStyle(.plain)
            NavigationLink {
                FollowListView(userId: userId, mode: .following)
            } label: {
                OtherStatColumn(value: profile.followingCount, label: loc.strings.profileStatFollowing)
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: Follow / unfollow button (primary, token-styled)

    /// Replaces ProfileView's "Edit profile" button. Label flips between
    /// `followButton` / `unfollowButton` from the observable follow state.
    /// Following uses a filled brand button; not-following a bordered page fill
    /// (so an existing follow reads as the "active" state). Tapping toggles
    /// optimistically through the store, which re-reads the follow state.
    private var followButton: some View {
        Button(action: toggleFollow) {
            Text(isFollowing ? loc.strings.unfollowButton : loc.strings.followButton)
                .font(.system(size: AppTypography.body, weight: .semibold))
                .foregroundColor(isFollowing ? AppColor.foreground : .white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, AppDimens.spacingMD)
                .background(
                    RoundedRectangle(cornerRadius: AppDimens.radiusMD)
                        .fill(isFollowing ? AppColor.page : AppColor.brand)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: AppDimens.radiusMD)
                        .stroke(AppColor.border, lineWidth: isFollowing ? AppDimens.hairline : 0)
                )
        }
        .buttonStyle(.plain)
        .disabled(isToggling)
        .opacity(isToggling ? 0.6 : 1)
    }

    private func toggleFollow() {
        guard !isToggling else { return }
        isToggling = true
        let completion: (Bool) -> Void = { _ in isToggling = false }
        if isFollowing {
            followStore.unfollow(targetId: userId, completion: completion)
        } else {
            followStore.follow(targetId: userId, completion: completion)
        }
    }
}

// MARK: - Private subviews

/// A single stat column (big number + muted caption). A local mirror of
/// `ProfileView`'s private `StatColumn` (which is file-private there), so the
/// other-profile stats row matches the own-profile look without touching
/// `ProfileView`'s private type.
private struct OtherStatColumn: View {
    let value: Int
    let label: String

    var body: some View {
        VStack(spacing: AppDimens.spacingXS) {
            Text("\(value)")
                .font(.system(size: AppTypography.title, weight: .bold))
                .foregroundColor(AppColor.foreground)
            Text(label)
                .font(.system(size: AppTypography.caption))
                .foregroundColor(AppColor.mutedForeground)
        }
        .frame(maxWidth: .infinity)
    }
}
