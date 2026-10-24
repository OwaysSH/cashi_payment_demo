package cashi.payment.oways.shared.data.firestore

import cashi.payment.oways.shared.domain.gateway.PaymentStore
import cashi.payment.oways.shared.domain.gateway.PaymentStoreContractTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the [PaymentStore] contract against [FirestorePaymentStore] on the Firestore emulator. The
 * emulator enforces `firestore.rules`, so this also tests the rules (validated create, no update).
 *
 * Prerequisites: `firebase emulators:start --only firestore` and `scripts/device-setup.sh`.
 * Run on demand with `./gradlew :shared:connectedAndroidDeviceTest`.
 */
class FirestorePaymentStoreContractTest : PaymentStoreContractTest() {

    override val flowTimeout: Duration = 10.seconds

    override fun createStore(): PaymentStore {
        FirestoreEmulator.clearData()
        return FirestorePaymentStore(FirestoreEmulator.newIsolatedFirestore())
    }
}