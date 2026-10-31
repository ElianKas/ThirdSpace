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
 * REAL SQLDelight round-trip tests for [LocalSpotCache], exercising the
 * generated [ThirdSpaceDb] queries against an IN-MEMORY SQLite database via
 * [JdbcSqliteDriver] (`JdbcSqliteDriver.IN_MEMORY`).
 *
 * This lives on the Android HOST (JVM) test target — NOT in commonTest —
 * because [JdbcSqliteDriver] is JVM-only (it cannot compile for the iOS native
 * target). The platform native/android drivers are proven by the build
 * (expect/actual compile); their actual on-device behavior is standard
 * SQLDelight and is not re-tested here. The pure caching logic is covered on
 * BOTH targets by commonTest's CachingSpotRepositoryTest.
 *
 * No device/emulator is required: the in-memory driver runs entirely in the
 * JVM host process.
 */
class LocalSpotCacheDbTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var cache: LocalSpotCache

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ThirdSpaceDb.Schema.create(driver)
        cache = LocalSpotCache(ThirdSpaceDb(driver))
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun spot(id: String, category: String = "Bar", verified: Boolean = true) = Spot(
        id = id,
        name = "Spot $id",
        category = category,
        neighborhood = "Mitte",
        verified = verified,
        checkInCount = 42,          // UI-only: must NOT round-trip (re-derived to 0)
        placeholderColorArgb = 7L,  // UI-only: re-derived from category
        lat = 52.5,
        lng = 13.4,
    )

    @Test
    fun replaceAllThenReadRoundTrip() {
        cache.replaceAll(listOf(spot("a", "Café"), spot("b", "Park", verified = false)))
        val read = cache.getCachedSpots().associateBy { it.id }

        assertEquals(setOf("a", "b"), read.keys)
        // Persisted fields survive.
        assertEquals("Spot a", read.getValue("a").name)
        assertEquals("Café", read.getValue("a").category)
        assertEquals(52.5, read.getValue("a").lat)
        assertEquals(13.4, read.getValue("a").lng)
        assertTrue(read.getValue("a").verified)
        assertFalse(read.getValue("b").verified)
        // UI-only fields are re-derived, NOT the stored input values.
        assertEquals(0, read.getValue("a").checkInCount)
        assertEquals(SpotMapping.argbForCategory("Café"), read.getValue("a").placeholderColorArgb)
    }

    @Test
    fun replaceAllIsDestructive() {
        cache.replaceAll(listOf(spot("a")))
        cache.replaceAll(listOf(spot("b"), spot("c")))
        assertEquals(setOf("b", "c"), cache.getCachedSpots().map { it.id }.toSet())
    }

    @Test
    fun upsertOverwritesByPrimaryKey() {
        cache.upsert(spot("a", "Bar"))
        cache.upsert(spot("a", "Park")) // same id, different category
        val all = cache.getCachedSpots()
        assertEquals(1, all.size)
        assertEquals("Park", all.first().category)
    }

    @Test
    fun pendingQueueEnqueueListDequeueRoundTrip() {
        cache.enqueuePending(spot("a"), ownerId = "owner-1", enqueuedAt = 200L)
        cache.enqueuePending(spot("b"), ownerId = "owner-2", enqueuedAt = 100L)

        val pending = cache.pendingInserts()
        // Ordered oldest-first by enqueued_at.
        assertEquals(listOf("b", "a"), pending.map { it.spot.id })
        assertEquals("owner-2", pending.first().ownerId)
        assertEquals(100L, pending.first().enqueuedAt)
        // UI-only fields re-derived on the queued spot too.
        assertEquals(0, pending.first().spot.checkInCount)

        cache.dequeuePending("b")
        assertEquals(listOf("a"), cache.pendingInserts().map { it.spot.id })
    }

    @Test
    fun endToEndOfflineThenFlushWithCachingRepoOverRealDb() {
        // CachingSpotRepository over the REAL SQLDelight cache + a fake remote,
        // proving the OFFLINE logic (write-through + queue + flush) against the
        // actual DB. The REAL Supabase round-trip stays BLOCKED-until-keys.
        val remote = object : RemoteSpotSource {
            var available = false
            val inserted = mutableListOf<String>()
            override fun isAvailable() = available
            override suspend fun fetchSpots(): List<Spot> = emptyList()
            override suspend fun insertSpot(spot: Spot, ownerId: String): Boolean {
                if (!available) return false
                inserted.add(spot.id); return true
            }
        }
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val repo = CachingSpotRepository(
            cache = cache, scope = scope, remote = remote,
            ownerIdProvider = { "owner-1" }, isAuthenticated = { true },
            clock = { 1L }, seedIfEmpty = emptyList(),
        )

        // Offline add: written to the DB cache + queued, not pushed.
        repo.addSpot(spot("x"))
        assertEquals(listOf("x"), cache.getCachedSpots().map { it.id })
        assertEquals(listOf("x"), cache.pendingInserts().map { it.spot.id })
        assertTrue(remote.inserted.isEmpty())

        // Come online and refresh -> the queued insert flushes and dequeues.
        remote.available = true
        repo.getSpots()
        assertEquals(listOf("x"), remote.inserted)
        assertTrue(cache.pendingInserts().isEmpty())
    }
}
