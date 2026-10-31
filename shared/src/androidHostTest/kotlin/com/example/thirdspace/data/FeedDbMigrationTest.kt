package com.example.thirdspace.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.thirdspace.db.ThirdSpaceDb
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REAL migration test proving the v1 -> v2 SQLDelight schema migration fixes the
 * confirmed iOS crash `no such table: cachedPosts`.
 *
 * ROOT CAUSE (confirmed): an EXISTING `thirdspace.db` created during the earlier
 * spots-only phase sits at `PRAGMA user_version = 1`. The feed tables
 * (cachedPosts/cachedComments/pendingCommentInserts/pendingPostInserts/
 * pendingVotes + the cachedComments index) were later added to the single `.sq`
 * WITHOUT a version bump or a migration, so on open user_version(1) ==
 * Schema.version(1) made the driver run NEITHER create() NOR migrate() — the
 * feed tables never existed → crash on `SELECT ... FROM cachedPosts`.
 *
 * This test reconstructs that exact pre-existing v1 (spots-only) database by
 * hand on a fresh in-memory [JdbcSqliteDriver], then runs the generated
 * [ThirdSpaceDb.Schema.migrate] (1 -> 2) and asserts the feed tables now exist
 * and round-trip. It is the only automated reproduction of the original crash.
 *
 * Lives on the Android HOST (JVM) target because [JdbcSqliteDriver] is JVM-only;
 * mirrors LocalFeedCacheDbTest / LocalSpotCacheDbTest.
 */
class FeedDbMigrationTest {

    private lateinit var driver: JdbcSqliteDriver

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    /**
     * Build the pre-existing v1 schema BY HAND: only the two spots tables that
     * existed during the spots-only phase, plus `PRAGMA user_version = 1` to
     * mark the DB as version 1 (the condition under which the crash occurred).
     * Statements copied verbatim from ThirdSpaceDb.sq.
     */
    private fun createV1SpotsOnlySchema() {
        driver.execute(
            null,
            """
            |CREATE TABLE cachedSpots (
            |    id TEXT NOT NULL PRIMARY KEY,
            |    owner_id TEXT NOT NULL DEFAULT '',
            |    name TEXT NOT NULL,
            |    category TEXT NOT NULL,
            |    neighborhood TEXT NOT NULL DEFAULT '',
            |    lat REAL NOT NULL,
            |    lng REAL NOT NULL,
            |    verified INTEGER NOT NULL DEFAULT 0
            |)
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            """
            |CREATE TABLE pendingSpotInserts (
            |    id TEXT NOT NULL PRIMARY KEY,
            |    owner_id TEXT NOT NULL DEFAULT '',
            |    name TEXT NOT NULL,
            |    category TEXT NOT NULL,
            |    neighborhood TEXT NOT NULL DEFAULT '',
            |    lat REAL NOT NULL,
            |    lng REAL NOT NULL,
            |    verified INTEGER NOT NULL DEFAULT 0,
            |    enqueued_at INTEGER NOT NULL
            |)
            """.trimMargin(),
            0,
        )
        driver.execute(null, "PRAGMA user_version = 1", 0)
    }

    @Test
    fun schemaVersionIsTwo() {
        // The presence of 1.sqm auto-bumps the generated Schema version to 2.
        assertEquals(2L, ThirdSpaceDb.Schema.version)
    }

    @Test
    fun migratingV1DbAddsFeedTablesAndRoundTrips() {
        // Reconstruct the crashing condition: a v1 (spots-only) DB.
        createV1SpotsOnlySchema()

        // Sanity: the feed table is genuinely absent before the migration
        // (this is exactly what caused the iOS crash).
        assertTrue(selectCountSucceeds("cachedSpots"), "spots-only v1 DB should have cachedSpots")
        assertTrue(!selectCountSucceeds("cachedPosts"), "v1 DB must NOT yet have cachedPosts")

        // Run the generated migration 1 -> 2 (ignore the QueryResult).
        ThirdSpaceDb.Schema.migrate(driver, 1, 2)

        // After migration the feed table exists: SELECT count(*) succeeds.
        assertTrue(selectCountSucceeds("cachedPosts"), "cachedPosts must exist after migrate(1,2)")

        // And a real round-trip through LocalFeedCache works against the migrated DB.
        val cache = LocalFeedCache(ThirdSpaceDb(driver))
        cache.replaceAllPosts(
            listOf(
                FeedPost(
                    id = "p1",
                    authorName = "Author",
                    spotName = "Spot",
                    category = "Bar",
                    neighborhood = "Mitte",
                    verified = true,
                    text = "hello",
                    createdAtEpoch = 100L,
                    score = 7,
                    commentCount = 2,
                    myVote = "UP",
                ),
            ),
        )
        val read = cache.getCachedPosts()
        assertEquals(listOf("p1"), read.map { it.id })
        assertEquals("hello", read.first().text)

        // The pre-existing spots data is untouched by the migration.
        assertTrue(selectCountSucceeds("cachedSpots"), "cachedSpots must survive the migration")
    }

    /** Returns true if `SELECT count(*) FROM <table>` executes without throwing. */
    private fun selectCountSucceeds(table: String): Boolean =
        try {
            driver.executeQuery(
                null,
                "SELECT count(*) FROM $table",
                { cursor -> cursor.next() },
                0,
            )
            true
        } catch (_: Throwable) {
            false
        }
}
