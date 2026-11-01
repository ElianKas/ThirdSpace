import SwiftUI

/// Native SwiftUI follower / following list — a PUSHED, `.searchable` sub-page
/// reused for BOTH the "Follower" and "Folge ich" lists (Phase F5).
///
/// Navigation: this is a PUSH destination (not a sheet). It is reached from the
/// tappable follower/following `StatColumn`s in `ProfileView` (inside the
/// Profile tab's `NavigationStack`) and from the matching stat columns in
/// `OtherProfileView` (which, when pushed from the profile tab OR presented in
/// a sheet, is itself wrapped in a `NavigationStack` — see those call sites).
/// Either way it lives inside a `NavigationStack`, so its own row taps can push
/// another `OtherProfileView`.
///
/// Data flows entirely through the shared follow seam via the `@Observable`
/// `FollowStore`: on appear it calls `loadFollowers(userId:)` /
/// `loadFollowing(userId:)` for the viewed `userId` and renders the store's
/// `followers` / `following` array. The store owns the remote refresh + the
/// live re-read via `observeFollows`, so this view just reads + renders.
///
/// The `.searchable` query filters CLIENT-SIDE by `name` / `username`
/// (case-insensitive). Those are DYNAMIC content and are NOT translated; only
/// the navigation title, search prompt, and empty-state text come from
/// `loc.strings.*`.
///
/// BLOCKED-UNTIL-SESSION: the real list is empty offline / pre-login (the
/// shared refresh is a no-op without keys + a live session); the UI then shows
/// the `followEmpty` state without crashing.
struct FollowListView: View {
    /// Which list this instance shows. Local to this screen (no shared type).
    enum Mode { case followers, following }

    @Environment(FollowStore.self) private var followStore
    @Environment(Localization.self) private var loc

    let userId: String
    let mode: Mode

    @State private var query = ""

    /// The source list for the current mode, read live from the observable
    /// store so a background refresh / change re-renders the list.
    private var users: [UIFollowUser] {
        switch mode {
        case .followers: return followStore.followers
        case .following: return followStore.following
        }
    }

    /// Client-side filter by name / username (case-insensitive). Dynamic
    /// content, NOT translated.
    private var filtered: [UIFollowUser] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return users }
        let needle = trimmed.lowercased()
        return users.filter {
            $0.name.lowercased().contains(needle)
                || $0.username.lowercased().contains(needle)
        }
    }

    private var title: String {
        switch mode {
        case .followers: return loc.strings.followListFollowersTitle
        case .following: return loc.strings.followListFollowingTitle
        }
    }

    var body: some View {
        Group {
            if filtered.isEmpty {
                emptyState
            } else {
                list
            }
        }
        .background(AppColor.background)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $query, prompt: loc.strings.followSearchPlaceholder)
        .onAppear(perform: load)
    }

    // MARK: Load

    private func load() {
        switch mode {
        case .followers: followStore.loadFollowers(userId: userId)
        case .following: followStore.loadFollowing(userId: userId)
        }
    }

    // MARK: List

    private var list: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(filtered) { user in
                    NavigationLink {
                        OtherProfileView(userId: user.id)
                    } label: {
                        AppListRow(
                            leading: .avatar(name: user.name),
                            title: user.name,
                            subtitle: "@\(user.username)",
                            detail: user.city.isEmpty ? nil : user.city,
                            trailing: .chevron
                        )
                    }
                    .buttonStyle(.plain)

                    Rectangle()
                        .fill(AppColor.hairline)
                        .frame(height: AppDimens.hairline)
                        .padding(.leading, AppDimens.spacingXL)
                }
            }
        }
    }

    // MARK: Empty state

    private var emptyState: some View {
        EmptyStateView(systemImage: "person.2", text: loc.strings.followEmpty)
    }
}
