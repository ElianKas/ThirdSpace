package com.example.thirdspace.data

/**
 * Pure, deterministic relative-time helper used shared-side to turn a post's /
 * comment's `created_at` epoch into a localized label (the feed's `dateLabel`
 * and the comments sheet's `timeLabel`), replacing SampleData's pre-baked
 * German strings.
 *
 * DESIGN DECISION (documented): the relative-time words are generated here in
 * Kotlin from the language code via a `when (lang)`, NOT added as new [i18n]
 * `Strings` keys. These labels are parameterized/formatted (a number + a unit,
 * or a "just now" special case) rather than static UI chrome, so keeping them
 * self-contained in [RelativeTime] keeps the i18n `Strings` table (and its
 * compile-time completeness test) focused on static strings. `StringsTest` is
 * unaffected. This object is unit-tested directly (exact labels in de+en).
 *
 * BOUNDARY NOTE: this stays INSIDE the shared module. The bridge calls
 * [format] and passes the resulting String across the flat FeedBridge boundary
 * — the epoch [Long] never crosses.
 */
object RelativeTime {

    private const val SECOND = 1_000L
    private const val MINUTE = 60 * SECOND
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /**
     * Format [epochMillis] relative to [nowMillis] as a localized label for the
     * language [lang] ("de" / "en"; anything else falls back to German, matching
     * the app's default-DE convention).
     *
     * Buckets (deterministic, no calendar math):
     * - < 1 min            -> "gerade eben" / "just now"
     * - < 1 hour           -> "vor N Min." / "Nm ago"
     * - < 1 day            -> "vor N Std." / "Nh ago"
     * - >= 1 day           -> "vor N Tag(en)" / "Nd ago"
     *
     * A future or clock-skewed timestamp (epoch > now) clamps to "just now".
     */
    fun format(epochMillis: Long, nowMillis: Long, lang: String): String {
        val diff = (nowMillis - epochMillis).coerceAtLeast(0L)
        val isEn = lang == "en"
        return when {
            diff < MINUTE -> if (isEn) "just now" else "gerade eben"
            diff < HOUR -> {
                val n = (diff / MINUTE).toInt()
                if (isEn) "${n}m ago" else "vor $n Min."
            }
            diff < DAY -> {
                val n = (diff / HOUR).toInt()
                if (isEn) "${n}h ago" else "vor $n Std."
            }
            else -> {
                val n = (diff / DAY).toInt()
                if (isEn) "${n}d ago" else {
                    val unit = if (n == 1) "Tag" else "Tagen"
                    "vor $n $unit"
                }
            }
        }
    }

    /**
     * Parse a PostgREST `timestamptz` ISO-8601 string to epoch MILLIS, without a
     * datetime dependency (kotlinx-datetime is not on the classpath; this keeps
     * the shared module's deps unchanged).
     *
     * Supports the shapes Supabase returns, e.g.:
     *   "2024-03-15T12:30:00Z"
     *   "2024-03-15T12:30:00.123456+00:00"
     *   "2024-03-15T12:30:00.123+02:00"
     *   "2024-03-15 12:30:00+00"   (space separator, short offset)
     *
     * Returns 0L on an unparseable string (defensive: a bad row sorts oldest and
     * formats as a large "days ago", never crashes).
     */
    fun parseIsoToEpochMillis(iso: String): Long {
        val s = iso.trim()
        if (s.isEmpty()) return 0L
        return try {
            // Split date and time on 'T' or a space.
            val sepIndex = s.indexOfFirst { it == 'T' || it == ' ' }
            if (sepIndex < 0) return 0L
            val datePart = s.substring(0, sepIndex)
            var timePart = s.substring(sepIndex + 1)

            // Extract the timezone offset (Z, +hh:mm, -hh:mm, +hh, -hh) if any.
            var offsetMillis = 0L
            val zIndex = timePart.indexOf('Z')
            if (zIndex >= 0) {
                timePart = timePart.substring(0, zIndex)
            } else {
                // Find a +/- that starts the offset (after the time, so skip
                // index 0). Times never contain a sign internally.
                val signIndex = timePart.indexOfLast { it == '+' || it == '-' }
                if (signIndex > 0) {
                    val sign = if (timePart[signIndex] == '-') -1 else 1
                    val off = timePart.substring(signIndex + 1)
                    timePart = timePart.substring(0, signIndex)
                    val offParts = off.split(':')
                    val offHours = offParts.getOrNull(0)?.toIntOrNull() ?: 0
                    val offMins = offParts.getOrNull(1)?.toIntOrNull() ?: 0
                    offsetMillis = sign * (offHours * HOUR + offMins * MINUTE)
                }
            }

            val dateParts = datePart.split('-')
            val year = dateParts[0].toInt()
            val month = dateParts[1].toInt()
            val day = dateParts[2].toInt()

            // Strip fractional seconds for the integer seconds; keep millis.
            val dotIndex = timePart.indexOf('.')
            var fractionMillis = 0L
            val hms = if (dotIndex >= 0) {
                val frac = timePart.substring(dotIndex + 1).take(3).padEnd(3, '0')
                fractionMillis = frac.toLongOrNull() ?: 0L
                timePart.substring(0, dotIndex)
            } else {
                timePart
            }
            val timeParts = hms.split(':')
            val hour = timeParts.getOrNull(0)?.toInt() ?: 0
            val minute = timeParts.getOrNull(1)?.toInt() ?: 0
            val second = timeParts.getOrNull(2)?.toInt() ?: 0

            val daysSinceEpoch = daysFromCivil(year, month, day)
            val utcMillis = daysSinceEpoch * DAY +
                hour * HOUR + minute * MINUTE + second * SECOND + fractionMillis
            // Convert the local wall-clock time to UTC by subtracting the offset.
            utcMillis - offsetMillis
        } catch (_: Throwable) {
            0L
        }
    }

    /**
     * Days from the Unix epoch (1970-01-01) for a proleptic-Gregorian civil
     * date. Howard Hinnant's well-known `days_from_civil` algorithm — exact,
     * branch-light, and dependency-free. [month] is 1..12, [day] is 1..31.
     */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = (y - era * 400).toLong()                       // [0, 399]
        val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy          // [0, 146096]
        return era.toLong() * 146097 + doe - 719468
    }
}
