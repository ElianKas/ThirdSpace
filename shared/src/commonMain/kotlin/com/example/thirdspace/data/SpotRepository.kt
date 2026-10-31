package com.example.thirdspace.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Read/write access to the set of spots.
 *
 * This interface is the OFFLINE-READY SEAM for spots. Offline is a shared,
 * cross-platform concern (not iOS-specific), so it lives in the shared module
 * where Android can adopt it later.
 *
 * EXTENSION POINTS (deliberately NOT implemented in Phase 2 — in-memory only):
 * - A local-persistence implementation (e.g. SQLDelight / a key-value store)
 *   can implement this same interface so the spots survive relaunch.
 * - A backend-sync implementation can wrap a local cache + a remote source,
 *   reconciling them, while still exposing exactly these operations.
 * Because callers depend only on this interface and on the flat [Spot] model
 * (which already carries [Spot.lat]/[Spot.lng]), persistence or networking can
 * be plugged in LATER WITHOUT reshaping the model or the call sites. Future
 * async variants (e.g. suspend functions / a Flow of spots) would be added here
 * as the networking layer lands; Phase 2 keeps the surface synchronous and
 * minimal on purpose.
 */
interface SpotRepository {
    /** All known spots, in insertion order. */
    fun getSpots(): List<Spot>

    /** Add a new [spot] to the set. */
    fun addSpot(spot: Spot)
}

/**
 * In-memory [SpotRepository] seeded from [SampleData]. Holds spots in a plain
 * mutable list; nothing is persisted or synced (see the interface doc for the
 * planned persistence/backend extension points).
 *
 * Seeding is derived from [SampleData.posts] so the map shows the SAME spots
 * the feed references (single source of truth), de-duplicated by [Spot.id]
 * because several posts can share a spot.
 */
class InMemorySpotRepository(
    seed: List<Spot> = defaultSeed(),
) : SpotRepository {

    private val spots: MutableList<Spot> = seed.toMutableList()

    override fun getSpots(): List<Spot> = spots.toList()

    override fun addSpot(spot: Spot) {
        spots.add(spot)
    }

    companion object {
        /** The seed spots: the distinct spots referenced by the sample feed. */
        fun defaultSeed(): List<Spot> =
            SampleData.posts.map { it.spot }.distinctBy { it.id }
    }
}

/**
 * The process-wide spot repository provider.
 *
 * Phase D: the default [repository] is an offline-first [CachingSpotRepository]
 * over the SQLDelight-backed [LocalSpotCache] + the supabase-kt
 * [SupabaseSpotRepository], running its background refresh/flush on a shared
 * [Dispatchers.Default] scope. The [SpotRepository] interface and the call
 * sites (FeedBridge.spots()/addSpot) are unchanged — only this provider swapped
 * in the persistent/synced implementation.
 *
 * [InMemorySpotRepository] stays available for tests and as a fallback.
 *
 * LISTENER: [setChangeListener] lets the iOS bridge register a flat callback
 * (delivered on its own Dispatchers.Main scope) that fires after a background
 * remote refresh or queue flush changes the cache, so Swift can re-read
 * FeedData.spots(). It is registered via the live repository if that repository
 * supports it.
 */
object Spots {

    /**
     * Background scope for the caching repository's remote refresh/flush. Uses
     * [Dispatchers.Default]; the suspend/supabase work stays here and never
     * reaches Swift. Separate from the bridge's Dispatchers.Main auth scope.
     */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The current onChanged listener, forwarded into the caching repository. */
    private var changeListener: () -> Unit = {}

    val repository: SpotRepository by lazy { buildDefault() }

    /**
     * Register a flat [listener] invoked after a background refresh/flush
     * changes the cache. The bridge wraps this to hop onto Dispatchers.Main.
     */
    fun setChangeListener(listener: () -> Unit) {
        changeListener = listener
    }

    private fun buildDefault(): SpotRepository =
        try {
            CachingSpotRepository(
                cache = LocalSpotCache.create(),
                scope = backgroundScope,
                onChanged = { changeListener() },
            )
        } catch (_: Throwable) {
            // Defensive fallback: if the SQLDelight driver cannot be created in
            // some environment, serve the in-memory seed rather than crash.
            InMemorySpotRepository()
        }
}
