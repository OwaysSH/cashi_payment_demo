package cashi.payment.oways.shared.domain.money

import cashi.payment.oways.shared.domain.model.Currency
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.ulp

/** Business limit: the largest single payment, 1,000,000.00 in a two-decimal currency. */
const val MAX_AMOUNT_MINOR: Long = 100_000_000L

private const val DECIMAL_BASE = 10.0

/**
 * Floating-point slack, in ULPs of the scaled value, still treated as a whole minor unit.
 * A decimal literal such as 0.29 is off by at most ~1.5 ULP after scaling; an extra decimal
 * digit (e.g. 10.123) is off by at least 0.1 minor units, far above this within [MAX_AMOUNT_MINOR].
 */
private const val ROUNDING_TOLERANCE_ULPS = 2

/**
 * Converts a major-unit [amount] (e.g. 10.5) to exact minor units (e.g. 1050).
 * Returns `null` if the amount is not finite, has more decimals than [Currency.fractionDigits],
 * or exceeds [MAX_AMOUNT_MINOR] in absolute value. The sign is preserved.
 */
fun Currency.toMinorUnits(amount: Double): Long? {
    if (!amount.isFinite()) return null

    val scaled = amount * minorUnitFactor()
    val rounded = round(scaled)
    val isWholeMinorUnit = abs(scaled - rounded) <= ROUNDING_TOLERANCE_ULPS * scaled.ulp
    val isWithinLimit = abs(rounded) <= MAX_AMOUNT_MINOR.toDouble()

    return if (isWholeMinorUnit && isWithinLimit) rounded.toLong() else null
}

/** Converts exact minor units (e.g. 1050) to a major-unit amount (e.g. 10.5). */
fun Currency.toMajorUnits(amountMinor: Long): Double = amountMinor / minorUnitFactor()

private fun Currency.minorUnitFactor(): Double = DECIMAL_BASE.pow(fractionDigits)