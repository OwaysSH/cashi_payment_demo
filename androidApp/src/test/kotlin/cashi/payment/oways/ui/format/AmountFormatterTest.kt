package cashi.payment.oways.ui.format

import cashi.payment.oways.shared.domain.model.Currency
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class AmountFormatterTest {

    @Test
    fun formatsMinorUnitsInTheGivenLocale() {
        assertEquals("$10.50", AmountFormatter(Locale.US).format(1_050L, Currency.USD))
        assertEquals("€25.50", AmountFormatter(Locale.US).format(2_550L, Currency.EUR))
        assertEquals("25,50 €", AmountFormatter(Locale.GERMANY).format(2_550L, Currency.EUR))
    }

    @Test
    fun keepsTwoDecimals_forWholeAndLargeAmounts() {
        assertEquals("$100.00", AmountFormatter(Locale.US).format(10_000L, Currency.USD))
        assertEquals("$1,000,000.00", AmountFormatter(Locale.US).format(100_000_000L, Currency.USD))
        assertEquals("$0.01", AmountFormatter(Locale.US).format(1L, Currency.USD))
    }
}