package cashi.payment.oways

import androidx.test.ext.junit.runners.AndroidJUnit4
import cashi.payment.oways.shared.data.firestore.createFirestorePaymentStore
import cashi.payment.oways.shared.di.FirestoreEmulatorConfig
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.result.Outcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * On-demand device smoke test: the store is built exactly as the app builds it (default FirebaseApp
 * from google-services.json + BuildConfig emulator address), proving BuildConfig, `adb reverse
 * tcp:8085` and the debug network security config work together.
 *
 * Prerequisites: `firebase emulators:start --only firestore` and `scripts/device-setup.sh`.
 * Skipped when the debug build uses real Firestore (`firestore.useEmulator=false`), so it never
 * writes to the real project. The test payment stays in the emulator.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreEmulatorConnectivityTest {

    @Test
    fun savedPayment_appearsInLiveHistory(): Unit = runBlocking {
        assumeTrue("Debug build uses real Firestore; skipping emulator test.", BuildConfig.USE_FIRESTORE_EMULATOR)
        val store = createFirestorePaymentStore(
            FirestoreEmulatorConfig(BuildConfig.FIRESTORE_EMULATOR_HOST, BuildConfig.FIRESTORE_EMULATOR_PORT),
        )
        val payment = Payment(
            id = "smoke-test-${Uuid.random()}",
            recipientEmail = "smoke-test@example.com",
            amountMinor = 100L,
            currency = Currency.USD,
            createdAt = Clock.System.now(),
        )

        assertEquals(Outcome.Success(Unit), store.save(payment))

        withTimeout(HISTORY_TIMEOUT) {
            store.observePayments().first { history -> history.any { it.id == payment.id } }
        }
    }

    private companion object {
        val HISTORY_TIMEOUT = 10.seconds
    }
}