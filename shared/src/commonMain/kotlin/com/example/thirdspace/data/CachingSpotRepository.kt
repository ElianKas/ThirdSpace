package com.example.thirdspace.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Offline-first [SpotRepository] composing a synchronous local [SpotCache]
 * (the source of truth the map reads) with a remote [RemoteSpotSource] and a
 * background [scope].
 *
 * SYNCHRONOUS SURFACE (keeps the existing [SpotRepository] contract, so
 * FeedBridge.spots()/addSpot stay flat and unchanged):
 * - [getSpots] returns the LOCAL cache immediately, then kicks off a background
 *   refresh (fetch remote -> replace-all into cache -> flush the pending-insert
 *   queue -> notify the listener).
 * - [addSpot] write-throughs to the cache immediately (so the pin shows at once
 *   via the existing in-session re-read) AND enqueues a pending insert, then
 *   kicks off a background push attempt.
 *
 * All suspend / coroutine / supabase work happens on [scope]; none of it
 * crosses to Swift. [onChanged] is an optional flat listener the bridge wires
 * to a Dispatchers.Main scope so Swift can re-read FeedData.spots() after a
 * background refresh/flush changes the cache.
 *
 * SEEDING: if the cache is empty on construction (first launch, pre-login), it
 * is seeded from [SampleData] so the map is not empty before the first remote
 * refpawn. Those seed spots are overwritten by the first successful remote
 * replace-all once configured + authenticated.
 *
 * NOT-CONFIGURED / OFFLINE: when the remote is unavailable or the user is not
 * authenticated, [getSpots] just serves the cache and [addSpot] leaves the item
 * queued (retried on the next [getSpots] refresh). Nothing throws.
 */
class CachingSpotRepository internal constructor(
    private val cache: SpotCache,
    private val scope: CoroutineScope,
    private val remote: RemoteSpotSource = SupabaseSpotRepository(),
    private val ownerIdProvider: () -> String = { AuthRepository.currentUserId() },
    private val isAuthenticated: () -> Boolean = { AuthRepository.isAuthenticated() },
    private val clock: () -> Long = { currentEpochMillis() },
    private val onChanged: () -> Unit = {},
    seedIfEmpty: List<Spot> = InMemorySpotRepository.defaultSeed(),
) : SpotRepository {

    // Serializes background refresh/flush runs so two getSpots() calls don't
    // race on the cache (replace-all + queue flush must be atomic-ish).
    private val syncMutex = Mutex()

    init {
        if (cache.getCachedSpots().isEmpty()) {
            cache.replaceAll(seedIfEmpty)
        }
    }

    override fun getSpots(): List<Spot> {
        val local = cache.getCachedSpots()
        scope.launch { refreshFromRemote() }
        return local
    }

    override fun addSpot(spot: Spot) {
        // Write-through: show the pin immediately and remember the push.
        cache.upsert(spot)
        cache.enqueuePending(spot, ownerIdProvider(), clock())
        scope.launch { flushPending() }
    }

    /**
     * Background refresh: fetch remote spots, replace-all into the cache, then
     * flush the pending queue. Serves the cache unchanged when the remote is
     * unavailable or unauthenticated. Swallows errors (offline/transient) so a
     * failed refresh never crashes; the queue is retried next time.
     */
    private suspend fun refreshFromRemote() {
        if (!remote.isAvailable() || !isAuthenticated()) return
        syncMutex.withLock {
            var changed = false
            try {
                val remoteSpots = remote.fetchSpots()
                cache.replaceAll(remoteSpots)
                changed = true
            } catch (_: Throwable) {
                // Offline / transient: keep serving the existing cache.
            }
            if (flushPendingLocked()) changed = true
            if (changed) onChanged()
        }
    }

    /** Flush the pending-insert queue under the sync lock. */
    private suspend fun flushPending() {
        if (!remote.isAvailable() || !isAuthenticated()) return
        syncMutex.withLock {
            if (flushPendingLocked()) onChanged()
        }
    }

    /**
     * Try to push each queued insert; dequeue on success. Returns `true` if any
     * item was successfully pushed (so the caller can notify). Stops the run on
     * the first failure (likely offline) and leaves the rest queued.
     */
    private suspend fun flushPendingLocked(): Boolean {
        var pushedAny = false
        for (pending in cache.pendingInserts()) {
            val ok = try {
                remote.insertSpot(pending.spot, pending.ownerId)
            } catch (_: Throwable) {
                false
            }
            if (ok) {
                cache.dequeuePending(pending.spot.id)
                pushedAny = true
            } else {
                // Unavailable or failed: stop; retry on the next refresh.
                break
            }
        }
        return pushedAny
    }
}
