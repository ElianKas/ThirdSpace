import SwiftUI
import Shared

/// App-wide state for the social graph (follows), for the native SwiftUI
/// follower/following list screens + the other-profile follow button (Phase F5
/// will consume it). Phase F4 adds ONLY this plumbing; no SwiftUI screen yet.
///
/// Thin `@Observable` wrapper around the shared `FeedBridge` follow seam,
/// mirroring `ProfileStore` / the `Session` / `Localization` pattern. It owns
/// the currently-viewed follower/following lists and drives follow/unfollow.
///
/// Views read it from the environment (wired in F5):
///
///     @Environment(FollowStore.self) private var followStore
///     ForEach(followStore.followers) { user in ... }
///
/// BOUNDARY: only flat value types cross the bridge. The bridge runs the shared
/// suspend/supabase follow work on a background scope and delivers every
/// completion / change callback below ON THE MAIN THREAD, so mutating this
/// `@Observable` directly is safe (no extra `DispatchQueue.main` hop).
///
/// READS ARE SIDE-EFFECT-FREE: `FeedData.followers`/`following`/`isFollowing`
/// are pure cache reads. The store triggers an EXPLICIT `load...` refresh for
/// the subject being viewed and re-reads the pure cache in the main-thread
/// completion, and also listens for background changes via `observeFollows`.
///
/// NOT-CONFIGURED / OFFLINE / PRE-LOGIN SAFETY: when there is no Supabase client
/// or no session, the bridge refresh is a no-op, the pure reads return empty
/// lists / `false` counts, and follow/unfollow report `false` (NO durable
/// queue). The REAL social graph is BLOCKED until the user's keys + migrations
/// + a live login.
///
/// `@Observable` + `@Environment(Type.self)` is the iOS 17+ Observation pattern,
/// valid at the project's iOS 18.2 target.
@Observable
final class FollowStore {
    /// The follower list currently being viewed. Set by `loadFollowers(userId:)`;
    /// re-read from the pure bridge cache for `viewingUserId`.
    private(set) var followers: [UIFollowUser] = []

    /// The following list currently being viewed. Set by `loadFollowing(userId:)`.
    private(set) var following: [UIFollowUser] = []

    /// The user id whose lists are currently loaded, so the `observeFollows`
    /// listener re-reads the RIGHT subject's lists on a background change. "" =
    /// nothing being viewed yet.
    private(set) var viewingUserId: String = ""

    /// Phase F5: OBSERVABLE follow-state cache, keyed by `targetId`. Mirrors the
    /// pure `FeedData.isFollowing(targetId:)` read into an `@Observable` map so
    /// the other-profile follow button (and any list row) re-renders when the
    /// state changes via an optimistic follow/unfollow or a background refresh.
    /// `isFollowing(targetId:)` below reads THIS map (seeded from the bridge),
    /// so SwiftUI tracks the dependency and updates live.
    private(set) var followState: [String: Bool] = [:]

    init() {
        // Observe future background follow-graph changes (delivered on the main
        // thread by the bridge). Register once per store lifetime: re-read the
        // currently-viewed subject's lists.
        FeedData.observeFollows { [weak self] in
            self?.reloadListsFromCache()
        }
    }

    /// Re-read the currently-viewed subject's follower/following lists from the
    /// pure bridge caches. Called from the main-thread `observeFollows` /
    /// `load*` completions, keeping the store the single owner of both lists.
    private func reloadListsFromCache() {
        if !viewingUserId.isEmpty {
            followers = FeedData.followers(userId: viewingUserId)
            following = FeedData.following(userId: viewingUserId)
        }
        // Re-read every tracked follow state from the pure bridge cache so the
        // observable map (and thus the follow button) reflects a background
        // refresh / optimistic change. Keys are only the targets we've looked
        // at, so this stays small.
        for targetId in followState.keys {
            followState[targetId] = FeedData.isFollowing(targetId: targetId)
        }
    }

    /// Load (and trigger a background refresh of) `userId`'s FOLLOWERS list.
    /// Seeds synchronously from the pure cache, then kicks off the explicit
    /// remote refresh and re-reads in the main-thread completion.
    func loadFollowers(userId: String) {
        viewingUserId = userId
        followers = FeedData.followers(userId: userId)
        FeedData.loadFollowers(userId: userId) { [weak self] in
            guard let self, self.viewingUserId == userId else { return }
            self.followers = FeedData.followers(userId: userId)
        }
    }

    /// Load (and trigger a background refresh of) `userId`'s FOLLOWING list.
    func loadFollowing(userId: String) {
        viewingUserId = userId
        following = FeedData.following(userId: userId)
        FeedData.loadFollowing(userId: userId) { [weak self] in
            guard let self, self.viewingUserId == userId else { return }
            self.following = FeedData.following(userId: userId)
        }
    }

    /// Whether the CURRENT user follows `targetId`. Reads the OBSERVABLE
    /// `followState` map (seeded from the pure bridge cache on first access) so
    /// SwiftUI tracks the dependency and the follow button re-renders on change.
    /// `false` before any `loadFollowState` / optimistic follow.
    func isFollowing(targetId: String) -> Bool {
        followState[targetId] ?? FeedData.isFollowing(targetId: targetId)
    }

    /// Trigger a background refresh of the current user's follow state toward
    /// `targetId`. Seeds the observable `followState` entry from the pure cache
    /// now, then re-reads it (plus the viewed lists) in the main-thread
    /// completion so the follow button stays fresh.
    func loadFollowState(targetId: String) {
        followState[targetId] = FeedData.isFollowing(targetId: targetId)
        FeedData.loadFollowState(targetId: targetId) { [weak self] in
            guard let self else { return }
            self.followState[targetId] = FeedData.isFollowing(targetId: targetId)
            self.reloadListsFromCache()
        }
    }

    /// Follow `targetId` (optimistic + best-effort remote push). Re-reads the
    /// observable follow state + viewed lists in the main-thread completion (the
    /// optimistic state already fired via `observeFollows`), then forwards the
    /// `Bool` outcome. `false` = NOT persisted remotely (offline / unauth — no
    /// durable queue).
    func follow(targetId: String, completion: @escaping (Bool) -> Void) {
        FeedData.followUser(targetId: targetId) { [weak self] ok in
            guard let self else { completion(ok); return }
            self.followState[targetId] = FeedData.isFollowing(targetId: targetId)
            self.reloadListsFromCache()
            completion(ok)
        }
    }

    /// Unfollow `targetId` (same optimistic + best-effort + flat-Bool contract).
    func unfollow(targetId: String, completion: @escaping (Bool) -> Void) {
        FeedData.unfollowUser(targetId: targetId) { [weak self] ok in
            guard let self else { completion(ok); return }
            self.followState[targetId] = FeedData.isFollowing(targetId: targetId)
            self.reloadListsFromCache()
            completion(ok)
        }
    }
}
