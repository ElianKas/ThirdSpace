package com.example.thirdspace

import com.example.thirdspace.data.AuthRepository
import com.example.thirdspace.data.AuthResult
import com.example.thirdspace.data.Feed
import com.example.thirdspace.data.RelativeTime
import com.example.thirdspace.data.SampleData
import com.example.thirdspace.data.Spot
import com.example.thirdspace.data.SpotCategory
import com.example.thirdspace.data.SpotMapping
import com.example.thirdspace.data.Spots
import com.example.thirdspace.data.SupabaseClientProvider
import com.example.thirdspace.data.currentEpochMillis
import com.example.thirdspace.i18n.AppLanguage
import com.example.thirdspace.i18n.AppStrings
import com.example.thirdspace.i18n.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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
 * Flat, ObjC-friendly snapshot of a spot for the SwiftUI Mapbox map.
 *
 * Only String / Boolean / Int / Double cross the boundary — no Kotlin enums, no
 * nullables, no Long. [lat] / [lng] are the pin coordinates (Double is safe
 * across the boundary). [colorHex] is a "#RRGGBB" string = the spot category's
 * marker color resolved from the shared [com.example.thirdspace.data.SpotCategory]
 * (so Swift never hardcodes a category color).
 */
data class IosSpot(
    val id: String,
    val name: String,
    val category: String,
    val neighborhood: String,
    val verified: Boolean,
    val checkInCount: Int,
    val lat: Double,
    val lng: Double,
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
    val key: String,
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
 * Flat, ObjC-friendly descriptor of a selectable UI language for the SwiftUI
 * language picker (the globe button on the profile).
 *
 * Only String crosses the boundary (no nullables, no Long, no enums). Mirrors
 * the shared [com.example.thirdspace.i18n.AppLanguage] so the picker has a
 * single source of truth in the shared module. [code] is the flat language
 * code ("de" / "en"); [displayName] is the endonym ("Deutsch" / "English"),
 * intentionally NOT translated.
 */
data class IosLanguage(
    val code: String,
    val displayName: String,
)

/**
 * Flat, ObjC-friendly snapshot of the current user's profile for the SwiftUI
 * profile screen.
 *
 * Only String / Int cross the boundary (no nullables, no Long, no enums).
 * Mirrors the shared [com.example.thirdspace.data.Profile] so the profile has a
 * single source of truth in the shared module. No avatar field — the avatar
 * initial is derived Swift-side from [name]. [monthYear] stays as-is; the
 * localized "seit"/"since" prefix is applied Swift-side via
 * [IosStrings.joinedSince].
 */
data class IosProfile(
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

/**
 * Flat, ObjC-friendly snapshot of ALL localized UI strings for one language.
 *
 * Only String crosses the boundary for the simple keys; the four
 * pluralized/interpolated strings are exposed as functions taking flat Int /
 * String args. Built from a shared [Strings] table via [FeedBridge.strings],
 * so the Swift side never sees the Kotlin [Strings] interface, the
 * [com.example.thirdspace.i18n.AppLanguage] enum, or any nullable/Long.
 *
 * Grouped by screen to mirror the shared [Strings] interface.
 */
data class IosStrings(
    /** Language code this snapshot was built for ("de" / "en"). */
    val languageCode: String,
    // Feed
    val feedEyebrow: String,
    val feedTitle: String,
    // Bottom navigation
    val tabPosts: String,
    val tabMap: String,
    val tabDiscover: String,
    val tabProfile: String,
    val discoverPlaceholder: String,
    // Map
    val mapSearchPlaceholder: String,
    val mapPlaceholder: String,
    // Spot detail sheet
    val spotNoDescription: String,
    val actionRoute: String,
    val actionSave: String,
    val actionWasHere: String,
    val writeComment: String,
    val share: String,
    val report: String,
    // Comments sheet
    val commentsEmpty: String,
    val commentPlaceholder: String,
    val localAuthorYou: String,
    val justNow: String,
    // Create post sheet
    val createTitle: String,
    val createSubtitle: String,
    val createSpotLabel: String,
    val createSpotSearch: String,
    val createTextLabel: String,
    val createCaptionPlaceholder: String,
    val createAddPhoto: String,
    val createVisibility: String,
    val visibilityPublic: String,
    val visibilityFriends: String,
    val createPost: String,
    // Create spot sheet
    val addSpotTitle: String,
    val addSpotSubtitle: String,
    val addSpotNameLabel: String,
    val addSpotNamePlaceholder: String,
    val addSpotCategoryLabel: String,
    val addSpotLocationLabel: String,
    val addSpotCreate: String,
    // Language picker
    val languageMenu: String,
    // Report sheet
    val reportTitle: String,
    val reportHint: String,
    val reportDetailsPlaceholder: String,
    val reportSubmit: String,
    val reportSelected: String,
    // Profile screen
    val profileStatSpots: String,
    val profileStatPosts: String,
    val profileStatFollowers: String,
    val profileStatFollowing: String,
    val profileEdit: String,
    val profileTabSpots: String,
    val profileTabPosts: String,
    val profileEmptySpots: String,
    val profileEmptyPosts: String,
    val profileSettings: String,
    // Profile edit screen
    val profileEditTitle: String,
    val editTabProfile: String,
    val editTabAccount: String,
    val fieldName: String,
    val fieldBio: String,
    val fieldCity: String,
    val bioPlaceholder: String,
    val chooseBanner: String,
    val remove: String,
    val chooseAvatar: String,
    val privateProfile: String,
    val privateProfileSubtitle: String,
    val save: String,
    val blockedUsers: String,
    val logout: String,
    val deleteAccount: String,
    val changeEmail: String,
    val newEmailPlaceholder: String,
    val sendConfirmationEmail: String,
    val changePassword: String,
    val newPasswordPlaceholder: String,
    val confirmPasswordPlaceholder: String,
    val changePasswordButton: String,
    // Auth screens (logout button reuses [logout] above)
    val authLoginTitle: String,
    val authSignUpTitle: String,
    val authEmailLabel: String,
    val authEmailPlaceholder: String,
    val authPasswordLabel: String,
    val authPasswordPlaceholder: String,
    val authNameLabel: String,
    val authNamePlaceholder: String,
    val authCityLabel: String,
    val authCityPlaceholder: String,
    val authLoginButton: String,
    val authSignUpButton: String,
    val authSwitchToSignUp: String,
    val authSwitchToLogin: String,
    val authErrorInvalidCredentials: String,
    val authErrorEmailExists: String,
    val authErrorWeakPassword: String,
    val authErrorNotConfigured: String,
    val authErrorNetwork: String,
    val authErrorGeneric: String,
) {
    /** The underlying shared table, used for the formatting functions below. */
    private val table: Strings get() = AppStrings.of(languageCode)

    // Pluralized / interpolated strings, exposed as flat functions.

    /** Check-in suffix for [count] (e.g. "war hier" / "waren hier"). */
    fun checkInLabel(count: Int): String = table.checkInLabel(count)

    /** Comment button label when a post has [count] comments. */
    fun showAllComments(count: Int): String = table.showAllComments(count)

    /** Comments sheet header for [count] loaded comments. */
    fun commentsHeader(count: Int): String = table.commentsHeader(count)

    /** Native share-sheet message for [spotName]. */
    fun shareMessage(spotName: String): String = table.shareMessage(spotName)

    /** Profile "joined since" line for the (non-translated) [monthYear]. */
    fun joinedSince(monthYear: String): String = table.joinedSince(monthYear)

    /** Account tab "current email" line for the (non-translated) [email]. */
    fun accountCurrentEmail(email: String): String = table.accountCurrentEmail(email)

    companion object {
        /** Build a flat [IosStrings] snapshot for the given [languageCode]. */
        fun from(languageCode: String): IosStrings {
            val s = AppStrings.of(languageCode)
            return IosStrings(
                languageCode = languageCode,
                feedEyebrow = s.feedEyebrow,
                feedTitle = s.feedTitle,
                tabPosts = s.tabPosts,
                tabMap = s.tabMap,
                tabDiscover = s.tabDiscover,
                tabProfile = s.tabProfile,
                discoverPlaceholder = s.discoverPlaceholder,
                mapSearchPlaceholder = s.mapSearchPlaceholder,
                mapPlaceholder = s.mapPlaceholder,
                languageMenu = s.languageMenu,
                spotNoDescription = s.spotNoDescription,
                actionRoute = s.actionRoute,
                actionSave = s.actionSave,
                actionWasHere = s.actionWasHere,
                writeComment = s.writeComment,
                share = s.share,
                report = s.report,
                commentsEmpty = s.commentsEmpty,
                commentPlaceholder = s.commentPlaceholder,
                localAuthorYou = s.localAuthorYou,
                justNow = s.justNow,
                createTitle = s.createTitle,
                createSubtitle = s.createSubtitle,
                createSpotLabel = s.createSpotLabel,
                createSpotSearch = s.createSpotSearch,
                createTextLabel = s.createTextLabel,
                createCaptionPlaceholder = s.createCaptionPlaceholder,
                createAddPhoto = s.createAddPhoto,
                createVisibility = s.createVisibility,
                visibilityPublic = s.visibilityPublic,
                visibilityFriends = s.visibilityFriends,
                createPost = s.createPost,
                addSpotTitle = s.addSpotTitle,
                addSpotSubtitle = s.addSpotSubtitle,
                addSpotNameLabel = s.addSpotNameLabel,
                addSpotNamePlaceholder = s.addSpotNamePlaceholder,
                addSpotCategoryLabel = s.addSpotCategoryLabel,
                addSpotLocationLabel = s.addSpotLocationLabel,
                addSpotCreate = s.addSpotCreate,
                reportTitle = s.reportTitle,
                reportHint = s.reportHint,
                reportDetailsPlaceholder = s.reportDetailsPlaceholder,
                reportSubmit = s.reportSubmit,
                reportSelected = s.reportSelected,
                profileStatSpots = s.profileStatSpots,
                profileStatPosts = s.profileStatPosts,
                profileStatFollowers = s.profileStatFollowers,
                profileStatFollowing = s.profileStatFollowing,
                profileEdit = s.profileEdit,
                profileTabSpots = s.profileTabSpots,
                profileTabPosts = s.profileTabPosts,
                profileEmptySpots = s.profileEmptySpots,
                profileEmptyPosts = s.profileEmptyPosts,
                profileSettings = s.profileSettings,
                profileEditTitle = s.profileEditTitle,
                editTabProfile = s.editTabProfile,
                editTabAccount = s.editTabAccount,
                fieldName = s.fieldName,
                fieldBio = s.fieldBio,
                fieldCity = s.fieldCity,
                bioPlaceholder = s.bioPlaceholder,
                chooseBanner = s.chooseBanner,
                remove = s.remove,
                chooseAvatar = s.chooseAvatar,
                privateProfile = s.privateProfile,
                privateProfileSubtitle = s.privateProfileSubtitle,
                save = s.save,
                blockedUsers = s.blockedUsers,
                logout = s.logout,
                deleteAccount = s.deleteAccount,
                changeEmail = s.changeEmail,
                newEmailPlaceholder = s.newEmailPlaceholder,
                sendConfirmationEmail = s.sendConfirmationEmail,
                changePassword = s.changePassword,
                newPasswordPlaceholder = s.newPasswordPlaceholder,
                confirmPasswordPlaceholder = s.confirmPasswordPlaceholder,
                changePasswordButton = s.changePasswordButton,
                authLoginTitle = s.authLoginTitle,
                authSignUpTitle = s.authSignUpTitle,
                authEmailLabel = s.authEmailLabel,
                authEmailPlaceholder = s.authEmailPlaceholder,
                authPasswordLabel = s.authPasswordLabel,
                authPasswordPlaceholder = s.authPasswordPlaceholder,
                authNameLabel = s.authNameLabel,
                authNamePlaceholder = s.authNamePlaceholder,
                authCityLabel = s.authCityLabel,
                authCityPlaceholder = s.authCityPlaceholder,
                authLoginButton = s.authLoginButton,
                authSignUpButton = s.authSignUpButton,
                authSwitchToSignUp = s.authSwitchToSignUp,
                authSwitchToLogin = s.authSwitchToLogin,
                authErrorInvalidCredentials = s.authErrorInvalidCredentials,
                authErrorEmailExists = s.authErrorEmailExists,
                authErrorWeakPassword = s.authErrorWeakPassword,
                authErrorNotConfigured = s.authErrorNotConfigured,
                authErrorNetwork = s.authErrorNetwork,
                authErrorGeneric = s.authErrorGeneric,
            )
        }
    }
}

/**
 * Bridge exposing shared sample data to Swift as flat value types.
 *
 * Declared as an `object` so Swift sees a singleton accessor
 * (`FeedBridge.shared.posts()`, `FeedBridge.shared.mapFilters()`), avoiding any
 * need to instantiate.
 */
object FeedBridge {

    // MARK: Language state

    /**
     * Current UI language code ("de" / "en"). DE is the default. There is no
     * language-selection UI yet; Swift can switch this via [setLanguage] (the
     * Localization wrapper does so), and the localized lists below resolve
     * against it.
     */
    private var currentLanguageCode = "de"

    /** Switch the current UI language. Unknown codes fall back to DE. */
    fun setLanguage(code: String) {
        // Normalize through the i18n fallback so an unknown code is stored as a
        // resolvable code ("de" / "en") rather than kept verbatim.
        currentLanguageCode = AppLanguage.fromCode(code).code
    }

    /** The current language code ("de" / "en"). */
    fun currentLanguageCode(): String = currentLanguageCode

    // MARK: Supabase backend (Phase A — inert injection point)

    /**
     * Flat injection point for the Supabase project [url] + publishable [publishableKey],
     * called once from Swift at launch (reading Info.plist). Flat String/String
     * in, Unit out — no supabase-kt, Ktor, or coroutine type crosses the
     * boundary (they stay internal to the shared module per the architecture
     * steering).
     *
     * PHASE A: this only forwards to [SupabaseClientProvider.configure], which
     * treats empty/placeholder values as "not configured" and builds no client.
     * No network call happens here; auth and persistence are Phases C/D.
     */
    fun configureSupabase(url: String, publishableKey: String) {
        SupabaseClientProvider.configure(url, publishableKey)
    }

    /**
     * Whether a Supabase client has been built (real keys supplied). Flat Bool
     * out. In Phase A with placeholder keys this stays `false`; exposed so Swift
     * can tell the inert-until-keys state without touching supabase-kt types.
     */
    fun isSupabaseConfigured(): Boolean = SupabaseClientProvider.isConfigured

    // MARK: Authentication (Phase C — flat, callback-based)

    /**
     * Bridge coroutine scope for the suspend auth operations.
     *
     * THREADING: scoped on [Dispatchers.Main], so every auth callback and
     * auth-state emission below is delivered to Swift ON THE MAIN THREAD. The
     * Swift `Session` wrapper mutates `@Observable` state directly in these
     * callbacks, so main-thread delivery is required (and avoids an extra
     * `DispatchQueue.main.async` hop Swift-side). All suspend/Flow/supabase
     * types stay INSIDE this scope — only flat Boolean/String cross the
     * callbacks below (no suspend, Flow, coroutine, or supabase type is ever
     * visible to Swift).
     */
    private val authScope = CoroutineScope(Dispatchers.Main)

    /**
     * Current authenticated email for the initial Swift state, or "" when there
     * is no session / the client is unconfigured. Flat String, synchronous.
     */
    fun currentAuthEmail(): String = AuthRepository.currentUserEmail()

    /**
     * Whether there is a live session right now, for the initial Swift state.
     * Flat Bool, synchronous.
     */
    fun isAuthenticated(): Boolean = AuthRepository.isAuthenticated()

    /**
     * Create a new account. Launches the suspend sign-up on the bridge scope
     * and delivers the flat outcome to [completion] on the main thread.
     *
     * [name] + [city] are passed into the user metadata so the DB
     * `handle_new_user()` trigger can populate the profile. The [completion]
     * receives four FLAT values: `(success, isAuthenticated, email, errorCode)`
     * — all Boolean/String, no suspend/Flow/supabase type crosses.
     */
    fun signUp(
        email: String,
        password: String,
        name: String,
        city: String,
        completion: (Boolean, Boolean, String, String) -> Unit,
    ) {
        authScope.launch {
            val result = AuthRepository.signUp(email, password, name, city)
            deliver(result, completion)
        }
    }

    /**
     * Sign in an existing account. Delivers `(success, isAuthenticated, email,
     * errorCode)` to [completion] on the main thread. Flat types only.
     */
    fun signIn(
        email: String,
        password: String,
        completion: (Boolean, Boolean, String, String) -> Unit,
    ) {
        authScope.launch {
            val result = AuthRepository.signIn(email, password)
            deliver(result, completion)
        }
    }

    /**
     * Sign out the current session. Delivers `(success, isAuthenticated, email,
     * errorCode)` to [completion] on the main thread (on success
     * isAuthenticated=false, email=""). Flat types only.
     */
    fun signOut(completion: (Boolean, Boolean, String, String) -> Unit) {
        authScope.launch {
            val result = AuthRepository.signOut()
            deliver(result, completion)
        }
    }

    /**
     * Restore a persisted session at startup (supabase-kt loads it from storage
     * automatically; this awaits that load). Delivers the resulting flat
     * `(success, isAuthenticated, email, errorCode)` to [completion] on the
     * main thread. Safe when unconfigured (success=false, "not_configured").
     */
    fun restoreSession(completion: (Boolean, Boolean, String, String) -> Unit) {
        authScope.launch {
            val result = AuthRepository.restoreSession()
            deliver(result, completion)
        }
    }

    /**
     * Observe auth-state changes. [listener] is invoked on the main thread with
     * flat `(isAuthenticated, email)` whenever the session status changes
     * (sign-in, sign-out, token refresh, restore). Collects the internal GoTrue
     * `sessionStatus` Flow on the bridge scope — the Flow/SessionStatus types
     * never leave the shared module; only Boolean/String reach Swift.
     *
     * When unconfigured, emits a single `(false, "")` so Swift still gets an
     * initial logged-out state without crashing.
     */
    fun observeAuth(listener: (Boolean, String) -> Unit) {
        AuthRepository.authStateFlow()
            .onEach { result -> listener(result.isAuthenticated, result.email) }
            .launchIn(authScope)
    }

    /**
     * Fan a flat [AuthResult] out to a four-arg Swift [completion]. Keeps the
     * mapping (and the "no nullables cross" guarantee) in one place.
     */
    private fun deliver(
        result: AuthResult,
        completion: (Boolean, Boolean, String, String) -> Unit,
    ) {
        completion(result.success, result.isAuthenticated, result.email, result.errorCode)
    }

    /** Flat localized strings for an explicit [languageCode]. */
    fun strings(languageCode: String): IosStrings = IosStrings.from(languageCode)

    /** Flat localized strings for the current language. */
    fun currentStrings(): IosStrings = IosStrings.from(currentLanguageCode)

    /**
     * All selectable UI languages, in [AppLanguage] declaration order, as flat
     * [IosLanguage] descriptors for the SwiftUI language picker. The shared
     * [AppLanguage] enum is the single source of truth; [IosLanguage.displayName]
     * carries the (non-translated) endonym.
     */
    fun availableLanguages(): List<IosLanguage> =
        AppLanguage.entries.map { IosLanguage(it.code, it.displayName) }

    /**
     * Flat snapshot of the current user's own dummy profile, safe to consume
     * from Swift. Dynamic content (name/username/city/bio/monthYear) is NOT
     * localized; the "seit"/"since" prefix is applied Swift-side via IosStrings.
     */
    fun profile(): IosProfile = SampleData.profile.let {
        IosProfile(
            it.name,
            it.username,
            it.city,
            it.monthYear,
            it.bio,
            it.email,
            it.spotCount,
            it.postCount,
            it.followerCount,
            it.followingCount,
        )
    }

    /**
     * The feed, sourced from the shared offline-first [Feed.posts] repository
     * (cache-first + background remote refresh), mapped to flat [IosPost].
     *
     * The [IosPost] output shape is UNCHANGED from the previous SampleData
     * version. Phase B only swaps the SOURCE:
     * - [IosPost.dateLabel] is now formatted SHARED-SIDE from the post's
     *   created_at epoch via [RelativeTime.format] for the current language
     *   (replacing SampleData's baked German labels). The epoch stays inside
     *   the shared module; only the formatted String crosses the boundary.
     * - [IosPost.colorHex] is the category's marker color from the shared
     *   [SpotCategory] (the remote `post_feed` view carries no per-post
     *   placeholder color; Swift never hardcodes a category color).
     * - [IosPost.checkInCount] is 0 (the feed row does not carry a spot
     *   check-in count; it was only ever a spot-detail stat).
     * - [IosPost.voteState] comes from the post's flat my_vote string.
     */
    fun posts(): List<IosPost> {
        val now = currentEpochMillis()
        return Feed.posts.getPosts().map { post ->
            IosPost(
                id = post.id,
                spotName = post.spotName,
                category = post.category,
                neighborhood = post.neighborhood,
                verified = post.verified,
                checkInCount = 0,
                authorName = post.authorName,
                dateLabel = RelativeTime.format(post.createdAtEpoch, now, currentLanguageCode),
                text = post.text,
                score = post.score,
                voteState = when (post.myVote) {
                    "UP" -> "UP"
                    "DOWN" -> "DOWN"
                    else -> "NONE"
                },
                commentCount = post.commentCount,
                colorHex = SpotCategory.colorHexForLabel(post.category),
            )
        }
    }

    /**
     * All spots to render on the Mapbox map, sourced from the shared
     * [Spots.repository] (the offline-ready seam), as flat [IosSpot] values.
     * [IosSpot.colorHex] is the category's marker color from the shared
     * [SpotCategory] — Swift does not hardcode category colors.
     */
    fun spots(): List<IosSpot> = Spots.repository.getSpots().map { spot ->
        IosSpot(
            id = spot.id,
            name = spot.name,
            category = spot.category,
            neighborhood = spot.neighborhood,
            verified = spot.verified,
            checkInCount = spot.checkInCount,
            lat = spot.lat,
            lng = spot.lng,
            colorHex = SpotCategory.colorHexForLabel(spot.category),
        )
    }

    /**
     * The "#RRGGBB" marker color for a stored category [category] label,
     * resolved from the shared [SpotCategory]. Flat String in/out. Exposed so
     * any future Swift caller resolves colors from the single shared source.
     */
    fun categoryColorHex(category: String): String =
        SpotCategory.colorHexForLabel(category)

    /**
     * The fixed category labels (the 8 [SpotCategory] entries), in declaration
     * order, as a flat `List<String>`. The create-spot sheet renders its
     * category pills from THIS list so Swift never hardcodes the category list;
     * labels stay DYNAMIC content (not i18n), consistent with the convention
     * that spot categories are not translated.
     */
    fun categories(): List<String> = SpotCategory.entries.map { it.label }

    /**
     * Persist a NEW spot through the shared [Spots.repository] so it shows up as
     * a pin on the map. Flat types only (String / Double) cross the boundary —
     * no nullable, no Long, no enum.
     *
     * The caller supplies a unique [id] (generated Swift-side with `UUID`, see
     * AddSpotSheetView) so id generation is not duplicated shared-side.
     * [neighborhood] may be an empty string for now (no reverse geocoding yet).
     * The stored [Spot.placeholderColorArgb] is derived from the category's own
     * marker color (a reasonable neutral default; the map pin color itself is
     * resolved from the category label via [SpotCategory.colorHexForLabel], so
     * this field only backs any future placeholder surface).
     *
     * New spots are in-memory only (they do NOT survive relaunch) — the
     * in-memory [Spots.repository] is the single source of truth the map
     * re-reads via [spots].
     */
    fun addSpot(
        id: String,
        name: String,
        category: String,
        neighborhood: String,
        lat: Double,
        lng: Double,
    ) {
        val spot = Spot(
            id = id,
            name = name,
            category = category,
            neighborhood = neighborhood,
            verified = false,
            checkInCount = 0,
            // Derived from the category's marker color (full alpha), via the
            // shared SpotMapping helper so the bridge and the offline cache
            // agree on the same derivation.
            placeholderColorArgb = SpotMapping.argbForCategory(category),
            lat = lat,
            lng = lng,
        )
        Spots.repository.addSpot(spot)
    }

    /**
     * Ask the shared repository to refresh spots from the backend, invoking the
     * flat [completion] on the main thread when the kick-off has been
     * scheduled. FLAT: no args, plain closure, no suspend/supabase/SQLDelight
     * type crosses.
     *
     * The refresh itself runs in the background inside the shared module (the
     * CachingSpotRepository's own scope); [getSpots] triggers it. This hook
     * simply re-triggers it on demand (e.g. a Swift pull-to-refresh) and lets
     * Swift re-read [spots] afterwards. A live remote result also arrives via
     * [observeSpots]. [completion] fires on the main thread via [authScope].
     */
    fun refreshSpots(completion: () -> Unit) {
        // Reading spots() kicks off the background remote refresh/flush.
        Spots.repository.getSpots()
        authScope.launch { completion() }
    }

    /**
     * Observe cache changes caused by a background remote refresh or a
     * pending-insert flush. [listener] is invoked (no args) on the MAIN THREAD
     * whenever the shared cache changes, so a SwiftUI view can re-read
     * [spots]. FLAT: no args, plain closure; no suspend/Flow/supabase/SQLDelight
     * type crosses.
     *
     * Optional for the map: the existing MapBaseView already re-reads
     * FeedData.spots() in-session after a create, and remote refreshes reflect
     * on the next view appearance. Wiring this listener additionally makes a
     * background remote refresh/flush reflect LIVE without any user action.
     */
    fun observeSpots(listener: () -> Unit) {
        Spots.setChangeListener {
            // Hop onto the main thread so Swift @Observable/@State mutations are
            // safe, mirroring the auth-callback threading contract.
            authScope.launch { listener() }
        }
    }

    // MARK: Feed (Phase B — flat, callback-based)

    /**
     * Ask the shared repository to refresh the feed from the backend, invoking
     * the flat [completion] on the MAIN THREAD once the refresh has been
     * kicked off. FLAT: no args, plain closure; no suspend/supabase/SQLDelight
     * type crosses. Mirrors [refreshSpots].
     *
     * Reading [Feed.posts].getPosts() kicks off the background remote
     * refresh/replace-all; a live remote result also arrives via [observeFeed].
     * Swift re-reads [posts] afterwards (e.g. on a pull-to-refresh).
     */
    fun loadFeed(completion: () -> Unit) {
        Feed.posts.getPosts()
        authScope.launch { completion() }
    }

    /**
     * Observe feed/comment cache changes caused by a background remote refresh
     * or a pending-comment flush. [listener] is invoked (no args) on the MAIN
     * THREAD whenever the shared feed cache changes, so a SwiftUI view can
     * re-read [posts] / [comments]. FLAT: no args, plain closure; no
     * suspend/Flow/supabase/SQLDelight type crosses. Mirrors [observeSpots].
     */
    fun observeFeed(listener: () -> Unit) {
        Feed.setChangeListener {
            authScope.launch { listener() }
        }
    }

    /**
     * Add a comment with [text] to the post [postId] through the shared
     * [Feed.comments] repository: an optimistic local write-through (shows at
     * once) + an enqueue + a background remote push. The flat [completion] is
     * invoked on the MAIN THREAD once the add has been scheduled, so Swift can
     * re-read [comments]. FLAT: String args, plain closure; no
     * suspend/supabase/SQLDelight type crosses.
     *
     * The optimistic author name is the current user's display name/email from
     * [AuthRepository]; the author id (for the remote insert) likewise comes
     * from the session. Offline / unconfigured / unauthenticated leaves the
     * comment queued and never crashes.
     */
    fun addComment(postId: String, text: String, completion: () -> Unit) {
        Feed.comments.addComment(postId, text)
        authScope.launch { completion() }
    }

    /**
     * Map a Mapbox POI's [maki] icon id and symbol [clazz] to a category label
     * (flat String in/out). Wired for Phase 4's POI-adopt prefill; exposed here
     * so the category spec is consumed from the single shared source.
     */
    fun categoryForMaki(maki: String, clazz: String): String =
        SpotCategory.categoryForMaki(maki, clazz)

    /**
     * The comments for [postId], sourced from the shared offline-first
     * [Feed.comments] repository (cache-first + background remote refresh),
     * mapped to flat [IosComment]. Empty list when the post has no comments
     * (drives the empty state). The [IosComment] output shape is UNCHANGED.
     */
    fun comments(postId: String): List<IosComment> {
        val now = currentEpochMillis()
        return Feed.comments.getComments(postId).map { comment ->
            IosComment(
                id = comment.id,
                authorName = comment.authorName,
                // Formatted shared-side from the comment's created_at epoch for
                // the current language (replacing SampleData's baked labels).
                timeLabel = RelativeTime.format(comment.createdAtEpoch, now, currentLanguageCode),
                text = comment.text,
            )
        }
    }

    /**
     * Shared report reasons with LOCALIZED labels for the current language.
     *
     * The stable [ReportReason.key] (and the list order) still comes from
     * SampleData; only the display [IosReportReason.label] is resolved from the
     * current language's string table.
     */
    fun reportReasons(): List<IosReportReason> {
        val s = AppStrings.of(currentLanguageCode)
        return SampleData.reportReasons.map { reason ->
            IosReportReason(
                key = reason.key,
                label = reasonLabel(s, reason.key, reason.label),
            )
        }
    }

    /**
     * Shared map filter chips with LOCALIZED labels for the current language.
     *
     * SampleData owns the chip order + active flags via stable keys; the
     * display label is resolved from the current language's string table here.
     */
    fun mapFilters(): List<IosFilterChip> {
        val s = AppStrings.of(currentLanguageCode)
        return SampleData.mapFilters.map { chip ->
            IosFilterChip(
                key = chip.key,
                label = chipLabel(s, chip.key),
                active = chip.active,
            )
        }
    }

    /**
     * Resolve a report-reason label from [s] by stable [key]. Falls back to the
     * SampleData German [fallback] if a key is somehow unknown (defensive; all
     * current keys are mapped).
     */
    private fun reasonLabel(s: Strings, key: String, fallback: String): String = when (key) {
        "spam" -> s.reasonSpam
        "harassment" -> s.reasonHarassment
        "inappropriate" -> s.reasonInappropriate
        "wrong_location" -> s.reasonWrongLocation
        "other" -> s.reasonOther
        else -> fallback
    }

    /** Resolve a map-chip label from [s] by stable [key]. */
    private fun chipLabel(s: Strings, key: String): String = when (key) {
        "chip_my_pins" -> s.chipMyPins
        "chip_all" -> s.chipAll
        "chip_cafes" -> s.chipCafes
        "chip_parks" -> s.chipParks
        "chip_bars" -> s.chipBars
        "chip_hidden" -> s.chipHidden
        "chip_verified" -> s.chipVerified
        "chip_friends" -> s.chipFriends
        "chip_saved" -> s.chipSaved
        else -> key
    }

}
