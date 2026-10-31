package com.example.thirdspace.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OFFLINE-provable logic tests for [CachingSpotRepository], the [SpotMapping]
 * row<->Spot derivation, and the pending-insert queue — all WITHOUT a real
 * backend, network, device, or SQLDelight driver.
 *
 * These run on BOTH shared targets (Android host + iOS simulator) because they
 * use an in-memory FAKE [SpotCache] and a FAKE [RemoteSpotSource] (no JVM-only
 * driver). The real SQLDelight round-trip is covered separately on the Android
 * host target (LocalSpotCacheDbTest) using the in-memory JdbcSqliteDriver.
 *
 * The background refresh/flush runs on a [Dispatchers.Unconfined] scope so the
 * launched coroutine executes synchronously up to its first real suspension;
 * the fake remote's suspend functions never actually suspend, so assertions
 * after a getSpots()/addSpot() call observe the completed background work
 * deterministically. Real remote persistence is BLOCKED-until-keys and is NOT
 * faked as a network round-trip here.
 */
class CachingSpotRepositoryTest {

    /** Synchronous in-memory [SpotCache] fake (no SQLDelight). */
    private class FakeCache : SpotCache {
        val spots = LinkedHashMap<String, Spot>()
        val pending = mutableListOf<PendingSpotInsert>()

        override fun getCachedSpots(): List<Spot> = spots.values.toList()
        override fun replaceAll(spots: List<Spot>) {
            this.spots.clear()
            spots.forEach { this.spots[it.id] = it }
        }
        override fun upsert(spot: Spot) { spots[spot.id] = spot }
        override fun enqueuePending(spot: Spot, ownerId: String, enqueuedAt: Long) {
            pending.removeAll { it.spot.id == spot.id }
            pending.add(PendingSpotInsert(spot, ownerId, enqueuedAt))
        }
        override fun pendingInserts(): List<PendingSpotInsert> =
            pending.sortedBy { it.enqueuedAt }
        override fun dequeuePending(id: String) { pending.removeAll { it.spot.id == id } }
    }

    /** Controllable fake remote: records inserts, returns canned fetch rows. */
    private class FakeRemote(
        var available: Boolean,
        var fetchResult: List<Spot> = emptyList(),
        var failInsert: Boolean = false,
    ) : RemoteSpotSource {
        val inserted = mutableListOf<Pair<Spot, String>>()
        var fetchCount = 0

        override fun isAvailable(): Boolean = available
        override suspend fun fetchSpots(): List<Spot> {
            fetchCount++
            return fetchResult
        }
        override suspend fun insertSpot(spot: Spot, ownerId: String): Boolean {
            if (!available) return false
            if (failInsert) throw IllegalStateException("insert failed")
            inserted.add(spot to ownerId)
            return true
        }
    }

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @AfterTest
    fun reset() {
        SupabaseClientProvider.configure("", "")
    }

    private fun sampleSpot(id: String, category: String = "Bar") = Spot(
        id = id,
        name = "Spot $id",
        category = category,
        neighborhood = "Mitte",
        verified = true,
        checkInCount = 99,          // should be discarded/re-derived to 0
        placeholderColorArgb = 1L,  // should be re-derived from category
        lat = 52.5,
        lng = 13.4,
    )

    // --- SpotMapping: row <-> Spot derivation ---

    @Test
    fun spotMappingDerivesCheckInCountAndColor() {
        val spot = SpotMapping.toSpot(
            id = "s1", name = "X", category = "Café", neighborhood = "",
            lat = 1.0, lng = 2.0, verifiedInt = 1L,
        )
        assertEquals(0, spot.checkInCount)
        assertTrue(spot.verified)
        // Café color is #F26419 -> opaque ARGB.
        assertEquals(SpotMapping.argbForCategory("Café"), spot.placeholderColorArgb)
        assertEquals(0xFFF26419L, spot.placeholderColorArgb)
    }

    @Test
    fun spotMappingVerifiedZeroIsFalse() {
        val spot = SpotMapping.toSpot("s", "X", "Bar", "", 0.0, 0.0, 0L)
        assertFalse(spot.verified)
    }

