package com.example.thirdspace.data

import com.example.thirdspace.db.ThirdSpaceDb

/**
 * An entry in the outbound pending-comment-insert queue: a locally-created
 * comment ([id], [postId], [text]) that still needs a remote insert, tagged
 * with its [authorId] (from the session at enqueue time) and [enqueuedAt]
 * (epoch millis, for oldest-first flush order). Mirrors [PendingSpotInsert].
 */
data class PendingCommentInsert(
    val id: String,
    val postId: String,
    val authorId: String,
    val text: String,
    val enqueuedAt: Long,
)

/**
 * An entry in the outbound pending-post-insert queue: a locally-created post
 * ([id] = the LOCAL optimistic id, used purely as the dequeue handle) on
 * [spotId], authored by [authorId] (from the session at enqueue time), with
 * [text] (may be empty — a post can be a pure check-in), tagged with
 * [enqueuedAt] (epoch millis, oldest-first flush order). Mirrors
 * [PendingCommentInsert]. The real DB-generated post id arrives on the next
 * remote refresh; this local id never reaches the remote insert.
 */
data class PendingPostInsert(
    val id: String,
    val spotId: String,
    val authorId: String,
    val text: String,
    val enqueuedAt: Long,
)

/**
 * The user's LATEST vote intent for a post, as queued for a remote push.
 * Keyed by [postId] (one row per post — latest-intent-wins). [value] is the
 * RESOLVED intent: `1` = upvote, `-1` = downvote, `0` = CLEAR (delete the
 * user's remote vote). [enqueuedAt] (epoch millis) orders the flush
 * oldest-first. The `0` clear intent is specific to this queue (the remote
 * `post_votes` row only ever holds +/-1).
 */
data class PendingVote(
    val postId: String,
    val value: Int,
    val enqueuedAt: Long,
)

/**
 * The local feed store seam: a synchronous cache of feed posts + per-post
 * comments (the source of truth the feed/comments UI reads), plus an outbound
 * queue of comment inserts awaiting remote push.
 *
 * An interface (mirroring [SpotCache]) so [FeedRepository] / [CommentRepository]
 * can be unit-tested against a trivial in-memory fake (no SQLDelight / no
 * driver), while the production implementation ([LocalFeedCache]) is
 * SQLDelight-backed. All operations are synchronous — no suspend / coroutine /
 * supabase type appears here, keeping the flat-boundary guarantees intact.
 */
interface FeedCache {
    // Posts (read-only mirror of the post_feed view).

    /** All cached posts, newest-first (by created_at). */
    fun getCachedPosts(): List<FeedPost>

    /** Replace the ENTIRE post cache with [posts] (remote replace-all refresh). */
    fun replaceAllPosts(posts: List<FeedPost>)

    /** Upsert a single [post] into the cache (used by the offline seed). */
    fun upsertPost(post: FeedPost)

    // Comments (per-post cache + optimistic local write-through).

    /** Cached comments for [postId], oldest-first. */
    fun getCachedComments(postId: String): List<FeedComment>

    /** Replace the cached comments for [postId] with [comments] (remote refresh). */
    fun replaceCommentsForPost(postId: String, comments: List<FeedComment>)

    /** Upsert a single [comment] (optimistic local write-through on create). */
    fun upsertComment(comment: FeedComment)

    // Outbound pending-comment queue.

    /** Enqueue a pending comment insert. Mirrors the spots pending queue. */
    fun enqueuePendingComment(
        id: String,
        postId: String,
        authorId: String,
        text: String,
        enqueuedAt: Long,
    )

    /** The pending comment inserts, oldest-first. */
    fun pendingComments(): List<PendingCommentInsert>

    /** Remove the pending comment with [id] (after a successful remote push). */
    fun dequeuePendingComment(id: String)

    // Outbound pending-post queue (post creation).

    /** Enqueue a pending post insert. Mirrors the pending-comment queue. */
    fun enqueuePendingPost(
        id: String,
        spotId: String,
        authorId: String,
        text: String,
        enqueuedAt: Long,
    )

    /** The pending post inserts, oldest-first. */
    fun pendingPosts(): List<PendingPostInsert>

    /** Remove the pending post with [id] (after a successful remote push). */
    fun dequeuePendingPost(id: String)

    // Voting: optimistic cache update + latest-intent outbound queue.

    /**
     * Optimistically set a cached post's [newScore] + [newMyVote] in place.
     * No-op when the post is not cached (defensive). Reads the current row and
     * rewrites it via [upsertPost] so the score/my_vote reflect the user's tap
     * immediately (reconciled by the next remote replace-all).
     */
    fun updatePostVote(postId: String, newScore: Int, newMyVote: String)

    /**
     * Enqueue (latest-intent-wins) the user's vote intent for [postId]. [value]
     * is `1`/`-1`/`0` (0 = clear). Keyed by [postId]: a newer intent REPLACES
     * the queued one.
     */
    fun upsertPendingVote(postId: String, value: Int, enqueuedAt: Long)

    /** The pending vote intents, oldest-first. */
    fun pendingVotes(): List<PendingVote>

    /** Remove the pending vote for [postId] (after a successful remote push). */
    fun dequeuePendingVote(postId: String)
}

