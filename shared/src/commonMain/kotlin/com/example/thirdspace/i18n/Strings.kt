package com.example.thirdspace.i18n

/**
 * The full set of localized UI strings, grouped by screen.
 *
 * Simple, non-parameterized strings are exposed as typed [String] properties.
 * Strings that need pluralization or interpolation are exposed as FUNCTIONS
 * (see the bottom of the interface) so the formatting logic lives in one place
 * per language rather than being rebuilt Swift-side.
 *
 * [De] is the default language; [En] mirrors every member. Adding a key to the
 * interface forces both implementations to provide it (compile-time safety).
 *
 * This is pure Kotlin with NO Compose and NO platform dependency, so it lives
 * in commonMain and is reused by both platforms through [AppStrings].
 */
interface Strings {

    // Feed (NativeFeedView)
    val feedEyebrow: String
    val feedTitle: String

    // Bottom navigation (RootTabView)
    val tabPosts: String
    val tabMap: String
    val tabDiscover: String
    val discoverPlaceholder: String

    // Map (MapBaseView)
    val mapSearchPlaceholder: String
    val mapPlaceholder: String

    // Map filter chips (resolved by stable key, order owned by SampleData)
    val chipMyPins: String
    val chipAll: String
    val chipCafes: String
    val chipParks: String
    val chipBars: String
    val chipHidden: String
    val chipVerified: String
    val chipFriends: String
    val chipSaved: String

    // Spot detail sheet (SpotSheetView)
    val spotNoDescription: String
    val actionRoute: String
    val actionSave: String
    val actionWasHere: String
    val writeComment: String
    val share: String
    val report: String

    // Comments sheet (CommentsSheetView)
    val commentsEmpty: String
    val commentPlaceholder: String
    val localAuthorYou: String
    val justNow: String

    // Create post sheet (CreatePostView)
    val createTitle: String
    val createSubtitle: String
    val createSpotLabel: String
    val createSpotSearch: String
    val createTextLabel: String
    val createCaptionPlaceholder: String
    val createAddPhoto: String
    val createVisibility: String
    val visibilityPublic: String
    val visibilityFriends: String
    val createPost: String

    // Report sheet (ReportSheetView)
    val reportTitle: String
    val reportHint: String
    val reportDetailsPlaceholder: String
    val reportSubmit: String
    val reportSelected: String

    // Report reason labels (keys stay in SampleData; labels resolved here)
    val reasonSpam: String
    val reasonHarassment: String
    val reasonInappropriate: String
    val reasonWrongLocation: String
    val reasonOther: String

    // Profile screen (ProfileView) — static UI text only; name/username/city/
    // bio/monthYear are dynamic content and are NOT part of the string table.
    val profileStatSpots: String
    val profileStatPosts: String
    val profileStatFollowers: String
    val profileStatFollowing: String
    val profileEdit: String
    val profileTabSpots: String
    val profileTabPosts: String
    val profileEmptySpots: String
    val profileEmptyPosts: String
    val profileSettings: String

    // Plural / interpolation functions
    /** Check-in suffix: DE "war hier"/"waren hier", EN "was here"/"were here". */
    fun checkInLabel(count: Int): String

    /** Comment button label when a post has comments (count interpolated). */
    fun showAllComments(count: Int): String

    /** Comments sheet header, with the loaded-comment count interpolated. */
    fun commentsHeader(count: Int): String

    /** Native share-sheet message, with the spot name interpolated. */
    fun shareMessage(spotName: String): String

    /**
     * Profile "joined since" line, with the (non-translated) [monthYear]
     * interpolated: DE "seit $monthYear", EN "since $monthYear".
     */
    fun joinedSince(monthYear: String): String
}

/** German strings — the default language. */
object De : Strings {

    // Feed
    override val feedEyebrow = "POSTS"
    override val feedTitle = "Feed"

    // Bottom navigation
    override val tabPosts = "Posts"
    override val tabMap = "Map"
    override val tabDiscover = "Discover"
    override val discoverPlaceholder = "Discover folgt"

    // Map
    override val mapSearchPlaceholder = "Ort oder Adresse suchen"
    override val mapPlaceholder = "Karte folgt"

    // Map filter chips
    override val chipMyPins = "Meine Pins"
    override val chipAll = "Alle"
    override val chipCafes = "Cafés"
    override val chipParks = "Parks"
    override val chipBars = "Bars"
    override val chipHidden = "Geheimtipps"
    override val chipVerified = "Verifiziert"
    override val chipFriends = "Freunde"
    override val chipSaved = "Gespeichert"

    // Spot detail sheet
    override val spotNoDescription = "Noch keine Beschreibung vorhanden."
    override val actionRoute = "Route"
    override val actionSave = "Speichern"
    override val actionWasHere = "Ich war hier"
    override val writeComment = "Kommentar schreiben"
    override val share = "Teilen"
    override val report = "Melden"

    // Comments sheet
    override val commentsEmpty = "Noch keine Kommentare. Sei die erste Person."
    override val commentPlaceholder = "Kommentar schreiben…"
    override val localAuthorYou = "Du"
    override val justNow = "gerade eben"

