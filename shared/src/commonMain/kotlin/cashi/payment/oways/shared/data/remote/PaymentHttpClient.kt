package cashi.payment.oways.shared.data.remote

import cashi.payment.oways.shared.api.ApiJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Timeouts of a payment call; exceeding any of them surfaces as a network error. */
data class PaymentHttpTimeouts(
    val connect: Duration = DEFAULT_CONNECT_TIMEOUT,
    val request: Duration = DEFAULT_REQUEST_TIMEOUT,
    val socket: Duration = DEFAULT_SOCKET_TIMEOUT,
) {
    private companion object {
        val DEFAULT_CONNECT_TIMEOUT = 10.seconds
        val DEFAULT_REQUEST_TIMEOUT = 15.seconds
        val DEFAULT_SOCKET_TIMEOUT = 15.seconds
    }
}

/**
 * HTTP client for the payments API. The [engine] is injected (OkHttp on Android, MockEngine in
 * tests). Status codes are handled explicitly by the API, so `expectSuccess` is off. No logging
 * plugin: request bodies contain personal data (emails). No automatic retries: the user retries
 * with the same idempotency key.
 */
fun createPaymentHttpClient(
    engine: HttpClientEngine,
    baseUrl: String,
    timeouts: PaymentHttpTimeouts = PaymentHttpTimeouts(),
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(ApiJson)
    }
    install(HttpTimeout) {
        connectTimeoutMillis = timeouts.connect.inWholeMilliseconds
        requestTimeoutMillis = timeouts.request.inWholeMilliseconds
        socketTimeoutMillis = timeouts.socket.inWholeMilliseconds
    }
    defaultRequest {
        url(baseUrl)
    }
}