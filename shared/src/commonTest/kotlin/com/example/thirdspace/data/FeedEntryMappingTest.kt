package com.example.thirdspace.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure mapping tests for the Phase 4 feed UNION view DTO
 * ([FeedEntryDto] -> [FeedPost] via [PostFeedMapping.toFeedPost]). Runs on BOTH
 * shared targets. No network / DB; `internal` types are visible to the same
 * module's tests.
 *
 * Covers the two column-contract guarantees the `feed_entries` view provides:
 *   * a "post" row maps to a [FeedPost] whose [FeedPost.id] is the `entry_id`
 *     (the post uuid) and whose [FeedPost.kind] is "post";
 *   * a bare "spot" row (empty text, 0 score/comments, my_vote NONE) maps to a
 *     [FeedPost] whose [FeedPost.id] is the `spot:`-prefixed `entry_id` and
 *     whose [FeedPost.kind] is "spot".
 */
class FeedEntryMappingTest {

    @Test
    fun postEntryMapsEntryIdToIdAndKindPost() {
        val dto = FeedEntryDto(
            kind = "post",
            entryId = "11111111-1111-1111-1111-111111111111",
            id = "11111111-1111-1111-1111-111111111111",
            authorId = "author-uid-1",
            authorName = "Alice",
            spotName = "Café Central",
            category = "Cafe",
            neighborhood = "Mitte",
            verified = true,
            text = "great coffee",
            createdAt = "", // RelativeTime tolerates empty -> 0 epoch
            score = 7,
            commentCount = 2,
            myVote = "UP",
        )
        val post = PostFeedMapping.toFeedPost(dto)
        assertEquals("11111111-1111-1111-1111-111111111111", post.id)
        assertEquals("post", post.kind)
        assertEquals("Alice", post.authorName)
        assertEquals("Café Central", post.spotName)
        assertEquals("great coffee", post.text)
        assertEquals(7, post.score)
        assertEquals(2, post.commentCount)
        assertEquals("UP", post.myVote)
        assertEquals(true, post.verified)
        // PHASE F3: author uid maps through for a "post" entry.
        assertEquals("author-uid-1", post.authorUserId)
    }

    @Test
    fun bareSpotEntryMapsPrefixedEntryIdAndKindSpot() {
        val dto = FeedEntryDto(
            kind = "spot",
            entryId = "spot:22222222-2222-2222-2222-222222222222",
            id = "22222222-2222-2222-2222-222222222222",
            authorId = "owner-uid-2",
            authorName = "Bob",
            spotName = "Secret Rooftop",
            category = "Bar",
            neighborhood = "Kreuzberg",
            verified = false,
            text = "",
            createdAt = "",
            score = 0,
            commentCount = 0,
            myVote = "NONE",
        )
        val post = PostFeedMapping.toFeedPost(dto)
        // The stable id is the `spot:`-prefixed entry_id (keeps ForEach unique).
        assertEquals("spot:22222222-2222-2222-2222-222222222222", post.id)
        assertEquals("spot", post.kind)
        // Bare-spot shape: owner as author, no text, zeroed post stats.
        assertEquals("Bob", post.authorName)
        assertEquals("Secret Rooftop", post.spotName)
        assertEquals("", post.text)
        assertEquals(0, post.score)
        assertEquals(0, post.commentCount)
        assertEquals("NONE", post.myVote)
        // PHASE F3: for a bare "spot" entry the author uid is the spot owner's.
        assertEquals("owner-uid-2", post.authorUserId)
    }

    @Test
    fun feedPostDefaultsToKindPost() {
        // The added `kind` has a default so existing construction (seed, tests,
        // the post_feed path) stays "post" without naming the param.
        val p = FeedPost(
            id = "x",
            authorName = "A",
            spotName = "S",
            category = "Cafe",
            neighborhood = "Mitte",
            verified = false,
            text = "t",
            createdAtEpoch = 0L,
            score = 0,
            commentCount = 0,
            myVote = "NONE",
        )
        assertEquals("post", p.kind)
        // PHASE F3: authorUserId also defaults so existing construction compiles.
        assertEquals("", p.authorUserId)
    }
}
