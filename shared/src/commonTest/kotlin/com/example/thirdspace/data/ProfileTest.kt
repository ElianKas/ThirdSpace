package com.example.thirdspace.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [SampleData.profile], the single source of truth for the
 * native profile screen's own dummy profile.
 *
 * Reuses the existing kotlin.test framework wired into commonTest (no new
 * dependency). Runs on every target the KMP toolchain can run host/native
 * tests on (Android host test + iOS simulator native test).
 *
 * These assert structural sanity only (non-empty dynamic fields, non-negative
 * counts). The dynamic content values themselves (name/city/bio/monthYear) are
 * NOT translated and are deliberately not pinned to exact strings beyond what
 * the UI contract needs.
 */
class ProfileTest {

    private val profile = SampleData.profile

    @Test
    fun dynamicTextFieldsAreNonEmpty() {
        assertTrue(profile.name.isNotEmpty())
        assertTrue(profile.username.isNotEmpty())
        assertTrue(profile.city.isNotEmpty())
        assertTrue(profile.monthYear.isNotEmpty())
        assertTrue(profile.bio.isNotEmpty())
        assertTrue(profile.email.isNotEmpty())
    }

    @Test
    fun countsAreNonNegative() {
        assertTrue(profile.spotCount >= 0)
        assertTrue(profile.postCount >= 0)
        assertTrue(profile.followerCount >= 0)
        assertTrue(profile.followingCount >= 0)
    }

    @Test
    fun postCountMirrorsFeedPosts() {
        // postCount is defined to mirror the feed's post list size.
        assertEquals(SampleData.posts.size, profile.postCount)
    }
}
