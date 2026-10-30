package com.example.thirdspace.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [SampleData.reportReasons], the single source of truth for the
 * native "Melden" (report) sheet reason list.
 *
 * Reuses the existing kotlin.test framework wired into commonTest (no new
 * dependency). Runs on every target the KMP toolchain can run host/native
 * tests on (Android host test + iOS simulator native test).
 */
class ReportReasonsTest {

    @Test
    fun hasExactlyFiveReasons() {
        assertEquals(5, SampleData.reportReasons.size)
    }

    @Test
    fun keysMatchExpectedOrder() {
        assertEquals(
            listOf("spam", "harassment", "inappropriate", "wrong_location", "other"),
            SampleData.reportReasons.map { it.key },
        )
    }

    @Test
    fun labelsMatchExpectedGermanStrings() {
        assertEquals(
            listOf("Spam", "Belästigung", "Unangemessener Inhalt", "Falsche Ortsangabe", "Sonstiges"),
            SampleData.reportReasons.map { it.label },
        )
    }

    @Test
    fun allKeysAndLabelsAreNonEmpty() {
        assertTrue(SampleData.reportReasons.all { it.key.isNotEmpty() && it.label.isNotEmpty() })
    }
}
