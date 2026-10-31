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
    private val authorIdProvider: () -> String = { AuthRepository.currentUserId() },
    private val authorNameProvider: () -> String = { AuthRepository.currentUserEmail() },
    private val idGenerator: () -> String = { "local-post-${currentEpochMillis()}" },
    // How the optimistic post resolves its spot metadata (name/category/
    // neighborhood/verified) from the chosen spotId. Defaults to the live
    // Spots repository; injectable for tests so no provider singleton is hit.
    private val spotLookup: (String) -> Spot? = { id ->
        Spots.repository.getSpots().firstOrNull { it.id == id }
    },
    seedIfEmpty: List<FeedPost> = FeedMapping.defaultPostSeed(currentEpochMillis()),
) {

    private val syncMutex = Mutex()

    // In-flight guard so concurrent/back-to-back refreshes coalesce to a single
    // remote fetch rather than stacking (root cause of the per-second GET loop:
    // a refresh's onChanged() re-triggered another refresh unbounded).
    private var refreshing = false

    init {
        if (cache.getCachedPosts().isEmpty() && seedIfEmpty.isNotEmpty()) {
            cache.replaceAllPosts(seedIfEmpty)
        }
    }

    /**
     * PURE cache read — returns the LOCAL cached posts and triggers NO remote
     * refresh. Reads must never kick off a GET; that self-reinforcing loop
     * (read -> GET -> replace-all -> onChanged -> re-read -> GET …) was the
     * confirmed per-second request flood. Use [refresh] for an explicit remote
     * pull (bridge loadFeed / pull-to-refresh / post-write reconcile).
     */
    fun getPosts(): List<FeedPost> = cache.getCachedPosts()

    /** Explicit remote refresh trigger (bridge loadFeed / post-write reconcile). */
    fun refresh() { scope.launch { refreshFromRemote() } }

    /**
     * Create a new post on [spotId] with [text]. Mirrors
     * [CommentRepository.addComment]:
     * 1. Build an OPTIMISTIC [FeedPost] (local id, author = current user email,
     *    spot metadata resolved from [spotLookup], created_at = now, score 0,
     *    myVote "NONE", commentCount 0) and write it through the cache so it
     *    appears at the TOP of the feed immediately (newest-first).
     * 2. Enqueue a pending post insert (spotId, authorId from the session, text,
     *    now).
     * 3. [onChanged], then a background flush that pushes via
     *    [FeedRemoteSource.insertPost] and dequeues on success; offline /
     *    unconfigured / unauthenticated leaves it queued (retried later);
     *    nothing throws.
     *
     * RECONCILIATION: the DB generates the real post id, so the optimistic
     * local-id row cannot share the authoritative post's id. On the next remote
     * refresh, [refreshFromRemote] does a DESTRUCTIVE replace-all, which drops
     * the transient local-id optimistic post and installs the authoritative
     * `post_feed` row. On a successful push we kick a refresh so that
     * reconciliation happens promptly (no lingering duplicate).
     *
     * [text] MAY be empty (a post can be a pure check-in) — unlike comments,
     * blank text is allowed. A blank [spotId] is rejected (no-op): a post must
     * belong to a spot.
     */
    fun createPost(spotId: String, text: String) {
        if (spotId.isBlank()) return
        val trimmed = text.trim()
        val id = idGenerator()
        val now = clock()
        val spot = spotLookup(spotId)
        cache.upsertPost(
            FeedPost(
                id = id,
                authorName = authorNameProvider(),
                spotName = spot?.name.orEmpty(),
                category = spot?.category.orEmpty(),
                neighborhood = spot?.neighborhood.orEmpty(),
                verified = spot?.verified ?: false,
                text = trimmed,
                createdAtEpoch = now,
                score = 0,
                commentCount = 0,
                myVote = "NONE",
            ),
        )
        cache.enqueuePendingPost(
            id = id,
            spotId = spotId,
            authorId = authorIdProvider(),
            text = trimmed,
            enqueuedAt = now,
        )
        onChanged()
        scope.launch { flushPendingPosts() }
    }

    /** Flush the pending-post queue under the sync lock. */
    private suspend fun flushPendingPosts() {
        if (!remote.isAvailable() || !isAuthenticated()) return
        var pushedAny = false
        syncMutex.withLock {
            for (pending in cache.pendingPosts()) {
                val ok = try {
                    remote.insertPost(pending.spotId, pending.authorId, pending.text)
                } catch (_: Throwable) {
                    false
                }
                if (ok) {
                    cache.dequeuePendingPost(pending.id)
                    pushedAny = true
                } else {
                    break
                }
            }
        }
        // On a successful push, refresh so the authoritative post_feed rows
        // replace the transient local-id optimistic post (replace-all drops it).
        if (pushedAny) refreshFromRemote()
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
            // Coalesce: if a refresh is already running, skip (prevents the
            // stacked/looping fetch). Reset in finally so later refreshes work.
            if (refreshing) return@withLock
            refreshing = true
            try {
                val remotePosts = remote.fetchFeed()
                cache.replaceAllPosts(remotePosts)
                onChanged()
            } catch (_: Throwable) {
                // Offline / transient: keep serving the existing cache.
            } finally {
                refreshing = false
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
    // PER-POST in-flight guard so back-to-back refreshes of the SAME post
    // coalesce to one fetch (comments share Feed.changeListener with posts, so
    // the read->GET->onChanged->read loop must be broken here too).
    private val inFlight = mutableSetOf<String>()

    /**
     * PURE cache read for [postId] — seeds once if empty, then returns the
     * LOCAL cached comments. Triggers NO remote refresh (reads must never kick
     * off a GET; comments share the feed change listener with posts, so a
     * read-triggered fetch fed the same per-second request loop). Use
     * [refresh] for an explicit remote pull of one post's comments.
     */
    fun getComments(postId: String): List<FeedComment> {
        seedIfNeeded(postId)
        return cache.getCachedComments(postId)
    }

    /** Explicit remote refresh of one post's comments (bridge loadComments). */
    fun refresh(postId: String) { scope.launch { refreshComments(postId) } }

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
            // Per-post coalesce: skip if a refresh for this post is already
            // running (prevents the stacked/looping fetch per post).
            if (postId in inFlight) return@withLock
            inFlight.add(postId)
            val pushed = flushPendingLocked()
            try {
                val remoteComments = remote.fetchComments(postId)
                cache.replaceCommentsForPost(postId, remoteComments)
                onChanged()
            } catch (_: Throwable) {
                // Offline / transient: keep serving the existing cache. Still
                // notify if a flush changed the cache.
                if (pushed) onChanged()
            } finally {
                inFlight.remove(postId)
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
 * Offline-first VOTE write path for posts (toggle state machine + optimistic
 * cache update + latest-intent outbound queue). Mirrors
 * [CommentRepository]'s write path but against the per-post vote state.
 *
 * The TOGGLE STATE MACHINE is resolved SHARED-SIDE from the current cached
 * post's [FeedPost.myVote] and a [tappedDirection] (`1` = up tapped, `-1` =
 * down tapped). Given the current vote and the tap, it produces the new vote +
 * the score delta (and the resolved remote intent value 1/-1/0 where 0 =
 * clear):
 *
 * | current | tap  | new   | score delta | intent |
 * |---------|------|-------|-------------|--------|
 * | NONE    | up   | UP    | +1          | 1      |
 * | NONE    | down | DOWN  | -1          | -1     |
 * | UP      | up   | NONE  | -1 (clear)  | 0      |
 * | UP      | down | DOWN  | -2          | -1     |
 * | DOWN    | down | NONE  | +1 (clear)  | 0      |
 * | DOWN    | up   | UP    | +2          | 1      |
 *
 * The new score/my_vote are applied OPTIMISTICALLY to the cached post so the UI
 * reflects the tap at once; a latest-intent pending vote is enqueued (keyed by
 * post id — a newer tap REPLACES the queued intent), [onChanged] fires, then a
 * background flush pushes via [FeedRemoteSource.applyVote] and dequeues on
 * success. Offline / unconfigured / unauthenticated leaves the latest intent
 * queued (the flush gates on availability + [isAuthenticated], like the other
 * write paths), so an unauthenticated tap stays optimistic + queued and never
 * crashes. On the next remote refresh the `post_feed` view's authoritative
 * score/my_vote replace the optimistic values.
 */
class VoteRepository internal constructor(
    private val cache: FeedCache,
    private val scope: CoroutineScope,
    private val remote: FeedRemoteSource = SupabaseFeedRepository(),
    private val userIdProvider: () -> String = { AuthRepository.currentUserId() },
    private val isAuthenticated: () -> Boolean = { AuthRepository.isAuthenticated() },
    private val clock: () -> Long = { currentEpochMillis() },
    private val onChanged: () -> Unit = {},
) {

    private val syncMutex = Mutex()

    /** The outcome of resolving the toggle state machine for one tap. */
    internal data class VoteOutcome(
        val newMyVote: String,
        val scoreDelta: Int,
        val intent: Int,
    )

    /**
     * Register a tap on [postId]: [tappedDirection] is `1` (up tapped) or `-1`
     * (down tapped). Resolves the toggle against the cached post, applies the
     * new score/my_vote optimistically, enqueues the latest intent, and kicks a
     * background push. No-op when the post is not cached, or for an out-of-range
     * direction.
     */
    fun vote(postId: String, tappedDirection: Int) {
        if (tappedDirection != 1 && tappedDirection != -1) return
        val current = cache.getCachedPosts().firstOrNull { it.id == postId } ?: return
        val outcome = resolve(current.myVote, tappedDirection)
        cache.updatePostVote(
            postId = postId,
            newScore = current.score + outcome.scoreDelta,
            newMyVote = outcome.newMyVote,
        )
        cache.upsertPendingVote(postId = postId, value = outcome.intent, enqueuedAt = clock())
        onChanged()
        scope.launch { flushPendingVotes() }
    }

    /** Flush the latest-intent vote queue under the sync lock. */
    private suspend fun flushPendingVotes() {
        if (!remote.isAvailable() || !isAuthenticated()) return
        syncMutex.withLock {
            var pushedAny = false
            val userId = userIdProvider()
            for (pending in cache.pendingVotes()) {
                val ok = try {
                    remote.applyVote(pending.postId, userId, pending.value)
                } catch (_: Throwable) {
                    false
                }
                if (ok) {
                    cache.dequeuePendingVote(pending.postId)
                    pushedAny = true
                } else {
                    break
                }
            }
            if (pushedAny) onChanged()
        }
    }

    internal companion object {
        /**
         * Pure toggle state machine. [current] is "UP"/"DOWN"/"NONE";
         * [tappedDirection] is 1 (up) or -1 (down). Exposed `internal` so a
         * commonTest can assert all six transitions without a cache/remote.
         */
        fun resolve(current: String, tappedDirection: Int): VoteOutcome = when (current) {
            "UP" -> if (tappedDirection == 1) {
                VoteOutcome(newMyVote = "NONE", scoreDelta = -1, intent = 0)
            } else {
                VoteOutcome(newMyVote = "DOWN", scoreDelta = -2, intent = -1)
            }
            "DOWN" -> if (tappedDirection == -1) {
                VoteOutcome(newMyVote = "NONE", scoreDelta = +1, intent = 0)
            } else {
                VoteOutcome(newMyVote = "UP", scoreDelta = +2, intent = 1)
            }
            else -> if (tappedDirection == 1) {
                VoteOutcome(newMyVote = "UP", scoreDelta = +1, intent = 1)
            } else {
                VoteOutcome(newMyVote = "DOWN", scoreDelta = -1, intent = -1)
            }
        }
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

    val votes: VoteRepository by lazy {
        VoteRepository(cache = cache, scope = backgroundScope, onChanged = { changeListener() })
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
    private val pendingPostsQueue = mutableListOf<PendingPostInsert>()
    private val pendingVotesQueue = LinkedHashMap<String, PendingVote>()

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

    override fun enqueuePendingPost(
        id: String,
        spotId: String,
        authorId: String,
        text: String,
        enqueuedAt: Long,
    ) {
        pendingPostsQueue.removeAll { it.id == id }
        pendingPostsQueue.add(PendingPostInsert(id, spotId, authorId, text, enqueuedAt))
    }

    override fun pendingPosts(): List<PendingPostInsert> =
        pendingPostsQueue.sortedBy { it.enqueuedAt }

    override fun dequeuePendingPost(id: String) { pendingPostsQueue.removeAll { it.id == id } }

    override fun updatePostVote(postId: String, newScore: Int, newMyVote: String) {
        val existing = posts[postId] ?: return
        posts[postId] = existing.copy(score = newScore, myVote = newMyVote)
    }

    override fun upsertPendingVote(postId: String, value: Int, enqueuedAt: Long) {
        pendingVotesQueue[postId] = PendingVote(postId, value, enqueuedAt)
    }

    override fun pendingVotes(): List<PendingVote> =
        pendingVotesQueue.values.sortedBy { it.enqueuedAt }

    override fun dequeuePendingVote(postId: String) { pendingVotesQueue.remove(postId) }
}
