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
 * IMPLEMENTATIONS:
 * - [CachingSpotRepository] (the DEFAULT, built by the [Spots] provider) is the
 *   offline-first implementation: a SQLDelight local cache + the supabase-kt
 *   [SupabaseSpotRepository] remote source, reconciled behind this interface so
 *   spots survive relaunch and sync when online.
 * - [InMemorySpotRepository] remains a fallback/test implementation that holds
 *   spots in a plain list with no persistence or sync.
 * Because callers depend only on this interface and on the flat [Spot] model
 * (which already carries [Spot.lat]/[Spot.lng]), the persistent/synced
 * implementation plugs in WITHOUT reshaping the model or the call sites. The
 * surface stays synchronous on purpose: the async remote work (suspend / Flow /
 * supabase types) stays internal to [CachingSpotRepository] and never crosses
 * this interface.
 */
interface SpotRepository {
    /** All known spots, in insertion order. */
    fun getSpots(): List<Spot>

    /** Add a new [spot] to the set. */
    fun addSpot(spot: Spot)
}

/**
 * In-memory [SpotRepository] seeded from [SampleData]. Holds spots in a plain
 * mutable list; nothing is persisted or synced. Serves as a fallback/test impl
 * (the persistent/synced default is [CachingSpotRepository]; see the interface
 * doc).
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
 * The default [repository] is an offline-first [CachingSpotRepository] over the
 * SQLDelight-backed [LocalSpotCache] + the supabase-kt [SupabaseSpotRepository],
 * running its background refresh/flush on a shared [Dispatchers.Default] scope.
 * The [SpotRepository] interface and the call sites (FeedBridge.spots()/addSpot)
 * are unchanged — this provider supplies the persistent/synced implementation.
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

    /**
     * Explicit remote refresh trigger for the bridge (refreshSpots /
     * pull-to-refresh). Forwards to the caching repository's refresh when the
     * live [repository] is a [CachingSpotRepository]; a no-op for the in-memory
     * fallback (which has nothing to refresh). Kept off the [SpotRepository]
     * interface so [InMemorySpotRepository] is unaffected.
     */
    fun refresh() { (repository as? CachingSpotRepository)?.refresh() }

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
