package com.example.thirdspace.data

import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTO for a row of `public.follows` (see the `init_follows` migration):
 * a single directed edge "`followerId` follows `followingId`". snake_case
 * remote columns mapped with [SerialName]. `created_at` is intentionally NOT
 * decoded (the lists do not need it; keeping the DTO minimal avoids a datetime
 * dep), so only the two id columns are selected/decoded.
 *
 * INTERNAL to the shared module: this @Serializable type never crosses the flat
 * FeedBridge boundary.
 */
@Serializable
internal data class FollowRowDto(
    @SerialName("follower_id") val followerId: String = "",
    @SerialName("following_id") val followingId: String = "",
)

/**
 * Payload sent on INSERT into `public.follows` (a new follow edge). Only the
 * two id columns are sent; `created_at` is DB-defaulted (`now()`). The remote
 * RLS "insert own" policy enforces `follower_id = auth.uid()`.
 *
 * INTERNAL to the shared module.
 */
@Serializable
internal data class FollowInsertDto(
    @SerialName("follower_id") val followerId: String,
    @SerialName("following_id") val followingId: String,
)

/**
 * Minimal `profiles` projection used to resolve a listed user's display
 * name/city for a [FollowUser]. `email` is NOT selected (the follows list only
 * needs name/city), so [ProfileMapping.deriveUsername] is called with `email`
 * = "" and falls back to the name-derived username — exactly the documented
 * fallback. INTERNAL to the shared module.
 */
@Serializable
internal data class FollowProfileDto(
    val id: String = "",
    val name: String = "",
    val city: String = "",
)

/**
 * The remote follow (social-graph) source seam (Phase F2). An interface so
 * [FollowRepository] can be unit-tested against a FAKE remote (no network),
 * while production uses the supabase-kt-backed [SupabaseFollowRepository]. All
 * suspend methods stay INTERNAL — they never reach Swift. Mirrors
 * [FeedRemoteSource] / [ProfileRemoteSource].
 *
 * `userId` is the subject whose graph is being read; `viewerId` is the CURRENT
 * user, used only to compute [FollowUser.isFollowedByMe] (whether the viewer
 * follows each listed user). Every method is BEST-EFFORT: unavailable /
 * empty-id / a thrown transport error degrades to an empty list / `false` / 0
 * and NEVER throws to the caller.
 */
internal interface FollowRemoteSource {
    /** Whether a usable remote is available right now (configured). */
    fun isAvailable(): Boolean

    /**
     * Users who follow [userId] (followers of [userId]), each marked with
     * [FollowUser.isFollowedByMe] = whether [viewerId] follows that user.
     */
    suspend fun fetchFollowers(userId: String, viewerId: String): List<FollowUser>

    /**
     * Users [userId] follows (the following list), each marked with
     * [FollowUser.isFollowedByMe] = whether [viewerId] follows that user.
     */
    suspend fun fetchFollowing(userId: String, viewerId: String): List<FollowUser>

    /** Count of users who follow [userId]. 0 when unavailable / empty-id. */
    suspend fun followerCount(userId: String): Int

    /** Count of users [userId] follows. 0 when unavailable / empty-id. */
    suspend fun followingCount(userId: String): Int

    /** Whether [followerId] already follows [followingId]. */
    suspend fun isFollowing(followerId: String, followingId: String): Boolean

    /**
     * Create a follow edge ([followerId] -> [followingId]). `true` on success,
     * `false` when unavailable / self-follow. Idempotent re-follow is safe (the
     * DB PK conflict is swallowed — see the impl).
     */
    suspend fun follow(followerId: String, followingId: String): Boolean

    /** Delete the follow edge ([followerId] -> [followingId]). */
    suspend fun unfollow(followerId: String, followingId: String): Boolean
}

