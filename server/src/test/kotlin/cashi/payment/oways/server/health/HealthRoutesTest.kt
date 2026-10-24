package cashi.payment.oways.server.health

import cashi.payment.oways.server.module
import cashi.payment.oways.server.payment.testPaymentService
import cashi.payment.oways.shared.api.ApiJson
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthRoutesTest {

    @Test
    fun health_returnsUp() = testApplication {
        application { module(testPaymentService()) }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(HealthResponse("UP"), ApiJson.decodeFromString<HealthResponse>(response.bodyAsText()))
    }
}