package com.example.thirdspace.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the shared i18n layer ([Strings], [De], [En], [AppStrings],
 * [AppLanguage]).
 *
 * Reuses the existing kotlin.test framework wired into commonTest (no new
 * dependency). Runs on every target the KMP toolchain can run host/native
 * tests on (Android host test + iOS simulator native test).
 */
class StringsTest {

    /** All non-parameterized string properties, per language instance. */
    private fun allProperties(s: Strings): List<String> = listOf(
        s.feedEyebrow, s.feedTitle,
        s.tabPosts, s.tabMap, s.tabDiscover, s.discoverPlaceholder,
        s.mapSearchPlaceholder, s.mapPlaceholder,
        s.chipMyPins, s.chipAll, s.chipCafes, s.chipParks, s.chipBars,
        s.chipHidden, s.chipVerified, s.chipFriends, s.chipSaved,
        s.spotNoDescription, s.actionRoute, s.actionSave, s.actionWasHere,
        s.writeComment, s.share, s.report,
        s.commentsEmpty, s.commentPlaceholder, s.localAuthorYou, s.justNow,
        s.createTitle, s.createSubtitle, s.createSpotLabel, s.createSpotSearch,
        s.createTextLabel, s.createCaptionPlaceholder, s.createAddPhoto,
        s.createVisibility, s.visibilityPublic, s.visibilityFriends, s.createPost,
        s.reportTitle, s.reportHint, s.reportDetailsPlaceholder, s.reportSubmit,
        s.reportSelected,
        s.reasonSpam, s.reasonHarassment, s.reasonInappropriate,
        s.reasonWrongLocation, s.reasonOther,
        s.profileStatSpots, s.profileStatPosts, s.profileStatFollowers,
        s.profileStatFollowing, s.profileEdit, s.profileTabSpots,
        s.profileTabPosts, s.profileEmptySpots, s.profileEmptyPosts,
        s.profileSettings,
    )

    // (a) Every property key is non-empty in BOTH De and En.

    @Test
    fun allDePropertiesAreNonEmpty() {
        assertTrue(allProperties(De).all { it.isNotEmpty() })
    }

    @Test
    fun allEnPropertiesAreNonEmpty() {
        assertTrue(allProperties(En).all { it.isNotEmpty() })
    }

    // (b) Plural / interpolation behavior.

    @Test
    fun checkInLabelDiffersBySingularPluralDe() {
        assertEquals("war hier", De.checkInLabel(1))
        assertEquals("waren hier", De.checkInLabel(2))
        assertNotEquals(De.checkInLabel(1), De.checkInLabel(2))
    }

    @Test
    fun checkInLabelDiffersBySingularPluralEn() {
        assertEquals("was here", En.checkInLabel(1))
        assertEquals("were here", En.checkInLabel(2))
        assertNotEquals(En.checkInLabel(1), En.checkInLabel(2))
    }

    @Test
    fun showAllCommentsContainsTheNumber() {
        for (table in listOf(De, En)) {
            assertTrue(table.showAllComments(0).contains("0"))
            assertTrue(table.showAllComments(1).contains("1"))
            assertTrue(table.showAllComments(5).contains("5"))
        }
    }

    // (c) Interpolation of spot name and count.

    @Test
    fun shareMessageContainsSpotName() {
        assertTrue(De.shareMessage("X").contains("X"))
        assertTrue(En.shareMessage("X").contains("X"))
    }

    @Test
    fun commentsHeaderContainsTheCount() {
        assertTrue(De.commentsHeader(3).contains("3"))
        assertTrue(En.commentsHeader(3).contains("3"))
    }

    @Test
    fun joinedSinceContainsMonthYearAndPrefixPerLanguage() {
        val monthYear = "März 2024"
        // Both languages interpolate the (non-translated) month/year verbatim.
        assertTrue(De.joinedSince(monthYear).contains(monthYear))
        assertTrue(En.joinedSince(monthYear).contains(monthYear))
        // Each language uses its own prefix.
        assertTrue(De.joinedSince(monthYear).startsWith("seit "))
        assertTrue(En.joinedSince(monthYear).startsWith("since "))
        assertEquals("seit $monthYear", De.joinedSince(monthYear))
        assertEquals("since $monthYear", En.joinedSince(monthYear))
    }

    // (d) AppStrings resolution returns the expected tables / sample values.

    @Test
    fun appStringsResolvesExpectedSampleValues() {
        assertEquals("Speichern", AppStrings.of("de").actionSave)
        assertEquals("Save", AppStrings.of("en").actionSave)
        assertEquals("Feed", AppStrings.of("de").feedTitle)
        assertEquals("Feed", AppStrings.of("en").feedTitle)
    }

    @Test
    fun appStringsResolvesTypedLanguage() {
        assertEquals(De, AppStrings.of(AppLanguage.DE))
        assertEquals(En, AppStrings.of(AppLanguage.EN))
    }

    // (e) Unknown code falls back to DE.

    @Test
    fun unknownCodeFallsBackToDe() {
        assertEquals(De, AppStrings.of("xx"))
        assertEquals("Speichern", AppStrings.of("xx").actionSave)
        assertEquals(AppLanguage.DE, AppLanguage.fromCode("xx"))
    }

    @Test
    fun knownCodesResolveToExpectedLanguage() {
        assertEquals(AppLanguage.DE, AppLanguage.fromCode("de"))
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("en"))
        assertEquals("de", AppLanguage.DE.code)
        assertEquals("en", AppLanguage.EN.code)
    }
}
