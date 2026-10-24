package cashi.payment.oways

import android.app.Application
import cashi.payment.oways.di.viewModelModule
import cashi.payment.oways.shared.di.FirestoreEmulatorConfig
import cashi.payment.oways.shared.di.PaymentConfig
import cashi.payment.oways.shared.di.platformModule
import cashi.payment.oways.shared.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

/** Starts Koin with the shared graph and the build-specific configuration. */
class CashiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CashiApplication)
            modules(appConfigModule, sharedModule(), platformModule(), viewModelModule)
        }
    }
}

/** The only place `BuildConfig` values enter the graph; shared code receives them as [PaymentConfig]. */
private val appConfigModule = module {
    single {
        PaymentConfig(
            apiBaseUrl = BuildConfig.API_BASE_URL,
            firestoreEmulator = if (BuildConfig.USE_FIRESTORE_EMULATOR) {
                FirestoreEmulatorConfig(BuildConfig.FIRESTORE_EMULATOR_HOST, BuildConfig.FIRESTORE_EMULATOR_PORT)
            } else {
                null
            },
        )
    }
}