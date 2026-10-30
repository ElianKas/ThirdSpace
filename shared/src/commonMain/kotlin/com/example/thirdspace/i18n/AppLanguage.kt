package com.example.thirdspace.i18n

/**
 * Supported UI languages for the app.
 *
 * DE is the default. [code] is the flat, ObjC-friendly string ("de" / "en")
 * that crosses the Kotlin<->Swift boundary — the enum itself NEVER crosses the
 * boundary (see the FeedBridge conventions).
 */
enum class AppLanguage(val code: String) {
    DE("de"),
    EN("en");

    companion object {
        /**
         * Resolve a language from its string [code]. Unknown or unexpected
         * codes fall back to [DE] (the default language), so the UI always has
         * a valid language even if a bad code is passed across the boundary.
         */
        fun fromCode(code: String): AppLanguage =
            entries.firstOrNull { it.code == code } ?: DE
    }
}
