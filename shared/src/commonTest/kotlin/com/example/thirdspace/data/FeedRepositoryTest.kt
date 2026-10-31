package com.example.thirdspace.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OFFLINE-provable logic tests for [FeedRepository] (posts) and
 * [CommentRepository] (comments), using an in-memory FAKE [FeedCache] and a
 * FAKE [FeedRemoteSource] — no real backend, network, device, or SQLDelight
 * driver. Runs on BOTH shared targets. The real SQLDelight round-trip is
 * covered separately on the Android host (LocalFeedCacheDbTest).
 *
 * Like CachingSpotRepositoryTest, the background refresh/push runs on a
 * [Dispatchers.Unconfined] scope so the launched coroutine executes
 * synchronously up to the fake's (non-suspending) suspend calls; assertions
 * after a getPosts()/addComment() observe the completed background work
 * deterministically. The REAL Supabase round-trip stays BLOCKED-until-keys and
 * is NOT faked as a network round-trip.
 */
class FeedRepositoryTest {

    /** Synchronous in-memory [FeedCache] fake (no SQLDelight). */
    private class FakeFeedCache : FeedCache {
        val posts = LinkedHashMap<String, FeedPost>()
        val comments = LinkedHashMap<String, FeedComment>()
        val pending = mutableListOf<PendingCommentInsert>()

        override fun getCachedPosts(): List<FeedPost> =
            posts.values.sortedByDescending { it.createdAtEpoch }

        override fun replaceAllPosts(posts: List<FeedPost>) {
            this.posts.clear()
            posts.forEach { this.posts[it.id] = it }
        }

        override fun upsertPost(post: FeedPost) { posts[post.id] = post }

        override fun getCachedComments(postId: String): List<FeedComment> =
            comments.values.filter { it.postId == postId }.sortedBy { it.createdAtEpoch }

        override fun replaceCommentsForPost(postId: String, comments: List<FeedComment>) {
            this.comments.values.removeAll { it.postId == postId }
            comments.forEach { this.comments[it.id] = it }
        }

        override fun upsertComment(comment: FeedComment) { comments[comment.id] = comment }

        override fun enqueuePendingComment(
            id: String,
            postId: String,
            authorId: String,
            text: String,
            enqueuedAt: Long,
        ) {
            pending.removeAll { it.id == id }
            pending.add(PendingCommentInsert(id, postId, authorId, text, enqueuedAt))
        }

        override fun pendingComments(): List<PendingCommentInsert> =
            pending.sortedBy { it.enqueuedAt }

        override fun dequeuePendingComment(id: String) { pending.removeAll { it.id == id } }
    }

    /** Controllable fake remote: records inserts, returns canned fetch rows. */
    private class FakeFeedRemote(
        var available: Boolean,
        var feedResult: List<FeedPost> = emptyList(),
        var commentsResult: List<FeedComment> = emptyList(),
        var failInsert: Boolean = false,
    ) : FeedRemoteSource {
        val inserted = mutableListOf<Triple<String, String, String>>()
        var feedFetchCount = 0
        var commentFetchCount = 0

        override fun isAvailable(): Boolean = available
        override suspend fun fetchFeed(): List<FeedPost> {
            feedFetchCount++
            return feedResult
        }
        override suspend fun fetchComments(postId: String): List<FeedComment> {
            commentFetchCount++
            return commentsResult.filter { it.postId == postId }
        }
        override suspend fun insertComment(postId: String, authorId: String, text: String): Boolean {
            if (!available) return false
            if (failInsert) throw IllegalStateException("insert failed")
            inserted.add(Triple(postId, authorId, text))
            return true
        }
    }

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun post(id: String, epoch: Long, category: String = "Bar") = FeedPost(
        id = id,
        authorName = "Author $id",
        spotName = "Spot $id",
        category = category,
        neighborhood = "Mitte",
        verified = true,
        text = "Text $id",
        createdAtEpoch = epoch,
        score = 10,
        commentCount = 1,
        myVote = "NONE",
    )

    private fun comment(id: String, postId: String, epoch: Long) = FeedComment(
        id = id,
        postId = postId,
        authorName = "Author $id",
        text = "Comment $id",
        createdAtEpoch = epoch,
    )

    // --- FeedRepository (posts) ---

    @Test
    fun getPostsServesLocalCacheImmediatelyNewestFirst() {
        val cache = FakeFeedCache()
        val remote = FakeFeedRemote(available = false)
        val repo = FeedRepository(
            cache = cache, scope = scope, remote = remote,
            isAuthenticated = { false }, clock = { 100L },
            seedIfEmpty = listOf(post("old", 100L), post("new", 200L)),
        )
        // Seeded because empty; returned newest-first.
        assertEquals(listOf("new", "old"), repo.getPosts().map { it.id })
    }

    @Test
    fun getPostsSeedsFromSampleWhenEmptyByDefault() {
        val cache = FakeFeedCache()
        val remote = FakeFeedRemote(available = false)
        // Default seed = FeedMapping.defaultPostSeed(now) = the 6 sample posts.
        val repo = FeedRepository(
            cache = cache, scope = scope, remote = remote,
            isAuthenticated = { false }, clock = { 100L },
        )
        val ids = repo.getPosts().map { it.id }
        assertEquals(SampleData.posts.size, ids.size)
        assertTrue(ids.containsAll(SampleData.posts.map { it.id }))
    }

