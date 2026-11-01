import SwiftUI

/// The NATIVE SwiftUI profile screen (STEP 1: static, read-only).
///
/// Shows the current user's own dummy profile sourced entirely from the shared
/// module via `FeedData.profile()` (no Swift-only data). All static UI text
/// goes through the injected `Localization` (`loc.strings.*`); only dynamic
/// content (name/username/city/bio/monthYear and the spot/post content) is
/// interpolated verbatim.
///
/// A live language picker (globe button) sits at the top-right of the banner;
/// tapping it opens a native `Menu` listing the available languages and
/// switches the entire UI language live (in-memory, not persisted).
///
/// Deliberately excluded in this step (per the approved plan): profile editing,
/// follow / DM, opening follower/following lists (counts are display-only),
/// count-up animations, skeletons, and real avatar/banner images.
///
/// Lives as its own bottom-navigation tab (`RootTabView`). It is no longer a
/// sheet, so there is no close/gear chrome and nothing to dismiss; the banner
/// is a plain decorative header that extends to the top like the other tabs.
struct ProfileView: View {
    @Environment(Localization.self) private var loc

    /// Two local tabs for the profile content. No shared segmented control
    /// exists; this stays an inline, non-abstracted enum on purpose.
    private enum ProfileTab { case spots, posts }

    @State private var selectedTab: ProfileTab = .posts

    /// The current user's profile, from the shared offline-first store (the
    /// real `profiles` row when logged in; a neutral non-dummy fallback
    /// otherwise). Replaces the old `FeedData.profile()` SampleData snapshot.
    @Environment(ProfileStore.self) private var profileStore
    private var profile: UIProfile { profileStore.profile }

    /// The current user's OWN posts / spots (Phase 4), from the shared
    /// offline-first store (posts where `author_id == uid`; spots where
    /// `owner_id == uid`). Replaces the old global-feed `FeedData.posts()` +
    /// distinct-by-name derivation. Refreshed by the store's
    /// `loadProfile`/`observeProfile` flow, so the tabs update with the profile.
    private var posts: [UIPost] { profileStore.ownPosts }
    private var spots: [UISpot] { profileStore.ownSpots }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                banner

                // Avatar overlaps the banner bottom edge.
                avatar
                    .padding(.leading, 20)
                    .offset(y: -35)
                    .padding(.bottom, -35)

                identity
                    .padding(.horizontal, AppDimens.spacingXL)
                    .padding(.top, AppDimens.spacingMD)

                statsRow
                    .padding(.horizontal, AppDimens.spacingXL)
                    .padding(.top, AppDimens.spacingLG)

                editButton
                    .padding(.horizontal, AppDimens.spacingXL)
                    .padding(.top, AppDimens.spacingLG)

                tabBar
                    .padding(.top, AppDimens.spacingXL)

