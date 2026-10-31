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
    val tabProfile: String
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

    // Create spot sheet (AddSpotSheetView) — tap-to-create a new spot at a
    // map coordinate. Category labels are DYNAMIC (from SpotCategory) and are
    // NOT part of this table; only the static chrome below is localized.
    val addSpotTitle: String
    val addSpotSubtitle: String
    val addSpotNameLabel: String
    val addSpotNamePlaceholder: String
    val addSpotCategoryLabel: String
    val addSpotLocationLabel: String
    val addSpotCreate: String

    // Language picker (ProfileView globe button) — accessibility label for the
    // menu that switches the UI language live.
    val languageMenu: String

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

    // Profile edit screen (ProfileEditView) — static UI text only; the current
    // email and the seeded name/bio/city are dynamic content and are NOT part
    // of the string table.
    val profileEditTitle: String
    val editTabProfile: String
    val editTabAccount: String
    val fieldName: String
    val fieldBio: String
    val fieldCity: String
    val bioPlaceholder: String
    val chooseBanner: String
    val remove: String
    val chooseAvatar: String
    val privateProfile: String
    val privateProfileSubtitle: String
    val save: String
    val blockedUsers: String
    val logout: String
    val deleteAccount: String
    val changeEmail: String
    val newEmailPlaceholder: String
    val sendConfirmationEmail: String
    val changePassword: String
    val newPasswordPlaceholder: String
    val confirmPasswordPlaceholder: String
    val changePasswordButton: String

    // Auth screens (LoginView / SignUpView) — the logged-out gate. Static UI
    // text only; the entered email/name/city are dynamic content and are NOT
    // part of the string table. The logout button reuses the existing [logout]
    // key above (no separate auth logout key).
    val authLoginTitle: String
    val authSignUpTitle: String
    val authEmailLabel: String
    val authEmailPlaceholder: String
    val authPasswordLabel: String
    val authPasswordPlaceholder: String
    val authNameLabel: String
    val authNamePlaceholder: String
    val authCityLabel: String
    val authCityPlaceholder: String
    val authLoginButton: String
    val authSignUpButton: String
    val authSwitchToSignUp: String
    val authSwitchToLogin: String
    val authErrorInvalidCredentials: String
    val authErrorEmailExists: String
    val authErrorWeakPassword: String
    val authErrorNotConfigured: String
    val authErrorNetwork: String
    val authErrorGeneric: String

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

    /**
     * Account tab "current email" line, with the (non-translated) [email]
     * interpolated: DE "Aktuell: $email", EN "Current: $email".
     */
    fun accountCurrentEmail(email: String): String
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
    override val tabProfile = "Profil"
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

    // Create spot sheet
    override val addSpotTitle = "Neuen Spot anlegen"
    override val addSpotSubtitle = "Markiere einen Ort auf der Karte und gib ihm einen Namen."
    override val addSpotNameLabel = "Name"
    override val addSpotNamePlaceholder = "Wie heißt dieser Ort?"
    override val addSpotCategoryLabel = "Kategorie"
    override val addSpotLocationLabel = "Standort"
    override val addSpotCreate = "Spot erstellen"

    // Language picker
    override val languageMenu = "Sprache ändern"

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

    // Profile edit screen
    override val profileEditTitle = "Profil-Einstellungen"
    override val editTabProfile = "Profil"
    override val editTabAccount = "Konto"
    override val fieldName = "Name"
    override val fieldBio = "Bio"
    override val fieldCity = "Stadt"
    override val bioPlaceholder = "Kurz über dich…"
    override val chooseBanner = "Banner wählen"
    override val remove = "Entfernen"
    override val chooseAvatar = "Profilbild wählen"
    override val privateProfile = "Privates Profil"
    override val privateProfileSubtitle = "Nur akzeptierte Follower sehen deine Inhalte."
    override val save = "Speichern"
    override val blockedUsers = "Blockierte Nutzer"
    override val logout = "Abmelden"
    override val deleteAccount = "Konto löschen"
    override val changeEmail = "E-Mail ändern"
    override val newEmailPlaceholder = "neue@beispiel.de"
    override val sendConfirmationEmail = "Bestätigungs-Mail senden"
    override val changePassword = "Passwort ändern"
    override val newPasswordPlaceholder = "Neues Passwort (mind. 8 Zeichen)"
    override val confirmPasswordPlaceholder = "Bestätigen"
    override val changePasswordButton = "Passwort ändern"

    // Auth screens
    override val authLoginTitle = "Anmelden"
    override val authSignUpTitle = "Konto erstellen"
    override val authEmailLabel = "E-Mail"
    override val authEmailPlaceholder = "name@beispiel.de"
    override val authPasswordLabel = "Passwort"
    override val authPasswordPlaceholder = "Dein Passwort"
    override val authNameLabel = "Name"
    override val authNamePlaceholder = "Wie heißt du?"
    override val authCityLabel = "Stadt"
    override val authCityPlaceholder = "Deine Stadt"
    override val authLoginButton = "Anmelden"
    override val authSignUpButton = "Registrieren"
    override val authSwitchToSignUp = "Noch kein Konto? Registrieren"
    override val authSwitchToLogin = "Schon ein Konto? Anmelden"
    override val authErrorInvalidCredentials = "E-Mail oder Passwort ist falsch."
    override val authErrorEmailExists = "Für diese E-Mail existiert bereits ein Konto."
    override val authErrorWeakPassword = "Das Passwort ist zu schwach."
    override val authErrorNotConfigured = "Backend ist nicht konfiguriert."
    override val authErrorNetwork = "Netzwerkfehler. Bitte erneut versuchen."
    override val authErrorGeneric = "Etwas ist schiefgelaufen."

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

    override fun accountCurrentEmail(email: String): String =
        "Aktuell: $email"
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
    override val tabProfile = "Profile"
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

    // Create spot sheet
    override val addSpotTitle = "Create a new spot"
    override val addSpotSubtitle = "Mark a place on the map and give it a name."
    override val addSpotNameLabel = "Name"
    override val addSpotNamePlaceholder = "What's this place called?"
    override val addSpotCategoryLabel = "Category"
    override val addSpotLocationLabel = "Location"
    override val addSpotCreate = "Create spot"

    // Language picker
    override val languageMenu = "Change language"

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

    // Profile edit screen
    override val profileEditTitle = "Profile settings"
    override val editTabProfile = "Profile"
    override val editTabAccount = "Account"
    override val fieldName = "Name"
    override val fieldBio = "Bio"
    override val fieldCity = "City"
    override val bioPlaceholder = "A bit about you…"
    override val chooseBanner = "Choose banner"
    override val remove = "Remove"
    override val chooseAvatar = "Choose photo"
    override val privateProfile = "Private profile"
    override val privateProfileSubtitle = "Only accepted followers can see your content."
    override val save = "Save"
    override val blockedUsers = "Blocked users"
    override val logout = "Log out"
    override val deleteAccount = "Delete account"
    override val changeEmail = "Change email"
    override val newEmailPlaceholder = "new@example.com"
    override val sendConfirmationEmail = "Send confirmation email"
    override val changePassword = "Change password"
    override val newPasswordPlaceholder = "New password (min. 8 characters)"
    override val confirmPasswordPlaceholder = "Confirm"
    override val changePasswordButton = "Change password"

    // Auth screens
    override val authLoginTitle = "Log in"
    override val authSignUpTitle = "Create account"
    override val authEmailLabel = "Email"
    override val authEmailPlaceholder = "name@example.com"
    override val authPasswordLabel = "Password"
    override val authPasswordPlaceholder = "Your password"
    override val authNameLabel = "Name"
    override val authNamePlaceholder = "What's your name?"
    override val authCityLabel = "City"
    override val authCityPlaceholder = "Your city"
    override val authLoginButton = "Log in"
    override val authSignUpButton = "Sign up"
    override val authSwitchToSignUp = "No account? Sign up"
    override val authSwitchToLogin = "Have an account? Log in"
    override val authErrorInvalidCredentials = "Invalid email or password."
    override val authErrorEmailExists = "An account with this email already exists."
    override val authErrorWeakPassword = "That password is too weak."
    override val authErrorNotConfigured = "Backend is not configured."
    override val authErrorNetwork = "Network error. Please try again."
    override val authErrorGeneric = "Something went wrong."

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

    override fun accountCurrentEmail(email: String): String =
        "Current: $email"
}
