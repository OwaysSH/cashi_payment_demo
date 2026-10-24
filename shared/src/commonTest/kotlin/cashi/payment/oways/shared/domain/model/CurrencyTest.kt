package cashi.payment.oways.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurrencyTest {

    @Test
    fun fromCode_returnsCurrency_forSupportedCodes() {
        assertEquals(Currency.USD, Currency.fromCode("USD"))
        assertEquals(Currency.EUR, Currency.fromCode("EUR"))
    }

    @Test
    fun fromCode_returnsNull_forUnsupportedOrMalformedCodes() {
        listOf("usd", "GBP", "", " USD", "USD ").forEach { code ->
            assertNull(Currency.fromCode(code), "expected null for '$code'")
        }
    }

    @Test
    fun codes_areThreeUppercaseLetters() {
        val isoCode = Regex("^[A-Z]{3}$")
        Currency.entries.forEach { currency ->
            assertTrue(isoCode.matches(currency.code), "invalid ISO code '${currency.code}'")
        }
    }

    @Test
    fun supportedCurrencies_useTwoFractionDigits() {
        assertEquals(2, Currency.USD.fractionDigits)
        assertEquals(2, Currency.EUR.fractionDigits)
    }
}