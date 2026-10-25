package cashi.payment.oways.shared.data.remote

import cashi.payment.oways.shared.domain.error.PaymentError
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.result.Outcome
import cashi.payment.oways.shared.testing.validatedPayment
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class KtorPaymentApiErrorTest {

    private val payment = validatedPayment()
    private val key = IdempotencyKey.random()

    @Test
    fun validationCodes_mapToDomainValidationErrors() = runTest {
        mapOf(
            "INVALID_EMAIL" to PaymentError.InvalidEmail,
            "INVALID_AMOUNT" to PaymentError.InvalidAmount,
            "UNSUPPORTED_CURRENCY" to PaymentError.UnsupportedCurrency,
        ).forEach { (code, expected) ->
            val api = apiRespondingWith { respondJson(HttpStatusCode.BadRequest, errorBody(code)) }

            assertEquals(Outcome.Failure(expected), api.createPayment(payment, key), code)
        }
    }

    @Test
    fun nonActionableAndUnknownCodes_mapToServerError() = runTest {
        listOf(
            HttpStatusCode.BadRequest to "MALFORMED_REQUEST",
            HttpStatusCode.BadRequest to "INVALID_IDEMPOTENCY_KEY",
            HttpStatusCode.UnprocessableEntity to "IDEMPOTENCY_KEY_MISMATCH",
            HttpStatusCode.UnsupportedMediaType to "UNSUPPORTED_MEDIA_TYPE",
            HttpStatusCode.NotFound to "NOT_FOUND",
            HttpStatusCode.MethodNotAllowed to "METHOD_NOT_ALLOWED",
            HttpStatusCode.InternalServerError to "INTERNAL_ERROR",
            HttpStatusCode.TooManyRequests to "RATE_LIMITED",
        ).forEach { (status, code) ->
            val api = apiRespondingWith { respondJson(status, errorBody(code)) }

            assertEquals(Outcome.Failure(PaymentError.Server(status.value)), api.createPayment(payment, key), code)
        }
    }

    @Test
    fun nonJsonOrEmptyErrorBodies_mapToServerError() = runTest {
        val htmlApi = apiRespondingWith {
            respond(
                content = "<html>Bad Gateway</html>",
                status = HttpStatusCode.BadGateway,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
            )
        }
        val emptyApi = apiRespondingWith { respond(content = "", status = HttpStatusCode.ServiceUnavailable) }
        val emptyJsonApi = apiRespondingWith { respondJson(HttpStatusCode.InternalServerError, "") }

        assertEquals(Outcome.Failure(PaymentError.Server(502)), htmlApi.createPayment(payment, key))
        assertEquals(Outcome.Failure(PaymentError.Server(503)), emptyApi.createPayment(payment, key))
        assertEquals(Outcome.Failure(PaymentError.Server(500)), emptyJsonApi.createPayment(payment, key))
    }

    @Test
    fun createdWithInvalidJson_mapsToServerError() = runTest {
        val api = apiRespondingWith { respondJson(HttpStatusCode.Created, """{"id":""") }

        assertEquals(Outcome.Failure(PaymentError.Server(201)), api.createPayment(payment, key))
    }

    @Test
    fun ioFailure_mapsToNetworkError() = runTest {
        val api = apiRespondingWith { throw IOException("connection reset") }

        assertEquals(Outcome.Failure(PaymentError.Network), api.createPayment(payment, key))
    }

    @Test
    fun requestTimeout_mapsToNetworkError() = runTest {
        val timeouts = PaymentHttpTimeouts(request = SHORT_TIMEOUT)
        val api = apiRespondingWith(timeouts) {
            delay(SLOW_RESPONSE)
            respondJson(HttpStatusCode.Created, "{}")
        }

        assertEquals(Outcome.Failure(PaymentError.Network), api.createPayment(payment, key))
    }

    @Test
    fun cancellation_isRethrown() = runTest {
        val api = apiRespondingWith { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { api.createPayment(payment, key) }
    }

    @Test
    fun unexpectedException_propagates() = runTest {
        val api = apiRespondingWith { error("bug") }

        assertFailsWith<IllegalStateException> { api.createPayment(payment, key) }
    }

    private fun apiRespondingWith(
        timeouts: PaymentHttpTimeouts = PaymentHttpTimeouts(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): KtorPaymentApi = KtorPaymentApi(createPaymentHttpClient(MockEngine(handler), BASE_URL, timeouts))

    private fun MockRequestHandleScope.respondJson(status: HttpStatusCode, body: String): HttpResponseData =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private fun errorBody(code: String) = """{"code":"$code","message":"irrelevant for the client"}"""

    private companion object {
        const val BASE_URL = "http://127.0.0.1:8080"
        val SHORT_TIMEOUT = 100.milliseconds
        val SLOW_RESPONSE = 5.seconds
    }
}