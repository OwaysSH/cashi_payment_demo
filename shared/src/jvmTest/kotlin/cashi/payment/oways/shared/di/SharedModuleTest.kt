package cashi.payment.oways.shared.di

import cashi.payment.oways.shared.api.ApiHeaders
import cashi.payment.oways.shared.domain.gateway.PaymentStore
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.PaymentDraft
import cashi.payment.oways.shared.domain.result.Outcome
import cashi.payment.oways.shared.domain.usecase.ObservePaymentsUseCase
import cashi.payment.oways.shared.domain.usecase.SendPaymentUseCase
import cashi.payment.oways.shared.testing.InMemoryPaymentStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Clock

/** Builds the real shared graph (with the JVM platform module) plus test doubles for config, engine and store. */
class SharedModuleTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val mockEngine = MockEngine { request ->
        requests += request
        respond(
            content = CREATED_RESPONSE,
            status = HttpStatusCode.Created,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }
    private val testModule = module {
        single { PaymentConfig(apiBaseUrl = BASE_URL, firestoreEmulator = null) }
        single<HttpClientEngine> { mockEngine }
        single<PaymentStore> { InMemoryPaymentStore(Clock.System) }
    }
    private val koin = koinApplication { modules(sharedModule(), platformModule(), testModule) }.koin

    @AfterTest
    fun closeKoin() {
        koin.close()
    }

    @Test
    fun useCases_resolve() {
        koin.get<SendPaymentUseCase>()
        koin.get<ObservePaymentsUseCase>()
    }

    @Test
    fun resolvedSendPayment_callsConfiguredBackendWithIdempotencyKey() = runTest {
        val key = IdempotencyKey.random()

        val result = koin.get<SendPaymentUseCase>()(PaymentDraft("alice@example.com", 10.5, "USD"), key)

        assertIs<Outcome.Success<*>>(result)
        val request = requests.single()
        assertEquals("$BASE_URL/payments", request.url.toString())
        assertEquals(key.value, request.headers[ApiHeaders.IDEMPOTENCY_KEY])
    }

    @Test
    fun statefulDependencies_areSingletons() {
        assertSame(koin.get<HttpClient>(), koin.get<HttpClient>())
        assertSame(koin.get<PaymentStore>(), koin.get<PaymentStore>())
    }

    private companion object {
        const val BASE_URL = "http://127.0.0.1:8080"
        const val CREATED_RESPONSE =
            """{"id":"p-1","recipientEmail":"alice@example.com","amount":10.5,"currency":"USD","createdAt":"2026-10-23T09:00:00Z"}"""
    }
}