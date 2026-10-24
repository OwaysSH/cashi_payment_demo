package cashi.payment.oways.shared.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AmountParserTest {

    @Test
    fun parse_acceptsDotOrCommaDecimals() {
        mapOf(
            "10.5" to 10.5,
            "10,5" to 10.5,
            "10.50" to 10.5,
            " 10.50 " to 10.5,
            "100" to 100.0,
            "0.01" to 0.01,
            "007" to 7.0,
        ).forEach { (text, expected) ->
            assertEquals(expected, AmountParser.parse(text, MAX_FRACTION_DIGITS), "text '$text'")
        }
    }

    @Test
    fun parse_rejectsTooManyFractionDigits_countedInText() {
        // "1,500" / "1.500" (meant as one thousand five hundred) must not become 1.5.
        listOf("1,500", "1.500", "10.123", "0.001").forEach { text ->
            assertNull(AmountParser.parse(text, MAX_FRACTION_DIGITS), "text '$text'")
        }
    }

    @Test
    fun parse_rejectsMalformedInput() {
        listOf(
            "", "   ", "abc", "1,000.50", "1.000,50", "-5", "+5", "1e3",
            "10.5.1", "10.", ".5", "NaN", "Infinity", "1 000",
        ).forEach { text ->
            assertNull(AmountParser.parse(text, MAX_FRACTION_DIGITS), "text '$text'")
        }
    }

    @Test
    fun parse_respectsGivenMaxFractionDigits() {
        assertNull(AmountParser.parse("10.5", maxFractionDigits = 0))
        assertEquals(10.0, AmountParser.parse("10", maxFractionDigits = 0))
    }

    private companion object {
        const val MAX_FRACTION_DIGITS = 2
    }
}