    // Create post sheet
    override val createTitle = "Ich war hier"
    override val createSubtitle = "Teile einen Spot mit deiner Community."
    override val createSpotLabel = "Spot"
    override val createSpotSearch = "Spot suchen…"
    override val createTextLabel = "Dein Text"
    override val createCaptionPlaceholder = "Schreib was über diesen Ort…"
    override val createAddPhoto = "Foto hinzufügen"
    override val createVisibility = "Sichtbarkeit"
    override val visibilityPublic = "Öffentlich"
    override val visibilityFriends = "Freunde"
    override val createPost = "Posten"

    // Report sheet
    override val reportTitle = "Melden"
    override val reportHint =
        "Wähle einen Grund. Reports sind anonym — die gemeldete Person sieht deinen Namen nicht."
    override val reportDetailsPlaceholder = "Zusätzliche Details (optional)…"
    override val reportSubmit = "Report senden"
    override val reportSelected = "Ausgewählt"

    // Report reason labels
    override val reasonSpam = "Spam"
    override val reasonHarassment = "Belästigung"
    override val reasonInappropriate = "Unangemessener Inhalt"
    override val reasonWrongLocation = "Falsche Ortsangabe"
    override val reasonOther = "Sonstiges"

    // Profile screen
    override val profileStatSpots = "Spots"
    override val profileStatPosts = "Posts"
    override val profileStatFollowers = "Follower"
    override val profileStatFollowing = "Folgt"
    override val profileEdit = "Profil bearbeiten"
    override val profileTabSpots = "Spots"
    override val profileTabPosts = "Posts"
    override val profileEmptySpots = "Noch keine Spots"
    override val profileEmptyPosts = "Noch keine Posts"
    override val profileSettings = "Einstellungen"

    // Plural / interpolation
    override fun checkInLabel(count: Int): String =
        if (count == 1) "war hier" else "waren hier"

    override fun showAllComments(count: Int): String =
        "Alle $count Kommentare anzeigen"

    override fun commentsHeader(count: Int): String =
        "Kommentare · $count"

    override fun shareMessage(spotName: String): String =
        "Schau dir $spotName auf ThirdSpace an."

    override fun joinedSince(monthYear: String): String =
        "seit $monthYear"
}

/** English strings. */
object En : Strings {

    // Feed
    override val feedEyebrow = "POSTS"
    override val feedTitle = "Feed"

    // Bottom navigation
    override val tabPosts = "Posts"
    override val tabMap = "Map"
    override val tabDiscover = "Discover"
    override val discoverPlaceholder = "Discover coming soon"

    // Map
    override val mapSearchPlaceholder = "Search place or address"
    override val mapPlaceholder = "Map coming soon"

    // Map filter chips
    override val chipMyPins = "My pins"
    override val chipAll = "All"
    override val chipCafes = "Cafés"
    override val chipParks = "Parks"
    override val chipBars = "Bars"
    override val chipHidden = "Hidden gems"
    override val chipVerified = "Verified"
    override val chipFriends = "Friends"
    override val chipSaved = "Saved"

    // Spot detail sheet
    override val spotNoDescription = "No description yet."
    override val actionRoute = "Directions"
    override val actionSave = "Save"
    override val actionWasHere = "I was here"
    override val writeComment = "Write a comment"
    override val share = "Share"
    override val report = "Report"

    // Comments sheet
    override val commentsEmpty = "No comments yet. Be the first."
    override val commentPlaceholder = "Write a comment…"
    override val localAuthorYou = "You"
    override val justNow = "just now"

    // Create post sheet
    override val createTitle = "I was here"
    override val createSubtitle = "Share a spot with your community."
    override val createSpotLabel = "Spot"
    override val createSpotSearch = "Search spot…"
    override val createTextLabel = "Your text"
    override val createCaptionPlaceholder = "Write something about this place…"
    override val createAddPhoto = "Add photo"
    override val createVisibility = "Visibility"
    override val visibilityPublic = "Public"
    override val visibilityFriends = "Friends"
    override val createPost = "Post"

    // Report sheet
    override val reportTitle = "Report"
    override val reportHint =
        "Choose a reason. Reports are anonymous — the reported person won't see your name."
    override val reportDetailsPlaceholder = "Additional details (optional)…"
    override val reportSubmit = "Send report"
    override val reportSelected = "Selected"

    // Report reason labels
    override val reasonSpam = "Spam"
    override val reasonHarassment = "Harassment"
    override val reasonInappropriate = "Inappropriate content"
    override val reasonWrongLocation = "Wrong location"
    override val reasonOther = "Other"

    // Profile screen
    override val profileStatSpots = "Spots"
    override val profileStatPosts = "Posts"
    override val profileStatFollowers = "Followers"
    override val profileStatFollowing = "Following"
    override val profileEdit = "Edit profile"
    override val profileTabSpots = "Spots"
    override val profileTabPosts = "Posts"
    override val profileEmptySpots = "No spots yet"
    override val profileEmptyPosts = "No posts yet"
    override val profileSettings = "Settings"

    // Plural / interpolation
    override fun checkInLabel(count: Int): String =
        if (count == 1) "was here" else "were here"

    override fun showAllComments(count: Int): String =
        "Show all $count comments"

    override fun commentsHeader(count: Int): String =
        "Comments · $count"

    override fun shareMessage(spotName: String): String =
        "Check out $spotName on ThirdSpace."

    override fun joinedSince(monthYear: String): String =
        "since $monthYear"
}
