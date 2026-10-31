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

    private fun post(id: String, epoch: Long, verified: Boolean = true) = FeedPost(
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
    )

    private fun comment(id: String, postId: String, epoch: Long) = FeedComment(
        id = id,
        postId = postId,
        authorName = "Author $id",
        text = "Comment $id",
        createdAtEpoch = epoch,
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

        // Come online and read -> the queued insert flushes and dequeues.
        remote.available = true
        repo.getComments("post-1")
        assertEquals(listOf("hi"), remote.inserted)
        assertTrue(cache.pendingComments().isEmpty())
    }
}
