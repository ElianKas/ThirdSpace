import SwiftUI
import Shared

/// App-wide state for saved-spot collections, for the native SwiftUI
/// collections tab + the "save to collection" picker (Phase C5/C6 will consume
/// it). Phase C4 adds ONLY this plumbing; no SwiftUI screen yet.
///
/// Thin `@Observable` wrapper around the shared `FeedBridge` collections seam,
/// mirroring `FollowStore` / `ProfileStore` / the `Session` / `Localization`
/// pattern. It owns the current user's collection list, the spots of the
/// currently-viewed collection, and a per-spot membership map that drives the
/// picker checkmarks.
///
/// Views read it from the environment (wired in C5/C6):
///
///     @Environment(CollectionsStore.self) private var collectionsStore
///     ForEach(collectionsStore.collections) { collection in ... }
///
/// BOUNDARY: only flat value types cross the bridge. The bridge runs the shared
/// suspend/supabase collection work on a background scope and delivers every
/// completion / change callback below ON THE MAIN THREAD, so mutating this
/// `@Observable` directly is safe (no extra `DispatchQueue.main` hop).
///
/// READS ARE SIDE-EFFECT-FREE: `FeedData.collections`/`spotsInCollection`/
/// `collectionsContaining`/`isSpotInCollection` are pure cache reads. The store
/// triggers an EXPLICIT `load...` refresh for the subject being viewed and
/// re-reads the pure cache in the main-thread completion, and also listens for
/// background changes via `observeCollections`.
///
/// NOT-CONFIGURED / OFFLINE / PRE-LOGIN SAFETY: when there is no Supabase client
/// or no session, the bridge refresh is a no-op, the pure reads return empty
/// lists, and create/rename/delete/add/remove report `false` (NO durable
/// queue, optimistic state kept). The REAL collections are BLOCKED until the
/// user's keys + migrations + a live login.
///
/// `@Observable` + `@Environment(Type.self)` is the iOS 17+ Observation pattern,
/// valid at the project's iOS 18.2 target.
@Observable
final class CollectionsStore {
    /// The current user's collections. Seeded from the pure bridge cache; re-read
    /// on every background change / `load...` completion.
    private(set) var collections: [UISpotCollection] = []

    /// The spots of the collection currently being viewed. Set by
    /// `loadSpots(inCollection:)`; re-read from the pure bridge cache for
    /// `viewingCollectionId`.
    private(set) var spotsInViewedCollection: [UISpot] = []

    /// The collection id whose spots are currently loaded, so the
    /// `observeCollections` listener re-reads the RIGHT collection's spots on a
    /// background change. "" = nothing being viewed yet.
    private(set) var viewingCollectionId: String = ""

    /// OBSERVABLE membership cache, keyed by `spotId` → the ids of the
    /// collections that contain it. Mirrors the pure
    /// `FeedData.collectionsContaining(spotId:)` read into an `@Observable` map
    /// so the "save to collection" picker's checkmarks re-render when membership
    /// changes via an optimistic add/remove or a background refresh.
    /// `isSpotInCollection(...)` below reads THIS map (seeded from the bridge),
    /// so SwiftUI tracks the dependency and updates live.
    private(set) var membership: [String: [String]] = [:]

    init() {
        // Observe future background collection changes (delivered on the main
        // thread by the bridge). Register once per store lifetime: re-read the
        // list, the viewed collection's spots, and every tracked membership key.
        FeedData.observeCollections { [weak self] in
            self?.reloadFromCache()
        }
        // Seed the list synchronously from the pure cache.
        collections = FeedData.collections()
    }

    /// Re-read everything we track from the pure bridge caches. Called from the
    /// main-thread `observeCollections` / `load*` / write completions, keeping
    /// the store the single owner of the list + spots + membership. Mirrors
    /// `FollowStore.reloadListsFromCache`.
    private func reloadFromCache() {
        collections = FeedData.collections()
        if !viewingCollectionId.isEmpty {
            spotsInViewedCollection = FeedData.spotsInCollection(collectionId: viewingCollectionId)
        }
        // Re-read every tracked spot's membership from the pure bridge cache so
        // the observable map (and thus the picker checkmarks) reflect a
        // background refresh / optimistic change. Keys are only the spots we've
        // looked at, so this stays small.
        for spotId in membership.keys {
            membership[spotId] = FeedData.collectionsContaining(spotId: spotId)
        }
    }

    /// Load (and trigger a background refresh of) the current user's collection
    /// list. Seeds synchronously from the pure cache, then kicks off the explicit
    /// remote refresh and re-reads in the main-thread completion.
    func loadCollections() {
        collections = FeedData.collections()
        FeedData.loadCollections { [weak self] in
            guard let self else { return }
            self.collections = FeedData.collections()
        }
    }

