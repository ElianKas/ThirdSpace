package com.example.thirdspace.data

/**
 * A single personal spot collection — the shared, flat model for the
 * collections seam (Phase C2), mirroring [FollowUser]'s flat style.
 *
 * BOUNDARY RULE (see architecture steering): intentionally made of ONLY flat,
 * non-nullable, ObjC/Swift-friendly primitives (String / Int) so a LATER phase
 * (C3) can surface it across the `FeedBridge` flat boundary without exposing
 * any supabase-kt, Ktor, coroutine, or nullable type — and with NO `Long`.
 * (The `IosSpotCollection` bridge type and the bridge wiring come in C3; this
 * phase adds NO bridge/Swift/SQLDelight.)
 *
 * - [id]             — the collection's uuid (`collections.id`). For an
 *                      OPTIMISTIC, not-yet-persisted create this is a local
 *                      `temp-…` id that a reconciling refresh replaces with the
 *                      real DB id (see [CollectionRepository.createCollection]).
 * - [name]           — the collection's display name (dynamic, user content,
 *                      NOT translated). "" is never a valid create (blank-name
 *                      create is a no-op).
 * - [spotCount]      — how many spots are saved in the collection (the count of
 *                      `collection_spots` rows for this collection). 0 for a
 *                      freshly-created, still-empty collection.
 * - [createdAtLabel] — a DERIVED, NON-translated "Monat Jahr" display string
 *                      (reusing [RelativeTime.formatMonthYear] on the
 *                      collection's `created_at`); "" when unknown (an
 *                      optimistic local row before the server timestamp exists,
 *                      or an unparseable value).
 */
data class SpotCollection(
    val id: String,
    val name: String,
    val spotCount: Int,
    val createdAtLabel: String,
)
