package com.example.thirdspace.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OFFLINE-provable logic tests for [ProfileRepository], the [ProfileMapping]
 * DTO<->Profile derivation, and the side-effect-free-read / coalesced-refresh
 * contract — all WITHOUT a real backend, network, device, or SQLDelight driver.
 *
 * These run on BOTH shared targets (Android host + iOS simulator) because they
 * use an in-memory FAKE [ProfileRemoteSource] (no JVM-only driver). The REAL
 * Supabase round-trip (reading the logged-in user's `profiles` row) is
 * BLOCKED-until-session (needs the user's keys + migrations + a live login) and
 * is NOT faked as a network round-trip here; these fakes are the automated
 * proof of the mapping + read/refresh behavior.
 *
 * The background refresh runs on a [Dispatchers.Unconfined] scope so the
 * launched coroutine executes synchronously up to its first real suspension;
 * the fake remote's suspend function never actually suspends (except the gated
 * coalesce test), so assertions after a refresh() call observe the completed
 * background work deterministically — mirroring CachingSpotRepositoryTest.
 */
class ProfileRepositoryTest {

    /** Controllable fake remote: records fetch/update calls, returns a canned row. */
    private class FakeRemote(
        var available: Boolean = true,
        var result: Profile? = null,
        // Phase 2: controllable UPDATE behavior.
        var updateResult: Boolean = true,
        var updateThrows: Boolean = false,
        // Phase 4: controllable OWN-content behavior. The lists are returned for
        // the requested uid (so per-user behavior can be asserted); set a throw
        // flag to prove best-effort degradation of one sub-query.
        var ownPosts: List<FeedPost> = emptyList(),
        var ownSpots: List<Spot> = emptyList(),
        var ownPostsThrows: Boolean = false,
        var ownSpotsThrows: Boolean = false,
    ) : ProfileRemoteSource {
        var fetchCount = 0
        var updateCount = 0
        var ownPostsCount = 0
        var ownSpotsCount = 0
        // Last uid the own-content fetches were called with (null until called).
        var lastOwnPostsUid: String? = null
        var lastOwnSpotsUid: String? = null
        // Last recorded update args (null until updateProfile is called).
        var lastUpdate: Update? = null

        data class Update(val userId: String, val name: String, val city: String, val bio: String)

        override fun isAvailable(): Boolean = available
        override suspend fun fetchCurrentProfile(): Profile? {
            fetchCount++
            return result
        }

        override suspend fun updateProfile(
            userId: String,
            name: String,
            city: String,
            bio: String,
        ): Boolean {
            updateCount++
            lastUpdate = Update(userId, name, city, bio)
            if (updateThrows) throw RuntimeException("boom")
            return updateResult
        }

        override suspend fun fetchOwnPosts(userId: String): List<FeedPost> {
            ownPostsCount++
            lastOwnPostsUid = userId
            if (ownPostsThrows) throw RuntimeException("boom-posts")
            return ownPosts
        }

        override suspend fun fetchOwnSpots(userId: String): List<Spot> {
            ownSpotsCount++
            lastOwnSpotsUid = userId
            if (ownSpotsThrows) throw RuntimeException("boom-spots")
            return ownSpots
        }
    }

    // --- Phase 4 test fixtures: canned own posts / spots ---

    private fun ownPost(id: String, text: String = "hi") = FeedPost(
        id = id,
        authorName = "Ada Lovelace",
        spotName = "Café",
        category = "Café",
        neighborhood = "Mitte",
        verified = false,
        text = text,
        createdAtEpoch = 0L,
        score = 0,
        commentCount = 0,
        myVote = "NONE",
    )

    private fun ownSpot(id: String, name: String = "Café") = Spot(
        id = id,
        name = name,
        category = "Café",
        neighborhood = "Mitte",
        verified = false,
        checkInCount = 0,
        placeholderColorArgb = 0L,
        lat = 0.0,
        lng = 0.0,
    )

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun profileRow(
        name: String = "Ada Lovelace",
        email: String = "Ada@Thirdspace.App",
        city: String = "Berlin",
        bio: String = "Rechnen mit Karten.",
        createdAt: String = "2024-03-15T12:30:00Z",
        spotCount: Int = 0,
        postCount: Int = 0,
    ) = ProfileMapping.toProfile(
        ProfileDto(
            id = "u-1",
            name = name,
            email = email,
            city = city,
            bio = bio,
            createdAt = createdAt,
        ),
        spotCount = spotCount,
        postCount = postCount,
    )

    // --- ProfileMapping: DTO -> Profile derivation (pure) ---

    @Test
    fun mappingDerivesUsernameMonthYearAndZeroCounts() {
        val profile = ProfileMapping.toProfile(
            ProfileDto(
                id = "u-1",
                name = "Ada Lovelace",
                email = "Ada@Thirdspace.App",
                city = "Berlin",
                bio = "Rechnen mit Karten.",
                createdAt = "2024-03-15T12:30:00Z",
            ),
        )
        // Pass-through fields.
        assertEquals("Ada Lovelace", profile.name)
        assertEquals("Berlin", profile.city)
        assertEquals("Rechnen mit Karten.", profile.bio)
        assertEquals("Ada@Thirdspace.App", profile.email)
        // username = email local-part, lowercased.
        assertEquals("ada", profile.username)
        // monthYear = non-translated German "Monat Jahr" from created_at.
        assertEquals("März 2024", profile.monthYear)
        // The no-arg (PURE, count-free) mapping still yields 0s — Phase-1
        // behavior is intentionally UNCHANGED. Real spot/post counts arrive via
        // the counted toProfile overload (see mappingWithCounts... below).
        assertEquals(0, profile.spotCount)
        assertEquals(0, profile.postCount)
        assertEquals(0, profile.followerCount)
        assertEquals(0, profile.followingCount)
    }

    @Test
    fun mappingWithCountsInjectsSpotAndPostButKeepsFollowZero() {
        // Phase 3: the counted overload injects the server-side spot/post counts
        // while follower/following stay 0 (social graph deferred).
        val profile = ProfileMapping.toProfile(
            ProfileDto(
                id = "u-1",
                name = "Ada Lovelace",
                email = "Ada@Thirdspace.App",
                city = "Berlin",
                bio = "Rechnen mit Karten.",
                createdAt = "2024-03-15T12:30:00Z",
            ),
            spotCount = 7,
            postCount = 12,
        )
        // Derived fields are identical to the no-arg mapping.
        assertEquals("ada", profile.username)
        assertEquals("März 2024", profile.monthYear)
        // Real counts injected.
        assertEquals(7, profile.spotCount)
        assertEquals(12, profile.postCount)
        // Social graph stays deferred.
        assertEquals(0, profile.followerCount)
        assertEquals(0, profile.followingCount)
    }

    @Test
    fun usernameFallsBackToNameWhenEmailHasNoLocalPart() {
        assertEquals("ada", ProfileMapping.deriveUsername(email = "ada@x.io", name = "Ignored"))
        // No '@' local part -> fall back to lowercased name.
        assertEquals("ada lovelace", ProfileMapping.deriveUsername(email = "", name = "Ada Lovelace"))
        // Nothing usable -> empty (never crashes the UI).
        assertEquals("", ProfileMapping.deriveUsername(email = "", name = ""))
    }

    @Test
    fun monthYearIsEmptyForUnparseableCreatedAt() {
        assertEquals("", RelativeTime.formatMonthYear(""))
        assertEquals("", RelativeTime.formatMonthYear("not-a-date"))
        // A known UTC timestamp in a different month.
        assertEquals("Dezember 2023", RelativeTime.formatMonthYear("2023-12-01T00:00:00Z"))
    }

    // --- refresh() stores the row; current() returns it ---

    @Test
    fun refreshStoresRowAndCurrentReturnsIt() {
        val remote = FakeRemote(available = true, result = profileRow())
        var notified = 0
        val repo = ProfileRepository(
            scope = scope, remote = remote,
            isAuthenticated = { true }, onChanged = { notified++ },
        )
        // Nothing cached before the first refresh.
        assertNull(repo.current())
        repo.refresh()
        val current = repo.current()
        assertEquals("Ada Lovelace", current?.name)
        assertEquals("ada", current?.username)
        assertEquals("März 2024", current?.monthYear)
        assertEquals(0, current?.spotCount)
        assertTrue(notified >= 1)
    }

    @Test
    fun refreshDerivesSpotAndPostCountsFromOwnListSizesAndKeepsFollowZero() {
        // Phase 4: the cached profile's spot/post counts are DERIVED from the
        // fetched own-list SIZES (not a server-side COUNT query). refresh() ->
        // current() must reflect ownSpots.size / ownPosts.size, with
        // follower/following still 0. Note the row's own count fields are
        // ignored here — the derivation is authoritative.
        val remote = FakeRemote(
            available = true,
            result = profileRow(spotCount = 999, postCount = 999),
            ownSpots = (1..5).map { ownSpot("s-$it") },
            ownPosts = (1..9).map { ownPost("p-$it") },
        )
        val repo = ProfileRepository(
            scope = scope, remote = remote, isAuthenticated = { true },
        )
        repo.refresh()
        val current = repo.current()
        assertEquals(5, current?.spotCount)
        assertEquals(9, current?.postCount)
        assertEquals(0, current?.followerCount)
        assertEquals(0, current?.followingCount)
    }

    // --- Reads are side-effect-free; refresh is explicit + coalesced ---

    @Test
    fun currentNeverTriggersRemoteFetchEvenRepeatedly() {
        val remote = FakeRemote(available = true, result = profileRow())
        val repo = ProfileRepository(
            scope = scope, remote = remote, isAuthenticated = { true },
        )
        repeat(5) { repo.current() }
        // Pure read: ZERO fetches.
        assertEquals(0, remote.fetchCount)
        assertNull(repo.current())
    }

    @Test
    fun refreshTriggersExactlyOneFetch() {
        val remote = FakeRemote(available = true, result = profileRow())
        val repo = ProfileRepository(
            scope = scope, remote = remote, isAuthenticated = { true },
        )
        repo.refresh()
        assertEquals(1, remote.fetchCount)
    }

    @Test
    fun backToBackRefreshCoalescesToOneInFlightFetch() {
        // Gated fake: the first fetch stays in flight (suspended on the gate)
        // so a second refresh cannot start a concurrent fetch (in-flight guard
        // + syncMutex coalesce). Mirrors CachingSpotRepositoryTest.
        val gate = CompletableDeferred<Unit>()
        val remote = object : ProfileRemoteSource {
            var ownPostsCount = 0
            override fun isAvailable() = true
            override suspend fun fetchCurrentProfile(): Profile? = null
            override suspend fun updateProfile(
                userId: String,
                name: String,
                city: String,
                bio: String,
            ): Boolean = true
            // The own-posts fetch runs FIRST in the refresh pass and is the gated
            // suspension point, so a second refresh cannot start while the first
            // is in flight (in-flight guard + syncMutex coalesce).
            override suspend fun fetchOwnPosts(userId: String): List<FeedPost> {
                ownPostsCount++
                gate.await()
                return emptyList()
            }
            override suspend fun fetchOwnSpots(userId: String): List<Spot> = emptyList()
        }
        val repo = ProfileRepository(
            scope = scope, remote = remote, isAuthenticated = { true },
        )
        repo.refresh()
        repo.refresh()
        assertEquals(1, remote.ownPostsCount)
        gate.complete(Unit)
    }

    // --- Offline / unconfigured / no-session: no crash, prior value kept ---

    @Test
    fun refreshWithUnavailableRemoteLeavesCurrentUnchanged() {
        val remote = FakeRemote(available = false, result = profileRow())
        val repo = ProfileRepository(
            scope = scope, remote = remote, isAuthenticated = { true },
        )
        repo.refresh()
        // Unavailable -> no fetch, cached stays null (neutral), no crash.
        assertEquals(0, remote.fetchCount)
        assertNull(repo.current())
    }

    @Test
    fun refreshWhenUnauthenticatedDoesNotFetch() {
        val remote = FakeRemote(available = true, result = profileRow())
        val repo = ProfileRepository(
            scope = scope, remote = remote, isAuthenticated = { false },
        )
        repo.refresh()
        assertEquals(0, remote.fetchCount)
        assertNull(repo.current())
    }

    @Test
    fun refreshAfterAuthBecomesAvailablePopulatesProfile() {
        // The relaunch-restore bug contract: at launch the session is not yet
        // restored, so the first refresh runs UNAUTHENTICATED and must no-op
        // (no fetch, cached stays null -> the bridge shows the neutral profile).
        // Once the async session restore completes, the Swift onChange hook
        // re-triggers refresh with a now-present uid; THAT refresh must fetch
        // and populate the profile. This proves the empty-then-present-uid
        // retry the Swift `onChange(of: session.isAuthenticated)` relies on.
        var authed = false
        val remote = FakeRemote(
            available = true,
            result = profileRow(name = "Ada Lovelace"),
            ownSpots = (1..3).map { ownSpot("s-$it") },
            ownPosts = (1..2).map { ownPost("p-$it") },
        )
        val repo = ProfileRepository(
            scope = scope, remote = remote, isAuthenticated = { authed },
        )

        // 1. Pre-restore: unauthenticated -> no-op, nothing cached, no fetch.
        repo.refresh()
        assertNull(repo.current())
        assertEquals(0, remote.fetchCount)

        // 2. Session restore completes -> uid now present -> retry refresh.
        authed = true
        repo.refresh()

        // Now the profile is populated (name + derived counts) and the fetch ran.
        val current = repo.current()
        assertEquals("Ada Lovelace", current?.name)
        assertEquals(3, current?.spotCount)
        assertEquals(2, current?.postCount)
        assertEquals(1, remote.fetchCount)
    }

    @Test
    fun refreshThatReturnsNullKeepsPriorCachedValue() {
        val good = profileRow(name = "Ada Lovelace")
        val remote = FakeRemote(available = true, result = good)
        val repo = ProfileRepository(
            scope = scope, remote = remote, isAuthenticated = { true },
        )
        repo.refresh()
        assertEquals("Ada Lovelace", repo.current()?.name)
        // A later refresh that finds no row must NOT wipe the good cached value.
        remote.result = null
        repo.refresh()
        assertEquals("Ada Lovelace", repo.current()?.name)
    }

    // --- Phase 2: updateProfile (EDIT/SAVE of name/city/bio) ---

    @Test
    fun updateProfileSuccessUpdatesCachedRecordsRemoteArgsAndReportsTrue() {
        // Seed a cached row so the optimistic .copy path is exercised. The fake
        // remote returns the SAME row on the reconciling refresh so the
        // optimistic values survive (a real backend would echo them back).
        val seeded = profileRow(name = "Ada Lovelace", city = "Berlin", bio = "Alt.")
        val remote = FakeRemote(available = true, result = seeded, updateResult = true)
        var notified = 0
        val repo = ProfileRepository(
            scope = scope, remote = remote,
            isAuthenticated = { true }, currentUserId = { "u-1" },
            onChanged = { notified++ },
        )
        repo.refresh() // populate cached + 1 notify
        val notifiedAfterRefresh = notified

        // Make the reconciling refresh return the EDITED row so the final
        // cached value reflects the new values deterministically.
        remote.result = profileRow(name = "Ada B.", city = "Hamburg", bio = "Neu.")

        var result: Boolean? = null
        repo.updateProfile(name = "Ada B.", city = "Hamburg", bio = "Neu.") { result = it }

        // Optimistic + reconciled cached row reflects the new values.
        assertEquals("Ada B.", repo.current()?.name)
        assertEquals("Hamburg", repo.current()?.city)
        assertEquals("Neu.", repo.current()?.bio)
        // Remote recorded the (uid, name, city, bio) it was called with.
        assertEquals(
            FakeRemote.Update("u-1", "Ada B.", "Hamburg", "Neu."),
            remote.lastUpdate,
        )
        assertEquals(1, remote.updateCount)
        assertEquals(true, result)
        // onChanged fired for the optimistic update (and again via reconcile).
        assertTrue(notified > notifiedAfterRefresh)
    }

    @Test
    fun updateProfileOptimisticCopyPreservesSpotAndPostCounts() {
        // Phase 2 x Phase 3 interaction: after a cached row with REAL counts,
        // an optimistic updateProfile(name/city/bio) must PRESERVE spot/post
        // counts (the .copy only touches name/city/bio). Gate the remote update
        // so we observe the optimistic state before any reconciling refresh.
        val gate = CompletableDeferred<Unit>()
        // Counts are DERIVED from the own-list sizes (Phase 4): supply 5 spots +
        // 9 posts so the cached row ends up with spotCount=5 / postCount=9.
        val seeded = profileRow(name = "Ada Lovelace")
        val seededSpots = (1..5).map { ownSpot("s-$it") }
        val seededPosts = (1..9).map { ownPost("p-$it") }
        val remote = object : ProfileRemoteSource {
            override fun isAvailable() = true
            override suspend fun fetchCurrentProfile(): Profile? = seeded
            override suspend fun updateProfile(
                userId: String,
                name: String,
                city: String,
                bio: String,
            ): Boolean {
                gate.await() // stay in flight so no reconcile runs yet
                return true
            }
            override suspend fun fetchOwnPosts(userId: String): List<FeedPost> = seededPosts
            override suspend fun fetchOwnSpots(userId: String): List<Spot> = seededSpots
        }
        val repo = ProfileRepository(
            scope = scope, remote = remote,
            isAuthenticated = { true }, currentUserId = { "u-1" },
        )
        repo.refresh() // cache the row with derived counts
        assertEquals(5, repo.current()?.spotCount)

        repo.updateProfile(name = "Ada B.", city = "Hamburg", bio = "Neu.") { }
        // name/city/bio updated optimistically...
        assertEquals("Ada B.", repo.current()?.name)
        // ...while the real counts are preserved by the .copy.
        assertEquals(5, repo.current()?.spotCount)
        assertEquals(9, repo.current()?.postCount)
        assertEquals(0, repo.current()?.followerCount)
        assertEquals(0, repo.current()?.followingCount)

        gate.complete(Unit)
    }

    @Test
    fun updateProfileAppliesOptimisticLocalUpdateImmediately() {
        // Gate the remote update so it stays in flight: the optimistic local
        // update + onChanged must already be observable BEFORE the remote
        // resolves (independent of the remote result).
        val gate = CompletableDeferred<Unit>()
        val seeded = profileRow(name = "Ada Lovelace", city = "Berlin", bio = "Alt.")
        var cached: Profile? = seeded
        val remote = object : ProfileRemoteSource {
            override fun isAvailable() = true
            override suspend fun fetchCurrentProfile(): Profile? = cached
            override suspend fun updateProfile(
                userId: String,
                name: String,
                city: String,
                bio: String,
            ): Boolean {
                gate.await() // stay in flight
                return true
            }
            override suspend fun fetchOwnPosts(userId: String): List<FeedPost> = emptyList()
            override suspend fun fetchOwnSpots(userId: String): List<Spot> = emptyList()
        }
        var notified = 0
        val repo = ProfileRepository(
            scope = scope, remote = remote,
            isAuthenticated = { true }, currentUserId = { "u-1" },
            onChanged = { notified++ },
        )
        repo.refresh()
        val notifiedAfterRefresh = notified

        repo.updateProfile(name = "Ada B.", city = "Hamburg", bio = "Neu.") { }
        // Remote update is still suspended on the gate, yet the cached row and
        // the notification already reflect the edit optimistically.
        assertEquals("Ada B.", repo.current()?.name)
        assertEquals("Hamburg", repo.current()?.city)
        assertEquals("Neu.", repo.current()?.bio)
        assertTrue(notified > notifiedAfterRefresh)

        gate.complete(Unit)
    }

    @Test
    fun updateProfileWhenUnavailableKeepsOptimisticLocalButReportsFalse() {
        // Documented offline story: NO durable queue — the optimistic in-memory
        // edit is KEPT for the session, but onResult reports FALSE (not
        // persisted). Remote update is never called when unavailable.
        val remote = FakeRemote(available = false, result = profileRow())
        val repo = ProfileRepository(
            scope = scope, remote = remote,
            isAuthenticated = { true }, currentUserId = { "u-1" },
        )
        var result: Boolean? = null
        repo.updateProfile(name = "Edit", city = "City", bio = "Bio") { result = it }
        // Optimistic local edit kept (cached was null -> constructed from values).
        assertEquals("Edit", repo.current()?.name)
        assertEquals("City", repo.current()?.city)
        assertEquals("Bio", repo.current()?.bio)
        // Not persisted: false, remote never called, no crash.
        assertEquals(false, result)
        assertEquals(0, remote.updateCount)
    }

    @Test
    fun updateProfileWhenUnauthenticatedKeepsOptimisticLocalButReportsFalse() {
        val remote = FakeRemote(available = true, result = profileRow())
        val repo = ProfileRepository(
            scope = scope, remote = remote,
            isAuthenticated = { false }, currentUserId = { "u-1" },
        )
        var result: Boolean? = null
        repo.updateProfile(name = "Edit", city = "City", bio = "Bio") { result = it }
        assertEquals("Edit", repo.current()?.name)
        assertEquals(false, result)
        assertEquals(0, remote.updateCount)
    }

    @Test
    fun updateProfileWhenRemoteThrowsIsCaughtAndReportsFalse() {
        val seeded = profileRow(name = "Ada Lovelace")
        val remote = FakeRemote(available = true, result = seeded, updateThrows = true)
        val repo = ProfileRepository(
            scope = scope, remote = remote,
            isAuthenticated = { true }, currentUserId = { "u-1" },
        )
        repo.refresh()
        var result: Boolean? = null
        repo.updateProfile(name = "Ada B.", city = "Hamburg", bio = "Neu.") { result = it }
        // Thrown error is caught; optimistic local value stays; false reported.
        assertEquals("Ada B.", repo.current()?.name)
        assertEquals(1, remote.updateCount)
        assertEquals(false, result)
    }

    // --- Phase 4: own posts / spots (profile "meine Posts" / "meine Spots") ---

    @Test
    fun ownPostsAndOwnSpotsAreEmptyBeforeFirstRefresh() {
        val remote = FakeRemote(
            available = true, result = profileRow(),
            ownPosts = listOf(ownPost("p-1")), ownSpots = listOf(ownSpot("s-1")),
        )
        val repo = ProfileRepository(
            scope = scope, remote = remote, isAuthenticated = { true },
        )
        // Pure reads before any refresh: empty, and NO fetch triggered.
        assertEquals(emptyList(), repo.ownPosts())
        assertEquals(emptyList(), repo.ownSpots())
        assertEquals(0, remote.ownPostsCount)
        assertEquals(0, remote.ownSpotsCount)
    }

    @Test
    fun refreshPopulatesOwnPostsAndOwnSpots() {
        val remote = FakeRemote(
            available = true, result = profileRow(),
            ownPosts = listOf(ownPost("p-1"), ownPost("p-2")),
            ownSpots = listOf(ownSpot("s-1"), ownSpot("s-2"), ownSpot("s-3")),
        )
        val repo = ProfileRepository(
            scope = scope, remote = remote, isAuthenticated = { true },
        )
        repo.refresh()
        assertEquals(listOf("p-1", "p-2"), repo.ownPosts().map { it.id })
        assertEquals(listOf("s-1", "s-2", "s-3"), repo.ownSpots().map { it.id })
        // Derived counts match the list sizes.
        assertEquals(3, repo.current()?.spotCount)
        assertEquals(2, repo.current()?.postCount)
    }

    @Test
    fun ownReadsAreSideEffectFreeEvenRepeatedly() {
        val remote = FakeRemote(
            available = true, result = profileRow(),
            ownPosts = listOf(ownPost("p-1")), ownSpots = listOf(ownSpot("s-1")),
        )
        val repo = ProfileRepository(
            scope = scope, remote = remote, isAuthenticated = { true },
        )
        repo.refresh()
        // Exactly one fetch of each from the single refresh pass.
        assertEquals(1, remote.ownPostsCount)
        assertEquals(1, remote.ownSpotsCount)
        // Repeated PURE reads trigger NO further fetches.
        repeat(5) { repo.ownPosts(); repo.ownSpots() }
        assertEquals(1, remote.ownPostsCount)
        assertEquals(1, remote.ownSpotsCount)
    }

    @Test
    fun ownContentFetchesArePerUser() {
        // The fake returns the canned lists for whatever uid it is asked; assert
        // the repository passes the CURRENT user id through to both fetches.
        val remote = FakeRemote(
            available = true, result = profileRow(),
            ownPosts = listOf(ownPost("p-1")), ownSpots = listOf(ownSpot("s-1")),
        )
        val repo = ProfileRepository(
            scope = scope, remote = remote,
            isAuthenticated = { true }, currentUserId = { "user-42" },
        )
        repo.refresh()
        assertEquals("user-42", remote.lastOwnPostsUid)
        assertEquals("user-42", remote.lastOwnSpotsUid)
    }

    @Test
    fun throwingOwnSpotsFetchLeavesOwnSpotsEmptyButKeepsProfileAndOwnPosts() {
        // Best-effort per sub-query: own-spots throws, but the profile row and
        // own-posts still load, and ownSpots stays at its prior (empty) value.
        val remote = FakeRemote(
            available = true, result = profileRow(name = "Ada Lovelace"),
            ownPosts = listOf(ownPost("p-1"), ownPost("p-2")),
            ownSpots = listOf(ownSpot("s-1")),
            ownSpotsThrows = true,
        )
        val repo = ProfileRepository(
            scope = scope, remote = remote, isAuthenticated = { true },
        )
        repo.refresh()
        // Profile loaded; own-posts loaded; own-spots degraded to empty (no crash).
        assertEquals("Ada Lovelace", repo.current()?.name)
        assertEquals(listOf("p-1", "p-2"), repo.ownPosts().map { it.id })
        assertEquals(emptyList(), repo.ownSpots())
        // Derived postCount tracks the loaded posts; spotCount is 0 (none loaded).
        assertEquals(2, repo.current()?.postCount)
        assertEquals(0, repo.current()?.spotCount)
    }

    @Test
    fun throwingOwnSpotsFetchKeepsPriorOwnSpotsFromAnEarlierRefresh() {
        // A good first refresh populates own-spots; a later refresh whose
        // own-spots fetch throws must KEEP the prior good own-spots (best-effort
        // degradation, not a wipe).
        val remote = FakeRemote(
            available = true, result = profileRow(),
            ownSpots = listOf(ownSpot("s-1"), ownSpot("s-2")),
        )
        val repo = ProfileRepository(
            scope = scope, remote = remote, isAuthenticated = { true },
        )
        repo.refresh()
        assertEquals(listOf("s-1", "s-2"), repo.ownSpots().map { it.id })
        // Next refresh: own-spots throws -> prior value kept.
        remote.ownSpotsThrows = true
        repo.refresh()
        assertEquals(listOf("s-1", "s-2"), repo.ownSpots().map { it.id })
    }

    // --- Sign-out: clear() empties the cache (cross-user data-leak fix) ---

    @Test
    fun clearEmptiesCacheSoADifferentUserDoesNotSeePriorData() {
        // The sign-out true->false contract: after a populated refresh, clear()
        // must reset current()/ownPosts()/ownSpots() to the neutral/empty state
        // (so a different user signing in on the same app launch never sees the
        // prior user's data) and fire onChanged so observers re-read.
        val remote = FakeRemote(
            available = true, result = profileRow(name = "Ada Lovelace"),
            ownPosts = listOf(ownPost("p-1"), ownPost("p-2")),
            ownSpots = listOf(ownSpot("s-1"), ownSpot("s-2"), ownSpot("s-3")),
        )
        var notified = 0
        val repo = ProfileRepository(
            scope = scope, remote = remote,
            isAuthenticated = { true }, onChanged = { notified++ },
        )

        // Populate: profile + own posts/spots all non-empty.
        repo.refresh()
        assertEquals("Ada Lovelace", repo.current()?.name)
        assertEquals(listOf("p-1", "p-2"), repo.ownPosts().map { it.id })
        assertEquals(listOf("s-1", "s-2", "s-3"), repo.ownSpots().map { it.id })
        val notifiedAfterRefresh = notified

        // Sign out: clear() empties the shared cache synchronously + notifies.
        repo.clear()

        // Neutral/empty state — exactly what the bridge shows pre-first-fetch.
        assertNull(repo.current())
        assertTrue(repo.ownPosts().isEmpty())
        assertTrue(repo.ownSpots().isEmpty())
        // onChanged fired for the clear so observers re-read the neutral state.
        assertTrue(notified > notifiedAfterRefresh)
    }
}
