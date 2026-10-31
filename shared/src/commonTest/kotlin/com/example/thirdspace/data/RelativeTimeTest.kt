package com.example.thirdspace.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [RelativeTime] — the shared relative-time formatter + the ISO
 * `timestamptz` -> epoch-millis parser that replace SampleData's baked German
 * date strings. Runs on BOTH shared targets (Android host + iOS simulator);
 * pure Kotlin, no backend/driver/network.
 *
 * All [RelativeTime.format] cases use a FIXED now + FIXED timestamps so the
 * expected labels are exact and deterministic, in both de and en.
 */
class RelativeTimeTest {

    private val second = 1_000L
    private val minute = 60 * second
    private val hour = 60 * minute
    private val day = 24 * hour
    private val now = 1_000_000_000_000L

    // --- format() buckets, exact labels, de + en ---

    @Test
    fun justNowUnderOneMinute() {
        assertEquals("gerade eben", RelativeTime.format(now - 30 * second, now, "de"))
        assertEquals("just now", RelativeTime.format(now - 30 * second, now, "en"))
        // Exactly now.
        assertEquals("gerade eben", RelativeTime.format(now, now, "de"))
        assertEquals("just now", RelativeTime.format(now, now, "en"))
    }

    @Test
    fun minutesBucket() {
        assertEquals("vor 2 Min.", RelativeTime.format(now - 2 * minute, now, "de"))
        assertEquals("2m ago", RelativeTime.format(now - 2 * minute, now, "en"))
        assertEquals("vor 59 Min.", RelativeTime.format(now - 59 * minute, now, "de"))
        assertEquals("59m ago", RelativeTime.format(now - 59 * minute, now, "en"))
    }

    @Test
    fun hoursBucket() {
        assertEquals("vor 2 Std.", RelativeTime.format(now - 2 * hour, now, "de"))
        assertEquals("2h ago", RelativeTime.format(now - 2 * hour, now, "en"))
        assertEquals("vor 23 Std.", RelativeTime.format(now - 23 * hour, now, "de"))
        assertEquals("23h ago", RelativeTime.format(now - 23 * hour, now, "en"))
    }

    @Test
    fun daysBucketSingularAndPlural() {
        assertEquals("vor 1 Tag", RelativeTime.format(now - 1 * day, now, "de"))
        assertEquals("1d ago", RelativeTime.format(now - 1 * day, now, "en"))
        assertEquals("vor 3 Tagen", RelativeTime.format(now - 3 * day, now, "de"))
        assertEquals("3d ago", RelativeTime.format(now - 3 * day, now, "en"))
    }

    @Test
    fun unknownLangFallsBackToGerman() {
        assertEquals("vor 2 Std.", RelativeTime.format(now - 2 * hour, now, "xx"))
    }

    @Test
    fun futureTimestampClampsToJustNow() {
        // Clock skew / future created_at should not produce a negative label.
        assertEquals("gerade eben", RelativeTime.format(now + 5 * hour, now, "de"))
        assertEquals("just now", RelativeTime.format(now + 5 * hour, now, "en"))
    }

    // --- parseIsoToEpochMillis() ---

    @Test
    fun parseIsoUtcZ() {
        // 2024-03-15T12:30:00Z -> known epoch millis.
        // Computed via days_from_civil: 1710505800 seconds -> *1000.
        assertEquals(1_710_505_800_000L, RelativeTime.parseIsoToEpochMillis("2024-03-15T12:30:00Z"))
    }

    @Test
    fun parseIsoWithFractionAndOffset() {
        // 2024-03-15T14:30:00.123+02:00 == 12:30:00.123 UTC.
        assertEquals(
            1_710_505_800_123L,
            RelativeTime.parseIsoToEpochMillis("2024-03-15T14:30:00.123+02:00"),
        )
    }

    @Test
    fun parseIsoSpaceSeparatorShortOffset() {
        // Postgres style: "2024-03-15 12:30:00+00".
        assertEquals(
            1_710_505_800_000L,
            RelativeTime.parseIsoToEpochMillis("2024-03-15 12:30:00+00"),
        )
    }

    @Test
    fun parseEpochZeroReference() {
        // The Unix epoch itself.
        assertEquals(0L, RelativeTime.parseIsoToEpochMillis("1970-01-01T00:00:00Z"))
    }

    @Test
    fun parseUnparseableReturnsZero() {
        assertEquals(0L, RelativeTime.parseIsoToEpochMillis("not-a-date"))
        assertEquals(0L, RelativeTime.parseIsoToEpochMillis(""))
    }
}
