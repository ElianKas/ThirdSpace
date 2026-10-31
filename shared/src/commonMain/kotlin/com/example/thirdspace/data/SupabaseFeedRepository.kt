package com.example.thirdspace.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTO for a row of the remote `public.post_feed` view (see the Phase A
 * `post_feed_view` migration). snake_case remote column names are mapped with
 * [SerialName]. `created_at` is an ISO-8601 `timestamptz` STRING here and is
 * parsed to epoch millis by [RelativeTime.parseIsoToEpochMillis] during the
 * map to [FeedPost] — NOT decoded as a datetime (no datetime dep).
 *
 * INTERNAL to the shared module: this @Serializable type never crosses the flat
 * FeedBridge boundary.
 */
@Serializable
internal data class PostFeedDto(
    val id: String,
    @SerialName("author_name") val authorName: String = "",
    @SerialName("spot_name") val spotName: String = "",
    val category: String = "",
    val neighborhood: String = "",
    val verified: Boolean = false,
    val text: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val score: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("my_vote") val myVote: String = "NONE",
)

/**
 * Wire DTO for a row of `public.post_comments`, with the comment author's
 * display name resolved via a PostgREST EMBEDDED select of the related
 * `profiles(name)` (see [SupabaseFeedRepository.fetchComments]). The embedded
 * profile is decoded as [ProfileNameDto]; `author_name` is derived from it.
 *
 * INTERNAL to the shared module.
 */
@Serializable
internal data class CommentDto(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("author_id") val authorId: String = "",
    val text: String = "",
    @SerialName("created_at") val createdAt: String = "",
    // Embedded one-to-one `profiles(name)`. Nullable to tolerate a missing join
    // row; the mapping falls back to an empty author name in that case.
    val profiles: ProfileNameDto? = null,
)

/** Minimal embedded `profiles` projection (just the display `name`). */
@Serializable
internal data class ProfileNameDto(
    val name: String = "",
)

/** Payload sent on INSERT into `public.post_comments`. */
@Serializable
internal data class CommentInsertDto(
    @SerialName("post_id") val postId: String,
    @SerialName("author_id") val authorId: String,
    val text: String,
)

/**
 * Payload sent on INSERT into `public.posts`. Omits `id` and `created_at`
 * (both DB-defaulted — `gen_random_uuid()` / `now()`); the authoritative row
 * arrives back on the next `post_feed` refresh. The remote RLS `insert own`
 * policy enforces `author_id = auth.uid()`.
 */
@Serializable
internal data class PostInsertDto(
    @SerialName("spot_id") val spotId: String,
    @SerialName("author_id") val authorId: String,
    val text: String,
)

/**
 * Payload used when UPSERTing a `public.post_votes` row for a (post_id,
 * user_id) pair. `value` is +/-1 (the clear case is a DELETE, not an upsert of
 * 0). Snake_case remote columns via [SerialName].
 */
@Serializable
internal data class PostVoteDto(
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String,
    val value: Int,
)

/**
 * The remote feed source seam. An interface so [FeedRepository] /
 * [CommentRepository] can be unit-tested against a FAKE remote (no network),
 * while production uses the supabase-kt-backed [SupabaseFeedRepository]. All
 * methods stay INTERNAL — they never reach Swift. Mirrors [RemoteSpotSource].
 */
internal interface FeedRemoteSource {
    /** Whether a usable remote is available right now (configured). */
    fun isAvailable(): Boolean

    /** Fetch the whole feed from `post_feed` (empty when unavailable). */
    suspend fun fetchFeed(): List<FeedPost>

    /** Fetch comments for [postId] (empty when unavailable). */
    suspend fun fetchComments(postId: String): List<FeedComment>

    /**
     * Insert a comment with [text] on [postId] authored by [authorId].
     * `true` on success, `false` if unavailable. Mirrors [RemoteSpotSource.insertSpot].
     */
    suspend fun insertComment(postId: String, authorId: String, text: String): Boolean

    /**
     * Insert a new post with [text] on [spotId] authored by [authorId].
     * `true` on success, `false` if unavailable. The DB generates the post id +
     * created_at; those are NOT sent. Mirrors [insertComment].
     */
    suspend fun insertPost(spotId: String, authorId: String, text: String): Boolean

    /**
     * Apply the user's vote intent for ([postId], [userId]):
     * - [value] `1` / `-1` → UPSERT the `post_votes` row with that value.
     * - [value] `0`        → DELETE the user's `post_votes` row (clear).
     * `true` on success, `false` if unavailable.
     */
    suspend fun applyVote(postId: String, userId: String, value: Int): Boolean
}

/**
 * Remote feed source over supabase-kt Postgrest. All operations are `suspend`
 * and INTERNAL — only ever called from the repositories' background coroutine
 * scope, so no suspend / supabase type reaches Swift. Mirrors
 * [SupabaseSpotRepository].
 *
 * NOT-CONFIGURED SAFETY: every call first resolves the client via
 * [SupabaseClientProvider]; when unconfigured the client is `null` and reads
 * return an empty list / a write returns `false`, so the app never crashes
 * offline. The repository decides what to do with that.
 *
 * COMMENT AUTHOR NAME — approach (b), PostgREST embedding: `post_comments`
 * stores only `author_id`, so we resolve the display name by embedding the
 * related `profiles(name)` row in the SELECT
 * (`id, post_id, author_id, text, created_at, profiles(name)`). supabase-kt
 * 3.8.0 supports this via [Columns.raw]. If the embed is absent for a row
 * (e.g. a missing profile), the author name falls back to an empty string
 * rather than showing wrong data. (A dedicated `comment_feed` view could
 * replace the embed later — a follow-up migration, out of Phase B scope.)
 */
