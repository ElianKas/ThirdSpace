package com.example.thirdspace.i18n

import com.example.thirdspace.data.SampleData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the i18n layer can provide a non-empty localized label for EVERY
 * stable key that [SampleData] owns (report reasons + map filter chips), in
 * BOTH languages.
 *
 * This asserts the same key -> localized-label contract that the iosMain
 * FeedBridge.reportReasons()/mapFilters() rely on, but from commonTest so it
 * runs on both targets (the bridge itself is iosMain and has no test target).
 */
class LocalizedListsTest {

    private fun reasonLabel(s: Strings, key: String): String? = when (key) {
        "spam" -> s.reasonSpam
        "harassment" -> s.reasonHarassment
        "inappropriate" -> s.reasonInappropriate
        "wrong_location" -> s.reasonWrongLocation
        "other" -> s.reasonOther
        else -> null
    }

    private fun chipLabel(s: Strings, key: String): String? = when (key) {
        "chip_my_pins" -> s.chipMyPins
        "chip_all" -> s.chipAll
        "chip_cafes" -> s.chipCafes
        "chip_parks" -> s.chipParks
        "chip_bars" -> s.chipBars
        "chip_hidden" -> s.chipHidden
        "chip_verified" -> s.chipVerified
        "chip_friends" -> s.chipFriends
        "chip_saved" -> s.chipSaved
        else -> null
    }

    @Test
    fun everyReportReasonKeyHasNonEmptyLabelInBothLanguages() {
        for (reason in SampleData.reportReasons) {
            for (table in listOf(De, En)) {
                val label = reasonLabel(table, reason.key)
                assertTrue(label != null && label.isNotEmpty(), "missing label for ${reason.key}")
            }
        }
    }

    @Test
    fun everyMapChipKeyHasNonEmptyLabelInBothLanguages() {
        for (chip in SampleData.mapFilters) {
            for (table in listOf(De, En)) {
                val label = chipLabel(table, chip.key)
                assertTrue(label != null && label.isNotEmpty(), "missing label for ${chip.key}")
            }
        }
    }

    @Test
    fun reportReasonLabelsAreLocalizedPerLanguage() {
        // "harassment" is a good sanity check: differs DE vs EN.
        assertEquals("Belästigung", reasonLabel(De, "harassment"))
        assertEquals("Harassment", reasonLabel(En, "harassment"))
    }

    @Test
    fun mapChipLabelsAreLocalizedPerLanguage() {
        assertEquals("Meine Pins", chipLabel(De, "chip_my_pins"))
        assertEquals("My pins", chipLabel(En, "chip_my_pins"))
    }
}
