package cashi.payment.oways.shared.domain.model

/**
 * Currencies the app supports. [code] is the ISO 4217 code used on the wire and in storage,
 * kept separate from the enum name so renaming an entry can't change persisted values.
 * [fractionDigits] is the number of minor-unit decimals (ISO 4217 exponent), e.g. 2 for cents.
 */
enum class Currency(val code: String, val fractionDigits: Int) {
    USD("USD", 2),
    EUR("EUR", 2);

    companion object {
        /** Exact, case-sensitive match on the ISO code; `null` if unsupported. */
        fun fromCode(code: String): Currency? = entries.firstOrNull { it.code == code }
    }
}