package com.example.thirdspace.data

/** Which way the current user voted on a post/spot. */
enum class VoteState { UP, DOWN, NONE }

/**
 * A place/spot referenced by a feed post.
 *
 * [placeholderColorArgb] is an ARGB color used to render a colored placeholder
 * surface instead of loading a real image (UI-only build, no network images).
 */
data class Spot(
    val id: String,
    val name: String,
    val category: String,
    val neighborhood: String,
    val verified: Boolean,
    val checkInCount: Int,
    val placeholderColorArgb: Long,
)

/** A feed post attached to a [spot]. */
data class Post(
    val id: String,
    val spot: Spot,
    val authorName: String,
    val dateLabel: String,
    val text: String?,
    val score: Int,
    val myVote: VoteState,
    val commentCount: Int,
    val placeholderColorArgb: Long,
)

/**
 * A comment attached to a [Post] (by [postId]).
 *
 * Intentionally flat and minimal: no replies/threading, no author avatar beyond
 * the name (the avatar initial is derived Swift-side). [timeLabel] is a
 * pre-formatted German relative label (e.g. "vor 1 Std.").
 */
data class Comment(
    val id: String,
    val postId: String,
    val authorName: String,
    val timeLabel: String,
    val text: String,
)

/**
 * A selectable reason for reporting a post.
 *
 * Flat and minimal so it can cross the Kotlin<->Swift boundary unchanged:
 * [key] is a stable machine id (e.g. "spam") used by future reporting logic;
 * [label] is the German display string shown in the report sheet.
 */
data class ReportReason(
    val key: String,
    val label: String,
)

/**
 * A static filter chip definition for the map screen.
 *
 * [key] is a stable machine id (e.g. "chip_cafes") used to resolve the
 * localized label via the i18n layer; [active] preserves the statically-active
 * chip. The display label is NOT stored here — it comes from the current
 * language's string table (see FeedBridge.mapFilters).
 */
data class FilterChipItem(
    val key: String,
    val active: Boolean,
)

/**
 * The current user's own profile (static dummy data for the profile screen).
 *
 * Intentionally flat and minimal so it can cross the Kotlin<->Swift boundary
 * unchanged (String / Int only; see IosProfile). There is no avatar field —
 * the avatar initial is derived Swift-side from [name], matching the comment
 * and feed-avatar convention.
 *
 * [monthYear] is a neutral, NON-translated dummy join month (e.g. "März 2024").
 * The localized "seit"/"since" prefix is applied separately via
 * [com.example.thirdspace.i18n.Strings.joinedSince]. All other fields
 * ([name], [username], [city], [bio], [email]) are dynamic content and are NOT
 * translated; the counts are plain display stats. [email] backs the account
 * tab's "current email" line on the edit screen.
 */
data class Profile(
    val name: String,
    val username: String,
    val city: String,
    val monthYear: String,
    val bio: String,
    val email: String,
    val spotCount: Int,
    val postCount: Int,
    val followerCount: Int,
    val followingCount: Int,
)

/** A bottom navigation destination. */
enum class NavTab { POSTS, MAP, DISCOVER }

data class NavItem(
    val tab: NavTab,
    val label: String,
)
