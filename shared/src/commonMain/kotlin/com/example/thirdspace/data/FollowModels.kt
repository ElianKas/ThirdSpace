package com.example.thirdspace.data

/**
 * A single entry in a follower / following list — the shared, flat model for
 * the social-graph (follows) seam (Phase F2).
 *
 * BOUNDARY RULE (see architecture steering): intentionally made of ONLY flat,
 * non-nullable, ObjC/Swift-friendly primitives (String / Boolean) so a later
 * phase can surface it across the `FeedBridge` flat boundary without exposing
 * any supabase-kt, Ktor, coroutine, or nullable type — and with NO `Long`.
 * (The `IosFollowUser` bridge type and the bridge wiring come in a LATER phase;
 * this phase adds NO bridge/Swift/SQLDelight.)
 *
 * - [id]              — the listed user's auth uid (also their `profiles.id`).
 * - [name]            — the user's display name (dynamic content, NOT
 *                       translated), resolved from the counterpart `profiles`
 *                       row. "" when unavailable.
 * - [username]        — the cosmetic username DERIVED via
 *                       [ProfileMapping.deriveUsername]. Since the follows list
 *                       resolves name/city (NOT email) from `profiles`, this is
 *                       derived from the name (email passed as "").
 * - [city]            — the user's city (dynamic content, NOT translated). ""
 *                       when unavailable.
 * - [isFollowedByMe]  — whether the CURRENT viewer already follows this listed
 *                       user (drives a follow/unfollow button in a later UI
 *                       phase). A user is never shown as following themselves.
 */
data class FollowUser(
    val id: String,
    val name: String,
    val username: String,
    val city: String,
    val isFollowedByMe: Boolean,
)
