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

    /** Filter chips for the map; "Meine Pins" is statically active. */
    val mapFilters: List<FilterChipItem> = listOf(
        FilterChipItem("Meine Pins", active = true),
        FilterChipItem("Alle", active = false),
        FilterChipItem("Cafés", active = false),
        FilterChipItem("Parks", active = false),
        FilterChipItem("Bars", active = false),
        FilterChipItem("Geheimtipps", active = false),
        FilterChipItem("Verifiziert", active = false),
        FilterChipItem("Freunde", active = false),
        FilterChipItem("Gespeichert", active = false),
    )

    /** Bottom navigation items (authoritative: Posts, Map, Discover). */
    val navItems: List<NavItem> = listOf(
        NavItem(NavTab.POSTS, "Posts"),
        NavItem(NavTab.MAP, "Map"),
        NavItem(NavTab.DISCOVER, "Discover"),
    )
}
