package com.example.thirdspace.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the sample comments data + [SampleData.commentsFor] lookup.
 *
 * Reuses the existing kotlin.test framework wired into commonTest (no new
 * dependency). Runs on every target the KMP toolchain can run host/native
 * tests on (Android host test + iOS simulator native test).
 */
class CommentsTest {

    @Test
    fun returnsExpectedCommentCountForPostsWithComments() {
        assertEquals(2, SampleData.commentsFor("post-1").size)
        assertEquals(3, SampleData.commentsFor("post-3").size)
        assertEquals(4, SampleData.commentsFor("post-4").size)
    }

    @Test
    fun returnsEmptyListForPostWithoutComments() {
        assertTrue(SampleData.commentsFor("post-2").isEmpty())
        assertTrue(SampleData.commentsFor("post-5").isEmpty())
        assertTrue(SampleData.commentsFor("post-6").isEmpty())
    }

    @Test
    fun returnsEmptyListForUnknownPostId() {
        assertTrue(SampleData.commentsFor("does-not-exist").isEmpty())
    }

    @Test
    fun everyCommentBelongsToItsRequestedPost() {
        val postOne = SampleData.commentsFor("post-1")
        assertTrue(postOne.all { it.postId == "post-1" })
    }
}
