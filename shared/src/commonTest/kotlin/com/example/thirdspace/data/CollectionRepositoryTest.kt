package com.example.thirdspace.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OFFLINE-provable logic tests for [CollectionRepository] (the personal
 * spot-collections seam, Phase C2) — all WITHOUT a real backend, network,
 * device, or SQLDelight driver.
 *
 * These run on BOTH shared targets (Android host + iOS simulator) because they
 * use an in-memory FAKE [CollectionRemoteSource] (no JVM-only driver). The REAL
 * Supabase collection round-trip (actual `collections` / `collection_spots`
 * INSERT/UPDATE/DELETE/SELECT) is BLOCKED-until-session (needs the user's keys
 * + migrations + a live login) and is NOT faked as a network round-trip here;
 * this fake is the automated proof of the optimistic / side-effect-free-read /
 * refresh behavior.
 *
 * The background push/refresh runs on a [Dispatchers.Unconfined] scope so the
 * launched coroutine executes synchronously up to the fake's (non-suspending)
 * suspend calls; assertions after a write/refresh observe the completed
 * background work deterministically — mirroring [FollowRepositoryTest].
 */
class CollectionRepositoryTest {

    /** Controllable fake remote: records calls, returns canned data. */
    private class FakeCollectionRemote(
        var available: Boolean = true,
        var collections: List<SpotCollection> = emptyList(),
        var spotsIn: List<Spot> = emptyList(),
        var containing: List<String> = emptyList(),
    ) : CollectionRemoteSource {
        var fetchCollectionsCount = 0
        var fetchSpotsInCount = 0
        var fetchContainingCount = 0
        var createCount = 0
        var renameCount = 0
        var deleteCount = 0
        var addCount = 0
        var removeCount = 0

        override fun isAvailable(): Boolean = available

        override suspend fun fetchCollections(ownerId: String): List<SpotCollection> {
            fetchCollectionsCount++
            return collections
        }

        override suspend fun fetchSpotsIn(collectionId: String): List<Spot> {
            fetchSpotsInCount++
            return spotsIn
        }

        override suspend fun fetchCollectionsContaining(spotId: String): List<String> {
            fetchContainingCount++
            return containing
        }

        override suspend fun createCollection(ownerId: String, name: String): Boolean {
            createCount++
            return true
        }

        override suspend fun renameCollection(collectionId: String, name: String): Boolean {
            renameCount++
            return true
        }

        override suspend fun deleteCollection(collectionId: String): Boolean {
            deleteCount++
            return true
        }

        override suspend fun addSpot(collectionId: String, spotId: String): Boolean {
            addCount++
            return true
        }

        override suspend fun removeSpot(collectionId: String, spotId: String): Boolean {
            removeCount++
            return true
        }
    }

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun spot(id: String) = Spot(
        id = id,
        name = "Spot $id",
        category = "Café",
        neighborhood = "Mitte",
        verified = false,
        checkInCount = 0,
        placeholderColorArgb = 0xFF000000L,
        lat = 52.0,
        lng = 13.0,
    )

    private fun collection(id: String, name: String = "Coll $id", spotCount: Int = 0) =
        SpotCollection(id = id, name = name, spotCount = spotCount, createdAtLabel = "")

    private fun repo(
        remote: FakeCollectionRemote,
        authed: Boolean = true,
        me: String = "me",
        onChanged: () -> Unit = {},
    ) = CollectionRepository(
        scope = scope, remote = remote,
        isAuthenticated = { authed }, currentUserId = { me }, onChanged = onChanged,
    )

    // --- create: optimistic, observable before the remote resolves ---

    @Test
    fun createCollectionIsOptimisticBeforeRemoteResolves() {
        // Gate the remote create so it stays in flight; the pure collections()
        // read must already show the new (temp) collection before it resolves.
        val gate = CompletableDeferred<Unit>()
        val remote = object : CollectionRemoteSource {
            override fun isAvailable() = true
            override suspend fun fetchCollections(ownerId: String) = emptyList<SpotCollection>()
            override suspend fun fetchSpotsIn(collectionId: String) = emptyList<Spot>()
            override suspend fun fetchCollectionsContaining(spotId: String) = emptyList<String>()
            override suspend fun createCollection(ownerId: String, name: String): Boolean {
                gate.await() // stay in flight
                return true
            }
            override suspend fun renameCollection(collectionId: String, name: String) = true
            override suspend fun deleteCollection(collectionId: String) = true
            override suspend fun addSpot(collectionId: String, spotId: String) = true
            override suspend fun removeSpot(collectionId: String, spotId: String) = true
        }
        val r = CollectionRepository(
            scope = scope, remote = remote,
            isAuthenticated = { true }, currentUserId = { "me" },
        )
        r.createCollection("Wasser") { }
        // Remote create still suspended, yet the list already reflects it.
        assertEquals(1, r.collections().size)
        assertEquals("Wasser", r.collections().first().name)
        assertEquals(0, r.collections().first().spotCount)
        assertTrue(r.collections().first().id.startsWith("temp-"))
        gate.complete(Unit)
    }

