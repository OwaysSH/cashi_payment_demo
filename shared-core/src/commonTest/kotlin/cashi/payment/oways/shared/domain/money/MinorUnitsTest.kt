package cashi.payment.oways.shared.domain.money

import cashi.payment.oways.shared.domain.model.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MinorUnitsTest {

    private val currency = Currency.USD

    @Test
    fun toMinorUnits_convertsExactly_forValidAmounts() {
        val cases = mapOf(
            10.5 to 1050L,
            10.1 to 1010L,
            0.29 to 29L,
            0.01 to 1L,
            19.99 to 1999L,
            100.0 to 10_000L,
            1_000_000.0 to MAX_AMOUNT_MINOR,
            -10.5 to -1050L,
        )
        cases.forEach { (amount, expected) ->
            assertEquals(expected, currency.toMinorUnits(amount), "amount $amount")
        }
    }

    @Test
    fun toMinorUnits_returnsNull_forTooManyDecimals() {
        listOf(10.123, 1.005, 0.001).forEach { amount ->
            assertNull(currency.toMinorUnits(amount), "amount $amount")
        }
    }

    @Test
    fun toMinorUnits_returnsNull_forNonFiniteAmounts() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { amount ->
            assertNull(currency.toMinorUnits(amount), "amount $amount")
        }
    }

    @Test
    fun toMinorUnits_returnsNull_aboveBusinessLimit() {
        assertNull(currency.toMinorUnits(1_000_000.01))
        assertNull(currency.toMinorUnits(-1_000_000.01))
    }

    @Test
    fun toMajorUnits_convertsMinorUnits() {
        assertEquals(10.5, currency.toMajorUnits(1050L))
        assertEquals(0.29, currency.toMajorUnits(29L))
    }

    @Test
    fun roundTrip_preservesEveryMinorAmount() {
        val nearLimit = (MAX_AMOUNT_MINOR - ROUND_TRIP_SAMPLE)..MAX_AMOUNT_MINOR
        (0L..ROUND_TRIP_SAMPLE).plus(nearLimit).forEach { minor ->
            assertEquals(minor, currency.toMinorUnits(currency.toMajorUnits(minor)), "minor $minor")
        }
    }

    private companion object {
        const val ROUND_TRIP_SAMPLE = 100_000L
    }
}