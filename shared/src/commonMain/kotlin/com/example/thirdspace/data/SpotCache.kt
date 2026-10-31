package com.example.thirdspace.data

import com.example.thirdspace.db.ThirdSpaceDb

/**
 * An entry in the outbound pending-insert queue: a locally-created [spot] that
 * still needs a remote insert, tagged with its [ownerId] (from the session at
 * enqueue time) and [enqueuedAt] (epoch millis, for oldest-first flush order).
 */
data class PendingSpotInsert(
    val spot: Spot,
    val ownerId: String,
    val enqueuedAt: Long,
)

/**
 * The local spot store seam: a synchronous cache of spots (the source of truth
 * the map reads) plus an outbound queue of inserts awaiting remote push.
 *
 * This is an interface so [CachingSpotRepository] can be unit-tested against a
 * trivial in-memory fake (no SQLDelight / no driver), while the production
 * implementation ([LocalSpotCache]) is SQLDelight-backed. All operations are
 * synchronous — no suspend / coroutine / supabase type appears here, keeping
 * the flat-boundary guarantees intact.
 */
interface SpotCache {
    /** All cached spots, with UI-only fields re-derived. */
    fun getCachedSpots(): List<Spot>

    /** Replace the ENTIRE cache with [spots] (remote replace-all refresh). */
    fun replaceAll(spots: List<Spot>)

    /** Upsert a single [spot] into the cache (local write-through). */
    fun upsert(spot: Spot)

    /** Enqueue a pending insert for [spot] owned by [ownerId] at [enqueuedAt]. */
    fun enqueuePending(spot: Spot, ownerId: String, enqueuedAt: Long)

    /** The pending inserts, oldest-first. */
    fun pendingInserts(): List<PendingSpotInsert>

    /** Remove the pending insert with [id] (after a successful remote push). */
    fun dequeuePending(id: String)
}

/**
 * SQLDelight-backed [SpotCache] over [ThirdSpaceDb].
 *
 * Maps rows <-> [Spot] via [SpotMapping] (checkInCount = 0; placeholderColorArgb
 * derived from the category), so a rehydrated spot is identical to one the
 * bridge built. Booleans are stored as INTEGER 0/1.
 *
 * Construct with [create] for production (platform driver) or pass any
 * [ThirdSpaceDb] (e.g. one built on an in-memory JdbcSqliteDriver) for host
 * tests.
 */
class LocalSpotCache(
    private val db: ThirdSpaceDb,
) : SpotCache {

    private val queries get() = db.thirdSpaceDbQueries

    override fun getCachedSpots(): List<Spot> =
        queries.selectAllCachedSpots { id, ownerId, name, category, neighborhood, lat, lng, verified ->
            // ownerId is persisted but not part of the UI Spot model; it is used
            // by the remote push path (pending queue carries its own ownerId).
            SpotMapping.toSpot(id, name, category, neighborhood, lat, lng, verified)
        }.executeAsList()

    override fun replaceAll(spots: List<Spot>) {
        queries.transaction {
            queries.deleteAllCachedSpots()
            spots.forEach { upsertRow(it, ownerId = "") }
        }
    }

    override fun upsert(spot: Spot) {
        upsertRow(spot, ownerId = "")
    }

    override fun enqueuePending(spot: Spot, ownerId: String, enqueuedAt: Long) {
        queries.enqueuePendingInsert(
            id = spot.id,
            owner_id = ownerId,
            name = spot.name,
            category = spot.category,
            neighborhood = spot.neighborhood,
            lat = spot.lat,
            lng = spot.lng,
            verified = if (spot.verified) 1L else 0L,
            enqueued_at = enqueuedAt,
        )
    }

    override fun pendingInserts(): List<PendingSpotInsert> =
        queries.selectPendingInserts { id, ownerId, name, category, neighborhood, lat, lng, verified, enqueuedAt ->
            PendingSpotInsert(
                spot = SpotMapping.toSpot(id, name, category, neighborhood, lat, lng, verified),
                ownerId = ownerId,
                enqueuedAt = enqueuedAt,
            )
        }.executeAsList()

    override fun dequeuePending(id: String) {
        queries.deletePendingInsert(id)
    }

    private fun upsertRow(spot: Spot, ownerId: String) {
        queries.upsertCachedSpot(
            id = spot.id,
            owner_id = ownerId,
            name = spot.name,
            category = spot.category,
            neighborhood = spot.neighborhood,
            lat = spot.lat,
            lng = spot.lng,
            verified = if (spot.verified) 1L else 0L,
        )
    }

    companion object {
        /** Build a production cache on the platform SQLDelight driver. */
        fun create(): LocalSpotCache =
            LocalSpotCache(ThirdSpaceDb(createSpotDbDriver()))
    }
}
