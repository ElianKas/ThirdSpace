package com.example.thirdspace.data

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
 * The process-wide spot repository instance. A single in-memory repository for
 * now; swapping in a persistent/synced implementation later means changing only
 * this provider, not the call sites.
 */
object Spots {
    val repository: SpotRepository = InMemorySpotRepository()
}