internal class SupabaseFeedRepository : FeedRemoteSource {

    override fun isAvailable(): Boolean = SupabaseClientProvider.isConfigured

    private val client get() = SupabaseClientProvider.client

    /**
     * Fetch the whole feed from `public.post_feed`, newest-first. Returns an
     * empty list (no throw) when unconfigured. `created_at` is parsed to epoch
     * millis here so the rest of the app stays datetime-free.
     */
    override suspend fun fetchFeed(): List<FeedPost> {
        val c = client ?: return emptyList()
        val rows = c.from("post_feed").select {
            order("created_at", Order.DESCENDING)
        }.decodeList<PostFeedDto>()
        return rows.map { dto ->
            FeedPost(
                id = dto.id,
                authorName = dto.authorName,
                spotName = dto.spotName,
                category = dto.category,
                neighborhood = dto.neighborhood,
                verified = dto.verified,
                text = dto.text,
                createdAtEpoch = RelativeTime.parseIsoToEpochMillis(dto.createdAt),
                score = dto.score,
                commentCount = dto.commentCount,
                myVote = dto.myVote,
            )
        }
    }

    /**
     * Fetch comments for [postId] from `public.post_comments`, oldest-first,
     * resolving the author display name via an embedded `profiles(name)`
     * (approach (b) — see the class doc). Empty list (no throw) when
     * unconfigured.
     */
    override suspend fun fetchComments(postId: String): List<FeedComment> {
        val c = client ?: return emptyList()
        val rows = c.from("post_comments").select(
            Columns.raw("id, post_id, author_id, text, created_at, profiles(name)"),
        ) {
            filter { eq("post_id", postId) }
            order("created_at", Order.ASCENDING)
        }.decodeList<CommentDto>()
        return rows.map { dto ->
            FeedComment(
                id = dto.id,
                postId = dto.postId,
                authorName = dto.profiles?.name.orEmpty(),
                text = dto.text,
                createdAtEpoch = RelativeTime.parseIsoToEpochMillis(dto.createdAt),
            )
        }
    }

    /**
     * Insert a comment on [postId] authored by [authorId]. Returns `true` on
     * success, `false` when unconfigured (so the caller keeps it queued). Any
     * thrown transport/REST error propagates to the caller, which treats it as
     * "stay queued, retry later" (caught on the background scope). The remote
     * RLS `insert own` policy also enforces `author_id = auth.uid()`.
     */
    override suspend fun insertComment(postId: String, authorId: String, text: String): Boolean {
        val c = client ?: return false
        c.from("post_comments").insert(
            CommentInsertDto(postId = postId, authorId = authorId, text = text),
        )
        return true
    }

    /**
     * Insert a new post on [spotId] authored by [authorId] with [text]. Returns
     * `true` on success, `false` when unconfigured (so the caller keeps it
     * queued). The DB defaults `id`/`created_at`, so they are not sent; the
     * authoritative row (with its generated id, score 0, my_vote NONE) arrives
     * back on the next `post_feed` refresh, which replaces the optimistic
     * local-id row. Any thrown REST error propagates ("stay queued, retry").
     */
    override suspend fun insertPost(spotId: String, authorId: String, text: String): Boolean {
        val c = client ?: return false
        c.from("posts").insert(
            PostInsertDto(spotId = spotId, authorId = authorId, text = text),
        )
        return true
    }

    /**
     * Apply the user's vote intent for ([postId], [userId]). For a non-zero
     * [value] (+/-1), UPSERT the `post_votes` row keyed by the composite
     * `(post_id, user_id)` unique constraint via supabase-kt 3.8.0
     * `upsert(...) { onConflict = "post_id,user_id" }`; for a zero [value]
     * (clear), DELETE the user's row via `delete { filter { eq(...) } }`.
     * Returns `true` on success, `false` when unconfigured. The `post_feed`
     * view recomputes the authoritative score/my_vote on the next refresh.
     *
     * supabase-kt 3.8.0 API (confirmed against the resolved postgrest-kt klib):
     * `PostgrestQueryBuilder.upsert(value) { }` takes an `UpsertRequestBuilder`
     * exposing `onConflict: String`; `delete { }` takes a
     * `PostgrestRequestBuilder` with `filter { eq(column, value) }`
     * (same filter DSL already used by [fetchComments]).
     */
    override suspend fun applyVote(postId: String, userId: String, value: Int): Boolean {
        val c = client ?: return false
        if (value == 0) {
            c.from("post_votes").delete {
                filter {
                    eq("post_id", postId)
                    eq("user_id", userId)
                }
            }
        } else {
            c.from("post_votes").upsert(
                PostVoteDto(postId = postId, userId = userId, value = value),
            ) {
                onConflict = "post_id,user_id"
            }
        }
        return true
    }
}
