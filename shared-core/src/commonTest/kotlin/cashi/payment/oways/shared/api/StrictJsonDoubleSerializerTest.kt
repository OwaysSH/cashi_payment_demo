package cashi.payment.oways.shared.api

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StrictJsonDoubleSerializerTest {

    @Test
    fun decodesJsonNumbers() {
        assertEquals(10.5, decodeAmount("10.5"))
        assertEquals(10.0, decodeAmount("10"))
    }

    @Test
    fun rejectsQuotedNumbersAndNull() {
        listOf("\"10.5\"", "null", "true").forEach { amountJson ->
            assertFailsWith<SerializationException>(amountJson) { decodeAmount(amountJson) }
        }
    }

    @Test
    fun encodesAsJsonNumber() {
        val json = ApiJson.encodeToString(PaymentRequest("alice@example.com", 10.5, "USD"))

        assertEquals("""{"recipientEmail":"alice@example.com","amount":10.5,"currency":"USD"}""", json)
    }

    private fun decodeAmount(amountJson: String): Double =
        ApiJson.decodeFromString<PaymentRequest>(
            """{"recipientEmail":"alice@example.com","amount":$amountJson,"currency":"USD"}"""
        ).amount
}