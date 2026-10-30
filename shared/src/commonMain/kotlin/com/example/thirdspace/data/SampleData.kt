package com.example.thirdspace.data

/**
 * Static dummy data used to populate the UI-only build. Colored placeholder
 * values stand in for images; nothing here talks to a backend.
 */
object SampleData {

    private val spotPrater = Spot(
        id = "spot-prater",
        name = "Prater Garten",
        category = "Bar",
        neighborhood = "Prenzlauer Berg",
        verified = true,
        checkInCount = 128,
        placeholderColorArgb = 0xFFE9B384,
    )

    private val spotVolkspark = Spot(
        id = "spot-volkspark",
        name = "Volkspark Friedrichshain",
        category = "Park",
        neighborhood = "Friedrichshain",
        verified = false,
        checkInCount = 54,
        placeholderColorArgb = 0xFFA3C9A8,
    )

    private val spotBonanza = Spot(
        id = "spot-bonanza",
        name = "Bonanza Coffee",
        category = "Café",
        neighborhood = "Kreuzberg",
        verified = true,
        checkInCount = 1,
        placeholderColorArgb = 0xFFD5A6BD,
    )

    private val spotKlunker = Spot(
        id = "spot-klunker",
        name = "Klunkerkranich",
        category = "Bar",
        neighborhood = "Neukölln",
        verified = false,
        checkInCount = 342,
        placeholderColorArgb = 0xFF8EA7C4,
    )

    private val spotTempelhof = Spot(
        id = "spot-tempelhof",
        name = "Tempelhofer Feld",
        category = "Park",
        neighborhood = "Tempelhof",
        verified = true,
        checkInCount = 87,
        placeholderColorArgb = 0xFFC4B58E,
    )

    private val spotCompanion = Spot(
        id = "spot-companion",
        name = "Companion Coffee",
        category = "Café",
        neighborhood = "Mitte",
        verified = false,
        checkInCount = 19,
        placeholderColorArgb = 0xFFB0A8C4,
    )

    /** Feed posts: varied text/verified/category/score. */
    val posts: List<Post> = listOf(
        Post(
            id = "post-1",
            spot = spotPrater,
            authorName = "Lena Fischer",
            dateLabel = "vor 2 Std.",
            text = "Bester Biergarten der Stadt, wenn die Sonne rauskommt. Kommt früh, sonst ist jeder Tisch weg.",
            score = 214,
            myVote = VoteState.UP,
            commentCount = 12,
            placeholderColorArgb = 0xFFE9B384,
        ),
        Post(
            id = "post-2",
            spot = spotVolkspark,
            authorName = "Max Becker",
            dateLabel = "vor 5 Std.",
            text = null,
            score = 47,
            myVote = VoteState.NONE,
            commentCount = 3,
            placeholderColorArgb = 0xFFA3C9A8,
        ),
        Post(
            id = "post-3",
            spot = spotBonanza,
            authorName = "Sophie Wagner",
            dateLabel = "gestern",
            text = "Der Flat White hier ist eine Klasse für sich. Kleiner Laden, große Qualität.",
            score = 89,
            myVote = VoteState.NONE,
            commentCount = 7,
            placeholderColorArgb = 0xFFD5A6BD,
        ),
        Post(
            id = "post-4",
            spot = spotKlunker,
            authorName = "Jonas Richter",
            dateLabel = "vor 2 Tagen",
            text = "Sonnenuntergang über den Dächern von Neukölln. Lohnt sich jedes Mal.",
            score = 1340,
            myVote = VoteState.DOWN,
            commentCount = 56,
            placeholderColorArgb = 0xFF8EA7C4,
        ),
        Post(
            id = "post-5",
            spot = spotTempelhof,
            authorName = "Amira Hassan",
            dateLabel = "vor 3 Tagen",
            text = null,
            score = 162,
            myVote = VoteState.NONE,
            commentCount = 9,
            placeholderColorArgb = 0xFFC4B58E,
        ),
        Post(
            id = "post-6",
            spot = spotCompanion,
            authorName = "Tobias Krüger",
            dateLabel = "vor 4 Tagen",
            text = "Ruhige Ecke zum Arbeiten am Vormittag. Guter Kaffee, freundliches Team.",
            score = 33,
            myVote = VoteState.NONE,
            commentCount = 2,
            placeholderColorArgb = 0xFFB0A8C4,
        ),
    )

