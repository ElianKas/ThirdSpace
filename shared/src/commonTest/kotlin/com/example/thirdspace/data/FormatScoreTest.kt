package com.example.thirdspace.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the pure, deterministic score-formatting logic. These run in
 * commonTest so they execute on every target the KMP toolchain can run
 * host/native tests on (Android host test + iOS simulator native test).
 *
 * This is also the "infrastructure proof": if these assertions run and pass,
 * the commonTest source set, kotlin.test dependency wiring, and the discovered
 * Gradle test tasks are all genuinely working.
 */
class FormatScoreTest {

    @Test
    fun formatsValuesBelowOneThousandVerbatim() {
        assertEquals("0", formatScore(0))
        assertEquals("1", formatScore(1))
        assertEquals("214", formatScore(214))
        assertEquals("999", formatScore(999))
    }

    @Test
    fun formatsThousandsWithOneDecimalAndCommaSeparator() {
        // 1340 -> 1.34k -> rounded to one decimal -> 1,3k
        assertEquals("1,3k", formatScore(1340))
        // 1000 -> exactly 1k, no decimal shown
        assertEquals("1k", formatScore(1000))
    }

    @Test
    fun dropsDecimalForLargeThousands() {
        // abs(k) >= 10 rounds to whole thousands
        assertEquals("12k", formatScore(12345))
        assertEquals("10k", formatScore(10000))
    }

    @Test
    fun handlesNegativeScores() {
        assertEquals("-5", formatScore(-5))
        assertEquals("-1,3k", formatScore(-1340))
    }
}
