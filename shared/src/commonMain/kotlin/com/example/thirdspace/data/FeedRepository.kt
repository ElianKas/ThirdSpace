package com.example.thirdspace.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Offline-first, READ-ONLY feed repository for posts. Mirrors
 * [CachingSpotRepository]'s synchronous surface: [getPosts] returns the LOCAL
 * cache immediately, then kicks off a background remote refresh
 * (fetch `post_feed` -> replace-all into the cache -> notify [onChanged]).
 *
 * All suspend / coroutine / supabase work happens on [scope]; none crosses to
 * Swift. [onChanged] is a flat listener the bridge wires to a Dispatchers.Main
 * scope so Swift can re-read the feed after a background refresh.
 *
 * SEEDING: if the post cache is empty on construction (first launch, pre-login,
 * offline), it is seeded from [SampleData] via [FeedMapping] so the feed is not
 * empty before the first remote refresh. Seed rows are overwritten by the first
 * successful remote replace-all once configured + authenticated — identical to
 * the spots seeding.
 *
 * NOT-CONFIGURED / OFFLINE / UNAUTHENTICATED: [getPosts] just serves the cache;
 * nothing throws.
 */
class FeedRepository internal constructor(
    private val cache: FeedCache,
    private val scope: CoroutineScope,
    private val remote: FeedRemoteSource = SupabaseFeedRepository(),
    private val isAuthenticated: () -> Boolean = { AuthRepository.isAuthenticated() },
    private val clock: () -> Long = { currentEpochMillis() },
    private val onChanged: () -> Unit = {},
    seedIfEmpty: List<FeedPost> = FeedMapping.defaultPostSeed(currentEpochMillis()),
) {

    private val syncMutex = Mutex()

    init {
        if (cache.getCachedPosts().isEmpty() && seedIfEmpty.isNotEmpty()) {
            cache.replaceAllPosts(seedIfEmpty)
        }
    }

    /** The cached posts (cache-first), kicking off a background refresh. */
    fun getPosts(): List<FeedPost> {
        val local = cache.getCachedPosts()
        scope.launch { refreshFromRemote() }
        return local
    }

    /**
     * Background refresh: fetch the remote feed, replace-all into the cache.
     * Serves the cache unchanged when the remote is unavailable or
     * unauthenticated. Swallows errors (offline/transient) so a failed refresh
     * never crashes.
     */
    private suspend fun refreshFromRemote() {
        if (!remote.isAvailable() || !isAuthenticated()) return
        syncMutex.withLock {
            try {
                val remotePosts = remote.fetchFeed()
                cache.replaceAllPosts(remotePosts)
                onChanged()
            } catch (_: Throwable) {
                // Offline / transient: keep serving the existing cache.
            }
        }
    }
}

/**
 * Offline-first feed repository for comments (READ + WRITE with an outbound
 * queue). Mirrors [CachingSpotRepository] for the write path.
 *
 * - [getComments] returns the LOCAL cached comments for a post immediately,
 *   then kicks off a per-post background refresh (fetch -> replace those post's
 *   cached comments -> notify).
 * - [addComment] write-throughs an OPTIMISTIC comment to the cache immediately
 *   (author name = the current user's display name/email, created_at = now, so
 *   it shows at once) AND enqueues a pending insert with the author id from the
 *   session, then kicks off a background push. On success the queue entry is
 *   dequeued; offline / unconfigured / unauthenticated leaves it queued (retried
 *   on the next refresh). Nothing throws.
 *
 * SEEDING: comments are seeded per-post ON FIRST READ from [SampleData] when
 * that post has no cached comments yet, so the comments sheet is not empty
 * pre-login. Seed comments are overwritten by the first successful per-post
 * remote refresh.
 */
