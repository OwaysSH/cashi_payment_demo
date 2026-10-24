package cashi.payment.oways.server.payment

import cashi.payment.oways.server.module
import cashi.payment.oways.shared.api.ApiErrorCode
import cashi.payment.oways.shared.api.ApiJson
import cashi.payment.oways.shared.api.ErrorResponse
import cashi.payment.oways.shared.api.PaymentResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaymentRoutesTest {

    @Test
    fun postPayment_returnsCreatedWithProcessedPayment() = testApplication {
        installServerModule()

        val response = postPayment("""{"recipientEmail":" alice@example.com ","amount":10.5,"currency":"USD"}""")

        assertEquals(HttpStatusCode.Created, response.status)
        val expected = PaymentResponse(
            id = FIXED_PAYMENT_ID,
            recipientEmail = "alice@example.com",
            amount = 10.5,
            currency = "USD",
            createdAt = FIXED_NOW,
        )
        assertEquals(expected, ApiJson.decodeFromString<PaymentResponse>(response.bodyAsText()))
    }

    @Test
    fun postPayment_ignoresUnknownFields() = testApplication {
        installServerModule()

        val response = postPayment(
            """{"recipientEmail":"alice@example.com","amount":10.5,"currency":"USD","note":"lunch"}"""
        )

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun postPayment_returnsBadRequestWithErrorCode_forInvalidInput() = testApplication {
        installServerModule()

        mapOf(
            """{"recipientEmail":"not-an-email","amount":10.5,"currency":"USD"}""" to ApiErrorCode.INVALID_EMAIL,
            """{"recipientEmail":"alice@example.com","amount":0,"currency":"USD"}""" to ApiErrorCode.INVALID_AMOUNT,
            """{"recipientEmail":"alice@example.com","amount":10.5,"currency":"GBP"}""" to
                ApiErrorCode.UNSUPPORTED_CURRENCY,
        ).forEach { (body, expectedCode) ->
            val response = postPayment(body)

            assertEquals(HttpStatusCode.BadRequest, response.status, body)
            val error = ApiJson.decodeFromString<ErrorResponse>(response.bodyAsText())
            assertEquals(expectedCode.wireValue, error.code, body)
            assertTrue(error.message.isNotBlank(), body)
        }
    }

    private fun ApplicationTestBuilder.installServerModule() {
        application { module(testPaymentService()) }
    }

    private suspend fun ApplicationTestBuilder.postPayment(json: String): HttpResponse =
        client.post("/payments") {
            contentType(ContentType.Application.Json)
            setBody(json)
        }
}