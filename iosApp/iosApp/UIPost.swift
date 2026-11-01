import SwiftUI
import Shared

// MARK: - Design tokens (mirrored from shared AppColors.kt)

/// App color tokens mirrored from `shared/.../ui/theme/AppColors.kt` so the
/// native SwiftUI feed matches the Compose feed. Kept in sync manually; this is
/// a comparison prototype, not a generated token pipeline.
enum AppColor {
    static let background = Color(hex: "#FAF9F7")
    static let page = Color(hex: "#F4F2EE")
    static let surface = Color(hex: "#FFFFFF")
    static let foreground = Color(hex: "#1A1A1A")
    static let brand = Color(hex: "#F26419")
    static let brandDark = Color(hex: "#C94E0E")
    static let secondary = Color(hex: "#7C3AED") // purple (verified badge)
    static let mutedForeground = Color(hex: "#6B6760")
    static let textFaint = Color(hex: "#9E9A94")
    static let border = Color(hex: "#E8E4DE")
    static let hairline = Color(hex: "#F0EDE8")
    static let navInactive = Color(hex: "#9E9A94")
    static let bodyText = Color(hex: "#3D3D3A")
    /// Red used for destructive actions (e.g. the "Report senden" button).
    /// Mirrors the legacy report button color (#DC2626). This enum is the only
    /// place `Color(hex:)` literals legitimately live.
    static let destructive = Color(hex: "#DC2626")

    /// Translucent white used by the frosted floating surfaces on the map
    /// (search bar, control stack). Mirrors AppColors.frost (ARGB 0xE6FFFFFF).
    static let frost = Color.white.opacity(0.85)
    /// Stronger frosted surface. Mirrors AppColors.frostStrong (ARGB 0xEBFFFFFF).
    static let frostStrong = Color.white.opacity(0.92)
}

// MARK: - Color(hex:) support

extension Color {
    /// Create a `Color` from a "#RRGGBB" (or "RRGGBB") hex string.
    /// Falls back to gray for malformed input so the UI never crashes.
    init(hex: String) {
        var cleaned = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleaned.hasPrefix("#") { cleaned.removeFirst() }

        guard cleaned.count == 6, let value = UInt64(cleaned, radix: 16) else {
            self = Color.gray
            return
        }

        let r = Double((value >> 16) & 0xFF) / 255.0
        let g = Double((value >> 8) & 0xFF) / 255.0
        let b = Double(value & 0xFF) / 255.0
        self = Color(red: r, green: g, blue: b)
    }
}

// MARK: - UIPost (Swift mirror of Kotlin IosPost)

/// Swift-side value type consumed by the SwiftUI views. Built from the bridged
/// `IosPost` returned by `FeedBridge.shared.posts()`.
struct UIPost: Identifiable {
    let id: String
    let spotName: String
    let category: String
    let neighborhood: String
    let verified: Bool
    let checkInCount: Int
    let authorName: String
    let dateLabel: String
    let text: String          // empty string means "no text"
    let score: Int
    let voteState: String     // "UP" / "DOWN" / "NONE"
    let commentCount: Int
    let colorHex: String      // "#RRGGBB"
    let kind: String          // "post" / "spot" (Phase 4)
    let authorUserId: String  // author's uid (= profiles.id), "" when unknown (Phase F3)

    var hasText: Bool { !text.isEmpty }

    /// Phase 4: a "spot" entry is a bare spot with no post. The feed hides the
    /// post-only chrome (caption + vote control + comment button) for it while
    /// keeping the spot header; a tap still opens the spot detail sheet.
    var isBareSpot: Bool { kind == "spot" }

    init(bridged: IosPost) {
        self.id = bridged.id
        self.spotName = bridged.spotName
        self.category = bridged.category
        self.neighborhood = bridged.neighborhood
        self.verified = bridged.verified
        self.checkInCount = Int(bridged.checkInCount)
        self.authorName = bridged.authorName
        self.dateLabel = bridged.dateLabel
        self.text = bridged.text
        self.score = Int(bridged.score)
        self.voteState = bridged.voteState
        self.commentCount = Int(bridged.commentCount)
        self.colorHex = bridged.colorHex
        self.kind = bridged.kind
        self.authorUserId = bridged.authorUserId
    }
}

