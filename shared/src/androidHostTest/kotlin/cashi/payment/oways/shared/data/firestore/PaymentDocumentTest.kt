package cashi.payment.oways.shared.data.firestore

import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.Payment
import com.google.firebase.Timestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Document mapping on the JVM host. The Firestore error-code mapping can't be tested here
 * (`FirebaseFirestoreException.Code` needs the Android runtime); the device contract test covers
 * the PERMISSION_DENIED path against the emulator's rules.
 */
class PaymentDocumentTest {

    private val payment = Payment(
        id = "p-1",
        recipientEmail = "alice@example.com",
        amountMinor = 1_050L,
        currency = Currency.EUR,
        createdAt = Instant.fromEpochSeconds(0),
    )

    private val storedData: Map<String, Any?> = mapOf(
        "recipientEmail" to "alice@example.com",
        "amountMinor" to 1_050L,
        "currency" to "EUR",
        "timestamp" to Timestamp(1_761_210_930L, 500_000_000),
    )

    @Test
    fun toDocumentData_hasExactlyTheFieldsTheRulesRequire() {
        assertEquals(setOf("recipientEmail", "amountMinor", "currency", "timestamp"), payment.toDocumentData().keys)
        assertEquals(1_050L, payment.toDocumentData()["amountMinor"])
        assertEquals("EUR", payment.toDocumentData()["currency"])
    }

    @Test
    fun paymentFromDocument_usesFirestoreTimestamp() {
        val expected = payment.copy(createdAt = Instant.fromEpochSeconds(1_761_210_930L, 500_000_000))

        assertEquals(expected, paymentFromDocument("p-1", storedData))
    }

    @Test
    fun paymentFromDocument_returnsNull_forMalformedDocuments() {
        listOf(
            storedData - "recipientEmail",
            storedData + ("amountMinor" to "1050"),
            storedData + ("currency" to "GBP"),
            storedData + ("timestamp" to null),
        ).forEach { data ->
            assertNull(paymentFromDocument("p-1", data), "expected null for $data")
        }
    }

    @Test
    fun hasSameContentAs_ignoresTimestamp_andComparesContent() {
        assertTrue(storedData.hasSameContentAs(payment))
        assertFalse(storedData.hasSameContentAs(payment.copy(amountMinor = 9_900L)))
        assertFalse(storedData.hasSameContentAs(payment.copy(currency = Currency.USD)))
    }
}