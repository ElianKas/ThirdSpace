package com.example.thirdspace.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Offline-first repository for the personal spot-collections seam (Phase C2).
 * Mirrors [FollowRepository] / [ProfileRepository]'s synchronous,
 * side-effect-free-read surface with the SMALLEST offline story (IN-MEMORY
 * caches only — NO SQLDelight, NO `.sqm` migration; nothing crosses the
 * FeedBridge boundary yet — that is C3).
 *
 * READS are PURE (trigger NO remote fetch), per the architecture convention:
 * - [collections] — the current user's cached collections (empty before the
 *   first [refreshCollections]).
 * - [spotsIn] — the cached spots of a collection (empty before its
 *   [refreshSpotsIn]).
 * - [collectionsContaining] — the cached collection-ids that contain a spot
 *   (empty before its [refreshMembership] / an optimistic add).
 * - [isSpotInCollection] — derived from the membership cache (false before any
 *   refresh / optimistic add).
 *
 * REFRESHES are the EXPLICIT remote pulls (what a bridge / pull-to-refresh will
 * call later), each on [scope] with an in-flight coalesce guard under a [Mutex]
 * (mirrors [FollowRepository]). Each gates on
 * `remote.isAvailable() && isAuthenticated()`, is best-effort (a throw keeps
 * the prior cached value), and fires [onChanged] once when something changed:
 * - [refreshCollections] — the current user's collection list (also feeds the
 *   reconcile-on-success that swaps an optimistic `temp-…` row for the real
 *   persisted one).
 * - [refreshSpotsIn] — the spots of one collection.
 * - [refreshMembership] — which collections contain one spot.
 *
 * WRITES are OPTIMISTIC: the in-memory caches update FIRST and [onChanged]
 * fires, THEN a best-effort remote push runs on [scope]. There is NO durable
 * write-queue (mirrors [FollowRepository.setFollow] /
 * [ProfileRepository.updateProfile]): offline / unauthenticated KEEPS the
 * optimistic state for the session but reports `false` via the flat `onResult`
 * callback (never a silent success). A blank-name create is a no-op reporting
 * `false`. After a SUCCESSFUL create / add / remove a reconciling
 * [refreshCollections] is kicked so the optimistic `temp-…` create row picks up
 * its real DB id + count (mirrors FollowRepository's reconcile-on-success).
 *
 * All suspend / coroutine / supabase work happens on [scope]; none crosses to
 * Swift. [onChanged] is a flat listener a later bridge wires to a
 * Dispatchers.Main scope so Swift can re-read after a background change.
 */
class CollectionRepository internal constructor(
    private val scope: CoroutineScope,
    private val remote: CollectionRemoteSource = SupabaseCollectionRepository(),
    private val isAuthenticated: () -> Boolean = { AuthRepository.isAuthenticated() },
    private val currentUserId: () -> String = { AuthRepository.currentUserId() },
    private val onChanged: () -> Unit = {},
) {

    private val syncMutex = Mutex()

    // In-memory caches (guarded via syncMutex on the refresh/push paths; the
    // optimistic write path mutates them directly before the lock, consistent
    // with FollowRepository's optimistic update).
    private var collections: List<SpotCollection> = emptyList()
    private val spotsByCollection = mutableMapOf<String, List<Spot>>()
    private val membershipBySpot = mutableMapOf<String, List<String>>()

    // In-flight guard so concurrent/back-to-back refreshes coalesce to a single
    // remote pass rather than stacking (mirrors FollowRepository).
    private var refreshing = false

    // --- PURE reads (NO remote fetch) ---

    /** The current user's cached collections (empty before [refreshCollections]). */
    fun collections(): List<SpotCollection> = collections

    /** Cached spots of [collectionId] (empty before its [refreshSpotsIn]). */
    fun spotsIn(collectionId: String): List<Spot> = spotsByCollection[collectionId].orEmpty()

    /**
     * Cached ids of the collections that contain [spotId] (empty before its
     * [refreshMembership] / an optimistic add). PURE — no remote fetch.
     */
    fun collectionsContaining(spotId: String): List<String> =
        membershipBySpot[spotId].orEmpty()

    /**
     * Whether [spotId] is saved in [collectionId] (derived from the membership
     * cache). `false` before any refresh / optimistic add. PURE — no fetch.
     */
    fun isSpotInCollection(collectionId: String, spotId: String): Boolean =
        membershipBySpot[spotId]?.contains(collectionId) ?: false

    // --- EXPLICIT refresh triggers ---

    /** Explicit remote refresh of the CURRENT user's collection list. */
    fun refreshCollections() {
        scope.launch { refreshCollectionsFromRemote() }
    }

    /** Explicit remote refresh of the spots saved in [collectionId]. */
    fun refreshSpotsIn(collectionId: String) {
        scope.launch { refreshSpotsInFromRemote(collectionId) }
    }

    /** Explicit remote refresh of which collections contain [spotId]. */
    fun refreshMembership(spotId: String) {
        scope.launch { refreshMembershipFromRemote(spotId) }
    }

    /**
     * Background refresh of the current user's collection list. Gated on
     * availability + auth; best-effort (a throw keeps the prior cached list);
     * coalesced; fires [onChanged] once when the list changed. A successful
     * fetch REPLACES the cache, which is what reconciles an optimistic
     * `temp-…` create row into its real persisted row.
     */
    private suspend fun refreshCollectionsFromRemote() {
        if (!remote.isAvailable() || !isAuthenticated()) return
        val owner = currentUserId()
        if (owner.isEmpty()) return
        syncMutex.withLock {
            if (refreshing) return@withLock
            refreshing = true
            var changed = false
            try {
                val fetched = runCatchingOrNull { remote.fetchCollections(owner) }
                if (fetched != null && fetched != collections) {
                    collections = fetched
                    changed = true
                }
            } finally {
                refreshing = false
            }
            if (changed) onChanged()
        }
    }

    private suspend fun refreshSpotsInFromRemote(collectionId: String) {
        if (!remote.isAvailable() || !isAuthenticated()) return
        if (collectionId.isEmpty()) return
        syncMutex.withLock {
            if (refreshing) return@withLock
            refreshing = true
            var changed = false
            try {
                val fetched = runCatchingOrNull { remote.fetchSpotsIn(collectionId) }
                if (fetched != null && spotsByCollection[collectionId] != fetched) {
                    spotsByCollection[collectionId] = fetched
                    changed = true
                }
            } finally {
                refreshing = false
            }
            if (changed) onChanged()
        }
    }

    private suspend fun refreshMembershipFromRemote(spotId: String) {
        if (!remote.isAvailable() || !isAuthenticated()) return
        if (spotId.isEmpty()) return
        syncMutex.withLock {
            if (refreshing) return@withLock
            refreshing = true
            var changed = false
            try {
                val fetched = runCatchingOrNull { remote.fetchCollectionsContaining(spotId) }
                if (fetched != null && membershipBySpot[spotId] != fetched) {
                    membershipBySpot[spotId] = fetched
                    changed = true
                }
            } finally {
                refreshing = false
            }
            if (changed) onChanged()
        }
    }

    // --- Optimistic writes ---

    /**
     * Create a collection named [name]: OPTIMISTIC in-memory append FIRST (a
     * [SpotCollection] with a local `temp-…` id, spotCount 0, empty
     * createdAtLabel), fire [onChanged], THEN best-effort remote push on
     * [scope]. Reports the remote outcome via [onResult]; on success a
     * reconciling [refreshCollections] swaps the temp row for the real one.
     *
     * A blank [name] is a no-op reporting `false`. Offline / unauthenticated
     * KEEPS the optimistic row but reports `false` (NO durable queue).
     */
    fun createCollection(name: String, onResult: (Boolean) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            onResult(false)
            return
        }
        val me = currentUserId()

        // 1. OPTIMISTIC local append + notify.
        val tempId = "temp-${Random.nextLong()}"
        collections = collections + SpotCollection(
            id = tempId,
            name = trimmed,
            spotCount = 0,
            createdAtLabel = "",
        )
        onChanged()

        // 2. Best-effort remote push.
        scope.launch {
            if (!remote.isAvailable() || !isAuthenticated() || me.isEmpty()) {
                onResult(false)
                return@launch
            }
            val ok = try {
                syncMutex.withLock { remote.createCollection(me, trimmed) }
            } catch (_: Throwable) {
                false
            }
            if (ok) refreshCollectionsFromRemote() // reconcile temp-id -> real row
            onResult(ok)
        }
    }

    /**
     * Rename [collectionId] to [name]: OPTIMISTIC in-memory name update FIRST,
     * fire [onChanged], THEN best-effort remote push. A blank [name] is a no-op
     * reporting `false`. Offline / unauthenticated keeps the optimistic name
     * but reports `false`.
     */
    fun renameCollection(collectionId: String, name: String, onResult: (Boolean) -> Unit) {
        val trimmed = name.trim()
        if (collectionId.isEmpty() || trimmed.isEmpty()) {
            onResult(false)
            return
        }

        // 1. OPTIMISTIC local rename + notify.
        collections = collections.map {
            if (it.id == collectionId) it.copy(name = trimmed) else it
        }
        onChanged()

        // 2. Best-effort remote push.
        scope.launch {
            if (!remote.isAvailable() || !isAuthenticated()) {
                onResult(false)
                return@launch
            }
            val ok = try {
                syncMutex.withLock { remote.renameCollection(collectionId, trimmed) }
            } catch (_: Throwable) {
                false
            }
            onResult(ok)
        }
    }

    /**
     * Delete [collectionId]: OPTIMISTIC in-memory removal FIRST (drop it from
     * the list, its spot cache, and any membership entries), fire [onChanged],
     * THEN best-effort remote push. Offline / unauthenticated keeps the
     * optimistic removal but reports `false`.
     */
    fun deleteCollection(collectionId: String, onResult: (Boolean) -> Unit) {
        if (collectionId.isEmpty()) {
            onResult(false)
            return
        }

        // 1. OPTIMISTIC local removal + notify.
        collections = collections.filterNot { it.id == collectionId }
        spotsByCollection.remove(collectionId)
        for ((spot, ids) in membershipBySpot.toMap()) {
            if (ids.contains(collectionId)) {
                membershipBySpot[spot] = ids.filterNot { it == collectionId }
            }
        }
        onChanged()

        // 2. Best-effort remote push.
        scope.launch {
            if (!remote.isAvailable() || !isAuthenticated()) {
                onResult(false)
                return@launch
            }
            val ok = try {
                syncMutex.withLock { remote.deleteCollection(collectionId) }
            } catch (_: Throwable) {
                false
            }
            onResult(ok)
        }
    }

    /**
     * Save [spotId] into [collectionId]: OPTIMISTIC in-memory update FIRST (add
     * the collection id to the spot's membership + bump the collection's
     * spotCount), fire [onChanged], THEN best-effort remote push. Reports the
     * remote outcome; on success a reconciling [refreshCollections] trues up
     * the counts. A no-op (already saved) reports `false`. Offline / unauth
     * keeps the optimistic state but reports `false`.
     */
    fun addSpot(collectionId: String, spotId: String, onResult: (Boolean) -> Unit) {
        if (collectionId.isEmpty() || spotId.isEmpty()) {
            onResult(false)
            return
        }
        // GUARD: a spotId that is not a canonical uuid can NEVER be a legal
        // `collection_spots.spot_id` (that column is a Postgres `uuid` FK to
        // `public.spots`). Sending a non-uuid id — e.g. a SampleData offline
        // SEED spot, or any not-yet-persisted local spot — triggers a Postgres
        // `22P02` "invalid input syntax for type uuid" 400 (the reported crash,
        // e.g. "spot-bonanza"). Reject it as a NO-OP reporting false WITHOUT
        // mutating the membership / spotCount cache and WITHOUT any remote call,
        // so the bad request never leaves the device. (The uuid shape is
        // necessary but not sufficient — a valid-shaped id that isn't a DB row
        // still fails the FK remotely; that path is blocked-until-session.)
        if (!Uuid.isValid(spotId)) {
            onResult(false)
            return
        }
        // No-op if already saved (avoids a double count bump).
        if (isSpotInCollection(collectionId, spotId)) {
            onResult(false)
            return
        }

        // 1. OPTIMISTIC membership add + spotCount bump + notify.
        membershipBySpot[spotId] = membershipBySpot[spotId].orEmpty() + collectionId
        collections = collections.map {
            if (it.id == collectionId) it.copy(spotCount = it.spotCount + 1) else it
        }
        onChanged()

        // 2. Best-effort remote push.
        scope.launch {
            if (!remote.isAvailable() || !isAuthenticated()) {
                onResult(false)
                return@launch
            }
            val ok = try {
                syncMutex.withLock { remote.addSpot(collectionId, spotId) }
            } catch (_: Throwable) {
                false
            }
            if (ok) refreshCollectionsFromRemote() // reconcile spotCount
            onResult(ok)
        }
    }

    /**
     * Unsave [spotId] from [collectionId]: OPTIMISTIC in-memory update FIRST
     * (drop the collection id from the spot's membership + decrement the
     * collection's spotCount, floored at 0), fire [onChanged], THEN best-effort
     * remote push. A no-op (not saved) reports `false`. Offline / unauth keeps
     * the optimistic state but reports `false`.
     */
    fun removeSpot(collectionId: String, spotId: String, onResult: (Boolean) -> Unit) {
        if (collectionId.isEmpty() || spotId.isEmpty()) {
            onResult(false)
            return
        }
        // No-op if not currently saved.
        if (!isSpotInCollection(collectionId, spotId)) {
            onResult(false)
            return
        }

        // 1. OPTIMISTIC membership removal + spotCount decrement + notify.
        membershipBySpot[spotId] = membershipBySpot[spotId].orEmpty().filterNot { it == collectionId }
        collections = collections.map {
            if (it.id == collectionId) it.copy(spotCount = maxOf(0, it.spotCount - 1)) else it
        }
        onChanged()

        // 2. Best-effort remote push.
        scope.launch {
            if (!remote.isAvailable() || !isAuthenticated()) {
                onResult(false)
                return@launch
            }
            val ok = try {
                syncMutex.withLock { remote.removeSpot(collectionId, spotId) }
            } catch (_: Throwable) {
                false
            }
            if (ok) refreshCollectionsFromRemote() // reconcile spotCount
            onResult(ok)
        }
    }

    /**
     * Run [block] and return its result, or `null` if it threw. Used so one
     * failing sub-query degrades gracefully without aborting the others
     * (best-effort per part). Mirrors [FollowRepository.runCatchingOrNull].
     */
    private inline fun <T> runCatchingOrNull(block: () -> T): T? =
        try { block() } catch (_: Throwable) { null }
}