// MARK: - UIFilterChip (Swift mirror of Kotlin IosFilterChip)

/// Swift-side value type for a map filter chip. Built from the bridged
/// `IosFilterChip` returned by `FeedBridge.shared.mapFilters()`. The shared
/// module is the single source of truth for the chip list (no Swift-only list).
struct UIFilterChip: Identifiable {
    let id = UUID()
    /// Stable machine key (e.g. "chip_cafes"). The shared module owns the
    /// chip order + active flags; the `label` is already localized by the
    /// bridge for the current language.
    let key: String
    let label: String
    let active: Bool

    init(bridged: IosFilterChip) {
        self.key = bridged.key
        self.label = bridged.label
        self.active = bridged.active
    }
}

// MARK: - UIComment (Swift mirror of Kotlin IosComment)

/// Swift-side value type for a comment. Built from the bridged `IosComment`
/// returned by `FeedBridge.shared.comments(postId:)`. The shared module is the
/// single source of truth for comments (no Swift-only list). No replies.
struct UIComment: Identifiable {
    let id: String
    let authorName: String
    let timeLabel: String
    let text: String
    let authorUserId: String  // author's uid (= profiles.id), "" when unknown (Phase F3)

    init(bridged: IosComment) {
        self.id = bridged.id
        self.authorName = bridged.authorName
        self.timeLabel = bridged.timeLabel
        self.text = bridged.text
        self.authorUserId = bridged.authorUserId
    }

    /// Local-only initializer, currently UNUSED. Comment creation now persists
    /// through the bridge (`FeedData.addComment` -> optimistic local
    /// write-through + offline queue to Supabase), after which the Comments
    /// sheet re-reads `comments(postId:)`, so comments are no longer built
    /// locally via this initializer. `authorUserId` defaults to "" so existing
    /// call sites compile unchanged (Phase F3).
    init(id: String, authorName: String, timeLabel: String, text: String, authorUserId: String = "") {
        self.id = id
        self.authorName = authorName
        self.timeLabel = timeLabel
        self.text = text
        self.authorUserId = authorUserId
    }
}

// MARK: - UIReportReason (Swift mirror of Kotlin IosReportReason)

/// Swift-side value type for a report reason. Built from the bridged
/// `IosReportReason` returned by `FeedBridge.shared.reportReasons()`. The shared
/// module is the single source of truth for the reason list (no Swift-only
/// list). `key` is the stable machine id; `label` is the German display string.
struct UIReportReason: Identifiable {
    let key: String
    let label: String
    var id: String { key }

    init(bridged: IosReportReason) {
        self.key = bridged.key
        self.label = bridged.label
    }
}

// MARK: - UILanguage (Swift mirror of Kotlin IosLanguage)

/// Swift-side value type for a selectable UI language, built from the bridged
/// `IosLanguage` returned by `FeedBridge.shared.availableLanguages()`. The
/// shared module (its `AppLanguage` enum) is the single source of truth for the
/// language list (no Swift-only list). `code` is the flat language code
/// ("de" / "en"); `displayName` is the endonym ("Deutsch" / "English"),
/// intentionally NOT translated.
struct UILanguage: Identifiable {
    let code: String
    let displayName: String
    var id: String { code }

    init(bridged: IosLanguage) {
        self.code = bridged.code
        self.displayName = bridged.displayName
    }
}

// MARK: - UIProfile (Swift mirror of Kotlin IosProfile)

/// Swift-side value type for the current user's own profile. Built from the
/// bridged `IosProfile` returned by `FeedBridge.shared.profile()`. The shared
/// module is the single source of truth for the profile (no Swift-only data).
/// There is no avatar field — the avatar initial is derived Swift-side from
/// `name` via `AvatarView`. `monthYear` stays as-is; the localized
/// "seit"/"since" prefix is applied in the view via `loc.strings.joinedSince`.
struct UIProfile {
    let name: String
    let username: String
    let city: String
    let monthYear: String
    let bio: String
    let email: String
    let spotCount: Int
    let postCount: Int
    let followerCount: Int
    let followingCount: Int

