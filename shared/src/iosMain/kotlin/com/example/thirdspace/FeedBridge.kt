package com.example.thirdspace

import com.example.thirdspace.data.SampleData
import com.example.thirdspace.data.VoteState

/**
 * Flat, ObjC-friendly snapshot of a feed post for the SwiftUI native feed.
 *
 * Only String / Int / Boolean cross the Kotlin<->Swift boundary here — no
 * Kotlin enums, no nullables, no Long — so the generated ObjC header stays
 * trivial to consume from Swift (no optionals, no KotlinLong, etc.).
 *
 * - [text] uses an empty string instead of null (check `text.isEmpty` in Swift).
 * - [voteState] is "UP" / "DOWN" / "NONE".
 * - [colorHex] is a "#RRGGBB" string (alpha dropped from the ARGB source).
 */
data class IosPost(
    val id: String,
    val spotName: String,
    val category: String,
    val neighborhood: String,
    val verified: Boolean,
    val checkInCount: Int,
    val authorName: String,
    val dateLabel: String,
    val text: String,
    val score: Int,
    val voteState: String,
    val commentCount: Int,
    val colorHex: String,
)

/**
 * Flat, ObjC-friendly filter chip for the SwiftUI map base.
 *
 * Only String / Boolean cross the boundary. Mirrors the shared
 * [com.example.thirdspace.data.FilterChipItem] so the map chip row has a single
 * source of truth in the shared module (not a Swift-only static list).
 */
data class IosFilterChip(
    val label: String,
    val active: Boolean,
)

/**
 * Flat, ObjC-friendly comment for the SwiftUI comments sheet.
 *
 * Only String crosses the boundary (no nullables, no Long, no enums). Mirrors
 * the shared [com.example.thirdspace.data.Comment] so the comments list has a
 * single source of truth in the shared module. No replies/threading.
 */
data class IosComment(
    val id: String,
    val authorName: String,
    val timeLabel: String,
    val text: String,
)

/**
 * Flat, ObjC-friendly report reason for the SwiftUI report ("Melden") sheet.
 *
 * Only String crosses the boundary (no nullables, no Long, no enums). Mirrors
 * the shared [com.example.thirdspace.data.ReportReason] so the reason list has
 * a single source of truth in the shared module (not a Swift-only list).
 */
data class IosReportReason(
    val key: String,
    val label: String,
)

/**
 * Bridge exposing shared sample data to Swift as flat value types.
 *
 * Declared as an `object` so Swift sees a singleton accessor
 * (`FeedBridge.shared.posts()`, `FeedBridge.shared.mapFilters()`), avoiding any
 * need to instantiate.
 */
object FeedBridge {

    /** Mapped copy of the shared sample feed, safe to consume from Swift. */
    fun posts(): List<IosPost> = SampleData.posts.map { post ->
        val spot = post.spot
        IosPost(
            id = post.id,
            spotName = spot.name,
            category = spot.category,
            neighborhood = spot.neighborhood,
            verified = spot.verified,
            checkInCount = spot.checkInCount,
            authorName = post.authorName,
            dateLabel = post.dateLabel,
            text = post.text ?: "",
            score = post.score,
            voteState = when (post.myVote) {
                VoteState.UP -> "UP"
                VoteState.DOWN -> "DOWN"
                VoteState.NONE -> "NONE"
            },
            commentCount = post.commentCount,
            colorHex = argbToHex(post.placeholderColorArgb),
        )
    }

    /**
     * Mapped copy of the shared comments for [postId], safe to consume from
     * Swift. Empty list when the post has no comments (drives the empty state).
     */
    fun comments(postId: String): List<IosComment> =
        SampleData.commentsFor(postId).map { comment ->
            IosComment(
                id = comment.id,
                authorName = comment.authorName,
                timeLabel = comment.timeLabel,
                text = comment.text,
            )
        }

    /** Mapped copy of the shared report reasons, safe to consume from Swift. */
    fun reportReasons(): List<IosReportReason> = SampleData.reportReasons.map { reason ->
        IosReportReason(
            key = reason.key,
            label = reason.label,
        )
    }

    /** Mapped copy of the shared map filter chips, safe to consume from Swift. */
    fun mapFilters(): List<IosFilterChip> = SampleData.mapFilters.map { chip ->
        IosFilterChip(
            label = chip.label,
            active = chip.active,
        )
    }

    /** Convert an ARGB Long (e.g. 0xFFE9B384) to a "#RRGGBB" string (alpha dropped). */
    private fun argbToHex(argb: Long): String {
        val rgb = (argb and 0xFFFFFF).toInt()
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return "#" + listOf(r, g, b).joinToString("") { component ->
            val hex = component.toString(16).uppercase()
            if (hex.length == 1) "0$hex" else hex
        }
    }
}