                tabContent
                    .padding(.top, AppDimens.spacingSM)
            }
        }
        .background(AppColor.background)
        // Native system pull-to-refresh, mirroring NativeFeedView. `.refreshable`
        // attaches to the root ScrollView; the store's @Observable drives the
        // re-render once the shared refresh lands, so no local @State copy is
        // needed here.
        .refreshable { await refreshProfile() }
        // As a tab the banner is a header: let the gradient extend under the
        // status bar at the top, consistent with the other tab screens' chrome.
        .ignoresSafeArea(edges: .top)
        // Language pill overlaid at the top-trailing edge. The overlay is
        // attached OUTSIDE the `.ignoresSafeArea(edges: .top)` ScrollView, so it
        // lives in the safe-area-respecting space: its top aligns with the
        // status-bar bottom, and `AppLayout.topChromeSpacing` adds the same gap
        // the old "+ 8" produced — WITHOUT reading `safeAreaInsets` manually and
        // WITHOUT reserving a strip that would push the borderless banner down.
        .overlay(alignment: .topTrailing) {
            languageButton
                .padding(.trailing, AppDimens.spacingLG)
                .padding(.top, AppLayout.topChromeSpacing)
        }
        // This screen is inside the Profile tab's NavigationStack (so the edit
        // button can push). Hide the nav bar HERE so the gradient banner still
        // reaches the top on the main profile page; the pushed edit page shows
        // its own nav bar + back button.
        .toolbar(.hidden, for: .navigationBar)
    }

    // MARK: Refresh

    /// Native pull-to-refresh: trigger a real background remote refresh of the
    /// profile + counts + own posts/spots via the flat bridge. Mirrors
    /// `NativeFeedView.refresh()`: bridges the escaping `FeedData.loadProfile`
    /// completion into the async `refreshable` with a continuation so the system
    /// spinner stays until the completion fires on the main thread. No local
    /// state is touched — `ProfileStore` re-reads itself in the completion /
    /// `observeProfile`, and its `@Observable` drives the re-render. Offline /
    /// unauthenticated no-ops in the shared layer and completes without crashing.
    private func refreshProfile() async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            FeedData.loadProfile {
                continuation.resume()
            }
        }
    }

    // MARK: Banner

    /// ~140pt rounded-bottom gradient placeholder built ONLY from AppColor
    /// tokens (no raw hex literals in the view). Approximates the legacy banner
    /// gradient; exact legacy hues are intentionally not reproduced here.
    ///
    /// A plain decorative header with NO icon buttons. As a tab (not a sheet)
    /// there is nothing to close and no gear placeholder; the gradient extends
    /// to the very top of the screen like a header.
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

    // MARK: Language picker (globe button + native Menu)

    /// A bordered pill showing the active language code + a globe glyph. Tapping
    /// opens a native `Menu` listing every available language by its endonym; a
    /// checkmark marks the active one. Selecting an entry switches the entire UI
    /// language live via `loc.setLanguage`. The pill reads `loc.languageCode`
    /// (and the a11y label `loc.strings.languageMenu`) so it updates live.
    ///
    /// Uses only `AppColor` tokens: a frosted strong fill + hairline border so
    /// it reads on the gradient, with foreground text in `AppColor.foreground`.
    private var languageButton: some View {
        Menu {
            ForEach(loc.availableLanguages) { lang in
                Button {
                    loc.setLanguage(lang.code)
                } label: {
                    if lang.code == loc.languageCode {
                        Label(lang.displayName, systemImage: "checkmark")
                    } else {
                        Text(lang.displayName)
                    }
                }
            }
        } label: {
            HStack(spacing: AppDimens.spacingSM) {
                Text(loc.languageCode.uppercased())
                    .font(.system(size: AppTypography.footnote, weight: .semibold))
                Image(systemName: "globe")
                    .font(.system(size: AppTypography.body, weight: .semibold))
            }
            .foregroundColor(AppColor.foreground)
            .padding(.horizontal, AppDimens.spacingMD)
            .frame(height: 34)
            .background(
                Capsule().fill(AppColor.frostStrong)
            )
            .overlay(
                Capsule().stroke(AppColor.border, lineWidth: AppDimens.hairline)
            )
        }
        .accessibilityLabel(loc.strings.languageMenu)
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
                // City is dynamic content; "seit"/"since" comes from i18n.
                Text("\(profile.city) · \(loc.strings.joinedSince(monthYear: profile.monthYear))")
                    .font(.system(size: AppTypography.footnote))
                    .foregroundColor(AppColor.mutedForeground)
            }
            .padding(.top, AppDimens.spacingXS)

            Text(profile.bio)
                .font(.system(size: AppTypography.body))
                .foregroundColor(AppColor.bodyText)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, AppDimens.spacingXS)
        }
    }

    // MARK: Stats row

    private var statsRow: some View {
        HStack(spacing: 0) {
            StatColumn(value: profile.spotCount, label: loc.strings.profileStatSpots)
            StatColumn(value: profile.postCount, label: loc.strings.profileStatPosts)
            StatColumn(value: profile.followerCount, label: loc.strings.profileStatFollowers)
            StatColumn(value: profile.followingCount, label: loc.strings.profileStatFollowing)
        }
    }

    // MARK: Edit button (pushes the edit sub-page)

    /// Pushes `ProfileEditView` onto the Profile tab's `NavigationStack`. The
    /// soft-fill bordered look is unchanged from before; `.buttonStyle(.plain)`
    /// keeps it from rendering as a default tappable link.
    private var editButton: some View {
        NavigationLink {
            ProfileEditView()
        } label: {
            Text(loc.strings.profileEdit)
                .font(.system(size: AppTypography.body, weight: .semibold))
                .foregroundColor(AppColor.foreground)
                .frame(maxWidth: .infinity)
                .padding(.vertical, AppDimens.spacingMD)
                .background(
                    RoundedRectangle(cornerRadius: AppDimens.radiusMD)
                        .fill(AppColor.page)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: AppDimens.radiusMD)
                        .stroke(AppColor.border, lineWidth: AppDimens.hairline)
                )
        }
        .buttonStyle(.plain)
    }

    // MARK: Inline two-tab segmented control

    private var tabBar: some View {
        HStack(spacing: 0) {
            tabButton(.spots, title: loc.strings.profileTabSpots)
            tabButton(.posts, title: loc.strings.profileTabPosts)
        }
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(AppColor.hairline)
                .frame(height: AppDimens.hairline)
        }
    }

    private func tabButton(_ tab: ProfileTab, title: String) -> some View {
        let active = selectedTab == tab
        return Button {
            selectedTab = tab
        } label: {
            VStack(spacing: AppDimens.spacingSM) {
                Text(title)
                    .font(.system(size: AppTypography.body, weight: active ? .semibold : .regular))
                    .foregroundColor(active ? AppColor.brand : AppColor.mutedForeground)
                Rectangle()
                    .fill(active ? AppColor.brand : Color.clear)
                    .frame(height: 2)
            }
            // Fill the full tab width AND pad vertically so the whole tab area
            // (text + indicator + surrounding space) is a comfortable tap
            // target, then make that entire rectangle hit-testable.
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppDimens.spacingSM)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    // MARK: Tab content

    @ViewBuilder
    private var tabContent: some View {
        switch selectedTab {
        case .posts:
            postsContent
        case .spots:
            spotsContent
        }
    }

    @ViewBuilder
    private var postsContent: some View {
        if posts.isEmpty {
            emptyState(loc.strings.profileEmptyPosts)
        } else {
            LazyVStack(spacing: 0) {
                ForEach(Array(posts.enumerated()), id: \.element.id) { index, post in
                    VStack(alignment: .leading, spacing: 0) {
                        PostCardView(post: post)
                        if index < posts.count - 1 {
                            Rectangle()
                                .fill(AppColor.hairline)
                                .frame(height: AppDimens.hairline)
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var spotsContent: some View {
        // The user's OWN spots (Phase 4), mapped to the lightweight row model so
        // the existing `ProfileSpotRow` look is unchanged. No VoteControl in V1.
        let rows = spots.map(ProfileSpot.init(spot:))
        if rows.isEmpty {
            emptyState(loc.strings.profileEmptySpots)
        } else {
            LazyVStack(spacing: 0) {
                ForEach(rows) { spot in
                    ProfileSpotRow(spot: spot)
                    Rectangle()
                        .fill(AppColor.hairline)
                        .frame(height: AppDimens.hairline)
                        .padding(.leading, AppDimens.spacingXL)
                }
            }
        }
    }

    private func emptyState(_ text: String) -> some View {
        Text(text)
            .font(.system(size: AppTypography.body))
            .foregroundColor(AppColor.mutedForeground)
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppDimens.spacingXXL)
    }
}

// MARK: - Private subviews

/// A single stat column (big number + muted caption) in the profile stats row.
private struct StatColumn: View {
    let value: Int
    let label: String

    var body: some View {
        VStack(spacing: AppDimens.spacingXS) {
            // Plain number, no count-up animation (deliberately excluded).
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

/// A lightweight spot descriptor for the profile Spots tab. As of Phase 4 it is
/// built from the user's OWN `UISpot` (identity by the real spot `id`, so two
/// spots with the same name stay distinct) rather than derived from feed posts.
private struct ProfileSpot: Identifiable {
    let id: String
    let name: String
    let category: String
    let neighborhood: String
    let colorHex: String

    init(spot: UISpot) {
        self.id = spot.id
        self.name = spot.name
        self.category = spot.category
        self.neighborhood = spot.neighborhood
        self.colorHex = spot.colorHex
    }
}

/// A simple spot row for the profile Spots tab: color swatch + name + meta.
private struct ProfileSpotRow: View {
    let spot: ProfileSpot

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
    }
}