    init(bridged: IosProfile) {
        self.name = bridged.name
        self.username = bridged.username
        self.city = bridged.city
        self.monthYear = bridged.monthYear
        self.bio = bridged.bio
        self.email = bridged.email
        self.spotCount = Int(bridged.spotCount)
        self.postCount = Int(bridged.postCount)
        self.followerCount = Int(bridged.followerCount)
        self.followingCount = Int(bridged.followingCount)
    }
}

// MARK: - UISpot (Swift mirror of Kotlin IosSpot)

/// Swift-side value type for a spot rendered on the Mapbox map. Built from the
/// bridged `IosSpot` returned by `FeedBridge.shared.spots()`. The shared module
/// is the single source of truth for the spots (and for the category marker
/// color): `colorHex` is resolved shared-side from `SpotCategory`, so no
/// category hex literal is hardcoded in Swift — it feeds `Color(hex:)` directly.
struct UISpot: Identifiable {
    let id: String
    let name: String
    let category: String
    let neighborhood: String
    let verified: Bool
    let checkInCount: Int
    let lat: Double
    let lng: Double
    let colorHex: String      // "#RRGGBB" from shared SpotCategory

    init(bridged: IosSpot) {
        self.id = bridged.id
        self.name = bridged.name
        self.category = bridged.category
        self.neighborhood = bridged.neighborhood
        self.verified = bridged.verified
        self.checkInCount = Int(bridged.checkInCount)
        self.lat = bridged.lat
        self.lng = bridged.lng
        self.colorHex = bridged.colorHex
    }
}

enum FeedData {
    /// Pull the bridged posts from the shared framework and map to `UIPost`.
    static func posts() -> [UIPost] {
        FeedBridge.shared.posts().map { UIPost(bridged: $0) }
    }

    /// Pull the bridged spots from the shared framework and map to `UISpot`.
    /// Single source of truth for the map pins (coordinates + category color).
    static func spots() -> [UISpot] {
        FeedBridge.shared.spots().map { UISpot(bridged: $0) }
    }

    /// Pull the CURRENT user's OWN posts (Phase 4) from the shared framework and
    /// map to `UIPost` — what the profile's "meine Posts" tab shows (posts where
    /// `author_id == uid`), replacing the old global-feed derivation. A PURE
    /// cache read; refreshed by the existing `loadProfile`/`observeProfile` flow.
    static func ownPosts() -> [UIPost] {
        FeedBridge.shared.ownPosts().map { UIPost(bridged: $0) }
    }

    /// Pull the CURRENT user's OWN spots (Phase 4) from the shared framework and
    /// map to `UISpot` — what the profile's "meine Spots" tab shows (spots where
    /// `owner_id == uid`), replacing the old global-feed derivation. A PURE cache
    /// read; refreshed by the existing `loadProfile`/`observeProfile` flow.
    static func ownSpots() -> [UISpot] {
        FeedBridge.shared.ownSpots().map { UISpot(bridged: $0) }
    }

    /// Pull the bridged current-user profile from the shared framework. Single
    /// source of truth for the profile screen. A PURE cache read: it does NOT
    /// trigger a remote fetch (use `loadProfile` for that). Returns a neutral,
    /// non-dummy snapshot until the first `loadProfile` populates the real row.
    static func profile() -> UIProfile {
        UIProfile(bridged: FeedBridge.shared.profile())
    }

    /// Kick off a background remote refresh of the current user's profile. The
    /// flat `completion` fires on the MAIN thread once the refresh is scheduled;
    /// callers re-read `profile()` in it to pick up the real `profiles` row.
    /// Reads alone no longer trigger a remote fetch, so this is the explicit
    /// pull path. Mirrors `loadFeed`.
    static func loadProfile(completion: @escaping () -> Void) {
        FeedBridge.shared.loadProfile(completion: completion)
    }

    /// Register a flat listener invoked on the MAIN thread whenever the shared
    /// cached profile changes from a background refresh, so the UI can re-read
    /// `profile()` live. Register once per view lifetime. Mirrors `observeFeed`.
    static func observeProfile(_ listener: @escaping () -> Void) {
        FeedBridge.shared.observeProfile(listener: listener)
    }

