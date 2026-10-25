package cashi.payment.oways.server.plugins

import cashi.payment.oways.server.module
import cashi.payment.oways.server.payment.testPaymentService
import cashi.payment.oways.shared.api.ApiErrorCode
import cashi.payment.oways.shared.api.ApiJson
import cashi.payment.oways.shared.api.ErrorResponse
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StatusPagesTest {

    @Test
    fun malformedBodies_returnMalformedRequest() = testApplication {
        installServerModule()

        listOf(
            """{"recipientEmail":""",
            """{"recipientEmail":"alice@example.com","amount":10.5}""",
            """{"recipientEmail":"alice@example.com","amount":"10.5","currency":"USD"}""",
            """{"recipientEmail":"alice@example.com","amount":null,"currency":"USD"}""",
        ).forEach { body ->
            val response = client.post(PAYMENTS_PATH) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertError(
                HttpStatusCode.BadRequest,
                ApiErrorCode.MALFORMED_REQUEST,
                "Request body is missing, malformed or has invalid field types",
                response,
                body,
            )
        }
    }

    @Test
    fun nonJsonContentType_returnsUnsupportedMediaType() = testApplication {
        installServerModule()

        val response = client.post(PAYMENTS_PATH) {
            contentType(ContentType.Text.Plain)
            setBody("hello")
        }

        assertError(
            HttpStatusCode.UnsupportedMediaType,
            ApiErrorCode.UNSUPPORTED_MEDIA_TYPE,
            "Content-Type must be application/json",
            response,
        )
    }

    @Test
    fun unknownRoute_returnsNotFound() = testApplication {
        installServerModule()

        assertError(HttpStatusCode.NotFound, ApiErrorCode.NOT_FOUND, "Resource not found", client.get("/nope"))
    }

    @Test
    fun wrongMethod_returnsMethodNotAllowed() = testApplication {
        installServerModule()

        assertError(
            HttpStatusCode.MethodNotAllowed,
            ApiErrorCode.METHOD_NOT_ALLOWED,
            "HTTP method not allowed for this resource",
            client.get(PAYMENTS_PATH),
        )
    }

    @Test
    fun unexpectedException_returnsInternalErrorWithoutDetails() = testApplication {
        installServerModule()
        application {
            routing { get(FAILING_PATH) { error(SECRET_DETAIL) } }
        }

        val response = client.get(FAILING_PATH)

        assertError(HttpStatusCode.InternalServerError, ApiErrorCode.INTERNAL_ERROR, "Unexpected server error", response)
        assertFalse(response.bodyAsText().contains(SECRET_DETAIL))
    }

    private fun ApplicationTestBuilder.installServerModule() {
        application { module(testPaymentService()) }
    }

    private suspend fun assertError(
        expectedStatus: HttpStatusCode,
        expectedCode: ApiErrorCode,
        expectedMessage: String,
        response: HttpResponse,
        context: String? = null,
    ) {
        assertEquals(expectedStatus, response.status, context)
        val error = ApiJson.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals(expectedCode.wireValue, error.code, context)
        assertEquals(expectedMessage, error.message, context)
    }

    private companion object {
        const val PAYMENTS_PATH = "/payments"
        const val FAILING_PATH = "/test-failure"
        const val SECRET_DETAIL = "db password is hunter2"
    }
}