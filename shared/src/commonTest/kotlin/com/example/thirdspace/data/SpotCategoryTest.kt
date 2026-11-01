package com.example.thirdspace.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [SpotCategory] (the single source of truth for the category
 * list, marker colors, and maki->category mapping) and for [SpotRepository].
 *
 * Reuses the existing kotlin.test framework wired into commonTest (no new
 * dependency). Runs on both the Android host target and the iOS simulator
 * target per the verification strategy.
 */
class SpotCategoryTest {

    private val hexRegex = Regex("^#[0-9A-Fa-f]{6}$")

    @Test
    fun everyCategoryHasNonBlankLabel() {
        assertTrue(SpotCategory.entries.all { it.label.isNotBlank() })
    }

    @Test
    fun everyCategoryHasValidRrggbbColor() {
        assertTrue(SpotCategory.entries.all { hexRegex.matches(it.colorHex) })
    }

    @Test
    fun categoryListAndColorsMatchLegacySpec() {
        assertEquals("#F26419", SpotCategory.CAFE.colorHex)
        assertEquals("#7C3AED", SpotCategory.BAR.colorHex)
        assertEquals("#15803D", SpotCategory.PARK.colorHex)
        assertEquals("#E11D48", SpotCategory.RESTAURANT.colorHex)
        assertEquals("#0369A1", SpotCategory.ROOFTOP.colorHex)
        assertEquals("#B45309", SpotCategory.GEHEIMTIPP.colorHex)
        assertEquals("#DB2777", SpotCategory.CLUB_SPOT.colorHex)
        assertEquals("#57534E", SpotCategory.SONSTIGES.colorHex)
        assertEquals(
            listOf("Café", "Bar", "Park", "Restaurant", "Rooftop", "Geheimtipp", "Club/Spot", "Sonstiges"),
            SpotCategory.entries.map { it.label },
        )
    }

    @Test
    fun colorHexForLabelResolvesKnownLabelAndFallsBack() {
        assertEquals("#7C3AED", SpotCategory.colorHexForLabel("Bar"))
        assertEquals("#F26419", SpotCategory.colorHexForLabel("Café"))
        // Unknown label falls back to the Sonstiges color.
        assertEquals(SpotCategory.SONSTIGES.colorHex, SpotCategory.colorHexForLabel("Nope"))
    }

    @Test
    fun categoryForMakiMatchesDocumentedMapping() {
        assertEquals("Café", SpotCategory.categoryForMaki("cafe", ""))
        assertEquals("Bar", SpotCategory.categoryForMaki("bar", ""))
        assertEquals("Restaurant", SpotCategory.categoryForMaki("restaurant", ""))
        assertEquals("Restaurant", SpotCategory.categoryForMaki("fast-food", ""))
        assertEquals("Restaurant", SpotCategory.categoryForMaki("ice-cream", ""))
        assertEquals("Park", SpotCategory.categoryForMaki("park", ""))
        assertEquals("Park", SpotCategory.categoryForMaki("garden", ""))
        assertEquals("Park", SpotCategory.categoryForMaki("", "park"))
        assertEquals("Club/Spot", SpotCategory.categoryForMaki("nightclub", ""))
        assertEquals("Restaurant", SpotCategory.categoryForMaki("", "food_and_drink"))
        assertEquals("Sonstiges", SpotCategory.categoryForMaki("unknown", ""))
        assertEquals("Sonstiges", SpotCategory.categoryForMaki("", ""))
    }

    @Test
    fun categoryForMakiIsCaseInsensitive() {
        assertEquals("Café", SpotCategory.categoryForMaki("CAFE", ""))
        assertEquals("Park", SpotCategory.categoryForMaki("", "PARK"))
    }

    @Test
    fun repositoryReturnsSeededSpots() {
        val repo = InMemorySpotRepository()
        val spots = repo.getSpots()
        // Seeded from the six distinct sample-feed spots. Assert by NAME (not a
        // hardcoded id literal): the seed spot ids are now canonical uuids (so
        // they are never a `22P02` foot-gun when saved to a collection), which
        // intentionally are not asserted verbatim here.
        assertEquals(6, spots.size)
        assertTrue(spots.any { it.name == "Prater Garten" })
        // Seeded spots carry real coordinates.
        val prater = spots.first { it.name == "Prater Garten" }
        assertEquals(52.5403, prater.lat)
        assertEquals(13.4120, prater.lng)
    }

    @Test
    fun repositoryReflectsAddSpot() {
        val repo = InMemorySpotRepository()
        val before = repo.getSpots().size
        val added = Spot(
            id = "spot-new",
            name = "Neuer Ort",
            category = "Café",
            neighborhood = "Wedding",
            verified = false,
            checkInCount = 0,
            placeholderColorArgb = 0xFF000000,
            lat = 52.5500,
            lng = 13.3600,
        )
        repo.addSpot(added)
        val after = repo.getSpots()
        assertEquals(before + 1, after.size)
        assertTrue(after.any { it.id == "spot-new" })
    }
}