/**
 * The process-wide collections repository provider, mirroring [Follows] /
 * [Profiles] / [Feed]. Builds a [CollectionRepository] over the supabase-kt
 * [SupabaseCollectionRepository], running background work on a shared
 * [Dispatchers.Default] scope.
 *
 * LISTENER: [setChangeListener] lets a later iOS bridge (C3) register a flat
 * callback (delivered on its own Dispatchers.Main scope) that fires after a
 * background change, so Swift can re-read. THE BRIDGE IS NOT WIRED IN THIS
 * PHASE — these forwarders exist for C3 to call.
 */
object Collections {

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var changeListener: () -> Unit = {}

    val repository: CollectionRepository by lazy {
        CollectionRepository(scope = backgroundScope, onChanged = { changeListener() })
    }

    /**
     * Register a flat [listener] invoked after a background change. A later
     * bridge wraps this to hop onto Dispatchers.Main.
     */
    fun setChangeListener(listener: () -> Unit) {
        changeListener = listener
    }

    /** Optimistic write forwarders (for the later C3 bridge). */
    fun createCollection(name: String, onResult: (Boolean) -> Unit) {
        repository.createCollection(name, onResult)
    }

    fun renameCollection(collectionId: String, name: String, onResult: (Boolean) -> Unit) {
        repository.renameCollection(collectionId, name, onResult)
    }

    fun deleteCollection(collectionId: String, onResult: (Boolean) -> Unit) {
        repository.deleteCollection(collectionId, onResult)
    }

    fun addSpot(collectionId: String, spotId: String, onResult: (Boolean) -> Unit) {
        repository.addSpot(collectionId, spotId, onResult)
    }

    fun removeSpot(collectionId: String, spotId: String, onResult: (Boolean) -> Unit) {
        repository.removeSpot(collectionId, spotId, onResult)
    }

    /** Explicit refresh forwarders (for the later C3 bridge). */
    fun refreshCollections() { repository.refreshCollections() }
    fun refreshSpotsIn(collectionId: String) { repository.refreshSpotsIn(collectionId) }
    fun refreshMembership(spotId: String) { repository.refreshMembership(spotId) }
}
