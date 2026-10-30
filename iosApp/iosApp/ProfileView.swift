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

    private let profile = FeedData.profile()
    private let posts = FeedData.posts()

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
                    .padding(.horizontal, 20)
                    .padding(.top, 12)

                statsRow
                    .padding(.horizontal, 20)
                    .padding(.top, 16)

                editButton
                    .padding(.horizontal, 20)
                    .padding(.top, 16)

                tabBar
                    .padding(.top, 20)

                tabContent
                    .padding(.top, 8)
            }
        }
        .background(AppColor.background)
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
                .padding(.trailing, 16)
                .padding(.top, AppLayout.topChromeSpacing)
        }
        // This screen is inside the Profile tab's NavigationStack (so the edit
        // button can push). Hide the nav bar HERE so the gradient banner still
        // reaches the top on the main profile page; the pushed edit page shows
        // its own nav bar + back button.
        .toolbar(.hidden, for: .navigationBar)
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
                bottomLeadingRadius: 20,
                bottomTrailingRadius: 20
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
            HStack(spacing: 6) {
                Text(loc.languageCode.uppercased())
                    .font(.system(size: 13, weight: .semibold))
                Image(systemName: "globe")
                    .font(.system(size: 14, weight: .semibold))
            }
            .foregroundColor(AppColor.foreground)
            .padding(.horizontal, 12)
            .frame(height: 34)
            .background(
                Capsule().fill(AppColor.frostStrong)
            )
            .overlay(
                Capsule().stroke(AppColor.border, lineWidth: 1)
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
        VStack(alignment: .leading, spacing: 6) {
            Text(profile.name)
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(AppColor.foreground)

            Text("@\(profile.username)")
                .font(.system(size: 14))
                .foregroundColor(AppColor.mutedForeground)

            HStack(spacing: 4) {
                Image(systemName: "mappin")
                    .font(.system(size: 13))
                    .foregroundColor(AppColor.brand)
                // City is dynamic content; "seit"/"since" comes from i18n.
                Text("\(profile.city) · \(loc.strings.joinedSince(monthYear: profile.monthYear))")
                    .font(.system(size: 13))
                    .foregroundColor(AppColor.mutedForeground)
            }
            .padding(.top, 2)

            Text(profile.bio)
                .font(.system(size: 14))
                .foregroundColor(AppColor.bodyText)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 4)
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
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(AppColor.foreground)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(AppColor.page)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(AppColor.border, lineWidth: 1)
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
                .frame(height: 1)
        }
    }

    private func tabButton(_ tab: ProfileTab, title: String) -> some View {
        let active = selectedTab == tab
        return Button {
            selectedTab = tab
        } label: {
            VStack(spacing: 8) {
                Text(title)
                    .font(.system(size: 14, weight: active ? .semibold : .regular))
                    .foregroundColor(active ? AppColor.brand : AppColor.mutedForeground)
                Rectangle()
                    .fill(active ? AppColor.brand : Color.clear)
                    .frame(height: 2)
            }
            // Fill the full tab width AND pad vertically so the whole tab area
            // (text + indicator + surrounding space) is a comfortable tap
            // target, then make that entire rectangle hit-testable.
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
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
                                .frame(height: 1)
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var spotsContent: some View {
        // Derive a distinct-by-name spot list from the posts (same approach as
        // CreatePostView). No VoteControl in V1.
        let spots = distinctSpots()
        if spots.isEmpty {
            emptyState(loc.strings.profileEmptySpots)
        } else {
            LazyVStack(spacing: 0) {
                ForEach(spots) { spot in
                    ProfileSpotRow(spot: spot)
                    Rectangle()
                        .fill(AppColor.hairline)
                        .frame(height: 1)
                        .padding(.leading, 20)
                }
            }
        }
    }

    private func emptyState(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 14))
            .foregroundColor(AppColor.mutedForeground)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 48)
    }

    /// Distinct spots derived from the feed posts, keyed by spot name.
    private func distinctSpots() -> [ProfileSpot] {
        var seen = Set<String>()
        var result: [ProfileSpot] = []
        for post in posts where !seen.contains(post.spotName) {
            seen.insert(post.spotName)
            result.append(
                ProfileSpot(
                    name: post.spotName,
                    category: post.category,
                    neighborhood: post.neighborhood,
                    colorHex: post.colorHex
                )
            )
        }
        return result
    }
}

// MARK: - Private subviews

/// A single stat column (big number + muted caption) in the profile stats row.
private struct StatColumn: View {
    let value: Int
    let label: String

    var body: some View {
        VStack(spacing: 2) {
            // Plain number, no count-up animation (deliberately excluded).
            Text("\(value)")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(AppColor.foreground)
            Text(label)
                .font(.system(size: 12))
                .foregroundColor(AppColor.mutedForeground)
        }
        .frame(maxWidth: .infinity)
    }
}

/// A lightweight spot descriptor derived from a feed post for the Spots tab.
private struct ProfileSpot: Identifiable {
    var id: String { name }
    let name: String
    let category: String
    let neighborhood: String
    let colorHex: String
}

/// A simple spot row for the profile Spots tab: color swatch + name + meta.
private struct ProfileSpotRow: View {
    let spot: ProfileSpot

    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color(hex: spot.colorHex))
                .frame(width: 44, height: 44)

            VStack(alignment: .leading, spacing: 2) {
                Text(spot.name)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(AppColor.foreground)
                Text("\(spot.category) · \(spot.neighborhood)")
                    .font(.system(size: 13))
                    .foregroundColor(AppColor.mutedForeground)
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }
}