    /// Save an EDIT of the current user's profile (name/city/bio) through the
    /// shared `FeedBridge.updateProfile` write path: an OPTIMISTIC in-memory
    /// update (surfaced live via `observeProfile`) + a best-effort remote UPDATE
    /// of the own `profiles` row. The flat `completion` fires on the MAIN thread
    /// with the `Bool` outcome; `false` means NOT persisted remotely (offline /
    /// unauth — there is no durable profile queue), so callers must not treat it
    /// as success. FLAT: String args + a Bool closure; no suspend/supabase types.
    static func updateProfile(
        name: String,
        city: String,
        bio: String,
        completion: @escaping (Bool) -> Void
    ) {
        // The bridged Kotlin closure hands back a boxed `KotlinBoolean`; unbox
        // it to a Swift `Bool` at the boundary (mirrors `vote`'s Int32 bridging).
        FeedBridge.shared.updateProfile(name: name, city: city, bio: bio) { ok in
            completion(ok.boolValue)
        }
    }

    /// Empty the shared profile cache on sign-out via the flat
    /// `FeedBridge.clearProfile` hook, so a DIFFERENT user signing in on the
    /// same app launch never briefly sees the prior user's profile/posts/spots.
    /// Synchronous + flat; the resulting shared `onChanged` is delivered to any
    /// `observeProfile` listener on the MAIN thread by the bridge.
    static func clearProfile() {
        FeedBridge.shared.clearProfile()
    }

    /// Pull the bridged report reasons from the shared framework. Single source
    /// of truth for the report sheet's selectable reasons.
    static func reportReasons() -> [UIReportReason] {
        FeedBridge.shared.reportReasons().map { UIReportReason(bridged: $0) }
    }

    /// Pull the bridged map filter chips from the shared framework.
    static func mapFilters() -> [UIFilterChip] {
        FeedBridge.shared.mapFilters().map { UIFilterChip(bridged: $0) }
    }

    /// Pull the bridged selectable UI languages from the shared framework.
    /// Single source of truth for the profile language picker.
    static func availableLanguages() -> [UILanguage] {
        FeedBridge.shared.availableLanguages().map { UILanguage(bridged: $0) }
    }

    /// Pull the bridged comments for a post from the shared framework. Empty
    /// when the post has no comments (drives the Comments sheet empty state).
    static func comments(postId: String) -> [UIComment] {
        FeedBridge.shared.comments(postId: postId).map { UIComment(bridged: $0) }
    }

    /// Kick off a background remote refresh of the feed. The flat `completion`
    /// fires on the MAIN thread once the refresh is scheduled; callers re-read
    /// `posts()` in/after it to pick up the (cache-first, then refreshed) data.
    static func loadFeed(completion: @escaping () -> Void) {
        FeedBridge.shared.loadFeed(completion: completion)
    }

    /// Register a flat listener invoked on the MAIN thread whenever the shared
    /// feed cache changes from a background refresh/flush, so the UI can re-read
    /// `posts()` / `comments(postId:)` live. Register once per view lifetime.
    static func observeFeed(_ listener: @escaping () -> Void) {
        FeedBridge.shared.observeFeed(listener: listener)
    }

    /// Persist a comment through the shared repository (optimistic write-through
    /// + offline queue). The flat `completion` fires on the MAIN thread after the
    /// shared write-through, so callers re-read `comments(postId:)` to show it.
    static func addComment(postId: String, text: String, completion: @escaping () -> Void) {
        FeedBridge.shared.addComment(postId: postId, text: text, completion: completion)
    }

    /// Kick off a background remote refresh of one post's comments. The flat
    /// `completion` fires on the MAIN thread once the refresh is scheduled;
    /// callers re-read `comments(postId:)` in it to pick up the refreshed data.
    /// Reads alone no longer trigger a remote fetch, so this is the explicit
    /// pull path.
    static func loadComments(postId: String, completion: @escaping () -> Void) {
        FeedBridge.shared.loadComments(postId: postId, completion: completion)
    }