class CommentRepository internal constructor(
    private val cache: FeedCache,
    private val scope: CoroutineScope,
    private val remote: FeedRemoteSource = SupabaseFeedRepository(),
    private val authorIdProvider: () -> String = { AuthRepository.currentUserId() },
    private val authorNameProvider: () -> String = { AuthRepository.currentUserEmail() },
    private val isAuthenticated: () -> Boolean = { AuthRepository.isAuthenticated() },
    private val clock: () -> Long = { currentEpochMillis() },
    private val idGenerator: () -> String = { "local-comment-${currentEpochMillis()}" },
    private val onChanged: () -> Unit = {},
    private val seedProvider: (String, Long) -> List<FeedComment> = { postId, now ->
        FeedMapping.defaultCommentSeed(postId, now)
    },
) {

    private val syncMutex = Mutex()
    // Posts whose comments have already been seed-checked, so the one-time
    // SampleData seed per post does not re-run after the user clears them.
    private val seeded = mutableSetOf<String>()

    /**
     * The cached comments for [postId] (cache-first), kicking off a background
     * flush of any pending inserts followed by a remote refresh of this post's
     * comments. The flush-then-fetch order means a just-pushed optimistic
     * comment is returned by the fetch rather than being clobbered by the
     * replace-all.
     */
    fun getComments(postId: String): List<FeedComment> {
        seedIfNeeded(postId)
        val local = cache.getCachedComments(postId)
        scope.launch { refreshComments(postId) }
        return local
    }

    /**
     * Add a comment with [text] to [postId]. Optimistic write-through +
     * enqueue + background push. Ignores a blank [text] (no-op).
     */
    fun addComment(postId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val id = idGenerator()
        val now = clock()
        // Optimistic local comment so it shows immediately.
        cache.upsertComment(
            FeedComment(
                id = id,
                postId = postId,
                authorName = authorNameProvider(),
                text = trimmed,
                createdAtEpoch = now,
            ),
        )
        cache.enqueuePendingComment(
            id = id,
            postId = postId,
            authorId = authorIdProvider(),
            text = trimmed,
            enqueuedAt = now,
        )
        onChanged()
        scope.launch { flushPending() }
    }

    /** Seed a post's comments from SampleData once, if its cache is empty. */
    private fun seedIfNeeded(postId: String) {
        if (postId in seeded) return
        seeded.add(postId)
        if (cache.getCachedComments(postId).isEmpty()) {
            val seed = seedProvider(postId, clock())
            if (seed.isNotEmpty()) {
                cache.replaceCommentsForPost(postId, seed)
            }
        }
    }

    /**
     * Background refresh for [postId]: FIRST flush any pending inserts, THEN
     * fetch remote comments and replace that post's cached comments. The
     * flush-before-fetch order ensures a just-pushed optimistic comment is
     * present in the fetched set (not wiped by the replace-all). Serves the
     * cache unchanged when unavailable / unauthenticated. Swallows errors.
     */
    private suspend fun refreshComments(postId: String) {
        if (!remote.isAvailable() || !isAuthenticated()) return
        syncMutex.withLock {
            val pushed = flushPendingLocked()
            try {
                val remoteComments = remote.fetchComments(postId)
                cache.replaceCommentsForPost(postId, remoteComments)
                onChanged()
            } catch (_: Throwable) {
                // Offline / transient: keep serving the existing cache. Still
                // notify if a flush changed the cache.
                if (pushed) onChanged()
            }
        }
    }

    /** Flush the pending-comment queue under the sync lock. */
    private suspend fun flushPending() {
        if (!remote.isAvailable() || !isAuthenticated()) return
        syncMutex.withLock {
            if (flushPendingLocked()) onChanged()
        }
    }

    /**
     * Try to push each queued insert; dequeue on success. Returns `true` if any
     * item was pushed. Stops on the first failure (likely offline) and leaves
     * the rest queued. Mirrors [CachingSpotRepository.flushPendingLocked].
     */
    private suspend fun flushPendingLocked(): Boolean {
        var pushedAny = false
        for (pending in cache.pendingComments()) {
            val ok = try {
                remote.insertComment(pending.postId, pending.authorId, pending.text)
            } catch (_: Throwable) {
                false
            }
            if (ok) {
                cache.dequeuePendingComment(pending.id)
                pushedAny = true
            } else {
                break
            }
        }
        return pushedAny
    }
}

/**
 * The process-wide feed repository provider, mirroring [Spots].
 *
 * Builds a [FeedRepository] (posts) + a [CommentRepository] (comments) over the
 * SQLDelight-backed [LocalFeedCache] + the supabase-kt [SupabaseFeedRepository],
 * running background refresh/push on a shared [Dispatchers.Default] scope. A
 * single shared cache instance backs both so a post's comment count and its
 * comments stay consistent.
 *
 * LISTENER: [setChangeListener] lets the iOS bridge register a flat callback
 * (delivered on its own Dispatchers.Main scope) that fires after a background
 * refresh/push changes the cache, so Swift can re-read the feed/comments.
 *
 * FALLBACK: if the SQLDelight driver cannot be created in some environment, a
 * safe in-memory [FeedCache] is used instead so nothing crashes; the feed still
 * serves the SampleData seed.
 */
object Feed {

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var changeListener: () -> Unit = {}

    private val cache: FeedCache by lazy { buildCache() }

    val posts: FeedRepository by lazy {
        FeedRepository(cache = cache, scope = backgroundScope, onChanged = { changeListener() })
    }

    val comments: CommentRepository by lazy {
        CommentRepository(cache = cache, scope = backgroundScope, onChanged = { changeListener() })
    }

    /**
     * Register a flat [listener] invoked after a background refresh/push changes
     * the cache. The bridge wraps this to hop onto Dispatchers.Main.
     */
    fun setChangeListener(listener: () -> Unit) {
        changeListener = listener
    }

    private fun buildCache(): FeedCache =
        try {
            LocalFeedCache.create()
        } catch (_: Throwable) {
            InMemoryFeedCache()
        }
}

/**
 * Minimal in-memory [FeedCache] fallback (no SQLDelight), used only when the
 * platform driver cannot be created. Keeps the feed serving the SampleData seed
 * rather than crashing. Not used in tests — those supply their own fakes.
 */
internal class InMemoryFeedCache : FeedCache {
    private val posts = LinkedHashMap<String, FeedPost>()
    private val comments = LinkedHashMap<String, FeedComment>()
    private val pending = mutableListOf<PendingCommentInsert>()

    override fun getCachedPosts(): List<FeedPost> =
        posts.values.sortedByDescending { it.createdAtEpoch }

    override fun replaceAllPosts(posts: List<FeedPost>) {
        this.posts.clear()
        posts.forEach { this.posts[it.id] = it }
    }

    override fun upsertPost(post: FeedPost) { posts[post.id] = post }

    override fun getCachedComments(postId: String): List<FeedComment> =
        comments.values.filter { it.postId == postId }.sortedBy { it.createdAtEpoch }

    override fun replaceCommentsForPost(postId: String, comments: List<FeedComment>) {
        this.comments.values.removeAll { it.postId == postId }
        comments.forEach { this.comments[it.id] = it }
    }

    override fun upsertComment(comment: FeedComment) { comments[comment.id] = comment }

    override fun enqueuePendingComment(
        id: String,
        postId: String,
        authorId: String,
        text: String,
        enqueuedAt: Long,
    ) {
        pending.removeAll { it.id == id }
        pending.add(PendingCommentInsert(id, postId, authorId, text, enqueuedAt))
    }

    override fun pendingComments(): List<PendingCommentInsert> =
        pending.sortedBy { it.enqueuedAt }

    override fun dequeuePendingComment(id: String) { pending.removeAll { it.id == id } }
}