/**
 * Remote follow source over supabase-kt Postgrest. All operations are `suspend`
 * and INTERNAL — only ever called from [FollowRepository]'s background
 * coroutine scope, so no suspend / supabase type reaches Swift. Mirrors
 * [SupabaseFeedRepository] / [SupabaseProfileRepository].
 *
 * NOT-CONFIGURED / BEST-EFFORT SAFETY: every call resolves the client via
 * [SupabaseClientProvider]; when unconfigured the client is `null` and the call
 * degrades to an empty list / `false` / 0. Network bodies are additionally
 * wrapped in try/catch so a transport/REST error ALSO degrades rather than
 * throwing — these reads back a non-critical social-graph UI, so one failing
 * query must never crash. Empty `userId` / `viewerId` short-circuits the same way.
 *
 * ---- DESIGN DECISION: embed vs. client-side join ----
 * `public.follows` has TWO FKs to `auth.users` (`follower_id`, `following_id`),
 * which makes a PostgREST `profiles(...)` embed AMBIGUOUS — it would require the
 * disambiguating `profiles!follows_following_id_fkey(name, city)` hint syntax,
 * which is NOT proven against supabase-kt 3.8.0 anywhere in this codebase.
 * Per the phase brief's guidance, this impl uses the SIMPLER, definitely-correct
 * CLIENT-SIDE JOIN with ONLY already-proven API:
 *   1. `select` the follow rows (just the two id columns) filtered by the
 *      relevant id with the proven `select { filter { eq(...) } }` DSL.
 *   2. For each counterpart user id, `select` its `profiles` row by
 *      `eq("id", id)` (the SAME single-row read proven in
 *      [SupabaseProfileRepository.fetchCurrentProfile]) and join in Kotlin.
 * No `in`/`isin` filter and no embed-disambiguation syntax is used (neither is
 * proven here). The per-id profile reads are N small round-trips; given typical
 * follower/following list sizes and that this is a best-effort, explicitly
 * triggered refresh (not a hot path), that is an acceptable tradeoff for using
 * strictly proven API. A `comment_feed`-style view or an embed could replace it
 * in a later migration once the embed syntax is proven.
 *
 * ---- DESIGN DECISION: count approach ----
 * `Count.EXACT` head-select is NOT proven anywhere in this codebase, so the
 * counts `decodeList` the id column of the matching follow rows and take
 * `.size`. Tradeoff: this transfers the id rows rather than a single HEAD count,
 * but it uses ONLY the proven `select { filter { eq(...) } }.decodeList<T>()`
 * pattern and is correct. Follow counts are small, so the overhead is minor.
 */
