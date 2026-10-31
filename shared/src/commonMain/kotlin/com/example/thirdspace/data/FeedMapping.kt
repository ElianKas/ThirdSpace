package com.example.thirdspace.data

/**
 * Shared helpers that bridge the UI-facing [Post] / [SampleData] into the flat
 * internal [FeedPost] shape used by the Phase B feed cache.
 *
 * Used ONLY for the offline SEED (mirroring the spots cache seeding): so the
 * feed is not empty before the first remote refresh / pre-login. The seed rows
 * are overwritten by the first successful remote replace-all once configured +
 * authenticated. Live data comes from `public.post_feed` via
 * [SupabaseFeedRepository], not from here.
 */
object FeedMapping {

    /**
     * Build a flat [FeedPost] seed from a sample [post] at index [index],
     * relative to [nowMillis]. SampleData has no real timestamps (only baked
     * German labels), so the seed synthesizes a DESCENDING created_at: each
     * successive sample post is one hour older, preserving the sample feed's
     * order under the cache's `ORDER BY created_at_epoch DESC`. The baked
     * `dateLabel` is intentionally dropped — the bridge formats a fresh label
     * from this epoch via [RelativeTime], so seeded + remote posts format
     * identically.
     */
    fun fromSamplePost(post: Post, index: Int, nowMillis: Long): FeedPost {
        val oneHour = 60L * 60L * 1000L
        return FeedPost(
            id = post.id,
            authorName = post.authorName,
            spotName = post.spot.name,
            category = post.spot.category,
            neighborhood = post.spot.neighborhood,
            verified = post.spot.verified,
            text = post.text.orEmpty(),
            createdAtEpoch = nowMillis - index * oneHour,
            score = post.score,
            commentCount = post.commentCount,
            myVote = when (post.myVote) {
                VoteState.UP -> "UP"
                VoteState.DOWN -> "DOWN"
                VoteState.NONE -> "NONE"
            },
        )
    }

    /** The default post seed: the sample feed, newest-first, relative to [nowMillis]. */
    fun defaultPostSeed(nowMillis: Long): List<FeedPost> =
        SampleData.posts.mapIndexed { index, post -> fromSamplePost(post, index, nowMillis) }

    /**
     * Build a flat [FeedComment] seed from a sample [Comment] at index [index],
     * relative to [nowMillis]. Like the post seed, synthesizes a descending
     * created_at (30 min apart) since SampleData comments only carry baked
     * German `timeLabel`s; the bridge re-formats from the epoch.
     */
    fun fromSampleComment(comment: Comment, index: Int, nowMillis: Long): FeedComment {
        val halfHour = 30L * 60L * 1000L
        return FeedComment(
            id = comment.id,
            postId = comment.postId,
            authorName = comment.authorName,
            text = comment.text,
            createdAtEpoch = nowMillis - index * halfHour,
        )
    }

    /** The default comment seed for [postId] (its sample comments), relative to [nowMillis]. */
    fun defaultCommentSeed(postId: String, nowMillis: Long): List<FeedComment> =
        SampleData.commentsFor(postId)
            .mapIndexed { index, comment -> fromSampleComment(comment, index, nowMillis) }
}
