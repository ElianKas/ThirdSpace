package com.example.thirdspace.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Offline-first repository for the social graph (follows) — Phase F2.
 * Mirrors [ProfileRepository] / [FeedRepository]'s synchronous,
 * side-effect-free-read surface with the SMALLEST offline story (IN-MEMORY
 * caches only — NO SQLDelight, NO `.sqm` migration; nothing crosses the
 * FeedBridge boundary yet).
 *
 * READS are PURE (trigger NO remote fetch), per the architecture convention:
 * - [followersOf] / [followingOf] — the cached follower/following lists for a
 *   user (empty before the first refresh).
 * - [isFollowing] — whether the current user follows a target (false before any
 *   refresh / optimistic follow).
 * - [followerCount] / [followingCount] — the cached counts (0 before refresh).
 *
 * REFRESHES are the EXPLICIT remote pulls (what a bridge / pull-to-refresh will
 * call later), each on [scope] with an in-flight coalesce guard under a [Mutex]
 * (mirrors [ProfileRepository.refresh] / [FeedRepository.refresh]). Each gates
 * on `remote.isAvailable() && isAuthenticated()`, is best-effort (a thrown
 * sub-query keeps the prior cached value), and fires [onChanged] once when
 * something changed:
 * - [refreshFollowers] / [refreshFollowing] — the lists for a user.
 * - [refreshFollowState] — whether the current user follows a target.
 * - [refreshCounts] — a user's follower + following counts.
 *
 * WRITES ([follow] / [unfollow]) are OPTIMISTIC: the in-memory follow-state +
 * the relevant cached counts update FIRST and [onChanged] fires, THEN a
 * best-effort remote push runs on [scope]. There is NO durable write-queue
 * (mirrors [ProfileRepository.updateProfile]'s documented no-queue behavior):
 * offline / unauthenticated KEEPS the optimistic state for the session but
 * reports `false` via the flat `onResult` callback (never a silent success).
 * A self-follow (target == current user) is a no-op reporting `false`.
 *
 * All suspend / coroutine / supabase work happens on [scope]; none crosses to
 * Swift. [onChanged] is a flat listener a later bridge wires to a
 * Dispatchers.Main scope so Swift can re-read after a background change.
 */
class FollowRepository internal constructor(
    private val scope: CoroutineScope,
    private val remote: FollowRemoteSource = SupabaseFollowRepository(),
    private val isAuthenticated: () -> Boolean = { AuthRepository.isAuthenticated() },
    private val currentUserId: () -> String = { AuthRepository.currentUserId() },
    private val onChanged: () -> Unit = {},
) {

    private val syncMutex = Mutex()

    // In-memory caches (all guarded via syncMutex on the refresh/push paths; the
    // optimistic write path mutates the state/counts maps directly before the
    // lock, consistent with ProfileRepository's optimistic update).
    private val followersByUser = mutableMapOf<String, List<FollowUser>>()
    private val followingByUser = mutableMapOf<String, List<FollowUser>>()
    private val followStateByTarget = mutableMapOf<String, Boolean>()
    private val followerCountByUser = mutableMapOf<String, Int>()
    private val followingCountByUser = mutableMapOf<String, Int>()

    // In-flight guard so concurrent/back-to-back refreshes coalesce to a single
    // remote pass rather than stacking (mirrors ProfileRepository/FeedRepository).
    private var refreshing = false

    // --- PURE reads (NO remote fetch) ---

    /** Cached followers of [userId] (empty before the first [refreshFollowers]). */
    fun followersOf(userId: String): List<FollowUser> = followersByUser[userId].orEmpty()

    /** Cached following list of [userId] (empty before the first [refreshFollowing]). */
    fun followingOf(userId: String): List<FollowUser> = followingByUser[userId].orEmpty()

    /**
     * Whether the CURRENT user follows [targetId] (cached). `false` before any
     * [refreshFollowState] or optimistic [follow]. PURE — no remote fetch.
     */
    fun isFollowing(targetId: String): Boolean = followStateByTarget[targetId] ?: false

    /** Cached follower count of [userId] (0 before the first [refreshCounts]). */
    fun followerCount(userId: String): Int = followerCountByUser[userId] ?: 0

    /** Cached following count of [userId] (0 before the first [refreshCounts]). */
    fun followingCount(userId: String): Int = followingCountByUser[userId] ?: 0

    // --- EXPLICIT refresh triggers ---

    /** Explicit remote refresh of [userId]'s FOLLOWERS list. */
    fun refreshFollowers(userId: String) {
        scope.launch { refreshList(userId, following = false) }
    }

    /** Explicit remote refresh of [userId]'s FOLLOWING list. */
    fun refreshFollowing(userId: String) {
        scope.launch { refreshList(userId, following = true) }
    }

    /** Explicit remote refresh of whether the CURRENT user follows [targetId]. */
    fun refreshFollowState(targetId: String) {
        scope.launch { refreshStateFromRemote(targetId) }
    }

    /** Explicit remote refresh of [userId]'s follower + following COUNTS. */
    fun refreshCounts(userId: String) {
        scope.launch { refreshCountsFromRemote(userId) }
    }

    /**
     * Background refresh of ONE list (followers or following) for [userId].
     * Gated on availability + auth; best-effort (a throw keeps the prior cached
     * list); coalesced with any in-flight refresh; fires [onChanged] once when
     * the list changed.
     */
    private suspend fun refreshList(userId: String, following: Boolean) {
        if (!remote.isAvailable() || !isAuthenticated()) return
        if (userId.isEmpty()) return
        val viewer = currentUserId()
        syncMutex.withLock {
            if (refreshing) return@withLock
            refreshing = true
            var changed = false
            try {
                val fetched = runCatchingOrNull {
                    if (following) remote.fetchFollowing(userId, viewer)
                    else remote.fetchFollowers(userId, viewer)
                }
                if (fetched != null) {
                    if (following) followingByUser[userId] = fetched
                    else followersByUser[userId] = fetched
                    changed = true
                }
            } finally {
                refreshing = false
            }
            if (changed) onChanged()
        }
    }

    /**
     * Background refresh of whether the current user follows [targetId].
     * Gated + best-effort + coalesced; fires [onChanged] once when the cached
     * state changed.
     */
    private suspend fun refreshStateFromRemote(targetId: String) {
        if (!remote.isAvailable() || !isAuthenticated()) return
        if (targetId.isEmpty()) return
        val viewer = currentUserId()
        if (viewer.isEmpty() || viewer == targetId) return
        syncMutex.withLock {
            if (refreshing) return@withLock
            refreshing = true
            var changed = false
            try {
                val following = runCatchingOrNull { remote.isFollowing(viewer, targetId) }
                if (following != null && followStateByTarget[targetId] != following) {
                    followStateByTarget[targetId] = following
                    changed = true
                }
            } finally {
                refreshing = false
            }
            if (changed) onChanged()
        }
    }

    /**
     * Background refresh of [userId]'s follower + following counts. Each count
     * is an independent best-effort sub-query (a throw keeps the prior cached
     * value); coalesced; fires [onChanged] once when either count changed.
     */
    private suspend fun refreshCountsFromRemote(userId: String) {
        if (!remote.isAvailable() || !isAuthenticated()) return
        if (userId.isEmpty()) return
        syncMutex.withLock {
            if (refreshing) return@withLock
            refreshing = true
            var changed = false
            try {
                val followers = runCatchingOrNull { remote.followerCount(userId) }
                if (followers != null && followerCountByUser[userId] != followers) {
                    followerCountByUser[userId] = followers
                    changed = true
                }
                val following = runCatchingOrNull { remote.followingCount(userId) }
                if (following != null && followingCountByUser[userId] != following) {
                    followingCountByUser[userId] = following
                    changed = true
                }
            } finally {
                refreshing = false
            }
            if (changed) onChanged()
        }
    }

    // --- Optimistic writes ---

    /**
     * Follow [targetId]: OPTIMISTIC in-memory update FIRST (set the follow
     * state true and bump the relevant cached counts), fire [onChanged], THEN
     * best-effort remote push on [scope]. Reports the remote outcome via
     * [onResult] (flat `Boolean`).
     *
     * A self-follow (target == current user) is a no-op reporting `false`.
     * A no-op is also reported when already following (state already true).
     * Offline / unauthenticated KEEPS the optimistic state but reports `false`
     * (NO durable queue — mirrors [ProfileRepository.updateProfile]).
     */
    fun follow(targetId: String, onResult: (Boolean) -> Unit) =
        setFollow(targetId, follow = true, onResult)

    /**
     * Unfollow [targetId]: OPTIMISTIC in-memory update FIRST (clear the follow
     * state and decrement the relevant cached counts, floored at 0), fire
     * [onChanged], THEN best-effort remote push. Same self / offline / no-queue
     * semantics as [follow].
     */
    fun unfollow(targetId: String, onResult: (Boolean) -> Unit) =
        setFollow(targetId, follow = false, onResult)

    private fun setFollow(targetId: String, follow: Boolean, onResult: (Boolean) -> Unit) {
        val me = currentUserId()
        // Self-follow (or an empty target / no session) is a no-op reporting false.
        if (targetId.isEmpty() || me.isEmpty() || targetId == me) {
            onResult(false)
            return
        }
        // No-op if already in the desired state (avoids a double count bump).
        val currentState = followStateByTarget[targetId] ?: false
        if (currentState == follow) {
            onResult(false)
            return
        }

        // 1. OPTIMISTIC local update + notify, independent of the remote result.
        followStateByTarget[targetId] = follow
        val delta = if (follow) 1 else -1
        // My following count changes (I follow one more / fewer user).
        followingCountByUser[me] = maxOf(0, (followingCountByUser[me] ?: 0) + delta)
        // The target's follower count changes (one more / fewer follower).
        followerCountByUser[targetId] = maxOf(0, (followerCountByUser[targetId] ?: 0) + delta)
        onChanged()

        // 2. Best-effort remote push on the background scope.
        scope.launch {
            // Offline / unconfigured / no session: keep the optimistic change,
            // but report failure — NO durable queue, no silent success.
            if (!remote.isAvailable() || !isAuthenticated()) {
                onResult(false)
                return@launch
            }
            val ok = try {
                syncMutex.withLock {
                    if (follow) remote.follow(me, targetId)
                    else remote.unfollow(me, targetId)
                }
            } catch (_: Throwable) {
                false
            }
            onResult(ok)
        }
    }

    /**
     * Run [block] and return its result, or `null` if it threw. Used so one
     * failing sub-query degrades gracefully without aborting the others
     * (best-effort per part). Mirrors [ProfileRepository.runCatchingOrNull].
     */
    private inline fun <T> runCatchingOrNull(block: () -> T): T? =
        try { block() } catch (_: Throwable) { null }
}

/**
 * The process-wide follow repository provider, mirroring [Profiles] / [Feed] /
 * [Spots]. Builds a [FollowRepository] over the supabase-kt
 * [SupabaseFollowRepository], running background work on a shared
 * [Dispatchers.Default] scope.
 *
 * LISTENER: [setChangeListener] lets a later iOS bridge register a flat
 * callback (delivered on its own Dispatchers.Main scope) that fires after a
 * background change, so Swift can re-read. THE BRIDGE IS NOT WIRED IN THIS
 * PHASE — these forwarders exist for the later phase to call.
 */
object Follows {

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var changeListener: () -> Unit = {}

    val repository: FollowRepository by lazy {
        FollowRepository(scope = backgroundScope, onChanged = { changeListener() })
    }

    /**
     * Register a flat [listener] invoked after a background change. A later
     * bridge wraps this to hop onto Dispatchers.Main.
     */
    fun setChangeListener(listener: () -> Unit) {
        changeListener = listener
    }

    /** Forward an optimistic FOLLOW to the [repository] (for the later bridge). */
    fun follow(targetId: String, onResult: (Boolean) -> Unit) {
        repository.follow(targetId, onResult)
    }

    /** Forward an optimistic UNFOLLOW to the [repository] (for the later bridge). */
    fun unfollow(targetId: String, onResult: (Boolean) -> Unit) {
        repository.unfollow(targetId, onResult)
    }

    /** Explicit refresh forwarders (for the later bridge). */
    fun refreshFollowers(userId: String) { repository.refreshFollowers(userId) }
    fun refreshFollowing(userId: String) { repository.refreshFollowing(userId) }
    fun refreshFollowState(targetId: String) { repository.refreshFollowState(targetId) }
    fun refreshCounts(userId: String) { repository.refreshCounts(userId) }
}