    /**
     * Sample comments keyed by [Comment.postId].
     *
     * NOTE (documented trade-off): this list is independent of the [Post.commentCount]
     * STORED field. The feed still shows the original `commentCount` numbers
     * (12/3/7/56/9/2); the Comments sheet header shows the count of LOADED
     * comments from this list. They are intentionally NOT kept in sync — the
     * feed count is a stored stat, not derived from loaded comments.
     *
     * Coverage: post-1 (2), post-3 (3), post-4 (4) have comments; post-2,
     * post-5, post-6 have none, to exercise the empty state.
     */
    val comments: List<Comment> = listOf(
        // post-1
        Comment(
            id = "comment-1",
            postId = "post-1",
            authorName = "Max Becker",
            timeLabel = "vor 1 Std.",
            text = "Kann ich nur bestätigen, war gestern da. Unbedingt die Brezn probieren!",
        ),
        Comment(
            id = "comment-2",
            postId = "post-1",
            authorName = "Sophie Wagner",
            timeLabel = "vor 45 Min.",
            text = "Schön, aber am Wochenende leider komplett überlaufen.",
        ),
        // post-3
        Comment(
            id = "comment-3",
            postId = "post-3",
            authorName = "Jonas Richter",
            timeLabel = "vor 20 Std.",
            text = "Der beste Flat White im Kiez, da stimme ich voll zu.",
        ),
        Comment(
            id = "comment-4",
            postId = "post-3",
            authorName = "Amira Hassan",
            timeLabel = "vor 18 Std.",
            text = "Die Bohnen kann man auch mitnehmen, super Qualität.",
        ),
        Comment(
            id = "comment-5",
            postId = "post-3",
            authorName = "Tobias Krüger",
            timeLabel = "gestern",
            text = "Etwas eng drinnen, aber der Kaffee macht das mehr als wett.",
        ),
        // post-4
        Comment(
            id = "comment-6",
            postId = "post-4",
            authorName = "Lena Fischer",
            timeLabel = "vor 1 Tag",
            text = "Dieser Sonnenuntergang ist einfach unschlagbar.",
        ),
        Comment(
            id = "comment-7",
            postId = "post-4",
            authorName = "Max Becker",
            timeLabel = "vor 1 Tag",
            text = "Früh kommen lohnt sich, die Schlange wird abends lang.",
        ),
        Comment(
            id = "comment-8",
            postId = "post-4",
            authorName = "Sophie Wagner",
            timeLabel = "vor 22 Std.",
            text = "Getränkepreise sind okay für die Aussicht.",
        ),
        Comment(
            id = "comment-9",
            postId = "post-4",
            authorName = "Amira Hassan",
            timeLabel = "vor 12 Std.",
            text = "Nehmt eine Jacke mit, oben wird es windig.",
        ),
    )

    /** Comments for a given post id, in list order. Empty if the post has none. */
    fun commentsFor(postId: String): List<Comment> =
        comments.filter { it.postId == postId }

    /**
     * Filter chips for the map; "Meine Pins" is statically active.
     *
     * This list owns the chip ORDER and the active flags. Each entry carries a
     * stable [FilterChipItem.key]; the localized display label is resolved from
     * the i18n layer per current language (see FeedBridge.mapFilters), not
     * stored here.
     */
    val mapFilters: List<FilterChipItem> = listOf(
        FilterChipItem("chip_my_pins", active = true),
        FilterChipItem("chip_all", active = false),
        FilterChipItem("chip_cafes", active = false),
        FilterChipItem("chip_parks", active = false),
        FilterChipItem("chip_bars", active = false),
        FilterChipItem("chip_hidden", active = false),
        FilterChipItem("chip_verified", active = false),
        FilterChipItem("chip_friends", active = false),
        FilterChipItem("chip_saved", active = false),
    )

    /**
     * Report reasons shown in the native "Melden" (report) sheet, in display
     * order. Single source of truth for both platforms: keys are stable machine
     * ids for future reporting logic; labels are the authentic German strings.
     */
    val reportReasons: List<ReportReason> = listOf(
        ReportReason("spam", "Spam"),
        ReportReason("harassment", "Belästigung"),
        ReportReason("inappropriate", "Unangemessener Inhalt"),
        ReportReason("wrong_location", "Falsche Ortsangabe"),
        ReportReason("other", "Sonstiges"),
    )

    /**
     * The current user's own dummy profile for the profile screen.
     *
     * Dynamic content (name/username/city/bio/monthYear) is NOT translated.
     * [postCount] mirrors [posts].size (6); the remaining counts are dummy
     * display stats. [monthYear] is a neutral join month; the localized
     * "seit"/"since" prefix is applied Swift-side via i18n.
     */
    val profile = Profile(
        name = "Elian Kasteleiner",
        username = "elian",
        city = "Berlin",
        monthYear = "März 2024",
        bio = "Immer auf der Suche nach dem nächsten guten Kaffee und ruhigen Ecken in der Stadt.",
        email = "elian@thirdspace.app",
        spotCount = 12,
        postCount = posts.size,
        followerCount = 184,
        followingCount = 97,
    )

    /** Bottom navigation items (authoritative: Posts, Map, Discover). */
    val navItems: List<NavItem> = listOf(
        NavItem(NavTab.POSTS, "Posts"),
        NavItem(NavTab.MAP, "Map"),
        NavItem(NavTab.DISCOVER, "Discover"),
    )
}
