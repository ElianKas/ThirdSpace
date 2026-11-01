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
    fun schemaVersionIsThree() {
        // 1.sq = v1, 1.sqm -> v2, 2.sqm (Phase 4 `kind` column) -> v3. The
        // presence of the migration files auto-bumps the generated version.
        assertEquals(3L, ThirdSpaceDb.Schema.version)
    }

    @Test
    fun migratingV1DbAddsFeedTablesAndRoundTrips() {
        // Reconstruct the crashing condition: a v1 (spots-only) DB.
        createV1SpotsOnlySchema()

        // Sanity: the feed table is genuinely absent before the migration
        // (this is exactly what caused the iOS crash).
        assertTrue(selectCountSucceeds("cachedSpots"), "spots-only v1 DB should have cachedSpots")
        assertTrue(!selectCountSucceeds("cachedPosts"), "v1 DB must NOT yet have cachedPosts")

        // Run the generated migration 1 -> 3 (1.sqm adds the feed tables; 2.sqm
        // adds the Phase 4 `kind` column). Ignore the QueryResult.
        ThirdSpaceDb.Schema.migrate(driver, 1, 3)

        // After migration the feed table exists: SELECT count(*) succeeds.
        assertTrue(selectCountSucceeds("cachedPosts"), "cachedPosts must exist after migrate(1,3)")

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

    /**
     * Build a pre-existing v2 `cachedPosts` BY HAND (the Phase B feed cache
     * WITHOUT the Phase 4 `kind` column) + `PRAGMA user_version = 2`, so we can
     * prove `migrate(2, 3)` adds the `kind` column in place. Statements mirror
     * the v2 shape (the current .sq `cachedPosts` minus `kind`).
     */
    private fun createV2CachedPostsNoKind() {
        driver.execute(
            null,
            """
            |CREATE TABLE cachedPosts (
            |    id TEXT NOT NULL PRIMARY KEY,
            |    author_name TEXT NOT NULL DEFAULT '',
            |    spot_name TEXT NOT NULL DEFAULT '',
            |    category TEXT NOT NULL DEFAULT '',
            |    neighborhood TEXT NOT NULL DEFAULT '',
            |    verified INTEGER NOT NULL DEFAULT 0,
            |    text TEXT NOT NULL DEFAULT '',
            |    created_at_epoch INTEGER NOT NULL DEFAULT 0,
            |    score INTEGER NOT NULL DEFAULT 0,
            |    comment_count INTEGER NOT NULL DEFAULT 0,
            |    my_vote TEXT NOT NULL DEFAULT 'NONE'
            |)
            """.trimMargin(),
            0,
        )
        // An existing row from before the migration (all such rows were posts).
        driver.execute(
            null,
            "INSERT INTO cachedPosts(id, author_name, spot_name, created_at_epoch) VALUES ('old', 'A', 'S', 50)",
            0,
        )
        driver.execute(null, "PRAGMA user_version = 2", 0)
    }

    @Test
    fun migratingV2DbAddsKindColumnAndBackfillsPost() {
        // Reconstruct a v2 feed cache that predates the Phase 4 `kind` column.
        createV2CachedPostsNoKind()
        assertTrue(!selectColumnExists("cachedPosts", "kind"), "v2 cachedPosts must NOT have `kind`")

        // Run the generated migration 2 -> 3 (2.sqm: ALTER TABLE ADD kind).
        ThirdSpaceDb.Schema.migrate(driver, 2, 3)

        // The `kind` column now exists and the pre-existing row backfilled to 'post'.
        assertTrue(selectColumnExists("cachedPosts", "kind"), "cachedPosts must have `kind` after migrate(2,3)")
        val cache = LocalFeedCache(ThirdSpaceDb(driver))
        val old = cache.getCachedPosts().first { it.id == "old" }
        assertEquals("post", old.kind)

        // A new bare-spot entry round-trips with kind "spot" through the migrated DB.
        cache.upsertPost(
            FeedPost(
                id = "spot:s1",
                authorName = "Owner",
                spotName = "Rooftop",
                category = "Bar",
                neighborhood = "Mitte",
                verified = false,
                text = "",
                createdAtEpoch = 100L,
                score = 0,
                commentCount = 0,
                myVote = "NONE",
                kind = "spot",
            ),
        )
        assertEquals("spot", cache.getCachedPosts().first { it.id == "spot:s1" }.kind)
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

    /** Returns true if `SELECT <column> FROM <table>` executes without throwing. */
    private fun selectColumnExists(table: String, column: String): Boolean =
        try {
            driver.executeQuery(
                null,
                "SELECT $column FROM $table LIMIT 1",
                { cursor -> cursor.next() },
                0,
            )
            true
        } catch (_: Throwable) {
            false
        }
}
