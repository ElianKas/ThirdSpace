package com.example.thirdspace.data

import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTO for a row of `public.spots` (see the Phase B migration).
 *
 * snake_case remote column names are mapped with [SerialName] so the Kotlin
 * properties stay camelCase. Only the columns the app reads/writes are present;
 * `created_at` / `updated_at` are decoded (read) but never sent on insert.
 *
 * INTERNAL to the shared module: this @Serializable type never crosses the flat
 * FeedBridge boundary (only flat [Spot]-derived values reach Swift).
 */
@Serializable
internal data class SpotDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val category: String,
    val neighborhood: String = "",
    val lat: Double,
    val lng: Double,
    val verified: Boolean = false,
)

/**
 * Payload sent on INSERT. Omits `id` (DB default `gen_random_uuid()` — but we
 * send the client id so the local cache and remote row share a primary key,
 * keeping replace-all idempotent), and omits server-managed timestamps. The id
 * is included so an offline-created spot keeps a stable identity across the
 * queue flush and the subsequent remote replace-all.
 */
@Serializable
internal data class SpotInsertDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val category: String,
    val neighborhood: String,
    val lat: Double,
    val lng: Double,
    val verified: Boolean,
)

/**
 * The remote spot source seam. An interface so [CachingSpotRepository] can be
 * unit-tested against a FAKE remote (no network), while production uses the
 * supabase-kt-backed [SupabaseSpotRepository]. Both methods are `suspend` and
 * stay INTERNAL — they never reach Swift.
 */
internal interface RemoteSpotSource {
    /** Whether a usable remote is available right now (configured). */
    fun isAvailable(): Boolean

    /** Fetch all remote spots (empty when unavailable). */
    suspend fun fetchSpots(): List<Spot>

    /** Insert [spot] owned by [ownerId]; `true` on success, `false` if unavailable. */
    suspend fun insertSpot(spot: Spot, ownerId: String): Boolean
}

/**
 * Remote spot source over supabase-kt Postgrest. All operations are `suspend`
 * and INTERNAL — they are only ever called from [CachingSpotRepository]'s
 * background coroutine scope, so no suspend / supabase type reaches Swift.
 *
 * NOT-CONFIGURED SAFETY: every call first checks
 * [SupabaseClientProvider.isConfigured]. When there are no keys (or no
 * session), these return a safe empty / no-op so the app never crashes offline;
 * the caller ([CachingSpotRepository]) decides what to do with that.
 *
 * owner_id for an insert comes from the current session via the supplied
 * ownerId (resolved from [AuthRepository]); the remote RLS `insert own` policy
 * also enforces `owner_id = auth.uid()`.
 */
internal class SupabaseSpotRepository : RemoteSpotSource {

    override fun isAvailable(): Boolean = SupabaseClientProvider.isConfigured

    private val client get() = SupabaseClientProvider.client

    /**
     * Fetch all spots from `public.spots`. Returns an empty list (no throw)
     * when unconfigured. Decodes into [SpotDto] and maps to [Spot] with the
     * UI-only fields re-derived via [SpotMapping].
     */
    override suspend fun fetchSpots(): List<Spot> {
        val c = client ?: return emptyList()
        val rows = c.from("spots").select().decodeList<SpotDto>()
        return rows.map { dto ->
            SpotMapping.toSpot(
                id = dto.id,
                name = dto.name,
                category = dto.category,
                neighborhood = dto.neighborhood,
                lat = dto.lat,
                lng = dto.lng,
                verifiedInt = if (dto.verified) 1L else 0L,
            )
        }
    }

    /**
     * Insert [spot] owned by [ownerId]. Returns `true` on success, `false` when
     * unconfigured (so the caller keeps it queued). Any thrown transport/REST
     * error propagates to the caller, which treats it as "stay queued, retry
     * later" (it is caught on the background scope).
     */
    override suspend fun insertSpot(spot: Spot, ownerId: String): Boolean {
        val c = client ?: return false
        c.from("spots").insert(
            SpotInsertDto(
                id = spot.id,
                ownerId = ownerId,
                name = spot.name,
                category = spot.category,
                neighborhood = spot.neighborhood,
                lat = spot.lat,
                lng = spot.lng,
                verified = spot.verified,
            ),
        )
        return true
    }
}
