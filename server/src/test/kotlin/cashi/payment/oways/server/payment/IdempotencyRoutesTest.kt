package cashi.payment.oways.server.payment

import cashi.payment.oways.server.module
import cashi.payment.oways.shared.api.ApiErrorCode
import cashi.payment.oways.shared.api.ApiHeaders
import cashi.payment.oways.shared.api.ApiJson
import cashi.payment.oways.shared.api.ErrorResponse
import cashi.payment.oways.shared.api.PaymentResponse
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IdempotencyRoutesTest {

    private val idGenerator = SequentialIdGenerator()

    @Test
    fun sameKeyTwice_replaysOriginalPayment() = testApplication {
        installServerModule()

        val first = postPayment(VALID_BODY, KEY)
        val second = postPayment(VALID_BODY, KEY)

        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals(HttpStatusCode.Created, second.status)
        assertEquals(first.payment(), second.payment())
        assertNull(first.headers[ApiHeaders.IDEMPOTENT_REPLAYED])
        assertEquals("true", second.headers[ApiHeaders.IDEMPOTENT_REPLAYED])
        assertEquals(1, idGenerator.generatedCount)
    }

    @Test
    fun equivalentRequestWithSameKey_isReplayed() = testApplication {
        installServerModule()

        val first = postPayment(VALID_BODY, KEY)
        val second = postPayment("""{"recipientEmail":" alice@example.com ","amount":10.50,"currency":"USD"}""", KEY)

        assertEquals(first.payment(), second.payment())
        assertEquals("true", second.headers[ApiHeaders.IDEMPOTENT_REPLAYED])
    }

    @Test
    fun differentKeysOrNoKey_createSeparatePayments() = testApplication {
        installServerModule()

        val ids = listOf(
            postPayment(VALID_BODY, "key-a"),
            postPayment(VALID_BODY, "key-b"),
            postPayment(VALID_BODY, key = null),
            postPayment(VALID_BODY, key = null),
        ).map { it.payment().id }

        assertEquals(4, ids.toSet().size)
    }

    @Test
    fun sameKeyWithDifferentPayment_returnsUnprocessableEntity() = testApplication {
        installServerModule()
        postPayment(VALID_BODY, KEY)

        val response = postPayment("""{"recipientEmail":"alice@example.com","amount":99,"currency":"USD"}""", KEY)

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(ApiErrorCode.IDEMPOTENCY_KEY_MISMATCH.wireValue, response.error().code)
        assertEquals(1, idGenerator.generatedCount)
    }

    @Test
    fun invalidKey_returnsBadRequest() = testApplication {
        installServerModule()

        listOf("   ", "k".repeat(256)).forEach { key ->
            val response = postPayment(VALID_BODY, key)

            assertEquals(HttpStatusCode.BadRequest, response.status, key)
            assertEquals(ApiErrorCode.INVALID_IDEMPOTENCY_KEY.wireValue, response.error().code, key)
        }
        assertEquals(0, idGenerator.generatedCount)
    }

    @Test
    fun validationFailure_isNotStored_soCorrectedRetrySucceeds() = testApplication {
        installServerModule()

        val failed = postPayment("""{"recipientEmail":"bad","amount":10.5,"currency":"USD"}""", KEY)
        val corrected = postPayment(VALID_BODY, KEY)

        assertEquals(HttpStatusCode.BadRequest, failed.status)
        assertEquals(HttpStatusCode.Created, corrected.status)
        assertNull(corrected.headers[ApiHeaders.IDEMPOTENT_REPLAYED])
    }

    @Test
    fun concurrentRequestsWithSameKey_createOnePayment() = testApplication {
        installServerModule()

        val responses = coroutineScope {
            (1..CONCURRENT_REQUESTS).map { async { postPayment(VALID_BODY, KEY) } }.awaitAll()
        }

        responses.forEach { assertEquals(HttpStatusCode.Created, it.status) }
        assertEquals(1, responses.map { it.payment().id }.toSet().size)
        assertEquals(1, idGenerator.generatedCount)
    }

    private fun ApplicationTestBuilder.installServerModule() {
        application { module(testPaymentService(idGenerator = idGenerator)) }
    }

    private suspend fun ApplicationTestBuilder.postPayment(json: String, key: String?): HttpResponse =
        client.post("/payments") {
            contentType(ContentType.Application.Json)
            key?.let { header(ApiHeaders.IDEMPOTENCY_KEY, it) }
            setBody(json)
        }

    private suspend fun HttpResponse.payment(): PaymentResponse = ApiJson.decodeFromString(bodyAsText())

    private suspend fun HttpResponse.error(): ErrorResponse = ApiJson.decodeFromString(bodyAsText())

    private companion object {
        const val KEY = "3f1c2b9e-0000-4e8b-9c1a-2d5e6f708192"
        const val VALID_BODY = """{"recipientEmail":"alice@example.com","amount":10.5,"currency":"USD"}"""
        const val CONCURRENT_REQUESTS = 20
    }
}