import Shared

// MARK: - UIFollowUser (Swift mirror of Kotlin IosFollowUser)

/// Swift-side value type for a single follower / following list entry (Phase
/// F4). Built from the bridged `IosFollowUser` returned by
/// `FeedBridge.shared.followers(userId:)` / `following(userId:)`. The shared
/// module is the single source of truth for the social graph (no Swift-only
/// list). `name` / `city` are dynamic content (NOT translated); `username` is
/// derived shared-side; `isFollowedByMe` drives a follow/unfollow button in the
/// F5 UI.
struct UIFollowUser: Identifiable {
    let id: String
    let name: String
    let username: String
    let city: String
    let isFollowedByMe: Bool

    init(bridged: IosFollowUser) {
        self.id = bridged.id
        self.name = bridged.name
        self.username = bridged.username
        self.city = bridged.city
        self.isFollowedByMe = bridged.isFollowedByMe
    }
}
