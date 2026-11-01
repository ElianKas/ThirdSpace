package com.example.thirdspace.data

import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTO for a row of `public.collections` (see the `init_collections`
 * migration): one named, owner-scoped personal collection. snake_case remote
 * columns mapped with [SerialName]. `updated_at` is intentionally NOT decoded
 * (the flat model does not surface it); `owner_id` is nullable-defaulted so a
 * projection that omits it still decodes.
 *
 * INTERNAL to the shared module: this @Serializable type never crosses the flat
 * FeedBridge boundary.
 */
@Serializable
internal data class CollectionRowDto(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String = "",
    val name: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

/**
 * Payload sent on INSERT into `public.collections`. Only `owner_id` + `name`
 * are sent; `id` / `created_at` / `updated_at` are DB-defaulted
 * (`gen_random_uuid()` / `now()`). The remote RLS "insert own" policy enforces
 * `owner_id = auth.uid()`. INTERNAL to the shared module.
 */
@Serializable
internal data class CollectionInsertDto(
    @SerialName("owner_id") val ownerId: String,
    val name: String,
)

/**
 * Payload sent on UPDATE (rename) of a `public.collections` row — only the
 * `name` column. `updated_at` is bumped by the DB trigger. The RLS "update own"
 * policy guards it. INTERNAL to the shared module.
 */
@Serializable
internal data class CollectionUpdateNameDto(
    val name: String,
)

/**
 * Wire DTO for a row of `public.collection_spots` (the many-to-many join).
 * snake_case columns mapped with [SerialName]; `created_at` is not decoded
 * (not needed). INTERNAL to the shared module.
 */
@Serializable
internal data class CollectionSpotRowDto(
    @SerialName("collection_id") val collectionId: String = "",
    @SerialName("spot_id") val spotId: String = "",
)

/**
 * Payload sent on INSERT into `public.collection_spots` (save a spot into a
 * collection). The composite PK `(collection_id, spot_id)` makes a re-add
 * idempotent (a repeat insert hits the PK conflict, swallowed as success).
 * INTERNAL to the shared module.
 */
@Serializable
internal data class CollectionSpotInsertDto(
    @SerialName("collection_id") val collectionId: String,
    @SerialName("spot_id") val spotId: String,
)

/**
 * The remote collections source seam (Phase C2). An interface so
 * [CollectionRepository] can be unit-tested against a FAKE remote (no network),
 * while production uses the supabase-kt-backed [SupabaseCollectionRepository].
 * All suspend methods stay INTERNAL — they never reach Swift. Mirrors
 * [FollowRemoteSource] / [RemoteSpotSource].
 *
 * Every method is BEST-EFFORT: unavailable / empty-id / a thrown transport
 * error degrades to an empty list / `false` and NEVER throws to the caller.
 */
internal interface CollectionRemoteSource {
    /** Whether a usable remote is available right now (configured). */
    fun isAvailable(): Boolean

    /**
     * The collections owned by [ownerId], each with its live `spotCount` (the
     * count of `collection_spots` rows for that collection). Empty when
     * unavailable / empty-id.
     */
    suspend fun fetchCollections(ownerId: String): List<SpotCollection>

    /**
     * The spots saved into [collectionId], resolved via a client-side join
     * (fetch the join rows, then resolve each `spot_id` against `spots`,
     * reusing [SpotDtoMapping]). Empty when unavailable / empty-id.
     */
    suspend fun fetchSpotsIn(collectionId: String): List<Spot>

    /**
     * The ids of the owner's collections that contain [spotId] (for the
     * picker's checkmarks). RLS scopes the join to the caller's own
     * collections, so this naturally returns only the caller's. Empty when
     * unavailable / empty-id.
     */
    suspend fun fetchCollectionsContaining(spotId: String): List<String>

    /** INSERT a new collection owned by [ownerId]. `true` on success. */
    suspend fun createCollection(ownerId: String, name: String): Boolean

    /** UPDATE the collection [collectionId]'s `name`. `true` on success. */
    suspend fun renameCollection(collectionId: String, name: String): Boolean

    /** DELETE the collection [collectionId] (join rows cascade). `true` on success. */
    suspend fun deleteCollection(collectionId: String): Boolean

    /**
     * INSERT a membership row (save [spotId] into [collectionId]). Idempotent:
     * a PK conflict counts as success. `true` on success.
     */
    suspend fun addSpot(collectionId: String, spotId: String): Boolean

    /** DELETE a membership row (unsave [spotId] from [collectionId]). `true` on success. */
    suspend fun removeSpot(collectionId: String, spotId: String): Boolean
}

/**
 * Remote collections source over supabase-kt Postgrest. All operations are
 * `suspend` and INTERNAL — only ever called from [CollectionRepository]'s
 * background coroutine scope, so no suspend / supabase type reaches Swift.
 * Mirrors [SupabaseFollowRepository] / [SupabaseSpotRepository].
 *
 * NOT-CONFIGURED / BEST-EFFORT SAFETY: every call resolves the client via
 * [SupabaseClientProvider]; when unconfigured the client is `null` and the call
 * degrades to an empty list / `false`. Network bodies are additionally wrapped
 * in try/catch so a transport/REST error ALSO degrades rather than throwing —
 * these back a non-critical personal-collections UI, so one failing query must
 * never crash. Empty ids short-circuit the same way.
 *
 * ---- DESIGN DECISION: embed vs. client-side join ----
 * Resolving the spots in a collection joins `collection_spots` -> `spots`. A
 * PostgREST `spots(...)` embed is NOT proven against supabase-kt 3.8.0 anywhere
 * in this codebase (and the `init_collections` FKs would need embed hints), so
 * — exactly as [SupabaseFollowRepository] documents — this impl uses the
 * SIMPLER, definitely-correct CLIENT-SIDE JOIN with ONLY already-proven API:
 *   1. `select` the `collection_spots` rows filtered by `collection_id` with
 *      the proven `select { filter { eq(...) } }` DSL.
 *   2. For each `spot_id`, `select` its `spots` row by `eq("id", id)` (the SAME
 *      single-row read proven in [SupabaseSpotRepository]) and map it via the
 *      shared [SpotDtoMapping], joining in Kotlin.
 * No `in`/`isin` filter and no embed-disambiguation syntax is used (neither is
 * proven here). The per-id reads are N small round-trips; given typical
 * collection sizes and that this is a best-effort, explicitly triggered refresh
 * (not a hot path), that is an acceptable tradeoff for using strictly proven
 * API.
 *
 * ---- DESIGN DECISION: count approach ----
 * `Count.EXACT` head-select is NOT proven anywhere in this codebase, so each
 * collection's `spotCount` `decodeList`s the matching `collection_spots` rows
 * and takes `.size` — the proven `select { filter { eq(...) } }.decodeList<T>()`
 * pattern. Tradeoff: this transfers the join rows rather than a single HEAD
 * count, but it is correct and uses only proven API. Collections are small.
 */
internal class SupabaseCollectionRepository(
    private val currentUserId: () -> String = { AuthRepository.currentUserId() },
) : CollectionRemoteSource {

    override fun isAvailable(): Boolean = SupabaseClientProvider.isConfigured

    private val client get() = SupabaseClientProvider.client

    override suspend fun fetchCollections(ownerId: String): List<SpotCollection> {
        val c = client ?: return emptyList()
        if (ownerId.isEmpty()) return emptyList()
        return try {
            val rows = c.from("collections").select {
                filter { eq("owner_id", ownerId) }
            }.decodeList<CollectionRowDto>()
            rows.map { row ->
                // Per-collection spotCount via the proven select+decodeList+size.
                val count = try {
                    c.from("collection_spots").select {
                        filter { eq("collection_id", row.id) }
                    }.decodeList<CollectionSpotRowDto>().size
                } catch (_: Throwable) {
                    0
                }
                SpotCollection(
                    id = row.id,
                    name = row.name,
                    spotCount = count,
                    createdAtLabel = RelativeTime.formatMonthYear(row.createdAt),
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun fetchSpotsIn(collectionId: String): List<Spot> {
        val c = client ?: return emptyList()
        if (collectionId.isEmpty()) return emptyList()
        return try {
            val rows = c.from("collection_spots").select {
                filter { eq("collection_id", collectionId) }
            }.decodeList<CollectionSpotRowDto>()
            val spotIds = rows.map { it.spotId }.filter { it.isNotEmpty() }.distinct()
            spotIds.mapNotNull { id ->
                val dto = c.from("spots").select {
                    filter { eq("id", id) }
                }.decodeList<SpotDto>().firstOrNull() ?: return@mapNotNull null
                SpotDtoMapping.toSpot(dto)
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun fetchCollectionsContaining(spotId: String): List<String> {
        val c = client ?: return emptyList()
        if (spotId.isEmpty()) return emptyList()
        return try {
            // RLS gates the join to the caller's own collections via EXISTS, so
            // this naturally returns only the caller's collection ids.
            c.from("collection_spots").select {
                filter { eq("spot_id", spotId) }
            }.decodeList<CollectionSpotRowDto>()
                .map { it.collectionId }
                .filter { it.isNotEmpty() }
                .distinct()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun createCollection(ownerId: String, name: String): Boolean {
        val c = client ?: return false
        if (ownerId.isEmpty() || name.isEmpty()) return false
        return try {
            c.from("collections").insert(
                CollectionInsertDto(ownerId = ownerId, name = name),
            )
            true
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun renameCollection(collectionId: String, name: String): Boolean {
        val c = client ?: return false
        if (collectionId.isEmpty() || name.isEmpty()) return false
        return try {
            c.from("collections").update(CollectionUpdateNameDto(name = name)) {
                filter { eq("id", collectionId) }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun deleteCollection(collectionId: String): Boolean {
        val c = client ?: return false
        if (collectionId.isEmpty()) return false
        return try {
            c.from("collections").delete {
                filter { eq("id", collectionId) }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * INSERT a membership row. A re-add that hits the `(collection_id, spot_id)`
     * PK conflict is treated as success (already saved) rather than an error —
     * mirroring [SupabaseFollowRepository.follow]'s re-follow handling. Any
     * other thrown error degrades to `false`.
     */
    override suspend fun addSpot(collectionId: String, spotId: String): Boolean {
        val c = client ?: return false
        if (collectionId.isEmpty() || spotId.isEmpty()) return false
        return try {
            c.from("collection_spots").insert(
                CollectionSpotInsertDto(collectionId = collectionId, spotId = spotId),
            )
            true
        } catch (_: Throwable) {
            // A PK conflict (already saved) or any transport error degrades to
            // false; the caller keeps its optimistic state and reports it.
            false
        }
    }

    override suspend fun removeSpot(collectionId: String, spotId: String): Boolean {
        val c = client ?: return false
        if (collectionId.isEmpty() || spotId.isEmpty()) return false
        return try {
            c.from("collection_spots").delete {
                filter {
                    eq("collection_id", collectionId)
                    eq("spot_id", spotId)
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }
}
