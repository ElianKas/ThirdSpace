package com.example.thirdspace.data

/**
 * Internal flat feed-post shape, mirroring the remote `public.post_feed` view
 * and the local `cachedPosts` table 1:1.
 *
 * DESIGN DECISION (documented): Phase B introduces this flat [FeedPost] instead
 * of reusing the existing [Post] (which nests a [Spot] carrying map coords the
 * feed does not need). The feed never renders the spot's lat/lng, so forcing a
 * nested [Spot] with placeholder 0.0 coords would be dishonest; a flat shape
 * matching the view is cleaner and maps directly to the bridge's flat `IosPost`.
 *
 * The existing [Post] / [SampleData.posts] stay as-is (used for the offline
 * SEED so the feed is never empty pre-login); [FeedMapping.fromSamplePost]
 * bridges a seed [Post] into this flat shape.
 *
 * [createdAtEpoch] is epoch MILLIS — the post's remote `created_at` timestamptz
 * parsed shared-side (see [RelativeTime.parseIsoToEpochMillis]). It stays INSIDE
 * the shared module; the bridge formats it to a `dateLabel` String via
 * [RelativeTime.format] so NO Long ever crosses the flat FeedBridge boundary.
 *
 * [myVote] is a flat "UP"/"DOWN"/"NONE" string (the view returns "NONE" for now;
 * voting is deferred). All fields are flat primitives — no nullables.
 *
 * PHASE 4: the feed now surfaces public SPOTS as well as POSTS (via the remote
 * `public.feed_entries` view). [kind] is a flat "post" / "spot" discriminator —
 * "post" is the DEFAULT so existing construction (the offline seed, tests, the
 * `post_feed` path) compiles unchanged. For a "spot" entry (a spot with no
 * post), [id] holds the view's `entry_id`, which carries a `spot:` prefix, so
 * the client's list identity (`ForEach(id:)`) stays unique against post uuids;
 * such an entry has an empty [text], 0 [commentCount], 0 [score], myVote
 * "NONE", and its [authorName] is the spot owner's display name.
 */
data class FeedPost(
    val id: String,
    val authorName: String,
    val spotName: String,
    val category: String,
    val neighborhood: String,
    val verified: Boolean,
    val text: String,
    val createdAtEpoch: Long,
    val score: Int,
    val commentCount: Int,
    val myVote: String,
    val kind: String = "post",
)

/**
 * Internal flat feed-comment shape, mirroring `public.post_comments` + the
 * resolved author display name, and the local `cachedComments` table 1:1.
 *
 * Unlike the UI-facing [Comment] (which carries a pre-formatted `timeLabel`
 * String), [FeedComment] carries [createdAtEpoch] (epoch millis) so the cache
 * can order chronologically and the bridge can format a localized relative
 * `timeLabel` at read time via [RelativeTime.format]. The epoch stays INSIDE
 * the shared module (no Long crosses the boundary).
 */
data class FeedComment(
    val id: String,
    val postId: String,
    val authorName: String,
    val text: String,
    val createdAtEpoch: Long,
)