    /// Load (and trigger a background refresh of) the spots saved in `id`.
    /// Seeds synchronously from the pure cache, then re-reads in the main-thread
    /// completion, guarding against a stale subject.
    func loadSpots(inCollection id: String) {
        viewingCollectionId = id
        spotsInViewedCollection = FeedData.spotsInCollection(collectionId: id)
        FeedData.loadSpotsInCollection(collectionId: id) { [weak self] in
            guard let self, self.viewingCollectionId == id else { return }
            self.spotsInViewedCollection = FeedData.spotsInCollection(collectionId: id)
        }
    }

    /// Load (and trigger a background refresh of) which collections contain
    /// `spotId`. Seeds the observable `membership` entry from the pure cache now,
    /// then re-reads it in the main-thread completion.
    func loadMembership(spotId: String) {
        membership[spotId] = FeedData.collectionsContaining(spotId: spotId)
        FeedData.loadMembership(spotId: spotId) { [weak self] in
            guard let self else { return }
            self.membership[spotId] = FeedData.collectionsContaining(spotId: spotId)
        }
    }

    /// Whether `spotId` is saved in `collectionId`. Reads the OBSERVABLE
    /// `membership` map (falling back to the pure `FeedData.isSpotInCollection`
    /// when the spot hasn't been tracked yet) so SwiftUI tracks the dependency
    /// and the picker checkmark re-renders on toggle.
    func isSpotInCollection(collectionId: String, spotId: String) -> Bool {
        if let ids = membership[spotId] {
            return ids.contains(collectionId)
        }
        return FeedData.isSpotInCollection(collectionId: collectionId, spotId: spotId)
    }

    /// Create a collection named `name` (optimistic + best-effort remote push).
    /// Re-reads `collections` in the main-thread completion (the optimistic row
    /// already fired via `observeCollections`), then forwards the `Bool` outcome.
    /// `false` = NOT persisted remotely (offline / unauth / blank — no durable
    /// queue).
    func create(name: String, completion: @escaping (Bool) -> Void) {
        FeedData.createCollection(name: name) { [weak self] ok in
            guard let self else { completion(ok); return }
            self.collections = FeedData.collections()
            completion(ok)
        }
    }

    /// Rename `collectionId` to `name` (same optimistic + best-effort +
    /// flat-Bool contract).
    func rename(collectionId: String, name: String, completion: @escaping (Bool) -> Void) {
        FeedData.renameCollection(collectionId: collectionId, name: name) { [weak self] ok in
            guard let self else { completion(ok); return }
            self.collections = FeedData.collections()
            completion(ok)
        }
    }

    /// Delete `collectionId` (same optimistic + best-effort + flat-Bool
    /// contract). Also re-reads the viewed spots + tracked membership in case the
    /// deleted collection was being viewed / contained tracked spots.
    func delete(collectionId: String, completion: @escaping (Bool) -> Void) {
        FeedData.deleteCollection(collectionId: collectionId) { [weak self] ok in
            guard let self else { completion(ok); return }
            self.reloadFromCache()
            completion(ok)
        }
    }

    /// Save `spotId` into `collectionId` (optimistic + best-effort). Re-reads the
    /// collection list + that spot's membership + the viewed spots in the
    /// main-thread completion, then forwards the `Bool` outcome.
    func addSpot(collectionId: String, spotId: String, completion: @escaping (Bool) -> Void) {
        FeedData.addSpotToCollection(collectionId: collectionId, spotId: spotId) { [weak self] ok in
            guard let self else { completion(ok); return }
            self.collections = FeedData.collections()
            self.membership[spotId] = FeedData.collectionsContaining(spotId: spotId)
            if !self.viewingCollectionId.isEmpty {
                self.spotsInViewedCollection = FeedData.spotsInCollection(collectionId: self.viewingCollectionId)
            }
            completion(ok)
        }
    }

    /// Unsave `spotId` from `collectionId` (same optimistic + best-effort +
    /// flat-Bool contract as `addSpot`).
    func removeSpot(collectionId: String, spotId: String, completion: @escaping (Bool) -> Void) {
        FeedData.removeSpotFromCollection(collectionId: collectionId, spotId: spotId) { [weak self] ok in
            guard let self else { completion(ok); return }
            self.collections = FeedData.collections()
            self.membership[spotId] = FeedData.collectionsContaining(spotId: spotId)
            if !self.viewingCollectionId.isEmpty {
                self.spotsInViewedCollection = FeedData.spotsInCollection(collectionId: self.viewingCollectionId)
            }
            completion(ok)
        }
    }
}
