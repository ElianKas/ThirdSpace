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
        val pendingPostsQueue = mutableListOf<PendingPostInsert>()
        val pendingVotesQueue = LinkedHashMap<String, PendingVote>()

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

        override fun enqueuePendingPost(
            id: String,
            spotId: String,
            authorId: String,
            text: String,
            enqueuedAt: Long,
        ) {
            pendingPostsQueue.removeAll { it.id == id }
            pendingPostsQueue.add(PendingPostInsert(id, spotId, authorId, text, enqueuedAt))
        }

        override fun pendingPosts(): List<PendingPostInsert> =
            pendingPostsQueue.sortedBy { it.enqueuedAt }

        override fun dequeuePendingPost(id: String) { pendingPostsQueue.removeAll { it.id == id } }

        override fun updatePostVote(postId: String, newScore: Int, newMyVote: String) {
            val existing = posts[postId] ?: return
            posts[postId] = existing.copy(score = newScore, myVote = newMyVote)
        }

        override fun upsertPendingVote(postId: String, value: Int, enqueuedAt: Long) {
            pendingVotesQueue[postId] = PendingVote(postId, value, enqueuedAt)
        }

        override fun pendingVotes(): List<PendingVote> =
            pendingVotesQueue.values.sortedBy { it.enqueuedAt }

        override fun dequeuePendingVote(postId: String) { pendingVotesQueue.remove(postId) }
    }

    /** Controllable fake remote: records inserts, returns canned fetch rows. */
    private class FakeFeedRemote(
        var available: Boolean,
        var feedResult: List<FeedPost> = emptyList(),
        var commentsResult: List<FeedComment> = emptyList(),
        var failInsert: Boolean = false,
        var failPostInsert: Boolean = false,
    ) : FeedRemoteSource {
        val inserted = mutableListOf<Triple<String, String, String>>()
        val insertedPosts = mutableListOf<Triple<String, String, String>>()
        // Recorded (postId, userId, value) applied-vote calls.
        val appliedVotes = mutableListOf<Triple<String, String, Int>>()
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
        override suspend fun insertPost(spotId: String, authorId: String, text: String): Boolean {
            if (!available) return false
            if (failPostInsert) throw IllegalStateException("post insert failed")
            insertedPosts.add(Triple(spotId, authorId, text))
            return true
        }
        override suspend fun applyVote(postId: String, userId: String, value: Int): Boolean {
            if (!available) return false
            appliedVotes.add(Triple(postId, userId, value))
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
        repo.refresh()
        assertEquals(listOf("remote-a", "remote-b"), cache.getCachedPosts().map { it.id })
        assertEquals(1, remote.feedFetchCount)
        assertTrue(notified >= 1)
    }

    @Test
    fun refreshUnauthenticatedServesCacheWithoutRemoteCall() {
        val cache = FakeFeedCache()
        cache.upsertPost(post("local", 50L))
        val remote = FakeFeedRemote(available = true, feedResult = listOf(post("remote", 300L)))
        val repo = FeedRepository(
            cache = cache, scope = scope, remote = remote,
            isAuthenticated = { false }, clock = { 100L }, seedIfEmpty = emptyList(),
        )
        repo.refresh()
        assertEquals(0, remote.feedFetchCount)
        assertEquals(listOf("local"), cache.getCachedPosts().map { it.id })
    }

    // --- ROOT-CAUSE regression: reads must NOT trigger remote fetches ---
    // (Automated proxy for the per-second Supabase GET flood.)

    @Test
    fun getPostsNeverTriggersRemoteFetchEvenRepeatedly() {
        val cache = FakeFeedCache()
        cache.upsertPost(post("local", 50L))
        val remote = FakeFeedRemote(available = true, feedResult = listOf(post("remote", 300L)))
        val repo = FeedRepository(
            cache = cache, scope = scope, remote = remote,
            isAuthenticated = { true }, clock = { 100L }, seedIfEmpty = emptyList(),
        )
        repeat(5) { repo.getPosts() }
        // Pure read: ZERO fetches, cache untouched by any refresh.
        assertEquals(0, remote.feedFetchCount)
        assertEquals(listOf("local"), cache.getCachedPosts().map { it.id })
    }

    @Test
    fun refreshTriggersExactlyOneFetch() {
        val cache = FakeFeedCache()
        val remote = FakeFeedRemote(available = true, feedResult = listOf(post("remote", 300L)))
        val repo = FeedRepository(
            cache = cache, scope = scope, remote = remote,
            isAuthenticated = { true }, clock = { 100L }, seedIfEmpty = emptyList(),
        )
        repo.refresh()
        assertEquals(1, remote.feedFetchCount)
    }

    @Test
    fun backToBackRefreshCoalescesToOneInFlightFetch() {
        val cache = FakeFeedCache()
        // Gated fake: fetchFeed() suspends until the gate is released, holding
        // the first refresh "in flight". A second refresh issued while the
        // first is still running must NOT produce a second concurrent fetch
        // (the in-flight guard + syncMutex coalesce it). Without coalescing the
        // read->refresh->onChanged->re-read->refresh loop stacked fetches ~1/s.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val remote = object : FeedRemoteSource {
            var feedFetchCount = 0
            override fun isAvailable() = true
            override suspend fun fetchFeed(): List<FeedPost> {
                feedFetchCount++
                gate.await() // hold this fetch open (in flight)
                return emptyList()
            }
            override suspend fun fetchComments(postId: String) = emptyList<FeedComment>()
            override suspend fun insertComment(postId: String, authorId: String, text: String) = true
            override suspend fun insertPost(spotId: String, authorId: String, text: String) = true
            override suspend fun applyVote(postId: String, userId: String, value: Int) = true
        }
        val repo = FeedRepository(
            cache = cache, scope = scope, remote = remote,
            isAuthenticated = { true }, clock = { 100L }, seedIfEmpty = emptyList(),
        )
        repo.refresh() // enters fetchFeed(), suspends on the gate while running
        repo.refresh() // cannot start a second concurrent fetch
        // While the first is still in flight, exactly ONE fetch exists.
        assertEquals(1, remote.feedFetchCount)
        gate.complete(Unit) // release so the first completes cleanly
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
        repo.refresh("post-1")
        assertEquals(listOf("r1", "r2"), cache.getCachedComments("post-1").map { it.id })
        assertEquals(1, remote.commentFetchCount)
        assertTrue(notified >= 1)
    }

    @Test
    fun getCommentsNeverTriggersRemoteFetchEvenRepeatedly() {
        val cache = FakeFeedCache()
        cache.upsertComment(comment("c1", "post-1", 10L))
        val remote = FakeFeedRemote(
            available = true,
            commentsResult = listOf(comment("r1", "post-1", 100L)),
        )
        val repo = commentRepo(cache, remote, authed = true)
        repeat(5) { repo.getComments("post-1") }
        // Pure read: ZERO comment fetches, cache untouched by any refresh.
        assertEquals(0, remote.commentFetchCount)
        assertEquals(listOf("c1"), cache.getCachedComments("post-1").map { it.id })
    }

    @Test
    fun refreshCommentsTriggersExactlyOneFetch() {
        val cache = FakeFeedCache()
        val remote = FakeFeedRemote(
            available = true,
            commentsResult = listOf(comment("r1", "post-1", 100L)),
        )
        val repo = commentRepo(cache, remote, authed = true)
        repo.refresh("post-1")
        assertEquals(1, remote.commentFetchCount)
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

    // --- FeedRepository (post creation write path) ---

    private fun feedRepoForCreate(
        cache: FeedCache,
        remote: FeedRemoteSource,
        authed: Boolean,
        onChanged: () -> Unit = {},
        spotLookup: (String) -> Spot? = { id ->
            Spot(
                id = id, name = "Spot $id", category = "Cafe", neighborhood = "Mitte",
                verified = true, checkInCount = 0, placeholderColorArgb = 0L, lat = 0.0, lng = 0.0,
            )
        },
    ) = FeedRepository(
        cache = cache, scope = scope, remote = remote,
        isAuthenticated = { authed }, clock = { 5_000L },
        onChanged = onChanged,
        authorIdProvider = { "author-9" }, authorNameProvider = { "me@example.com" },
        idGenerator = { "local-post-x" }, spotLookup = spotLookup,
        seedIfEmpty = emptyList(),
    )

    @Test
    fun createPostWritesOptimisticPostAtTopAndEnqueuesWhenOffline() {
        val cache = FakeFeedCache()
        // An existing older post so we can assert the new one sorts to the top.
        cache.upsertPost(post("existing", 1_000L))
        val remote = FakeFeedRemote(available = false)
        var notified = 0
        val repo = feedRepoForCreate(cache, remote, authed = false, onChanged = { notified++ })
        repo.createPost("spot-7", "my first post")

        // Optimistic post is newest (createdAt = clock 5000) -> at the TOP.
        val posts = cache.getCachedPosts()
        assertEquals("local-post-x", posts.first().id)
        val created = posts.first()
        assertEquals("me@example.com", created.authorName)
        assertEquals("Spot spot-7", created.spotName)
        assertEquals("Cafe", created.category)
        assertEquals("Mitte", created.neighborhood)
        assertTrue(created.verified)
        assertEquals("my first post", created.text)
        assertEquals(0, created.score)
        assertEquals("NONE", created.myVote)
        assertEquals(0, created.commentCount)
        // Queued because offline; not pushed.
        assertEquals(listOf("local-post-x"), cache.pendingPosts().map { it.id })
        assertTrue(remote.insertedPosts.isEmpty())
        assertTrue(notified >= 1)
    }

    @Test
    fun createPostAllowsEmptyText() {
        val cache = FakeFeedCache()
        val remote = FakeFeedRemote(available = false)
        val repo = feedRepoForCreate(cache, remote, authed = false)
        repo.createPost("spot-7", "")
        // Empty text is ALLOWED (a pure check-in): the post + queue entry exist.
        assertEquals(listOf("local-post-x"), cache.getCachedPosts().map { it.id })
        assertEquals(1, cache.pendingPosts().size)
        assertEquals("", cache.getCachedPosts().first().text)
    }

    @Test
    fun createPostIgnoresBlankSpotId() {
        val cache = FakeFeedCache()
        val remote = FakeFeedRemote(available = false)
        val repo = feedRepoForCreate(cache, remote, authed = false)
        repo.createPost("   ", "text")
        assertTrue(cache.getCachedPosts().isEmpty())
        assertTrue(cache.pendingPosts().isEmpty())
    }

    @Test
    fun createPostPushesAndDequeuesWhenOnline() {
        val cache = FakeFeedCache()
        val remote = FakeFeedRemote(available = true)
        val repo = feedRepoForCreate(cache, remote, authed = true)
        repo.createPost("spot-7", "hello world")
        // Pushed with (spotId, authorId, text) and dequeued on success.
        assertEquals(1, remote.insertedPosts.size)
        assertEquals(Triple("spot-7", "author-9", "hello world"), remote.insertedPosts.first())
        assertTrue(cache.pendingPosts().isEmpty())
    }

    @Test
    fun createPostFailedPushLeavesItemQueued() {
        val cache = FakeFeedCache()
        val remote = FakeFeedRemote(available = true, failPostInsert = true)
        val repo = feedRepoForCreate(cache, remote, authed = true)
        repo.createPost("spot-7", "boom")
        // Insert threw -> stays queued for a later retry; optimistic post stays.
        assertEquals(listOf("local-post-x"), cache.pendingPosts().map { it.id })
        assertTrue(remote.insertedPosts.isEmpty())
        assertEquals(listOf("local-post-x"), cache.getCachedPosts().map { it.id })
    }

    // --- VoteRepository (voting write path) ---

    @Test
    fun voteToggleStateMachineCoversAllSixTransitions() {
        // NONE + up -> UP (+1, intent 1)
        VoteRepository.resolve("NONE", 1).let {
            assertEquals("UP", it.newMyVote); assertEquals(1, it.scoreDelta); assertEquals(1, it.intent)
        }
        // NONE + down -> DOWN (-1, intent -1)
        VoteRepository.resolve("NONE", -1).let {
            assertEquals("DOWN", it.newMyVote); assertEquals(-1, it.scoreDelta); assertEquals(-1, it.intent)
        }
        // UP + up -> NONE (-1 clear, intent 0)
        VoteRepository.resolve("UP", 1).let {
            assertEquals("NONE", it.newMyVote); assertEquals(-1, it.scoreDelta); assertEquals(0, it.intent)
        }
        // UP + down -> DOWN (-2, intent -1)
        VoteRepository.resolve("UP", -1).let {
            assertEquals("DOWN", it.newMyVote); assertEquals(-2, it.scoreDelta); assertEquals(-1, it.intent)
        }
        // DOWN + down -> NONE (+1 clear, intent 0)
        VoteRepository.resolve("DOWN", -1).let {
            assertEquals("NONE", it.newMyVote); assertEquals(1, it.scoreDelta); assertEquals(0, it.intent)
        }
        // DOWN + up -> UP (+2, intent 1)
        VoteRepository.resolve("DOWN", 1).let {
            assertEquals("UP", it.newMyVote); assertEquals(2, it.scoreDelta); assertEquals(1, it.intent)
        }
    }

    private fun voteRepo(
        cache: FeedCache,
        remote: FeedRemoteSource,
        authed: Boolean,
        clock: () -> Long = { 7_000L },
        onChanged: () -> Unit = {},
    ) = VoteRepository(
        cache = cache, scope = scope, remote = remote,
        userIdProvider = { "user-3" }, isAuthenticated = { authed },
        clock = clock, onChanged = onChanged,
    )

    @Test
    fun voteAppliesOptimisticScoreAndMyVoteOnCachedPost() {
        val cache = FakeFeedCache()
        // Post with score 10 and no current vote.
        cache.upsertPost(post("p1", 100L).copy(score = 10, myVote = "NONE"))
        val remote = FakeFeedRemote(available = false)
        var notified = 0
        val repo = voteRepo(cache, remote, authed = false, onChanged = { notified++ })
        repo.vote("p1", 1) // up
        val updated = cache.getCachedPosts().first { it.id == "p1" }
        assertEquals(11, updated.score)
        assertEquals("UP", updated.myVote)
        // Queued (offline), intent 1; not pushed.
        assertEquals(listOf("p1"), cache.pendingVotes().map { it.postId })
        assertEquals(1, cache.pendingVotes().first().value)
        assertTrue(remote.appliedVotes.isEmpty())
        assertTrue(notified >= 1)
    }

    @Test
    fun voteIsNoOpForUncachedPost() {
        val cache = FakeFeedCache()
        val remote = FakeFeedRemote(available = true)
        val repo = voteRepo(cache, remote, authed = true)
        repo.vote("missing", 1)
        assertTrue(cache.pendingVotes().isEmpty())
        assertTrue(remote.appliedVotes.isEmpty())
    }

    @Test
    fun voteLatestIntentWinsCollapsesToOneQueuedRow() {
        val cache = FakeFeedCache()
        cache.upsertPost(post("p1", 100L).copy(score = 0, myVote = "NONE"))
        val remote = FakeFeedRemote(available = false) // offline so nothing flushes
        var t = 1_000L
        val repo = voteRepo(cache, remote, authed = false, clock = { t })
        // Tap up: NONE -> UP (intent 1). Then tap up again: UP -> NONE (intent 0).
        repo.vote("p1", 1); t = 2_000L
        repo.vote("p1", 1)
        // One queued row (keyed by post_id), carrying the LATEST intent (0 = clear).
        assertEquals(1, cache.pendingVotes().size)
        assertEquals("p1", cache.pendingVotes().first().postId)
        assertEquals(0, cache.pendingVotes().first().value)
        // Optimistic state reflects the latest tap: back to NONE, score 0.
        val p = cache.getCachedPosts().first { it.id == "p1" }
        assertEquals(0, p.score)
        assertEquals("NONE", p.myVote)
    }

    @Test
    fun voteOnlinePushesAndDequeues() {
        val cache = FakeFeedCache()
        cache.upsertPost(post("p1", 100L).copy(score = 0, myVote = "NONE"))
        val remote = FakeFeedRemote(available = true)
        val repo = voteRepo(cache, remote, authed = true)
        repo.vote("p1", -1) // down -> intent -1
        assertEquals(1, remote.appliedVotes.size)
        assertEquals(Triple("p1", "user-3", -1), remote.appliedVotes.first())
        assertTrue(cache.pendingVotes().isEmpty())
    }

    @Test
    fun voteOnlineClearPushesIntentZero() {
        val cache = FakeFeedCache()
        cache.upsertPost(post("p1", 100L).copy(score = 5, myVote = "UP"))
        val remote = FakeFeedRemote(available = true)
        val repo = voteRepo(cache, remote, authed = true)
        repo.vote("p1", 1) // UP + up -> NONE clear -> intent 0 -> remote DELETE
        assertEquals(Triple("p1", "user-3", 0), remote.appliedVotes.first())
        assertTrue(cache.pendingVotes().isEmpty())
        val p = cache.getCachedPosts().first { it.id == "p1" }
        assertEquals(4, p.score)
        assertEquals("NONE", p.myVote)
    }

    @Test
    fun voteUnauthenticatedStaysOptimisticAndQueued() {
        val cache = FakeFeedCache()
        cache.upsertPost(post("p1", 100L).copy(score = 0, myVote = "NONE"))
        // available=true but NOT authenticated -> flush gates, stays queued.
        val remote = FakeFeedRemote(available = true)
        val repo = voteRepo(cache, remote, authed = false)
        repo.vote("p1", 1)
        assertTrue(remote.appliedVotes.isEmpty())
        assertEquals(listOf("p1"), cache.pendingVotes().map { it.postId })
        // Optimistic UI state still applied.
        assertEquals("UP", cache.getCachedPosts().first { it.id == "p1" }.myVote)
    }
}
