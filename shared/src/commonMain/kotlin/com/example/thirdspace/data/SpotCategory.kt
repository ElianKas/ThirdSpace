package com.example.thirdspace.data

/**
 * The fixed set of spot categories: the SINGLE SOURCE OF TRUTH for the category
 * list, each category's map-marker color, and the Mapbox maki/POI-class ->
 * category mapping.
 *
 * Ported verbatim from the legacy web spec:
 * - the fixed list + hex colors from `legacy-project/.../src/lib/categories.ts`
 *   (`CATEGORY_LIST` + `CATEGORY_COLORS`);
 * - the maki/class mapping from `makiToCategory` in the legacy `MapView.tsx`.
 *
 * Category labels are DYNAMIC strings and are intentionally NOT part of the
 * i18n [com.example.thirdspace.i18n.Strings] layer, consistent with the project
 * convention that spot names/categories are not translated. The [label] stored
 * on a [Spot] stays a plain [String]; this enum provides the color + maki
 * mapping AROUND that stored label (look up a category via [fromLabel]).
 *
 * [colorHex] is a "#RRGGBB" string so it can cross the FeedBridge boundary
 * unchanged and feed the Swift `Color(hex:)` initializer directly.
 */
enum class SpotCategory(val label: String, val colorHex: String) {
    CAFE("Café", "#F26419"),
    BAR("Bar", "#7C3AED"),
    PARK("Park", "#15803D"),
    RESTAURANT("Restaurant", "#E11D48"),
    ROOFTOP("Rooftop", "#0369A1"),
    GEHEIMTIPP("Geheimtipp", "#B45309"),
    CLUB_SPOT("Club/Spot", "#DB2777"),
    SONSTIGES("Sonstiges", "#57534E");

    companion object {
        /** The fallback category when a label/maki is unknown. */
        val DEFAULT: SpotCategory = SONSTIGES

        /**
         * Resolve a [SpotCategory] from its stored display [label]
         * (e.g. "Bar"). Falls back to [DEFAULT] for an unknown label so callers
         * always get a usable color.
         */
        fun fromLabel(label: String): SpotCategory =
            entries.firstOrNull { it.label == label } ?: DEFAULT

        /**
         * The "#RRGGBB" marker color for a stored category [label]. Unknown
         * labels resolve to the [DEFAULT] (Sonstiges) color.
         */
        fun colorHexForLabel(label: String): String = fromLabel(label).colorHex

        /**
         * Map a Mapbox POI's `maki` icon id and symbol `class` to a category
         * label (flat [String] in/out so it can cross the FeedBridge boundary).
         *
         * Reproduces the legacy `makiToCategory` exactly (case-insensitive):
         * - maki "cafe" -> Café
         * - maki "bar" -> Bar
         * - maki "restaurant" / "fast-food" / "ice-cream" -> Restaurant
         * - maki "park" / "garden" OR class "park" -> Park
         * - maki "nightclub" -> Club/Spot
         * - class "food_and_drink" -> Restaurant
         * - everything else -> Sonstiges
         *
         * This is wired for Phase 4 (POI-adopt prefill); it is defined here now
         * so the category spec lives in exactly one place.
         */
        fun categoryForMaki(maki: String, clazz: String): String {
            val m = maki.lowercase()
            val c = clazz.lowercase()
            return when {
                m == "cafe" -> CAFE.label
                m == "bar" -> BAR.label
                m == "restaurant" || m == "fast-food" || m == "ice-cream" -> RESTAURANT.label
                m == "park" || m == "garden" || c == "park" -> PARK.label
                m == "nightclub" -> CLUB_SPOT.label
                c == "food_and_drink" -> RESTAURANT.label
                else -> SONSTIGES.label
            }
        }
    }
}
