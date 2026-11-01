import SwiftUI
import Shared

/// App-wide state for the CURRENT user's own profile, for the native SwiftUI
/// profile screens.
///
/// Thin `@Observable` wrapper around the shared `FeedBridge` profile seam,
/// mirroring the `Session` / `Localization` pattern. It holds the flat
/// `UIProfile` snapshot and drives `ProfileView` / `ProfileEditView`.
///
/// Views read it from the environment:
///
///     @Environment(ProfileStore.self) private var profileStore
///     Text(profileStore.profile.name)
///
/// BOUNDARY: only flat value types cross the bridge. The bridge runs the shared
/// suspend/supabase profile fetch on a background scope and delivers every
/// completion / change callback below ON THE MAIN THREAD, so mutating this
/// `@Observable` directly is safe (no extra `DispatchQueue.main` hop).
///
/// READS ARE SIDE-EFFECT-FREE: `FeedData.profile()` is a pure cache read. The
/// store seeds from it synchronously (a neutral, non-dummy snapshot pre-fetch),
/// then triggers ONE explicit `loadProfile` refresh on init and listens for
/// future background refreshes via `observeProfile`.
///
/// NOT-CONFIGURED / OFFLINE / PRE-LOGIN SAFETY: when there is no Supabase client
/// or no session, the bridge returns a neutral `IosProfile` (empty strings + the
/// session email if any) and the refresh is a no-op; nothing crashes. The REAL
/// profile read is BLOCKED until the user's keys + migrations + a live login.
///
/// `@Observable` + `@Environment(Type.self)` is the iOS 17+ Observation pattern
/// and is valid at the project's iOS 18.2 target.
@Observable
final class ProfileStore {
    /// The current user's profile snapshot. Seeded from the bridge cache read,
    /// refreshed from the backend on init and on every background change.
    private(set) var profile: UIProfile

    /// The current user's OWN posts (Phase 4) — what the profile's "meine Posts"
    /// tab shows (posts where `author_id == uid`). Seeded from the pure bridge
    /// cache read and re-read in the SAME `loadProfile`/`observeProfile`
    /// completions that update `profile`, so the tab refreshes with the profile.
    private(set) var ownPosts: [UIPost]

    /// The current user's OWN spots (Phase 4) — what the profile's "meine Spots"
    /// tab shows (spots where `owner_id == uid`). Seeded + refreshed exactly like
    /// `ownPosts` above.
    private(set) var ownSpots: [UISpot]

    init() {
        // Seed synchronously from the pure bridge cache reads (neutral pre-fetch).
        profile = FeedData.profile()
        ownPosts = FeedData.ownPosts()
        ownSpots = FeedData.ownSpots()

        // Observe future background refreshes (delivered on the main thread by
        // the bridge). Register once per store lifetime. The own posts/spots are
        // refreshed by the SAME profile refresh pass, so re-read them here too.
        FeedData.observeProfile { [weak self] in
            self?.reloadFromCache()
        }

        // Trigger the initial explicit remote refresh (no-op when unconfigured /
        // pre-login). Re-read the pure caches in the main-thread completion.
        FeedData.loadProfile { [weak self] in
            self?.reloadFromCache()
        }
    }

    /// Re-read the profile + own posts/spots from the pure bridge caches. Called
    /// from the main-thread `observeProfile`/`loadProfile` completions (and after
    /// a save), keeping the store the single owner of all three snapshots.
    private func reloadFromCache() {
        profile = FeedData.profile()
        ownPosts = FeedData.ownPosts()
        ownSpots = FeedData.ownSpots()
    }

    /// Save an EDIT of the current user's profile (name/city/bio) through the
    /// shared write path. The store stays the SINGLE owner of the profile
    /// snapshot: in the main-thread completion it re-reads `FeedData.profile()`
    /// (which already reflects the optimistic change and, on success, the
    /// reconciled remote values) before forwarding the `Bool` outcome.
    ///
    /// `ok == false` means the change was NOT persisted remotely (offline /
    /// unauth — there is no durable profile queue). The optimistic local value
    /// is still reflected for the session; the caller decides how to surface the
    /// failure. Mirrors the bridge's flat Bool-on-main contract.
    func save(name: String, city: String, bio: String, completion: @escaping (Bool) -> Void) {
        FeedData.updateProfile(name: name, city: city, bio: bio) { [weak self] ok in
            self?.reloadFromCache()
            completion(ok)
        }
    }
}