    @Test
    fun createCollectionReconcilesTempIdFromRefresh() {
        // After a successful create, the repo kicks a reconciling refresh; the
        // fake returns the real persisted row, replacing the temp-id row.
        val remote = FakeCollectionRemote(available = true)
        remote.collections = listOf(collection("real-1", name = "Wasser", spotCount = 0))
        val r = repo(remote)
        var result: Boolean? = null
        r.createCollection("Wasser") { result = it }
        assertEquals(true, result)
        // Reconciled: the temp-id row is gone, the real row is present.
        assertEquals(listOf("real-1"), r.collections().map { it.id })
        assertEquals(1, remote.createCount)
        assertTrue(remote.fetchCollectionsCount >= 1)
    }

    // --- rename / delete through the pure read ---

    @Test
    fun renameCollectionUpdatesCachedName() {
        val remote = FakeCollectionRemote(available = true)
        remote.collections = listOf(collection("c1", name = "Old"))
        val r = repo(remote)
        r.refreshCollections()
        assertEquals("Old", r.collections().first().name)

        var result: Boolean? = null
        r.renameCollection("c1", "New") { result = it }
        assertEquals("New", r.collections().first { it.id == "c1" }.name)
        assertEquals(true, result)
        assertEquals(1, remote.renameCount)
    }

    @Test
    fun deleteCollectionRemovesIt() {
        val remote = FakeCollectionRemote(available = true)
        remote.collections = listOf(collection("c1"), collection("c2"))
        val r = repo(remote)
        r.refreshCollections()
        assertEquals(setOf("c1", "c2"), r.collections().map { it.id }.toSet())

        var result: Boolean? = null
        r.deleteCollection("c1") { result = it }
        assertEquals(listOf("c2"), r.collections().map { it.id })
        assertEquals(true, result)
        assertEquals(1, remote.deleteCount)
    }

    // --- add / remove spot membership + spotCount ---

    @Test
    fun addSpotReflectsInMembershipAndBumpsCountThenRemoveReverses() {
        // Seed one collection, refresh. The reconcile refresh after add returns
        // the same list (spotCount reconciliation is exercised separately), so
        // keep the fake's collections stable to observe the optimistic bump.
        val remote = FakeCollectionRemote(available = true)
        remote.collections = listOf(collection("c1", spotCount = 0))
        val r = repo(remote)
        r.refreshCollections()

        var result: Boolean? = null
        r.addSpot("c1", "s1") { result = it }
        assertEquals(true, result)
        assertTrue(r.isSpotInCollection("c1", "s1"))
        assertEquals(listOf("c1"), r.collectionsContaining("s1"))

        // removeSpot reverses membership.
        r.removeSpot("c1", "s1") { result = it }
        assertEquals(true, result)
        assertFalse(r.isSpotInCollection("c1", "s1"))
        assertEquals(emptyList(), r.collectionsContaining("s1"))
        assertEquals(1, remote.addCount)
        assertEquals(1, remote.removeCount)
    }

    @Test
    fun addSpotBumpsSpotCountOptimistically() {
        // Gate the remote add so the reconcile refresh cannot run; the
        // optimistic spotCount bump must be observable on its own.
        val gate = CompletableDeferred<Unit>()
        val remote = object : CollectionRemoteSource {
            override fun isAvailable() = true
            override suspend fun fetchCollections(ownerId: String) =
                listOf(SpotCollection("c1", "C", 0, ""))
            override suspend fun fetchSpotsIn(collectionId: String) = emptyList<Spot>()
            override suspend fun fetchCollectionsContaining(spotId: String) = emptyList<String>()
            override suspend fun createCollection(ownerId: String, name: String) = true
            override suspend fun renameCollection(collectionId: String, name: String) = true
            override suspend fun deleteCollection(collectionId: String) = true
            override suspend fun addSpot(collectionId: String, spotId: String): Boolean {
                gate.await() // keep in flight so no reconcile overwrites the bump
                return true
            }
            override suspend fun removeSpot(collectionId: String, spotId: String) = true
        }
        val r = CollectionRepository(
            scope = scope, remote = remote,
            isAuthenticated = { true }, currentUserId = { "me" },
        )
        r.refreshCollections()
        assertEquals(0, r.collections().first { it.id == "c1" }.spotCount)
        r.addSpot("c1", "s1") { }
        // Optimistic bump observable while the remote add is still suspended.
        assertEquals(1, r.collections().first { it.id == "c1" }.spotCount)
        assertTrue(r.isSpotInCollection("c1", "s1"))
        gate.complete(Unit)
    }