    @Test
    fun getPostsRefreshReplacesCacheWhenOnline() {
        val cache = FakeFeedCache()
        cache.upsertPost(post("seed", 50L))
        val remote = FakeFeedRemote(
            available = true,
            feedResult = listOf(post("remote-a", 300L), post("remote-b", 200L)),
        )
        var notified = 0
        val repo = FeedRepository(
            cache = cache, scope = scope, remote = remote,
            isAuthenticated = { true }, clock = { 100L },
            onChanged = { notified++ }, seedIfEmpty = emptyList(),
        )
        repo.getPosts()
        assertEquals(listOf("remote-a", "remote-b"), cache.getCachedPosts().map { it.id })
        assertEquals(1, remote.feedFetchCount)
        assertTrue(notified >= 1)
    }

    @Test
    fun getPostsUnauthenticatedServesCacheWithoutRemoteCall() {
        val cache = FakeFeedCache()
        cache.upsertPost(post("local", 50L))
        val remote = FakeFeedRemote(available = true, feedResult = listOf(post("remote", 300L)))
        val repo = FeedRepository(
            cache = cache, scope = scope, remote = remote,
            isAuthenticated = { false }, clock = { 100L }, seedIfEmpty = emptyList(),
        )
        repo.getPosts()
        assertEquals(0, remote.feedFetchCount)
        assertEquals(listOf("local"), cache.getCachedPosts().map { it.id })
    }

    // --- CommentRepository (comments) ---

    private fun commentRepo(
        cache: FeedCache,
        remote: FeedRemoteSource,
        authed: Boolean,
        onChanged: () -> Unit = {},
        seed: (String, Long) -> List<FeedComment> = { _, _ -> emptyList() },
    ) = CommentRepository(
        cache = cache, scope = scope, remote = remote,
        authorIdProvider = { "author-1" }, authorNameProvider = { "me@example.com" },
        isAuthenticated = { authed }, clock = { 1_000L },
        idGenerator = { "local-cmt" }, onChanged = onChanged, seedProvider = seed,
    )

    @Test
    fun getCommentsServesLocalCacheForThatPost() {
        val cache = FakeFeedCache()
        cache.upsertComment(comment("c1", "post-1", 10L))
        cache.upsertComment(comment("c2", "post-1", 20L))
        cache.upsertComment(comment("c3", "post-2", 30L))
        val remote = FakeFeedRemote(available = false)
        val repo = commentRepo(cache, remote, authed = false)
        assertEquals(listOf("c1", "c2"), repo.getComments("post-1").map { it.id })
    }

    @Test
    fun getCommentsSeedsWhenEmpty() {
        val cache = FakeFeedCache()
        val remote = FakeFeedRemote(available = false)
        val repo = commentRepo(cache, remote, authed = false, seed = { pid, now ->
            listOf(comment("seed-$pid", pid, now))
        })
        assertEquals(listOf("seed-post-9"), repo.getComments("post-9").map { it.id })
    }

    @Test
    fun getCommentsRefreshReplacesForPostWhenOnline() {
        val cache = FakeFeedCache()
        cache.upsertComment(comment("stale", "post-1", 10L))
        val remote = FakeFeedRemote(
            available = true,
            commentsResult = listOf(comment("r1", "post-1", 100L), comment("r2", "post-1", 200L)),
        )
        var notified = 0
        val repo = commentRepo(cache, remote, authed = true, onChanged = { notified++ })
        repo.getComments("post-1")
        assertEquals(listOf("r1", "r2"), cache.getCachedComments("post-1").map { it.id })
        assertEquals(1, remote.commentFetchCount)
        assertTrue(notified >= 1)
    }

    @Test
    fun addCommentWritesThroughAndEnqueuesWhenOffline() {
        val cache = FakeFeedCache()
        val remote = FakeFeedRemote(available = false)
        var notified = 0
        val repo = commentRepo(cache, remote, authed = false, onChanged = { notified++ })
        repo.addComment("post-1", "hi there")
        // Optimistic local comment shown immediately with the current user's name.
        val local = cache.getCachedComments("post-1")
        assertEquals(1, local.size)
        assertEquals("me@example.com", local.first().authorName)
        assertEquals("hi there", local.first().text)
        // Queued because offline; not pushed.
        assertEquals(listOf("local-cmt"), cache.pendingComments().map { it.id })
        assertTrue(remote.inserted.isEmpty())
        assertTrue(notified >= 1) // optimistic onChanged fired
    }

    @Test
    fun addCommentPushesAndDequeuesWhenOnline() {
        val cache = FakeFeedCache()
        val remote = FakeFeedRemote(available = true)
        val repo = commentRepo(cache, remote, authed = true)
        repo.addComment("post-1", "hello")
        // Pushed with the post id, author id and text; dequeued on success.
        assertEquals(1, remote.inserted.size)
        assertEquals(Triple("post-1", "author-1", "hello"), remote.inserted.first())
        assertTrue(cache.pendingComments().isEmpty())
        // Optimistic local copy remains in the cache.
        assertEquals(1, cache.getCachedComments("post-1").size)
    }

    @Test
    fun addCommentFailedPushLeavesItemQueued() {
        val cache = FakeFeedCache()
        val remote = FakeFeedRemote(available = true, failInsert = true)
        val repo = commentRepo(cache, remote, authed = true)
        repo.addComment("post-1", "boom")
        // Insert threw -> stays queued for a later retry; no crash.
        assertEquals(listOf("local-cmt"), cache.pendingComments().map { it.id })
        assertTrue(remote.inserted.isEmpty())
        // Optimistic local comment still present.
        assertEquals(1, cache.getCachedComments("post-1").size)
    }

    @Test
    fun addCommentIgnoresBlankText() {
        val cache = FakeFeedCache()
        val remote = FakeFeedRemote(available = true)
        val repo = commentRepo(cache, remote, authed = true)
        repo.addComment("post-1", "   ")
        assertTrue(cache.getCachedComments("post-1").isEmpty())
        assertTrue(cache.pendingComments().isEmpty())
        assertFalse(remote.inserted.any())
    }
}
