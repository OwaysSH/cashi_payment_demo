package cashi.payment.oways.ui.format

import cashi.payment.oways.shared.domain.model.Currency
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Formats exact minor-unit amounts for display in the user's locale, e.g. 1050 USD → "$10.50".
 * Works from `BigDecimal` (never Double) and needs no `java.time`, so minSdk 24 is fine.
 */
class AmountFormatter(private val locale: Locale = Locale.getDefault()) {

    fun format(amountMinor: Long, currency: Currency): String {
        val numberFormat = NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = java.util.Currency.getInstance(currency.code)
            minimumFractionDigits = currency.fractionDigits
            maximumFractionDigits = currency.fractionDigits
        }
        return numberFormat.format(BigDecimal.valueOf(amountMinor, currency.fractionDigits))
    }
}