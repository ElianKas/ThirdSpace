package com.example.thirdspace

import com.example.thirdspace.data.SampleData
import com.example.thirdspace.data.Spot
import com.example.thirdspace.data.SpotCategory
import com.example.thirdspace.data.Spots
import com.example.thirdspace.data.VoteState
import com.example.thirdspace.i18n.AppLanguage
import com.example.thirdspace.i18n.AppStrings
import com.example.thirdspace.i18n.Strings

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
            placeholderColorArgb = argbForCategory(category),
            lat = lat,
            lng = lng,
        )
        Spots.repository.addSpot(spot)
    }

    /**
     * Derive an opaque ARGB Long from a category label's "#RRGGBB" marker color
     * (full alpha). Used only as the new spot's [Spot.placeholderColorArgb]
     * default; the map pin color is resolved separately from the category.
     */
    private fun argbForCategory(category: String): Long {
        val hex = SpotCategory.colorHexForLabel(category).removePrefix("#")
        val rgb = hex.toLong(16)
        return 0xFF000000L or rgb
    }

    /**
     * Map a Mapbox POI's [maki] icon id and symbol [clazz] to a category label
     * (flat String in/out). Wired for Phase 4's POI-adopt prefill; exposed here
     * so the category spec is consumed from the single shared source.
     */
    fun categoryForMaki(maki: String, clazz: String): String =
        SpotCategory.categoryForMaki(maki, clazz)

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
