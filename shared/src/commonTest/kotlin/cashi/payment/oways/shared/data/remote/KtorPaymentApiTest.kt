package cashi.payment.oways.shared.data.remote

import cashi.payment.oways.shared.api.ApiHeaders
import cashi.payment.oways.shared.domain.error.PaymentError
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.ValidatedPayment
import cashi.payment.oways.shared.domain.result.Outcome
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class KtorPaymentApiTest {

    private val payment = ValidatedPayment("alice@example.com", 1050L, Currency.USD)
    private val key = IdempotencyKey.random()

    @Test
    fun createPayment_sendsBriefContractWithIdempotencyKey() = runTest {
        var captured: HttpRequestData? = null
        val api = apiRespondingWith { request ->
            captured = request
            respondJson(HttpStatusCode.Created, VALID_RESPONSE)
        }

        api.createPayment(payment, key)

        val request = checkNotNull(captured)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("$BASE_URL/payments", request.url.toString())
        assertEquals(key.value, request.headers[ApiHeaders.IDEMPOTENCY_KEY])
        val body = request.body as TextContent
        assertEquals(ContentType.Application.Json, body.contentType.withoutParameters())
        assertEquals(
            Json.parseToJsonElement("""{"recipientEmail":"alice@example.com","amount":10.5,"currency":"USD"}"""),
            Json.parseToJsonElement(body.text),
        )
    }

    @Test
    fun createPayment_mapsCreatedResponseToPayment() = runTest {
        val api = apiRespondingWith { respondJson(HttpStatusCode.Created, VALID_RESPONSE) }

        val result = api.createPayment(payment, key)

        val expected = Payment(
            id = PAYMENT_ID,
            recipientEmail = "alice@example.com",
            amountMinor = 1050L,
            currency = Currency.USD,
            createdAt = Instant.parse(CREATED_AT),
        )
        assertEquals(Outcome.Success(expected), result)
    }

    @Test
    fun createPayment_ignoresUnknownResponseFields() = runTest {
        val withExtraField = VALID_RESPONSE.replace("}", ""","status":"COMPLETED"}""")
        val api = apiRespondingWith { respondJson(HttpStatusCode.Created, withExtraField) }

        val result = api.createPayment(payment, key)

        check(result is Outcome.Success) { "expected success but was $result" }
    }

    @Test
    fun createPayment_mapsContractViolationToServerError() = runTest {
        listOf(
            VALID_RESPONSE.replace("\"USD\"", "\"GBP\""),
            VALID_RESPONSE.replace(CREATED_AT, "yesterday"),
            VALID_RESPONSE.replace("10.5", "10.123"),
        ).forEach { body ->
            val api = apiRespondingWith { respondJson(HttpStatusCode.Created, body) }

            assertEquals(Outcome.Failure(PaymentError.Server(HttpStatusCode.Created.value)), api.createPayment(payment, key))
        }
    }

    private fun apiRespondingWith(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): KtorPaymentApi = KtorPaymentApi(createPaymentHttpClient(MockEngine(handler), BASE_URL))

    private fun MockRequestHandleScope.respondJson(status: HttpStatusCode, body: String): HttpResponseData =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private companion object {
        const val BASE_URL = "http://127.0.0.1:8080"
        const val PAYMENT_ID = "58148a0c-1f2d-45ea-b233-ad188103a693"
        const val CREATED_AT = "2026-10-23T09:15:30Z"
        const val VALID_RESPONSE =
            """{"id":"$PAYMENT_ID","recipientEmail":"alice@example.com","amount":10.5,"currency":"USD","createdAt":"$CREATED_AT"}"""
    }
}