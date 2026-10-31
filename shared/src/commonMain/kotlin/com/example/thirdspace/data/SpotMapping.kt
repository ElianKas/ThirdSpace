package com.example.thirdspace.data

/**
 * Shared helpers for reconstructing the UI-only / derived [Spot] fields that
 * the offline cache does NOT persist (see ThirdSpaceDb.sq).
 *
 * The persisted columns are: id, owner_id, name, category, neighborhood, lat,
 * lng, verified. On read we re-derive:
 * - [Spot.checkInCount] = 0 (not tracked yet),
 * - [Spot.placeholderColorArgb] = the category's marker color as an opaque
 *   ARGB Long — exactly the logic FeedBridge.addSpot used inline, now factored
 *   here so the bridge and the cache agree on a single derivation.
 */
object SpotMapping {

    /** Re-derived check-in count for a persisted spot (not tracked yet). */
    const val DERIVED_CHECK_IN_COUNT: Int = 0

    /**
     * Derive an opaque ARGB [Long] from a category label's "#RRGGBB" marker
     * color (full alpha). Mirrors the old FeedBridge.argbForCategory so the
     * stored/derived placeholder color is identical whether a spot was built by
     * the bridge or rehydrated from the cache.
     */
    fun argbForCategory(category: String): Long {
        val hex = SpotCategory.colorHexForLabel(category).removePrefix("#")
        val rgb = hex.toLong(16)
        return 0xFF000000L or rgb
    }

    /**
     * Build a full [Spot] from the persisted columns, re-deriving the UI-only
     * fields. `verifiedInt` is the stored 0/1 INTEGER.
     */
    fun toSpot(
        id: String,
        name: String,
        category: String,
        neighborhood: String,
        lat: Double,
        lng: Double,
        verifiedInt: Long,
    ): Spot = Spot(
        id = id,
        name = name,
        category = category,
        neighborhood = neighborhood,
        verified = verifiedInt != 0L,
        checkInCount = DERIVED_CHECK_IN_COUNT,
        placeholderColorArgb = argbForCategory(category),
        lat = lat,
        lng = lng,
    )
}
