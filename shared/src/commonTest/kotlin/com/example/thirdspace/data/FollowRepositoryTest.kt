package com.example.thirdspace.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OFFLINE-provable logic tests for [FollowRepository] (the social-graph seam,
 * Phase F2) and the [FollowUser] username derivation — all WITHOUT a real
 * backend, network, device, or SQLDelight driver.
 *
 * These run on BOTH shared targets (Android host + iOS simulator) because they
 * use an in-memory FAKE [FollowRemoteSource] (no JVM-only driver). The REAL
 * Supabase follow round-trip (an actual `follows` INSERT/DELETE/SELECT) is
 * BLOCKED-until-session (needs the user's keys + migrations + a live login) and
 * is NOT faked as a network round-trip here; this fake is the automated proof
 * of the optimistic / side-effect-free-read / refresh behavior.
 *
 * The background push/refresh runs on a [Dispatchers.Unconfined] scope so the
 * launched coroutine executes synchronously up to the fake's (non-suspending)
 * suspend calls; assertions after a follow()/refresh*() observe the completed
 * background work deterministically — mirroring [ProfileRepositoryTest].
 */
class FollowRepositoryTest {

    /** Controllable fake remote: records calls, returns canned lists/counts. */
    private class FakeFollowRemote(
        var available: Boolean = true,
        var followers: List<FollowUser> = emptyList(),
        var following: List<FollowUser> = emptyList(),
        var followerCountResult: Int = 0,
        var followingCountResult: Int = 0,
        var isFollowingResult: Boolean = false,
        var followThrows: Boolean = false,
    ) : FollowRemoteSource {
        var followCount = 0
        var unfollowCount = 0
        var fetchFollowersCount = 0
        var fetchFollowingCount = 0
        val followedTargets = mutableListOf<Pair<String, String>>()
        val unfollowedTargets = mutableListOf<Pair<String, String>>()

        override fun isAvailable(): Boolean = available

        override suspend fun fetchFollowers(userId: String, viewerId: String): List<FollowUser> {
            fetchFollowersCount++
            return followers
        }

        override suspend fun fetchFollowing(userId: String, viewerId: String): List<FollowUser> {
            fetchFollowingCount++
            return following
        }

        override suspend fun followerCount(userId: String): Int = followerCountResult
        override suspend fun followingCount(userId: String): Int = followingCountResult

        override suspend fun isFollowing(followerId: String, followingId: String): Boolean =
            isFollowingResult

        override suspend fun follow(followerId: String, followingId: String): Boolean {
            followCount++
            if (followThrows) throw RuntimeException("boom-follow")
            followedTargets.add(followerId to followingId)
            return true
        }

        override suspend fun unfollow(followerId: String, followingId: String): Boolean {
            unfollowCount++
            unfollowedTargets.add(followerId to followingId)
            return true
        }
    }

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun followUser(id: String, name: String = "User $id") = FollowUser(
        id = id,
        name = name,
        username = ProfileMapping.deriveUsername(email = "", name = name),
        city = "Berlin",
        isFollowedByMe = false,
    )

    private fun repo(
        remote: FakeFollowRemote,
        authed: Boolean = true,
        me: String = "me",
        onChanged: () -> Unit = {},
    ) = FollowRepository(
        scope = scope, remote = remote,
        isAuthenticated = { authed }, currentUserId = { me }, onChanged = onChanged,
    )

    // --- follow / unfollow round-trip through the pure read ---

    @Test
    fun followThenIsFollowingTrueUnfollowThenFalse() {
        val remote = FakeFollowRemote(available = true)
        val r = repo(remote)
        var result: Boolean? = null
        r.follow("target") { result = it }
        assertTrue(r.isFollowing("target"))
        assertEquals(true, result)
        assertEquals(1, remote.followCount)
        assertEquals("me" to "target", remote.followedTargets.first())

        r.unfollow("target") { result = it }
        assertFalse(r.isFollowing("target"))
        assertEquals(true, result)
        assertEquals(1, remote.unfollowCount)
        assertEquals("me" to "target", remote.unfollowedTargets.first())
    }

    // --- optimistic: state + counts update BEFORE the remote resolves ---

    @Test
    fun followIsOptimisticAndBumpsCounts() {
        // Gate the remote follow so it stays in flight; the pure isFollowing and
        // the bumped counts must already be observable before it resolves.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val remote = object : FollowRemoteSource {
            override fun isAvailable() = true
            override suspend fun fetchFollowers(userId: String, viewerId: String) = emptyList<FollowUser>()
            override suspend fun fetchFollowing(userId: String, viewerId: String) = emptyList<FollowUser>()
            override suspend fun followerCount(userId: String) = 0
            override suspend fun followingCount(userId: String) = 0
            override suspend fun isFollowing(followerId: String, followingId: String) = false
            override suspend fun follow(followerId: String, followingId: String): Boolean {
                gate.await() // stay in flight
                return true
            }
            override suspend fun unfollow(followerId: String, followingId: String) = true
        }
        val r = FollowRepository(
            scope = scope, remote = remote,
            isAuthenticated = { true }, currentUserId = { "me" },
        )
        r.follow("target") { }
        // Remote follow still suspended, yet state + counts already reflect it.
        assertTrue(r.isFollowing("target"))
        assertEquals(1, r.followingCount("me")) // I now follow one more user
        assertEquals(1, r.followerCount("target")) // target gained a follower
        gate.complete(Unit)
    }

    // --- lists: populated after refresh, EMPTY before any refresh ---

    @Test
    fun followersAndFollowingEmptyBeforeRefreshAndNoFetch() {
        val remote = FakeFollowRemote(
            available = true,
            followers = listOf(followUser("a")),
            following = listOf(followUser("b")),
        )
        val r = repo(remote)
        // Pure reads before any refresh: empty, and NO fetch triggered.
        assertEquals(emptyList(), r.followersOf("u"))
        assertEquals(emptyList(), r.followingOf("u"))
        assertEquals(0, remote.fetchFollowersCount)
        assertEquals(0, remote.fetchFollowingCount)
    }

    @Test
    fun refreshPopulatesFollowersAndFollowingLists() {
        val remote = FakeFollowRemote(
            available = true,
            followers = listOf(followUser("a"), followUser("b")),
            following = listOf(followUser("c")),
        )
        val r = repo(remote)
        r.refreshFollowers("u")
        r.refreshFollowing("u")
        assertEquals(listOf("a", "b"), r.followersOf("u").map { it.id })
        assertEquals(listOf("c"), r.followingOf("u").map { it.id })
        assertEquals(1, remote.fetchFollowersCount)
        assertEquals(1, remote.fetchFollowingCount)
    }

    @Test
    fun listReadsAreSideEffectFreeEvenRepeatedly() {
        val remote = FakeFollowRemote(available = true, followers = listOf(followUser("a")))
        val r = repo(remote)
        r.refreshFollowers("u")
        assertEquals(1, remote.fetchFollowersCount)
        repeat(5) { r.followersOf("u"); r.followingOf("u") }
        // Repeated PURE reads trigger NO further fetches.
        assertEquals(1, remote.fetchFollowersCount)
        assertEquals(0, remote.fetchFollowingCount)
    }

    // --- offline / unauth follow: reports false, no crash, no remote call ---

    @Test
    fun followWhenUnavailableKeepsOptimisticButReportsFalse() {
        val remote = FakeFollowRemote(available = false)
        val r = repo(remote, authed = true)
        var result: Boolean? = null
        r.follow("target") { result = it }
        // Optimistic state kept for the session, but NOT persisted -> false.
        assertTrue(r.isFollowing("target"))
        assertEquals(false, result)
        assertEquals(0, remote.followCount)
    }

    @Test
    fun followWhenUnauthenticatedKeepsOptimisticButReportsFalse() {
        val remote = FakeFollowRemote(available = true)
        val r = repo(remote, authed = false)
        var result: Boolean? = null
        r.follow("target") { result = it }
        assertTrue(r.isFollowing("target"))
        assertEquals(false, result)
        assertEquals(0, remote.followCount)
    }

    @Test
    fun followWhenRemoteThrowsIsCaughtAndReportsFalse() {
        val remote = FakeFollowRemote(available = true, followThrows = true)
        val r = repo(remote)
        var result: Boolean? = null
        r.follow("target") { result = it }
        // Thrown error caught; optimistic state stays; false reported; no crash.
        assertTrue(r.isFollowing("target"))
        assertEquals(1, remote.followCount)
        assertEquals(false, result)
    }

    // --- self-follow is a no-op reporting false ---

    @Test
    fun selfFollowIsNoOpReportsFalse() {
        val remote = FakeFollowRemote(available = true)
        val r = repo(remote, me = "me")
        var result: Boolean? = null
        r.follow("me") { result = it }
        assertFalse(r.isFollowing("me"))
        assertEquals(false, result)
        assertEquals(0, remote.followCount)
        // No optimistic count bump for a self-follow.
        assertEquals(0, r.followingCount("me"))
        assertEquals(0, r.followerCount("me"))
    }

    @Test
    fun followAlreadyFollowingIsNoOpReportsFalse() {
        val remote = FakeFollowRemote(available = true)
        val r = repo(remote)
        r.follow("target") { }
        assertEquals(1, remote.followCount)
        // A second follow() while already following is a no-op (no double bump).
        var result: Boolean? = null
        r.follow("target") { result = it }
        assertEquals(false, result)
        assertEquals(1, remote.followCount)
        assertEquals(1, r.followingCount("me")) // not double-bumped
    }

    // --- counts: reflect the fake remote after refreshCounts ---

    @Test
    fun refreshCountsReflectsRemoteCounts() {
        val remote = FakeFollowRemote(
            available = true,
            followerCountResult = 42,
            followingCountResult = 7,
        )
        val r = repo(remote)
        // Zero before any refresh (pure read).
        assertEquals(0, r.followerCount("u"))
        assertEquals(0, r.followingCount("u"))
        r.refreshCounts("u")
        assertEquals(42, r.followerCount("u"))
        assertEquals(7, r.followingCount("u"))
    }

    // --- refreshFollowState mirrors the remote follow-state ---

    @Test
    fun refreshFollowStateReflectsRemote() {
        val remote = FakeFollowRemote(available = true, isFollowingResult = true)
        val r = repo(remote, me = "me")
        assertFalse(r.isFollowing("target")) // false before refresh
        r.refreshFollowState("target")
        assertTrue(r.isFollowing("target"))
    }

    @Test
    fun refreshFollowStateWhenUnavailableDoesNotChangeCachedState() {
        val remote = FakeFollowRemote(available = false, isFollowingResult = true)
        val r = repo(remote)
        r.refreshFollowState("target")
        // Unavailable -> no change, stays false, no crash.
        assertFalse(r.isFollowing("target"))
    }

    // --- username derivation (FollowUser) ---

    @Test
    fun followUserUsernameDerivesFromNameWhenEmailAbsent() {
        // The follows list resolves name/city (not email), so username derives
        // from the name via the shared ProfileMapping (email passed as "").
        val u = followUser("id-1", name = "Ada Lovelace")
        assertEquals("ada lovelace", u.username)
        // Empty name -> empty username (never crashes).
        assertEquals("", ProfileMapping.deriveUsername(email = "", name = ""))
    }
}
