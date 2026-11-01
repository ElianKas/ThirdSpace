package com.example.thirdspace.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTO for a row of `public.profiles` (see the `init_profiles` migration +
 * the `profile_add_bio` migration). snake_case remote column names are mapped
 * with [SerialName] so the Kotlin properties stay camelCase.
 *
 * [createdAt] is an ISO-8601 `timestamptz` STRING (parsed to a "Monat Jahr"
 * label by [ProfileMapping] via [RelativeTime.formatMonthYear] — NOT decoded as
 * a datetime; the shared module has no datetime dep). [avatarUrl] is
 * nullable-tolerant (the column is nullable), but that nullable stays INTERNAL:
 * the mapped [Profile] has no avatar field, so no nullable crosses the flat
 * FeedBridge boundary.
 *
 * INTERNAL to the shared module: this @Serializable type never crosses the flat
 * FeedBridge boundary (only the flat [Profile]-derived values reach Swift).
 */
@Serializable
internal data class ProfileDto(
    val id: String,
    val name: String = "",
    val email: String = "",
    val city: String = "",
    val bio: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/**
 * Wire DTO for the editable subset of a `public.profiles` row that Phase 2
 * writes (name / city / bio). Used as the typed body of the Postgrest UPDATE,
 * mirroring the `insert`/`upsert` DTO style already shipping in
 * [SupabaseFeedRepository] (e.g. `PostVoteDto`). INTERNAL: never crosses the
 * flat FeedBridge boundary. The `id` is NOT part of the update body (it is the
 * filter + RLS key); email/created_at/avatar_url are NOT editable here.
 */
@Serializable
internal data class ProfileUpdateDto(
    val name: String,
    val city: String,
    val bio: String,
)

/**
 * The remote profile source seam. An interface so [ProfileRepository] can be
 * unit-tested against a FAKE remote (no network), while production uses the
 * supabase-kt-backed [SupabaseProfileRepository]. [fetchCurrentProfile] is
 * `suspend` and stays INTERNAL — it never reaches Swift. Mirrors
 * [RemoteSpotSource] / [FeedRemoteSource].
 */
internal interface ProfileRemoteSource {
    /** Whether a usable remote is available right now (configured). */
    fun isAvailable(): Boolean

    /**
     * Fetch the CURRENT user's profile ROW. Returns `null` when unavailable
     * (unconfigured), when there is no session, or when the row is not found.
     * A transport/REST error is allowed to propagate to the caller (caught on
     * the background scope).
     *
     * PHASE 4 NOTE: the returned [Profile] carries ZERO spot/post counts here.
     * The authoritative spot/post counts are now DERIVED by [ProfileRepository]
     * from the SIZES of the own-content lists it fetches via [fetchOwnSpots] /
     * [fetchOwnPosts] in the same refresh pass (one fewer round-trip each, and
     * the counts are guaranteed consistent with the lists the UI shows). The
     * server-side COUNT queries used in Phase 3 were removed for that reason.
     */
    suspend fun fetchCurrentProfile(): Profile?

    /**
     * UPDATE the own `profiles` row ([userId]) setting `name` / `city` / `bio`.
     * Returns `true` on success; `false` when unavailable (unconfigured) or when
     * [userId] is empty (no session). A transport/REST error is allowed to
     * propagate to the caller (caught on the background scope). The remote RLS
     * "update own" policy (`auth.uid() = id`) is the authoritative guard;
     * [userId] is the session user id passed from [AuthRepository.currentUserId].
     */
    suspend fun updateProfile(userId: String, name: String, city: String, bio: String): Boolean

    /**
     * Fetch the CURRENT user's OWN posts (Phase 4) from the `post_feed` view
     * filtered by `author_id == [userId]`, newest-first, mapped to the flat
     * [FeedPost] the global feed already uses (reuses [PostFeedMapping]). Empty
     * list when unconfigured or when [userId] is empty (no session). BEST-EFFORT:
     * a transport/REST error degrades to an empty list rather than propagating,
     * so one failing own-content query never breaks the whole profile load.
     */
    suspend fun fetchOwnPosts(userId: String): List<FeedPost>

    /**
     * Fetch the CURRENT user's OWN spots (Phase 4) from the `spots` table
     * filtered by `owner_id == [userId]`, mapped to the flat [Spot] the map
     * already uses (reuses [SpotDtoMapping]). Empty list when unconfigured or
     * when [userId] is empty (no session). BEST-EFFORT: a transport/REST error
     * degrades to an empty list rather than propagating.
     */
    suspend fun fetchOwnSpots(userId: String): List<Spot>
}

/**
 * Pure DTO -> [Profile] derivation, exposed `internal` so a commonTest can
 * assert the mapping (username + monthYear derivation, zeroed counts) without a
 * live backend. Kept separate from the supabase-kt impl so it runs on both
 * targets with no network.
 *
 * PRODUCT DECISIONS (Phase 1, do-not-deviate):
 * - `username` is DERIVED/cosmetic (no DB column): the email local-part (before
 *   `@`), lowercased. Falls back to the lowercased name when the email has no
 *   local part (defensive; real profiles always have an email).
 * - `monthYear` is DERIVED from `created_at` via [RelativeTime.formatMonthYear]
 *   (a non-translated "Monat Jahr" German label, e.g. "März 2024"); "" when the
 *   timestamp is empty/unparseable.
 * - followerCount / followingCount are always 0 — the social-graph counts stay
 *   DEFERRED (no social graph is built).
 * - spotCount / postCount are the REAL counts of the current user's own rows.
 *   As of Phase 4 they are DERIVED by [ProfileRepository] from the sizes of the
 *   own-content lists it fetches (`ownSpots().size` / `ownPosts().size`), not
 *   from a server-side COUNT query (removed). The no-arg [toProfile] overload
 *   stays PURE and still yields 0s (so the Phase-1 mapping contract is
 *   unchanged); the counted overload [toProfile] (dto, spotCount, postCount)
 *   injects the derived counts. followerCount / followingCount remain 0 in both.
 */
internal object ProfileMapping {

    /**
     * Map a decoded [ProfileDto] to the shared flat [Profile] with ZERO counts.
     * This no-arg overload is intentionally PURE/count-free (unchanged Phase-1
     * behavior): all four counts are 0. The counted [toProfile] overload below
     * is used by the remote source once it has computed the real spot/post
     * counts server-side.
     */
    fun toProfile(dto: ProfileDto): Profile = toProfile(dto, spotCount = 0, postCount = 0)

    /**
     * Map a decoded [ProfileDto] to the shared flat [Profile], injecting the
     * REAL [spotCount] / [postCount] (Phase 3 — counts of the current user's
     * own `spots` / `posts` rows, computed server-side by the remote source).
     * followerCount / followingCount stay 0 (social graph deferred).
     */
    fun toProfile(dto: ProfileDto, spotCount: Int, postCount: Int): Profile = Profile(
        name = dto.name,
        username = deriveUsername(dto.email, dto.name),
        city = dto.city,
        monthYear = RelativeTime.formatMonthYear(dto.createdAt),
        bio = dto.bio,
        email = dto.email,
        spotCount = spotCount,
        postCount = postCount,
        // The social graph (followers/following) stays deferred: always 0.
        followerCount = 0,
        followingCount = 0,
    )

    /**
     * Derive the cosmetic username from [email]'s local-part (before `@`),
     * lowercased. Falls back to the lowercased [name] (then ""), so the UI
     * always has a non-crashing value. Exposed `internal` for a focused test.
     */
    fun deriveUsername(email: String, name: String): String {
        val local = email.substringBefore('@', "").trim()
        if (local.isNotEmpty()) return local.lowercase()
        return name.trim().lowercase()
    }
}

/**
 * Remote profile source over supabase-kt Postgrest. The single `suspend` read
 * is INTERNAL — it is only ever called from [ProfileRepository]'s background
 * coroutine scope, so no suspend / supabase type reaches Swift. Mirrors
 * [SupabaseSpotRepository] / [SupabaseFeedRepository].
 *
 * NOT-CONFIGURED / NO-SESSION SAFETY: [fetchCurrentProfile] first checks
 * [SupabaseClientProvider.isConfigured] and the session user id from
 * [AuthRepository.currentUserId]; with no keys or no session it returns `null`
 * (no throw) so the app never crashes offline / pre-login. The caller decides
 * what to do with that.
 *
 * supabase-kt 3.8.0 Postgrest API used (confirmed against the resolved
 * postgrest-kt 3.8.0 klib — the same `select { filter { eq(...) } }` +
 * `decodeList<T>()` builder already shipping in [SupabaseFeedRepository]):
 * `c.from("profiles").select { filter { eq("id", uid) } }.decodeList<ProfileDto>().firstOrNull()`.
 * (`decodeSingleOrNull` IS available in the klib, but the shipping code uses
 * `decodeList(...).firstOrNull()`, which is tolerant of 0-or-1 rows without a
 * throw; we keep that proven pattern.)
 *
 * OWN CONTENT + COUNTS (Phase 4): the profile's "meine Posts" / "meine Spots"
 * tabs now show the CURRENT user's OWN rows (not a global-feed derivation).
 * [fetchOwnPosts] reads the `post_feed` VIEW filtered by `author_id == uid`
 * (newest-first), reusing the SAME [PostFeedMapping] the global feed uses;
 * [fetchOwnSpots] reads the `spots` table filtered by `owner_id == uid`,
 * reusing the SAME [SpotDtoMapping] the map uses. Both filter with the proven
 * `select { filter { eq(col, uid) } }` DSL and decode the EXISTING
 * [PostFeedDto] / [SpotDto]. Both are BEST-EFFORT: a throw / unconfigured /
 * empty-uid degrades to an EMPTY list so one failing own-content query never
 * breaks the whole profile load.
 *
 * The spot/post COUNTS are DERIVED by [ProfileRepository] from the sizes of
 * these own-content lists (so the stat and the list are always consistent, one
 * fewer round-trip each). The Phase-3 server-side HEAD+exact-count queries were
 * REMOVED for that reason.
 */
internal class SupabaseProfileRepository(
    private val currentUserId: () -> String = { AuthRepository.currentUserId() },
) : ProfileRemoteSource {

    override fun isAvailable(): Boolean = SupabaseClientProvider.isConfigured

    private val client get() = SupabaseClientProvider.client

    /**
     * Select the current user's row from `public.profiles` filtered by
     * `id == auth.uid()` (the session user id). Returns `null` when
     * unconfigured, when there is no session, or when no row is found. The
     * existing "viewable by authenticated" SELECT RLS policy already allows
     * reading own row. Decodes into [ProfileDto] and maps via [ProfileMapping].
     */
    override suspend fun fetchCurrentProfile(): Profile? {
        val c = client ?: return null
        val uid = currentUserId()
        if (uid.isEmpty()) return null
        val row = c.from("profiles").select {
            filter { eq("id", uid) }
        }.decodeList<ProfileDto>().firstOrNull() ?: return null
        // Phase 4: counts are DERIVED by ProfileRepository from the own-content
        // list sizes (see the class doc); the profile row itself carries 0s.
        return ProfileMapping.toProfile(row)
    }

    /**
     * Fetch the current user's OWN posts from the `post_feed` VIEW filtered by
     * `author_id == [userId]`, newest-first, mapped via the shared
     * [PostFeedMapping] (the SAME map the global feed uses). The `post_feed`
     * view exposes `author_id` (confirmed in the `post_feed_view` migration:
     * `p.author_id AS author_id`). BEST-EFFORT: an unconfigured client / empty
     * [userId] / any thrown transport error degrades to an EMPTY list.
     */
    override suspend fun fetchOwnPosts(userId: String): List<FeedPost> {
        val c = client ?: return emptyList()
        if (userId.isEmpty()) return emptyList()
        return try {
            c.from("post_feed").select {
                filter { eq("author_id", userId) }
                order("created_at", Order.DESCENDING)
            }.decodeList<PostFeedDto>().map { PostFeedMapping.toFeedPost(it) }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Fetch the current user's OWN spots from the `spots` table filtered by
     * `owner_id == [userId]`, mapped via the shared [SpotDtoMapping] (the SAME
     * map the map screen uses). BEST-EFFORT: an unconfigured client / empty
     * [userId] / any thrown transport error degrades to an EMPTY list.
     */
    override suspend fun fetchOwnSpots(userId: String): List<Spot> {
        val c = client ?: return emptyList()
        if (userId.isEmpty()) return emptyList()
        return try {
            c.from("spots").select {
                filter { eq("owner_id", userId) }
            }.decodeList<SpotDto>().map { SpotDtoMapping.toSpot(it) }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * UPDATE the own `profiles` row ([userId]) setting `name` / `city` / `bio`
     * via supabase-kt 3.8.0 Postgrest `update(value) { filter { eq(...) } }` —
     * the typed-body form, matching the shipping `insert`/`upsert` DTO style in
     * [SupabaseFeedRepository]. Returns `false` when unconfigured (`client` is
     * null) or when [userId] is empty (no session); otherwise issues the PATCH
     * and returns `true`. The caller catches any thrown transport/REST error.
     *
     * supabase-kt 3.8.0 API (confirmed against the resolved postgrest-kt 3.8.0
     * klib): `PostgrestQueryBuilder.update(value: T) { PostgrestRequestBuilder }`
     * serializes [value] to the PATCH body and applies `filter { eq(column,
     * value) }` (the same filter DSL used by [fetchCurrentProfile] /
     * [SupabaseFeedRepository.applyVote]). The RLS "update own" policy
     * (`auth.uid() = id`) enforces that only the session user's row changes;
     * [userId] is passed defensively as the filter.
     */
    override suspend fun updateProfile(
        userId: String,
        name: String,
        city: String,
        bio: String,
    ): Boolean {
        val c = client ?: return false
        if (userId.isEmpty()) return false
        c.from("profiles").update(
            ProfileUpdateDto(name = name, city = city, bio = bio),
        ) {
            filter { eq("id", userId) }
        }
        return true
    }
}