    // --- FakeCache round-trip sanity (mirrors LocalSpotCache contract) ---

    @Test
    fun cacheReplaceAllAndReadRoundTrip() {
        val cache = FakeCache()
        cache.replaceAll(listOf(sampleSpot("a"), sampleSpot("b")))
        assertEquals(listOf("a", "b"), cache.getCachedSpots().map { it.id })
    }

    @Test
    fun pendingQueueEnqueueListDequeue() {
        val cache = FakeCache()
        cache.enqueuePending(sampleSpot("a"), "owner-1", enqueuedAt = 2)
        cache.enqueuePending(sampleSpot("b"), "owner-1", enqueuedAt = 1)
        // Ordered oldest-first by enqueuedAt.
        assertEquals(listOf("b", "a"), cache.pendingInserts().map { it.spot.id })
        cache.dequeuePending("b")
        assertEquals(listOf("a"), cache.pendingInserts().map { it.spot.id })
    }

    // --- CachingSpotRepository: synchronous surface + background behavior ---

    @Test
    fun getSpotsServesLocalCacheImmediately() {
        val cache = FakeCache()
        val remote = FakeRemote(available = false)
        val repo = CachingSpotRepository(
            cache = cache, scope = scope, remote = remote,
            ownerIdProvider = { "owner-1" }, isAuthenticated = { false },
            clock = { 100L }, seedIfEmpty = listOf(sampleSpot("seed")),
        )
        // Seeded because the cache was empty.
        assertEquals(listOf("seed"), repo.getSpots().map { it.id })
    }

    @Test
    fun addSpotWritesThroughAndEnqueuesWhenOffline() {
        val cache = FakeCache()
        val remote = FakeRemote(available = false) // offline/unconfigured
        val repo = CachingSpotRepository(
            cache = cache, scope = scope, remote = remote,
            ownerIdProvider = { "owner-1" }, isAuthenticated = { false },
            clock = { 100L }, seedIfEmpty = emptyList(),
        )
        repo.addSpot(sampleSpot("new"))
        // Written through to the cache immediately (pin shows at once).
        assertEquals(listOf("new"), cache.getCachedSpots().map { it.id })
        // Still queued because the remote is unavailable; no crash.
        assertEquals(listOf("new"), cache.pendingInserts().map { it.spot.id })
        assertTrue(remote.inserted.isEmpty())
    }

    @Test
    fun addSpotPushesAndDequeuesWhenOnline() {
        val cache = FakeCache()
        val remote = FakeRemote(available = true)
        var notified = 0
        val repo = CachingSpotRepository(
            cache = cache, scope = scope, remote = remote,
            ownerIdProvider = { "owner-7" }, isAuthenticated = { true },
            clock = { 100L }, onChanged = { notified++ }, seedIfEmpty = emptyList(),
        )
        repo.addSpot(sampleSpot("new"))
        // Pushed with the owner id and dequeued on success.
        assertEquals(1, remote.inserted.size)
        assertEquals("new" to "owner-7", remote.inserted.first().let { it.first.id to it.second })
        assertTrue(cache.pendingInserts().isEmpty())
        assertTrue(notified >= 1)
    }

    @Test
    fun getSpotsRefreshReplacesCacheAndFlushesQueue() {
        val cache = FakeCache()
        // One item already queued (e.g. created earlier offline).
        cache.upsert(sampleSpot("queued"))
        cache.enqueuePending(sampleSpot("queued"), "owner-1", enqueuedAt = 1)
        val remote = FakeRemote(
            available = true,
            fetchResult = listOf(sampleSpot("remote-a"), sampleSpot("remote-b")),
        )
        var notified = 0
        val repo = CachingSpotRepository(
            cache = cache, scope = scope, remote = remote,
            ownerIdProvider = { "owner-1" }, isAuthenticated = { true },
            clock = { 100L }, onChanged = { notified++ }, seedIfEmpty = emptyList(),
        )
        repo.refresh()
        // Replace-all installed the remote rows.
        assertEquals(listOf("remote-a", "remote-b"), cache.getCachedSpots().map { it.id })
        // The previously-queued insert was flushed and dequeued.
        assertTrue(cache.pendingInserts().isEmpty())
        assertEquals(1, remote.inserted.size)
        assertEquals("queued", remote.inserted.first().first.id)
        assertTrue(notified >= 1)
    }

