package cashi.payment.oways.shared.di

import cashi.payment.oways.shared.data.remote.KtorPaymentApi
import cashi.payment.oways.shared.data.remote.createPaymentHttpClient
import cashi.payment.oways.shared.domain.gateway.PaymentApi
import cashi.payment.oways.shared.domain.usecase.ObservePaymentsUseCase
import cashi.payment.oways.shared.domain.usecase.SendPaymentUseCase
import cashi.payment.oways.shared.domain.validation.PaymentValidator
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Values the app supplies (e.g. from Android `BuildConfig`); shared code never reads build config.
 * [firestoreEmulator] is `null` to use the real Firestore project.
 */
data class PaymentConfig(
    val apiBaseUrl: String,
    val firestoreEmulator: FirestoreEmulatorConfig?,
)

/**
 * Platform-independent graph. Requires a [PaymentConfig] from the app and, from [platformModule],
 * an `HttpClientEngine` and a `PaymentStore`. Stateful objects are singletons; use cases are
 * stateless factories.
 */
fun sharedModule(): Module = module {
    single { PaymentValidator() }
    single<HttpClient> { createPaymentHttpClient(get(), get<PaymentConfig>().apiBaseUrl) }
    single<PaymentApi> { KtorPaymentApi(get()) }
    factory { SendPaymentUseCase(get(), get(), get()) }
    factory { ObservePaymentsUseCase(get()) }
}