package cashi.payment.oways.shared.di

/** Address of a local Firestore emulator (e.g. `127.0.0.1:8085` via `adb reverse`). */
data class FirestoreEmulatorConfig(val host: String, val port: Int)