    @Test
    fun unauthenticatedRefreshServesCacheWithoutRemoteCall() {
        val cache = FakeCache()
        cache.upsert(sampleSpot("local"))
        val remote = FakeRemote(available = true, fetchResult = listOf(sampleSpot("remote")))
        val repo = CachingSpotRepository(
            cache = cache, scope = scope, remote = remote,
            ownerIdProvider = { "" }, isAuthenticated = { false }, // not signed in
            clock = { 100L }, seedIfEmpty = emptyList(),
        )
        repo.refresh()
        // Not authenticated -> no remote fetch, cache untouched.
        assertEquals(0, remote.fetchCount)
        assertEquals(listOf("local"), cache.getCachedSpots().map { it.id })
    }

    // --- ROOT-CAUSE regression: reads must NOT trigger remote fetches ---
    // (Automated proxy for the per-second Supabase GET flood.)

    @Test
    fun getSpotsNeverTriggersRemoteFetchEvenRepeatedly() {
        val cache = FakeCache()
        cache.upsert(sampleSpot("local"))
        val remote = FakeRemote(available = true, fetchResult = listOf(sampleSpot("remote")))
        val repo = CachingSpotRepository(
            cache = cache, scope = scope, remote = remote,
            ownerIdProvider = { "owner-1" }, isAuthenticated = { true },
            clock = { 100L }, seedIfEmpty = emptyList(),
        )
        repeat(5) { repo.getSpots() }
        // Pure read: ZERO fetches, cache untouched by any refresh.
        assertEquals(0, remote.fetchCount)
        assertEquals(listOf("local"), cache.getCachedSpots().map { it.id })
    }

    @Test
    fun refreshTriggersExactlyOneFetch() {
        val cache = FakeCache()
        val remote = FakeRemote(available = true, fetchResult = listOf(sampleSpot("remote")))
        val repo = CachingSpotRepository(
            cache = cache, scope = scope, remote = remote,
            ownerIdProvider = { "owner-1" }, isAuthenticated = { true },
            clock = { 100L }, seedIfEmpty = emptyList(),
        )
        repo.refresh()
        assertEquals(1, remote.fetchCount)
    }

    @Test
    fun backToBackRefreshCoalescesToOneInFlightFetch() {
        val cache = FakeCache()
        // Gated fake: the first fetch stays in flight (suspended on the gate)
        // so a second refresh cannot start a concurrent fetch (in-flight guard
        // + syncMutex coalesce). Without coalescing the read-loop stacked ~1/s.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val remote = object : RemoteSpotSource {
            var fetchCount = 0
            override fun isAvailable() = true
            override suspend fun fetchSpots(): List<Spot> {
                fetchCount++
                gate.await()
                return emptyList()
            }
            override suspend fun insertSpot(spot: Spot, ownerId: String) = true
        }
        val repo = CachingSpotRepository(
            cache = cache, scope = scope, remote = remote,
            ownerIdProvider = { "owner-1" }, isAuthenticated = { true },
            clock = { 100L }, seedIfEmpty = emptyList(),
        )
        repo.refresh()
        repo.refresh()
        assertEquals(1, remote.fetchCount)
        gate.complete(Unit)
    }

    @Test
    fun failedRemoteInsertLeavesItemQueued() = runBlocking {
        val cache = FakeCache()
        val remote = FakeRemote(available = true, failInsert = true)
        val repo = CachingSpotRepository(
            cache = cache, scope = scope, remote = remote,
            ownerIdProvider = { "owner-1" }, isAuthenticated = { true },
            clock = { 100L }, seedIfEmpty = emptyList(),
        )
        repo.addSpot(sampleSpot("new"))
        // Insert threw -> item stays queued for a later retry; no crash.
        assertEquals(listOf("new"), cache.pendingInserts().map { it.spot.id })
        assertTrue(remote.inserted.isEmpty())
    }
}
