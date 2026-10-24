package cashi.payment.oways.shared.domain.validation

/**
 * Parses the amount typed in the payment form. Accepts digits with an optional `.` or `,` decimal
 * separator (many locales' numeric keyboards type a comma), surrounded by optional whitespace.
 *
 * Fraction digits are counted in the TEXT, before converting to Double: "1,500" (meant as one
 * thousand five hundred) has 3 fraction digits and is rejected, instead of becoming 1.5 and silently
 * sending 1.50. Grouping separators, signs, exponents and anything else are rejected too.
 *
 * Returns `null` when the text isn't an acceptable amount; callers report that as an invalid amount.
 * Range and precision against the business rules are still checked by [PaymentValidator].
 */
object AmountParser {

    private const val DECIMAL_POINT = '.'
    private val AMOUNT_PATTERN = Regex("""^(\d+)(?:[.,](\d+))?$""")

    fun parse(text: String, maxFractionDigits: Int): Double? {
        val match = AMOUNT_PATTERN.matchEntire(text.trim()) ?: return null
        val (integerPart, fractionPart) = match.destructured
        if (fractionPart.length > maxFractionDigits) return null
        val normalized = if (fractionPart.isEmpty()) integerPart else "$integerPart$DECIMAL_POINT$fractionPart"
        return normalized.toDouble()
    }
}
