package cashi.payment.oways.shared.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentContractSerializationTest {

    @Test
    fun paymentRequest_encodesToBriefContract() {
        val request = PaymentRequest(recipientEmail = "alice@example.com", amount = 10.5, currency = "USD")

        val json = Json.parseToJsonElement(Json.encodeToString(request)).jsonObject

        val expected = JsonObject(
            mapOf(
                "recipientEmail" to JsonPrimitive("alice@example.com"),
                "amount" to JsonPrimitive(10.5),
                "currency" to JsonPrimitive("USD"),
            )
        )
        assertEquals(expected, json)
    }

    @Test
    fun paymentRequest_decodingFails_whenFieldMissing() {
        val body = """{"recipientEmail":"alice@example.com","amount":10.5}"""

        assertFailsWith<SerializationException> {
            Json.decodeFromString<PaymentRequest>(body)
        }
    }

    @Test
    fun paymentResponse_decodesServerPayload() {
        val body = """
            {
              "id": "3f1c2b9e-7a4d-4e8b-9c1a-2d5e6f708192",
              "recipientEmail": "alice@example.com",
              "amount": 10.5,
              "currency": "EUR",
              "createdAt": "2026-10-22T12:30:00Z"
            }
        """.trimIndent()

        val response = Json.decodeFromString<PaymentResponse>(body)

        val expected = PaymentResponse(
            id = "3f1c2b9e-7a4d-4e8b-9c1a-2d5e6f708192",
            recipientEmail = "alice@example.com",
            amount = 10.5,
            currency = "EUR",
            createdAt = "2026-10-22T12:30:00Z",
        )
        assertEquals(expected, response)
    }
}