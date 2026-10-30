package com.example.thirdspace.i18n

/**
 * Single, encapsulated resolution point for localized strings.
 *
 * All callers (shared code, the FeedBridge, tests) go through [AppStrings]
 * rather than referencing [De] / [En] directly. This keeps the language ->
 * table mapping in one place and makes the string-code overload the natural
 * entry point from the flat Swift<->Kotlin boundary.
 */
object AppStrings {

    /** Resolve the string table for a typed [language]. */
    fun of(language: AppLanguage): Strings = when (language) {
        AppLanguage.DE -> De
        AppLanguage.EN -> En
    }

    /**
     * Resolve the string table for a language [code] ("de" / "en"). Unknown
     * codes fall back to the default language (DE) via [AppLanguage.fromCode].
     */
    fun of(code: String): Strings = of(AppLanguage.fromCode(code))
}
