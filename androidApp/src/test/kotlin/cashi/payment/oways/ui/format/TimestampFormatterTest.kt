package cashi.payment.oways.ui.format

import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TimestampFormatterTest {

    private val instant = Instant.parse("2026-10-23T09:15:30Z")
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun formatsMediumDateAndShortTime_inTheGivenLocaleAndZone() {
        assertEquals("Oct 23, 2026, 9:15 AM", plain(TimestampFormatter(Locale.US, utc).format(instant)))
        assertEquals("23.10.2026, 09:15", plain(TimestampFormatter(Locale.GERMANY, utc).format(instant)))
    }

    @Test
    fun usesTheGivenTimeZone() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")

        assertEquals("Oct 23, 2026, 11:15 AM", plain(TimestampFormatter(Locale.US, berlin).format(instant)))
    }

    /** Some JDKs put a narrow no-break space before "AM"; compare with plain spaces. */
    private fun plain(text: String) = text.replace(' ', ' ')
}