/**
 * SQLDelight-backed [FeedCache] over [ThirdSpaceDb] (the same DB that backs the
 * spots cache; the Phase B tables are additive).
 *
 * Maps rows <-> the flat [FeedPost] / [FeedComment] shapes 1:1 (booleans stored
 * as INTEGER 0/1; `created_at` stored as epoch millis). Construct with [create]
 * for production (platform driver) or pass any [ThirdSpaceDb] (e.g. one built
 * on an in-memory JdbcSqliteDriver) for host tests.
 */
class LocalFeedCache(
    private val db: ThirdSpaceDb,
) : FeedCache {

    private val queries get() = db.thirdSpaceDbQueries

    override fun getCachedPosts(): List<FeedPost> =
        queries.selectAllCachedPosts { id, authorName, spotName, category, neighborhood, verified, text, createdAtEpoch, score, commentCount, myVote ->
            FeedPost(
                id = id,
                authorName = authorName,
                spotName = spotName,
                category = category,
                neighborhood = neighborhood,
                verified = verified != 0L,
                text = text,
                createdAtEpoch = createdAtEpoch,
                score = score.toInt(),
                commentCount = commentCount.toInt(),
                myVote = myVote,
            )
        }.executeAsList()

    override fun replaceAllPosts(posts: List<FeedPost>) {
        queries.transaction {
            queries.deleteAllCachedPosts()
            posts.forEach { upsertPostRow(it) }
        }
    }

    override fun upsertPost(post: FeedPost) {
        upsertPostRow(post)
    }

    override fun getCachedComments(postId: String): List<FeedComment> =
        queries.selectCachedComments(postId) { id, pid, authorName, text, createdAtEpoch ->
            FeedComment(
                id = id,
                postId = pid,
                authorName = authorName,
                text = text,
                createdAtEpoch = createdAtEpoch,
            )
        }.executeAsList()

    override fun replaceCommentsForPost(postId: String, comments: List<FeedComment>) {
        queries.transaction {
            queries.deleteCommentsForPost(postId)
            comments.forEach { upsertCommentRow(it) }
        }
    }

    override fun upsertComment(comment: FeedComment) {
        upsertCommentRow(comment)
    }

    override fun enqueuePendingComment(
        id: String,
        postId: String,
        authorId: String,
        text: String,
        enqueuedAt: Long,
    ) {
        queries.enqueuePendingComment(
            id = id,
            post_id = postId,
            author_id = authorId,
            text = text,
            enqueued_at = enqueuedAt,
        )
    }

    override fun pendingComments(): List<PendingCommentInsert> =
        queries.selectPendingComments { id, postId, authorId, text, enqueuedAt ->
            PendingCommentInsert(
                id = id,
                postId = postId,
                authorId = authorId,
                text = text,
                enqueuedAt = enqueuedAt,
            )
        }.executeAsList()

    override fun dequeuePendingComment(id: String) {
        queries.deletePendingComment(id)
    }

    override fun enqueuePendingPost(
        id: String,
        spotId: String,
        authorId: String,
        text: String,
        enqueuedAt: Long,
    ) {
        queries.enqueuePendingPost(
            id = id,
            spot_id = spotId,
            author_id = authorId,
            text = text,
            enqueued_at = enqueuedAt,
        )
    }

    override fun pendingPosts(): List<PendingPostInsert> =
        queries.selectPendingPosts { id, spotId, authorId, text, enqueuedAt ->
            PendingPostInsert(
                id = id,
                spotId = spotId,
                authorId = authorId,
                text = text,
                enqueuedAt = enqueuedAt,
            )
        }.executeAsList()

    override fun dequeuePendingPost(id: String) {
        queries.deletePendingPost(id)
    }

    override fun updatePostVote(postId: String, newScore: Int, newMyVote: String) {
        val existing = getCachedPosts().firstOrNull { it.id == postId } ?: return
        upsertPostRow(existing.copy(score = newScore, myVote = newMyVote))
    }

    override fun upsertPendingVote(postId: String, value: Int, enqueuedAt: Long) {
        queries.upsertPendingVote(post_id = postId, vote_value = value.toLong(), enqueued_at = enqueuedAt)
    }

    override fun pendingVotes(): List<PendingVote> =
        queries.selectPendingVotes { postId, value, enqueuedAt ->
            PendingVote(postId = postId, value = value.toInt(), enqueuedAt = enqueuedAt)
        }.executeAsList()

    override fun dequeuePendingVote(postId: String) {
        queries.deletePendingVote(postId)
    }

    private fun upsertPostRow(post: FeedPost) {
        queries.upsertCachedPost(
            id = post.id,
            author_name = post.authorName,
            spot_name = post.spotName,
            category = post.category,
            neighborhood = post.neighborhood,
            verified = if (post.verified) 1L else 0L,
            text = post.text,
            created_at_epoch = post.createdAtEpoch,
            score = post.score.toLong(),
            comment_count = post.commentCount.toLong(),
            my_vote = post.myVote,
        )
    }

    private fun upsertCommentRow(comment: FeedComment) {
        queries.upsertCachedComment(
            id = comment.id,
            post_id = comment.postId,
            author_name = comment.authorName,
            text = comment.text,
            created_at_epoch = comment.createdAtEpoch,
        )
    }

    companion object {
        /** Build a production cache on the platform SQLDelight driver. */
        fun create(): LocalFeedCache =
            LocalFeedCache(ThirdSpaceDb(createSpotDbDriver()))
    }
}