    @Test
    fun addSpotAlreadySavedIsNoOpReportsFalse() {
        val remote = FakeCollectionRemote(available = true)
        val r = repo(remote)
        r.addSpot("c1", "s1") { }
        assertEquals(1, remote.addCount)
        var result: Boolean? = null
        r.addSpot("c1", "s1") { result = it }
        assertEquals(false, result)
        assertEquals(1, remote.addCount) // not called again
    }

    // --- refreshes populate from the fake; EMPTY + no fetch before refresh ---

    @Test
    fun readsAreEmptyBeforeAnyRefreshAndTriggerNoFetch() {
        val remote = FakeCollectionRemote(
            available = true,
            collections = listOf(collection("c1")),
            spotsIn = listOf(spot("s1")),
            containing = listOf("c1"),
        )
        val r = repo(remote)
        // Pure reads before any refresh: empty, and NO fetch triggered.
        assertEquals(emptyList(), r.collections())
        assertEquals(emptyList(), r.spotsIn("c1"))
        assertEquals(emptyList(), r.collectionsContaining("s1"))
        assertFalse(r.isSpotInCollection("c1", "s1"))
        assertEquals(0, remote.fetchCollectionsCount)
        assertEquals(0, remote.fetchSpotsInCount)
        assertEquals(0, remote.fetchContainingCount)
    }

    @Test
    fun refreshCollectionsPopulatesFromRemote() {
        val remote = FakeCollectionRemote(
            available = true,
            collections = listOf(collection("c1", spotCount = 3), collection("c2")),
        )
        val r = repo(remote)
        r.refreshCollections()
        assertEquals(listOf("c1", "c2"), r.collections().map { it.id })
        assertEquals(3, r.collections().first { it.id == "c1" }.spotCount)
        assertEquals(1, remote.fetchCollectionsCount)
    }

    @Test
    fun refreshSpotsInPopulatesFromRemote() {
        val remote = FakeCollectionRemote(
            available = true,
            spotsIn = listOf(spot("s1"), spot("s2")),
        )
        val r = repo(remote)
        r.refreshSpotsIn("c1")
        assertEquals(listOf("s1", "s2"), r.spotsIn("c1").map { it.id })
        assertEquals(1, remote.fetchSpotsInCount)
    }

    @Test
    fun refreshMembershipPopulatesFromRemote() {
        val remote = FakeCollectionRemote(
            available = true,
            containing = listOf("c1", "c2"),
        )
        val r = repo(remote)
        r.refreshMembership("s1")
        assertEquals(listOf("c1", "c2"), r.collectionsContaining("s1"))
        assertTrue(r.isSpotInCollection("c1", "s1"))
        assertTrue(r.isSpotInCollection("c2", "s1"))
        assertEquals(1, remote.fetchContainingCount)
    }

    // --- offline / unauth keeps optimistic state, reports false, no remote call ---

    @Test
    fun createWhenUnavailableKeepsOptimisticButReportsFalse() {
        val remote = FakeCollectionRemote(available = false)
        val r = repo(remote, authed = true)
        var result: Boolean? = null
        r.createCollection("Wasser") { result = it }
        assertEquals(1, r.collections().size) // optimistic kept
        assertEquals(false, result)
        assertEquals(0, remote.createCount)
    }

    @Test
    fun createWhenUnauthenticatedKeepsOptimisticButReportsFalse() {
        val remote = FakeCollectionRemote(available = true)
        val r = repo(remote, authed = false)
        var result: Boolean? = null
        r.createCollection("Wasser") { result = it }
        assertEquals(1, r.collections().size)
        assertEquals(false, result)
        assertEquals(0, remote.createCount)
    }

    @Test
    fun addSpotWhenUnavailableKeepsOptimisticButReportsFalse() {
        val remote = FakeCollectionRemote(available = false)
        val r = repo(remote, authed = true)
        var result: Boolean? = null
        r.addSpot("c1", "s1") { result = it }
        assertTrue(r.isSpotInCollection("c1", "s1")) // optimistic kept
        assertEquals(false, result)
        assertEquals(0, remote.addCount)
    }

    // --- blank-name create is a no-op reporting false ---

    @Test
    fun blankNameCreateIsNoOpReportsFalse() {
        val remote = FakeCollectionRemote(available = true)
        val r = repo(remote)
        var result: Boolean? = null
        r.createCollection("   ") { result = it }
        assertEquals(false, result)
        assertEquals(emptyList(), r.collections()) // nothing added
        assertEquals(0, remote.createCount)
    }
}
