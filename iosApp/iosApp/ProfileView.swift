import SwiftUI

/// The NATIVE SwiftUI profile screen (STEP 1: static, read-only).
///
/// Shows the current user's own dummy profile sourced entirely from the shared
/// module via `FeedData.profile()` (no Swift-only data). All static UI text
/// goes through the injected `Localization` (`loc.strings.*`); only dynamic
/// content (name/username/city/bio/monthYear and the spot/post content) is
/// interpolated verbatim.
///
/// Deliberately excluded in this step (per the approved plan): profile editing,
/// follow / DM, opening follower/following lists (counts are display-only), the
/// language picker, count-up animations, skeletons, and real avatar/banner
/// images. The top-right gear is a VISIBLE NO-OP placeholder reserved for the
/// step-2 language settings.
///
/// Presented as a sheet from the feed header avatar; follows the project's
/// sheet pattern at the call site (`.presentationDetents` +
/// `.presentationDragIndicator(.visible)`).
struct ProfileView: View {
    /// Optional explicit close handler. Defaults to `nil`, in which case the
    /// environment `dismiss` is used (so the view works both as a sheet and in
    /// any future push context).
    var onClose: (() -> Void)? = nil

    @Environment(\.dismiss) private var dismiss
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
    }

    // MARK: Banner

    /// ~140pt rounded-bottom gradient placeholder built ONLY from AppColor
    /// tokens (no raw hex literals in the view). Approximates the legacy banner
    /// gradient; exact legacy hues are intentionally not reproduced here.
    private var banner: some View {
        ZStack(alignment: .top) {
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

            HStack {
                // Close control (top-left) in a scrim circle.
                Button {
                    (onClose ?? { dismiss() })()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(width: 32, height: 32)
                        .background(Circle().fill(AppColor.foreground.opacity(0.35)))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close")

                Spacer()

                // Gear (top-right) — NO-OP placeholder for the step-2 language
                // settings. Visible but does nothing yet.
                Button {
                    // Intentionally empty: language settings arrive in step 2.
                } label: {
                    Image(systemName: "gearshape")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(width: 32, height: 32)
                        .background(Circle().fill(AppColor.foreground.opacity(0.35)))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(loc.strings.profileSettings)
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
        }
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

    // MARK: Edit button (no-op)

    private var editButton: some View {
        Button {
            // No-op in step 1: profile editing is deliberately excluded.
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
            .frame(maxWidth: .infinity)
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