internal class SupabaseFollowRepository(
    private val currentUserId: () -> String = { AuthRepository.currentUserId() },
) : FollowRemoteSource {

    override fun isAvailable(): Boolean = SupabaseClientProvider.isConfigured

    private val client get() = SupabaseClientProvider.client

    /**
     * Followers of [userId]: the `follower_id`s of every `follows` row whose
     * `following_id == userId`, resolved to [FollowUser]s via a client-side
     * join (see the class doc). Each is marked [FollowUser.isFollowedByMe] by
     * testing membership in [viewerId]'s following-set (fetched once).
     */
    override suspend fun fetchFollowers(userId: String, viewerId: String): List<FollowUser> {
        val c = client ?: return emptyList()
        if (userId.isEmpty()) return emptyList()
        return try {
            val rows = c.from("follows").select {
                filter { eq("following_id", userId) }
            }.decodeList<FollowRowDto>()
            val userIds = rows.map { it.followerId }.filter { it.isNotEmpty() }.distinct()
            buildFollowUsers(c, userIds, viewerId)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * The following list of [userId]: the `following_id`s of every `follows`
     * row whose `follower_id == userId`, resolved to [FollowUser]s via a
     * client-side join. Same [FollowUser.isFollowedByMe] semantics relative to
     * [viewerId].
     */
    override suspend fun fetchFollowing(userId: String, viewerId: String): List<FollowUser> {
        val c = client ?: return emptyList()
        if (userId.isEmpty()) return emptyList()
        return try {
            val rows = c.from("follows").select {
                filter { eq("follower_id", userId) }
            }.decodeList<FollowRowDto>()
            val userIds = rows.map { it.followingId }.filter { it.isNotEmpty() }.distinct()
            buildFollowUsers(c, userIds, viewerId)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun followerCount(userId: String): Int {
        val c = client ?: return 0
        if (userId.isEmpty()) return 0
        return try {
            c.from("follows").select {
                filter { eq("following_id", userId) }
            }.decodeList<FollowRowDto>().size
        } catch (_: Throwable) {
            0
        }
    }

    override suspend fun followingCount(userId: String): Int {
        val c = client ?: return 0
        if (userId.isEmpty()) return 0
        return try {
            c.from("follows").select {
                filter { eq("follower_id", userId) }
            }.decodeList<FollowRowDto>().size
        } catch (_: Throwable) {
            0
        }
    }

    override suspend fun isFollowing(followerId: String, followingId: String): Boolean {
        val c = client ?: return false
        if (followerId.isEmpty() || followingId.isEmpty()) return false
        if (followerId == followingId) return false
        return try {
            c.from("follows").select {
                filter {
                    eq("follower_id", followerId)
                    eq("following_id", followingId)
                }
            }.decodeList<FollowRowDto>().isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * INSERT a follow edge. Self-follow is refused client-side (the DB CHECK
     * also forbids it). A re-follow that hits the `(follower_id, following_id)`
     * PK conflict is treated as success (already following) rather than an
     * error. Any other thrown error degrades to `false`.
     */
    override suspend fun follow(followerId: String, followingId: String): Boolean {
        val c = client ?: return false
        if (followerId.isEmpty() || followingId.isEmpty()) return false
        if (followerId == followingId) return false
        return try {
            c.from("follows").insert(
                FollowInsertDto(followerId = followerId, followingId = followingId),
            )
            true
        } catch (_: Throwable) {
            // A PK conflict (already following) or any transport error degrades
            // to false; the caller keeps its optimistic state and reports it.
            false
        }
    }

    override suspend fun unfollow(followerId: String, followingId: String): Boolean {
        val c = client ?: return false
        if (followerId.isEmpty() || followingId.isEmpty()) return false
        return try {
            c.from("follows").delete {
                filter {
                    eq("follower_id", followerId)
                    eq("following_id", followingId)
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Resolve each user id in [userIds] to a [FollowUser] via the client-side
     * join (per-id `profiles` read, mirroring
     * [SupabaseProfileRepository.fetchCurrentProfile]). [FollowUser.username]
     * is derived from the name (email unavailable here -> passed as "") via the
     * shared [ProfileMapping.deriveUsername]. [FollowUser.isFollowedByMe] is
     * membership in [viewerId]'s following-set, which is fetched ONCE. A user
     * is never shown as following themselves.
     *
     * The `client` is passed in (already null-checked by the callers) so this
     * helper only does the per-id resolution.
     */
    private suspend fun buildFollowUsers(
        c: io.github.jan.supabase.SupabaseClient,
        userIds: List<String>,
        viewerId: String,
    ): List<FollowUser> {
        if (userIds.isEmpty()) return emptyList()
        // The viewer's following-set, fetched once, for the isFollowedByMe flag.
        val viewerFollowing: Set<String> = if (viewerId.isEmpty()) {
            emptySet()
        } else {
            c.from("follows").select {
                filter { eq("follower_id", viewerId) }
            }.decodeList<FollowRowDto>().map { it.followingId }.toSet()
        }
        return userIds.mapNotNull { id ->
            val profile = c.from("profiles").select {
                filter { eq("id", id) }
            }.decodeList<FollowProfileDto>().firstOrNull() ?: return@mapNotNull null
            FollowUser(
                id = id,
                name = profile.name,
                // email is not selected here -> deriveUsername falls back to name.
                username = ProfileMapping.deriveUsername(email = "", name = profile.name),
                city = profile.city,
                // A user never follows themselves.
                isFollowedByMe = id != viewerId && viewerFollowing.contains(id),
            )
        }
    }
}
