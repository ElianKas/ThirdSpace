package com.example.thirdspace.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Offline-first repository for the CURRENT user's own profile (reads + the
 * Phase 2 EDIT/SAVE write path, [updateProfile]).
 * Mirrors [FeedRepository]'s synchronous, side-effect-free-read surface, but
 * with the SMALLEST offline story (an IN-MEMORY cached single row — NO
 * SQLDelight, NO `.sqm` migration): Phase 1 reads the real `profiles` row and
 * shows it; durable on-device caching is not needed for a single row that is
 * cheap to re-fetch on the next launch.
 *
 * - [current] is a PURE read of the cached row and triggers NO remote fetch
 *   (reads are side-effect-free, per the architecture convention). Returns
 *   `null` until the first successful [refresh] stores a row.
 * - [ownPosts] / [ownSpots] (Phase 4) are PURE reads of the current user's OWN
 *   posts / spots (what the profile's "meine Posts" / "meine Spots" tabs show,
 *   replacing the old global-feed derivation). Default empty; populated by the
 *   SAME [refresh] pass as the profile row (one `loadProfile` refreshes row +
 *   own posts + own spots together). No separate refresh hook is needed; the
 *   existing [onChanged] / bridge `observeProfile` covers them.
 * - [refresh] is the EXPLICIT remote pull (bridge `loadProfile` /
 *   pull-to-refresh): fetch the current user's row + own posts + own spots ->
 *   store -> notify [onChanged]. The spot/post COUNTS on the cached [Profile]
 *   are DERIVED from the own-list SIZES (so the stat and the list are always
 *   consistent, one fewer round-trip each than a separate COUNT query).
 *   Back-to-back refreshes COALESCE to one in-flight fetch via an in-flight
 *   guard under a [Mutex] (mirrors [FeedRepository.refresh]). The REAL
 *   follower/following counts (Phase F3) are ALSO fetched in the same pass (via
 *   the injected [followerCountProvider] / [followingCountProvider], which
 *   default to the remote-backed [SupabaseFollowRepository]'s counts) and
 *   folded onto the cached [Profile] — the same `.copy(...)` injection used for
 *   the spot/post counts. Best-effort: a failed count fetch keeps the prior
 *   value (defaulting 0), never crashes.
 * - BEST-EFFORT per sub-query: the own-row / own-posts / own-spots fetches are
 *   independent; a failure of one (e.g. own-spots throwing) does NOT wipe the
 *   others — each falls back to its prior cached value.
 *
 * All suspend / coroutine / supabase work happens on [scope]; none crosses to
 * Swift. [onChanged] is a flat listener the bridge wires to a Dispatchers.Main
 * scope so Swift can re-read the profile after a background refresh.
 *
 * NOT-CONFIGURED / OFFLINE / NO-SESSION: [refresh] with an unavailable remote
 * (or `fetchCurrentProfile` returning `null`) leaves [cached] at its prior
 * value (null before the first successful fetch); nothing throws. The bridge
 * renders a neutral, non-dummy [IosProfile] when [current] is `null`.
 */
class ProfileRepository internal constructor(
    private val scope: CoroutineScope,
    private val remote: ProfileRemoteSource = SupabaseProfileRepository(),
    private val isAuthenticated: () -> Boolean = { AuthRepository.isAuthenticated() },
    private val currentUserId: () -> String = { AuthRepository.currentUserId() },
    private val onChanged: () -> Unit = {},
    // Phase F3: REAL follower/following count sources for the current user,
    // folded into the cached Profile the same way spot/post counts are derived.
    // Injected as flat suspend `(uid) -> Int` seams (defaulting to the
    // supabase-kt-backed SupabaseFollowRepository's remote counts) so a FAKE can
    // drive them in tests WITHOUT touching ProfileRemoteSource / the follow
    // repository's wider surface. Best-effort: a throw / 0 keeps the prior value.
    private val followerCountProvider: suspend (String) -> Int =
        defaultFollowRemote::followerCount,
    private val followingCountProvider: suspend (String) -> Int =
        defaultFollowRemote::followingCount,
) {

    private companion object {
        // The default remote-backed follow-count source. A single shared
        // SupabaseFollowRepository instance whose suspend count reads are the
        // production defaults for followerCountProvider / followingCountProvider.
        // Unconfigured / offline degrades to 0 internally (never throws).
        private val defaultFollowRemote: FollowRemoteSource = SupabaseFollowRepository()
    }

    private val syncMutex = Mutex()

    // The in-memory cached single row (seed null; the bridge shows a neutral
    // state until the first successful refresh so the old hardcoded
    // "Elian Kasteleiner" dummy is never shown).
    private var cached: Profile? = null

    // Phase 4: the current user's OWN posts / spots (what the profile tabs show).
    // In-memory only, default empty; populated by the same refresh pass.
    private var cachedOwnPosts: List<FeedPost> = emptyList()
    private var cachedOwnSpots: List<Spot> = emptyList()

    // In-flight guard so concurrent/back-to-back refreshes coalesce to a single
    // remote fetch rather than stacking (mirrors FeedRepository).
    private var refreshing = false

    /**
     * PURE cache read — returns the cached profile row (or `null` before the
     * first successful [refresh]) and triggers NO remote fetch. Use [refresh]
     * for an explicit remote pull.
     */
    fun current(): Profile? = cached

    /**
     * PURE read of the current user's OWN posts (Phase 4) — the profile's
     * "meine Posts" tab. Triggers NO remote fetch; empty until the first
     * successful [refresh]. Refreshed by the same [refresh] pass as [current].
     */
    fun ownPosts(): List<FeedPost> = cachedOwnPosts

    /**
     * PURE read of the current user's OWN spots (Phase 4) — the profile's
     * "meine Spots" tab. Triggers NO remote fetch; empty until the first
     * successful [refresh]. Refreshed by the same [refresh] pass as [current].
     */
    fun ownSpots(): List<Spot> = cachedOwnSpots

    /** Explicit remote refresh trigger (bridge loadProfile / pull-to-refresh). */
    fun refresh() { scope.launch { refreshFromRemote() } }

    /**
     * SIGN-OUT RESET: synchronously empty the in-memory cache so a DIFFERENT
     * user signing in on the same app launch can never briefly see the previous
     * user's data. Resets [cached] to `null` and both own-content lists to
     * empty — the exact neutral/empty state the pure reads ([current] /
     * [ownPosts] / [ownSpots]) report before the first successful [refresh] —
     * then fires [onChanged] once so observers (the iOS `observeProfile`
     * listener) re-read the now-neutral snapshot.
     *
     * This is a WRITE/reset, NOT a read, so it intentionally mutates state;
     * [current]/[ownPosts]/[ownSpots] stay side-effect-free. It is synchronous
     * and pure (NO remote fetch): the fields it touches are the same plain
     * `var`s the refresh/update path mutates, so a direct reset is consistent
     * with how they are otherwise written. Necessary because the neutral bridge
     * fallback only fires while [current] is `null`, which never recurs after
     * the first fetch — so without this reset the prior user's row lingers.
     */
    fun clear() {
        cached = null
        cachedOwnPosts = emptyList()
        cachedOwnSpots = emptyList()
        onChanged()
    }

    /**
     * Background refresh: fetch the current user's row + OWN posts + OWN spots
     * (Phase 4), store them, and notify ONCE. Serves the existing caches
     * unchanged when the remote is unavailable, unauthenticated, or returns no
     * data. Swallows errors (offline/transient) so a failed refresh never
     * crashes.
     *
     * BEST-EFFORT per sub-query: the own-row fetch and the two own-content
     * fetches are independent. The own-content fetches are already best-effort
     * internally (they return an empty list on throw), but each is additionally
     * guarded here so that if one throws, the others still apply and the prior
     * cached value for the failing one is kept. Only a SINGLE [onChanged] fires
     * per refresh (after all parts are applied) so Swift re-reads once.
     *
     * COUNTS: the cached [Profile]'s spot/post counts are DERIVED from the
     * freshly fetched own-list sizes (consistent with the lists the tabs show).
     */
    private suspend fun refreshFromRemote() {
        if (!remote.isAvailable() || !isAuthenticated()) return
        val uid = currentUserId()
        syncMutex.withLock {
            // Coalesce: if a refresh is already running, skip. Reset in finally
            // so later refreshes work.
            if (refreshing) return@withLock
            refreshing = true
            var changed = false
            try {
                // Fetch the OWN content first so the derived counts can be folded
                // into the profile row when it is stored. Each is best-effort:
                // a thrown fetch keeps the prior cached list (and prior count).
                val fetchedPosts = runCatchingOrNull { remote.fetchOwnPosts(uid) }
                if (fetchedPosts != null) {
                    cachedOwnPosts = fetchedPosts
                    changed = true
                }
                val fetchedSpots = runCatchingOrNull { remote.fetchOwnSpots(uid) }
                if (fetchedSpots != null) {
                    cachedOwnSpots = fetchedSpots
                    changed = true
                }

                // Fetch the REAL follower/following counts for the current user
                // (Phase F3). Best-effort per count: a thrown fetch keeps the
                // prior cached value (the prior Profile's count, defaulting 0).
                // These are folded into the cached Profile below the same way
                // spot/post counts are. Done before the row fetch so both the
                // fresh-row and keep-prior-row branches can use the latest value.
                val priorFollowers = cached?.followerCount ?: 0
                val priorFollowing = cached?.followingCount ?: 0
                val fetchedFollowers = runCatchingOrNull { followerCountProvider(uid) } ?: priorFollowers
                val fetchedFollowing = runCatchingOrNull { followingCountProvider(uid) } ?: priorFollowing

                // Fetch the profile row, injecting the DERIVED spot/post counts
                // from the (now current) own-list sizes AND the real
                // follower/following counts. Best-effort: a thrown fetch keeps
                // the prior cached row (but still folds the fresh follow counts
                // onto it, so a count change without a row change still shows).
                val fetched = runCatchingOrNull { remote.fetchCurrentProfile() }
                if (fetched != null) {
                    cached = fetched.copy(
                        spotCount = cachedOwnSpots.size,
                        postCount = cachedOwnPosts.size,
                        followerCount = fetchedFollowers,
                        followingCount = fetchedFollowing,
                    )
                    changed = true
                } else {
                    // No fresh row, but fold the (possibly updated) follow counts
                    // onto the prior cached row so a count refresh is not lost.
                    val prior = cached
                    if (prior != null &&
                        (prior.followerCount != fetchedFollowers ||
                            prior.followingCount != fetchedFollowing)
                    ) {
                        cached = prior.copy(
                            followerCount = fetchedFollowers,
                            followingCount = fetchedFollowing,
                        )
                        changed = true
                    }
                }
            } finally {
                refreshing = false
            }
            if (changed) onChanged()
        }
    }

    /**
     * Run [block] and return its result, or `null` if it threw. Used so one
     * failing sub-query (own-row / own-posts / own-spots) degrades gracefully
     * without aborting the others (best-effort per part).
     */
    private inline fun <T> runCatchingOrNull(block: () -> T): T? =
        try { block() } catch (_: Throwable) { null }

    /**
     * EDIT/SAVE of the own profile (Phase 2): optimistically apply [name] /
     * [city] / [bio] to the in-memory cached row, notify observers, then
     * best-effort push the UPDATE to the backend and report the outcome via
     * [onResult] (a flat `Boolean`).
     *
     * ORDER OF EFFECTS:
     * 1. OPTIMISTIC local update FIRST: copy [cached] with the new values (or,
     *    when nothing is cached yet, construct a minimal row from the passed
     *    values so the edit is still reflected). Then fire [onChanged] so the
     *    SwiftUI profile re-reads immediately — independent of the remote.
     * 2. BACKGROUND remote push on [scope]: when the remote is available AND
     *    there is a session, call [ProfileRemoteSource.updateProfile]; on
     *    success reconcile authoritative values via [refreshFromRemote]
     *    (updated_at, server-side normalizations); deliver `true`. Any thrown
     *    transport/REST error is caught and reported as `false`.
     *
     * OFFLINE / UNAVAILABLE / UNAUTH behavior (DOCUMENTED — there is NO durable
     * write-queue for the profile; a SQLDelight profile queue is deliberately
     * OUT OF SCOPE this phase):
     * - The optimistic in-memory change is KEPT for the session (so the UI does
     *   not visibly lose the user's edit), BUT [onResult] reports `false`
     *   because the change was NOT persisted remotely. We never claim success
     *   when offline/unauth — the caller can surface the failure. The edit is
     *   NOT durably queued; a relaunch re-reads the authoritative remote row.
     *
     * CONCURRENCY: the remote push + reconciling refresh run under [syncMutex]
     * (the same lock [refreshFromRemote] uses) so a save does not race a
     * concurrent refresh. The optimistic local update + [onChanged] happen
     * BEFORE the lock so the UI reflects the edit immediately.
     *
     * All suspend/supabase work stays on [scope]; [onResult] is a flat closure
     * the bridge hops onto Dispatchers.Main — no suspend/supabase type crosses.
     */
    fun updateProfile(name: String, city: String, bio: String, onResult: (Boolean) -> Unit) {
        // 1. Optimistic local update + notify, independent of the remote result.
        val base = cached
        cached = base?.copy(name = name, city = city, bio = bio)
            ?: Profile(
                name = name,
                username = ProfileMapping.deriveUsername(AuthRepository.currentUserEmail(), name),
                city = city,
                monthYear = "",
                bio = bio,
                email = AuthRepository.currentUserEmail(),
                spotCount = 0,
                postCount = 0,
                followerCount = 0,
                followingCount = 0,
            )
        onChanged()

        // 2. Best-effort remote push on the background scope.
        scope.launch {
            // Offline / unconfigured / no session: keep the optimistic local
            // change, but report failure — NO durable queue, no silent success.
            if (!remote.isAvailable() || !isAuthenticated()) {
                onResult(false)
                return@launch
            }
            val ok = try {
                syncMutex.withLock {
                    remote.updateProfile(currentUserId(), name, city, bio)
                }
            } catch (_: Throwable) {
                // Transport/REST error: optimistic local value stays; report false.
                false
            }
            if (ok) {
                // Reconcile authoritative values (updated_at etc.). Refresh has
                // its own coalesce/lock; it is a no-op if a refresh is running.
                refreshFromRemote()
            }
            onResult(ok)
        }
    }
}

/**
 * The process-wide profile repository provider, mirroring [Spots] / [Feed].
 *
 * Named [Profiles] (plural) to avoid clashing with the [Profile] data class,
 * and for symmetry with [Spots]. Builds a [ProfileRepository] over the
 * supabase-kt [SupabaseProfileRepository], running background refresh on a
 * shared [Dispatchers.Default] scope.
 *
 * LISTENER: [setChangeListener] lets the iOS bridge register a flat callback
 * (delivered on its own Dispatchers.Main scope) that fires after a background
 * refresh changes the cached row, so Swift can re-read `FeedBridge.profile()`.
 */
object Profiles {

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var changeListener: () -> Unit = {}

    val repository: ProfileRepository by lazy {
        ProfileRepository(scope = backgroundScope, onChanged = { changeListener() })
    }

    /**
     * Register a flat [listener] invoked after a background refresh changes the
     * cached profile. The bridge wraps this to hop onto Dispatchers.Main.
     */
    fun setChangeListener(listener: () -> Unit) {
        changeListener = listener
    }

    /** Explicit remote refresh trigger for the bridge (loadProfile). */
    fun refresh() { repository.refresh() }

    /**
     * SIGN-OUT RESET for the bridge (clearProfile): forward to
     * [ProfileRepository.clear] so the in-memory cache is emptied on sign-out
     * and a different user never sees the prior user's profile/posts/spots.
     * The repository's [onChanged] fires, which the bridge's `observeProfile`
     * wiring hops onto the main thread.
     */
    fun clear() { repository.clear() }

    /**
     * Forward an EDIT/SAVE of the own profile (name/city/bio) to the
     * [repository] for the bridge's flat `updateProfile` hook. [onResult] is a
     * flat `Boolean` the bridge hops onto Dispatchers.Main.
     */
    fun updateProfile(name: String, city: String, bio: String, onResult: (Boolean) -> Unit) {
        repository.updateProfile(name, city, bio, onResult)
    }
}
