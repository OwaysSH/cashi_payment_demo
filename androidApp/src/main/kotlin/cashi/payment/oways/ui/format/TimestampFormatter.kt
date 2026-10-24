package cashi.payment.oways.ui.format

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Instant

/**
 * Formats a payment timestamp for display, e.g. "Oct 23, 2026, 9:15 AM". Uses `java.text.DateFormat`
 * (API 1) instead of `java.time` (API 26), so minSdk stays 24.
 */
class TimestampFormatter(
    private val locale: Locale = Locale.getDefault(),
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {
    fun format(instant: Instant): String {
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).apply {
            this.timeZone = this@TimestampFormatter.timeZone
        }
        return dateFormat.format(Date(instant.toEpochMilliseconds()))
    }
}