package cashi.payment.oways.shared.di

import cashi.payment.oways.shared.data.firestore.createFirestorePaymentStore
import cashi.payment.oways.shared.data.remote.createOkHttpEngine
import cashi.payment.oways.shared.domain.gateway.PaymentStore
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.dsl.module

/** OkHttp engine and the Firestore-backed store; the store is a single because Firestore can be configured only once. */
actual fun platformModule(): Module = module {
    single<HttpClientEngine> { createOkHttpEngine() }
    single<PaymentStore> { createFirestorePaymentStore(get<PaymentConfig>().firestoreEmulator) }
}