    /// Persist a new post for `spotId` with `text` through the shared repository
    /// (optimistic write-through at the top of the feed + offline queue). Empty
    /// `text` is allowed (a pure check-in). The flat `completion` fires on the
    /// MAIN thread after the shared write-through, so callers re-read `posts()`
    /// to show it. FLAT: String args + plain closure; no suspend/supabase types.
    static func createPost(spotId: String, text: String, completion: @escaping () -> Void) {
        FeedBridge.shared.createPost(spotId: spotId, text: text, completion: completion)
    }

    /// Register a vote tap on `postId` through the shared repository. `direction`
    /// is `1` (up tapped) or `-1` (down tapped); the shared toggle state machine
    /// resolves the new score/vote-state, applies it optimistically, and queues
    /// the latest intent. The flat `completion` fires on the MAIN thread after
    /// the shared write-through, so callers re-read `posts()` to reflect the
    /// updated score/voteState. FLAT: Int/String args + plain closure.
    static func vote(postId: String, direction: Int, completion: @escaping () -> Void) {
        FeedBridge.shared.vote(postId: postId, direction: Int32(direction), completion: completion)
    }

    /// The fixed category labels (8 `SpotCategory` entries) from the shared
    /// bridge, in declaration order. The create-spot sheet renders its category
    /// pills from this list, so Swift never hardcodes the category list.
    static func categories() -> [String] {
        FeedBridge.shared.categories()
    }

    /// The shared "#RRGGBB" marker color for a category label, resolved by the
    /// shared `SpotCategory`. Used to tint the selected category pill without
    /// hardcoding any hex Swift-side.
    static func categoryColorHex(_ category: String) -> String {
        FeedBridge.shared.categoryColorHex(category: category)
    }

    /// Resolve a category label (one of `categories()`) from a tapped Mapbox
    /// POI's `maki` icon id and symbol `clazz`, via the shared `SpotCategory`
    /// mapping (single source of truth). Flat String in/out. Used by the map's
    /// POI-adopt flow so the create-spot sheet preselects a sensible category.
    static func categoryForMaki(maki: String, clazz: String) -> String {
        FeedBridge.shared.categoryForMaki(maki: maki, clazz: clazz)
    }

    /// Persist a new spot through the shared repository so it appears as a pin.
    /// All flat types cross the boundary. The caller supplies the `id`.
    static func addSpot(
        id: String,
        name: String,
        category: String,
        neighborhood: String,
        lat: Double,
        lng: Double
    ) {
        FeedBridge.shared.addSpot(
            id: id,
            name: name,
            category: category,
            neighborhood: neighborhood,
            lat: lat,
            lng: lng
        )
    }

    // MARK: - Follows + other-user profiles (Phase F4)

    /// The current authenticated user's id (= `profiles.id`), or "" when there
    /// is no session / the client is unconfigured. A flat, synchronous pure
    /// read (no callback). Used Swift-side (Phase F5) to hide the follow button
    /// on your own profile and suppress self author-taps in the feed/comments.
    static func currentUserId() -> String {
        FeedBridge.shared.currentUserId()
    }

    /// Follow `targetId` through the shared follow seam (optimistic + best-effort
    /// remote push). The flat `completion` fires on the MAIN thread with the
    /// `Bool` outcome; `false` means NOT persisted remotely (offline / unauth /
    /// self-follow / already following — no durable queue). The bridged Kotlin
    /// closure hands back a boxed `KotlinBoolean`; unbox it at the boundary.
    static func followUser(targetId: String, completion: @escaping (Bool) -> Void) {
        FeedBridge.shared.followUser(targetId: targetId) { ok in
            completion(ok.boolValue)
        }
    }

    /// Unfollow `targetId` (same optimistic + best-effort + flat-Bool-on-main
    /// contract as `followUser`).
    static func unfollowUser(targetId: String, completion: @escaping (Bool) -> Void) {
        FeedBridge.shared.unfollowUser(targetId: targetId) { ok in
            completion(ok.boolValue)
        }
    }

