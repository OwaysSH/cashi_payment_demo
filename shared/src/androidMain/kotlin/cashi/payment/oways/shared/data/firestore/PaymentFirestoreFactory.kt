package cashi.payment.oways.shared.data.firestore

import cashi.payment.oways.shared.di.FirestoreEmulatorConfig
import cashi.payment.oways.shared.domain.gateway.PaymentStore
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

/**
 * The app's [PaymentStore], on the default FirebaseApp (initialized from `google-services.json`).
 * With [emulator], Firestore is pointed at the local emulator with an in-memory cache, so a wiped
 * emulator never shows stale cached payments; without it, the real project with Firestore's default
 * persistent cache (history available offline).
 *
 * Call once per process (Koin `single`): Firestore can only be configured before its first use.
 */
fun createFirestorePaymentStore(emulator: FirestoreEmulatorConfig?): PaymentStore =
    FirestorePaymentStore(createPaymentFirestore(emulator))

internal fun createPaymentFirestore(emulator: FirestoreEmulatorConfig?): FirebaseFirestore =
    FirebaseFirestore.getInstance().apply {
        if (emulator != null) {
            useEmulator(emulator.host, emulator.port)
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }