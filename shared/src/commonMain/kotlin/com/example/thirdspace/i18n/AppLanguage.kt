package com.example.thirdspace.i18n

/**
 * Supported UI languages for the app.
 *
 * DE is the default. [code] is the flat, ObjC-friendly string ("de" / "en")
 * that crosses the Kotlin<->Swift boundary — the enum itself NEVER crosses the
 * boundary (see the FeedBridge conventions).
 *
 * [displayName] is the language's ENDONYM (its own name in its own language,
 * e.g. "Deutsch" / "English"). Endonyms are proper names and are intentionally
 * NOT translated, so the language picker always lists each option in its own
 * language regardless of the currently active UI language.
 */
enum class AppLanguage(val code: String, val displayName: String) {
    DE("de", "Deutsch"),
    EN("en", "English");

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
