package com.example.thirdspace.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.thirdspace.db.ThirdSpaceDb
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * REAL SQLDelight round-trip tests for [LocalFeedCache] (Phase B feed cache:
 * cachedPosts + cachedComments + pendingCommentInserts), exercising the
 * generated [ThirdSpaceDb] queries against an IN-MEMORY SQLite database via
 * [JdbcSqliteDriver].
 *
 * Lives on the Android HOST (JVM) test target because [JdbcSqliteDriver] is
 * JVM-only. Mirrors LocalSpotCacheDbTest. No device/emulator required. The pure
 * caching/repository logic is covered on BOTH targets by commonTest's
 * FeedRepositoryTest.
 */
class LocalFeedCacheDbTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var cache: LocalFeedCache

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ThirdSpaceDb.Schema.create(driver)
        cache = LocalFeedCache(ThirdSpaceDb(driver))
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun post(
        id: String,
        epoch: Long,
        verified: Boolean = true,
        kind: String = "post",
        authorUserId: String = "uid-$id",
    ) = FeedPost(
        id = id,
        authorName = "Author $id",
        spotName = "Spot $id",
        category = "Bar",
        neighborhood = "Mitte",
        verified = verified,
        text = "Text $id",
        createdAtEpoch = epoch,
        score = 42,
        commentCount = 3,
        myVote = "UP",
        kind = kind,
        authorUserId = authorUserId,
    )

    private fun comment(id: String, postId: String, epoch: Long, authorUserId: String = "uid-$id") = FeedComment(
        id = id,
        postId = postId,
        authorName = "Author $id",
        text = "Comment $id",
        createdAtEpoch = epoch,
        authorUserId = authorUserId,
    )

    // --- cachedPosts ---

    @Test
    fun replaceAllPostsThenReadRoundTripNewestFirst() {
        cache.replaceAllPosts(listOf(post("a", 100L), post("b", 300L), post("c", 200L)))
        val read = cache.getCachedPosts()
        // ORDER BY created_at_epoch DESC.
        assertEquals(listOf("b", "c", "a"), read.map { it.id })
        val a = read.first { it.id == "a" }
        assertEquals("Author a", a.authorName)
        assertEquals("Spot a", a.spotName)
        assertEquals("Bar", a.category)
        assertEquals(42, a.score)
        assertEquals(3, a.commentCount)
        assertEquals("UP", a.myVote)
        assertTrue(a.verified)
        assertEquals(100L, a.createdAtEpoch)
        // PHASE 4: the default-shaped post round-trips as kind "post".
        assertEquals("post", a.kind)
        // PHASE F3: the author uid round-trips via the new author_user_id column.
        assertEquals("uid-a", a.authorUserId)
    }

    @Test
    fun authorUserIdRoundTripsForPostsAndComments() {
        // PHASE F3: author_user_id persists + reads back on BOTH feed caches,
        // including the "" (unknown author) default.
        cache.upsertPost(post("p1", 100L, authorUserId = "author-1"))
        cache.upsertPost(post("p2", 200L, authorUserId = ""))
        val posts = cache.getCachedPosts()
        assertEquals("author-1", posts.first { it.id == "p1" }.authorUserId)
        assertEquals("", posts.first { it.id == "p2" }.authorUserId)

        cache.replaceCommentsForPost(
            "p1",
            listOf(
                comment("c1", "p1", 10L, authorUserId = "commenter-1"),
                comment("c2", "p1", 20L, authorUserId = ""),
            ),
        )
        val comments = cache.getCachedComments("p1")
        assertEquals("commenter-1", comments.first { it.id == "c1" }.authorUserId)
        assertEquals("", comments.first { it.id == "c2" }.authorUserId)
    }

    @Test
    fun kindColumnRoundTripsForBareSpotEntry() {
        // PHASE 4: a bare-spot feed entry (kind="spot") must persist + read back
        // with its kind via the new `kind` column, alongside regular posts.
        cache.replaceAllPosts(
            listOf(
                post("p1", 200L, kind = "post"),
                post("spot:s1", 100L, kind = "spot"),
            ),
        )
        val read = cache.getCachedPosts()
        assertEquals("post", read.first { it.id == "p1" }.kind)
        assertEquals("spot", read.first { it.id == "spot:s1" }.kind)
    }

    @Test
    fun replaceAllPostsIsDestructive() {
        cache.replaceAllPosts(listOf(post("a", 1L)))
        cache.replaceAllPosts(listOf(post("b", 2L), post("c", 3L)))
        assertEquals(setOf("b", "c"), cache.getCachedPosts().map { it.id }.toSet())
    }

    @Test
    fun upsertPostOverwritesByPrimaryKey() {
        cache.upsertPost(post("a", 1L, verified = true))
        cache.upsertPost(post("a", 1L, verified = false))
        val all = cache.getCachedPosts()
        assertEquals(1, all.size)
        assertFalse(all.first().verified)
    }

    // --- cachedComments ---

    @Test
    fun replaceCommentsForPostRoundTripScopedAndOrdered() {
        cache.replaceCommentsForPost(
            "post-1",
            listOf(comment("c2", "post-1", 200L), comment("c1", "post-1", 100L)),
        )
        cache.replaceCommentsForPost("post-2", listOf(comment("c3", "post-2", 50L)))

        // Scoped to the post, ordered oldest-first.
        assertEquals(listOf("c1", "c2"), cache.getCachedComments("post-1").map { it.id })
        assertEquals(listOf("c3"), cache.getCachedComments("post-2").map { it.id })
        assertEquals("Comment c1", cache.getCachedComments("post-1").first().text)
    }

    @Test
    fun replaceCommentsForPostDoesNotTouchOtherPosts() {
        cache.replaceCommentsForPost("post-1", listOf(comment("a", "post-1", 1L)))
        cache.replaceCommentsForPost("post-2", listOf(comment("b", "post-2", 1L)))
        // Replacing post-1 again leaves post-2 intact.
        cache.replaceCommentsForPost("post-1", listOf(comment("a2", "post-1", 2L)))
        assertEquals(listOf("a2"), cache.getCachedComments("post-1").map { it.id })
        assertEquals(listOf("b"), cache.getCachedComments("post-2").map { it.id })
    }

    @Test
    fun upsertCommentWriteThrough() {
        cache.upsertComment(comment("c1", "post-1", 10L))
        assertEquals(listOf("c1"), cache.getCachedComments("post-1").map { it.id })
    }

    // --- pendingCommentInserts ---

    @Test
    fun pendingCommentQueueEnqueueListDequeueRoundTrip() {
        cache.enqueuePendingComment("p2", "post-1", "owner-1", "second", enqueuedAt = 200L)
        cache.enqueuePendingComment("p1", "post-1", "owner-1", "first", enqueuedAt = 100L)

        val pending = cache.pendingComments()
        // Oldest-first by enqueued_at.
        assertEquals(listOf("p1", "p2"), pending.map { it.id })
        assertEquals("first", pending.first().text)
        assertEquals("owner-1", pending.first().authorId)
        assertEquals("post-1", pending.first().postId)
        assertEquals(100L, pending.first().enqueuedAt)

        cache.dequeuePendingComment("p1")
        assertEquals(listOf("p2"), cache.pendingComments().map { it.id })
    }

    // --- pendingPostInserts ---

    @Test
    fun pendingPostQueueEnqueueListDequeueRoundTrip() {
        cache.enqueuePendingPost("lp2", "spot-1", "owner-1", "second", enqueuedAt = 200L)
        cache.enqueuePendingPost("lp1", "spot-2", "owner-1", "", enqueuedAt = 100L)

        val pending = cache.pendingPosts()
        // Oldest-first by enqueued_at.
        assertEquals(listOf("lp1", "lp2"), pending.map { it.id })
        val first = pending.first()
        assertEquals("spot-2", first.spotId)
        assertEquals("owner-1", first.authorId)
        assertEquals("", first.text) // empty text is allowed for posts
        assertEquals(100L, first.enqueuedAt)

        cache.dequeuePendingPost("lp1")
        assertEquals(listOf("lp2"), cache.pendingPosts().map { it.id })
    }

    // --- pendingVotes (latest-intent-wins by post_id PK) ---

    @Test
    fun pendingVoteQueueUpsertListDequeueRoundTrip() {
        cache.upsertPendingVote("post-b", value = -1, enqueuedAt = 200L)
        cache.upsertPendingVote("post-a", value = 1, enqueuedAt = 100L)

        val pending = cache.pendingVotes()
        // Oldest-first by enqueued_at.
        assertEquals(listOf("post-a", "post-b"), pending.map { it.postId })
        assertEquals(1, pending.first().value)
        assertEquals(100L, pending.first().enqueuedAt)

        cache.dequeuePendingVote("post-a")
        assertEquals(listOf("post-b"), cache.pendingVotes().map { it.postId })
    }

    @Test
    fun pendingVoteLatestIntentReplacesByPostId() {
        cache.upsertPendingVote("post-a", value = 1, enqueuedAt = 100L)
        // A newer intent on the SAME post replaces the row (PK = post_id).
        cache.upsertPendingVote("post-a", value = 0, enqueuedAt = 200L)
        val pending = cache.pendingVotes()
        assertEquals(1, pending.size)
        assertEquals("post-a", pending.first().postId)
        assertEquals(0, pending.first().value) // latest intent (clear) wins
        assertEquals(200L, pending.first().enqueuedAt)
    }

    @Test
    fun updatePostVoteRewritesScoreAndMyVoteInPlace() {
        cache.replaceAllPosts(listOf(post("a", 100L)))
        // Seed post() has score 42 / myVote "UP".
        cache.updatePostVote("a", newScore = 7, newMyVote = "DOWN")
        val read = cache.getCachedPosts().first { it.id == "a" }
        assertEquals(7, read.score)
        assertEquals("DOWN", read.myVote)
        // No-op for an unknown post (no crash, no row created).
        cache.updatePostVote("missing", newScore = 1, newMyVote = "UP")
        assertEquals(1, cache.getCachedPosts().size)
    }

    // --- end-to-end offline-then-flush over the REAL DB ---

    @Test
    fun endToEndOfflineThenFlushWithCommentRepoOverRealDb() {
        val remote = object : FeedRemoteSource {
            var available = false
            val inserted = mutableListOf<String>()
            override fun isAvailable() = available
            override suspend fun fetchFeed(): List<FeedPost> = emptyList()
            override suspend fun fetchComments(postId: String): List<FeedComment> = emptyList()
            override suspend fun insertComment(postId: String, authorId: String, text: String): Boolean {
                if (!available) return false
                inserted.add(text); return true
            }
            override suspend fun insertPost(spotId: String, authorId: String, text: String): Boolean {
                if (!available) return false
                return true
            }
            override suspend fun applyVote(postId: String, userId: String, value: Int): Boolean {
                if (!available) return false
                return true
            }
        }
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val repo = CommentRepository(
            cache = cache, scope = scope, remote = remote,
            authorIdProvider = { "owner-1" }, authorNameProvider = { "me" },
            isAuthenticated = { true }, clock = { 1L }, idGenerator = { "cmt-x" },
            seedProvider = { _, _ -> emptyList() },
        )

        // Offline add: written to the DB cache + queued, not pushed.
        repo.addComment("post-1", "hi")
        assertEquals(listOf("cmt-x"), cache.getCachedComments("post-1").map { it.id })
        assertEquals(listOf("cmt-x"), cache.pendingComments().map { it.id })
        assertTrue(remote.inserted.isEmpty())

        // Come online and refresh -> the queued insert flushes and dequeues.
        // (Reads are now pure; refresh(postId) is the explicit remote trigger.)
        remote.available = true
        repo.refresh("post-1")
        assertEquals(listOf("hi"), remote.inserted)
        assertTrue(cache.pendingComments().isEmpty())
    }
}