    /// Whether the current user follows `targetId` — a PURE cache read (no
    /// remote fetch). `false` before any `loadFollowState` / optimistic follow.
    static func isFollowing(targetId: String) -> Bool {
        FeedBridge.shared.isFollowing(targetId: targetId)
    }

    /// Kick off a background refresh of whether the current user follows
    /// `targetId`. The flat `completion` fires on the MAIN thread once the
    /// refresh is scheduled; callers re-read `isFollowing(targetId:)` in it.
    static func loadFollowState(targetId: String, completion: @escaping () -> Void) {
        FeedBridge.shared.loadFollowState(targetId: targetId, completion: completion)
    }

    /// The cached FOLLOWERS of `userId` as `UIFollowUser` — a PURE cache read
    /// (empty before the first `loadFollowers`). No remote fetch.
    static func followers(userId: String) -> [UIFollowUser] {
        FeedBridge.shared.followers(userId: userId).map { UIFollowUser(bridged: $0) }
    }

    /// The cached FOLLOWING list of `userId` as `UIFollowUser` — a PURE cache
    /// read (empty before the first `loadFollowing`). No remote fetch.
    static func following(userId: String) -> [UIFollowUser] {
        FeedBridge.shared.following(userId: userId).map { UIFollowUser(bridged: $0) }
    }

    /// Kick off a background refresh of `userId`'s FOLLOWERS list. The flat
    /// `completion` fires on the MAIN thread once scheduled; callers re-read
    /// `followers(userId:)` in it.
    static func loadFollowers(userId: String, completion: @escaping () -> Void) {
        FeedBridge.shared.loadFollowers(userId: userId, completion: completion)
    }

    /// Kick off a background refresh of `userId`'s FOLLOWING list. The flat
    /// `completion` fires on the MAIN thread once scheduled; callers re-read
    /// `following(userId:)` in it.
    static func loadFollowing(userId: String, completion: @escaping () -> Void) {
        FeedBridge.shared.loadFollowing(userId: userId, completion: completion)
    }

    /// Kick off a background refresh of `userId`'s follower + following COUNTS
    /// (so a profile page can refresh another user's counts). The flat
    /// `completion` fires on the MAIN thread once scheduled; callers re-read
    /// `followerCount`/`followingCount` in it.
    static func loadFollowCounts(userId: String, completion: @escaping () -> Void) {
        FeedBridge.shared.loadFollowCounts(userId: userId, completion: completion)
    }

    /// Cached follower count of `userId` — a PURE read (0 before the first
    /// `loadFollowCounts`). The bridged value is a boxed `Int32`; unbox it.
    static func followerCount(userId: String) -> Int {
        Int(FeedBridge.shared.followerCount(userId: userId))
    }

    /// Cached following count of `userId` — a PURE read (0 before the first
    /// `loadFollowCounts`).
    static func followingCount(userId: String) -> Int {
        Int(FeedBridge.shared.followingCount(userId: userId))
    }

    /// Register a flat listener invoked on the MAIN thread whenever the shared
    /// follow graph changes (background refresh or optimistic follow/unfollow),
    /// so the UI can re-read the follow lists / counts / state. Register once
    /// per view lifetime. Mirrors `observeProfile`.
    static func observeFollows(_ listener: @escaping () -> Void) {
        FeedBridge.shared.observeFollows(listener: listener)
    }

    /// The last-fetched snapshot of ANOTHER user's profile for `userId` — a PURE
    /// read from the bridge's in-memory holder (populated by `loadOtherProfile`).
    /// Returns a neutral `UIProfile` when not yet fetched. Does NOT fetch.
    static func otherProfile(userId: String) -> UIProfile {
        UIProfile(bridged: FeedBridge.shared.otherProfile(userId: userId))
    }

    /// Kick off a best-effort background fetch of ANOTHER user's profile (row +
    /// own post/spot counts + real follower/following counts). The flat
    /// `completion` fires on the MAIN thread once the fetch resolves; callers
    /// re-read `otherProfile(userId:)` in it. No durable cache is kept.
    static func loadOtherProfile(userId: String, completion: @escaping () -> Void) {
        FeedBridge.shared.loadOtherProfile(userId: userId, completion: completion)
    }
}
