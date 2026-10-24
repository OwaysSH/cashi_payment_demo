package cashi.payment.oways.shared.data.firestore

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import java.net.HttpURLConnection
import java.net.URL
import kotlin.uuid.Uuid

/**
 * Access to the local Firestore emulator from a device, via `adb reverse tcp:8085 tcp:8085`.
 * Every [newIsolatedFirestore] call creates its own FirebaseApp with an empty in-memory cache, so
 * one test can't see another test's cached documents.
 *
 * The project id must match the one the emulator was started with. It comes from the instrumentation
 * argument `firestoreProjectId`, which the build sets from `androidApp/google-services.json`
 * (`cashi-demo` for the owner, `demo-cashi` with the example file). Override it with
 * `-Pandroid.testInstrumentationRunnerArguments.firestoreProjectId=<id>`.
 */
internal object FirestoreEmulator {

    private const val HOST = "127.0.0.1"
    private const val PORT = 8085
    private const val PROJECT_ID_ARGUMENT = "firestoreProjectId"
    private const val DEFAULT_PROJECT_ID = "demo-cashi"
    private const val HTTP_DELETE = "DELETE"

    /** Placeholder credentials; the emulator doesn't check them. */
    private const val EMULATOR_APP_ID = "1:000000000000:android:0000000000000000"
    private const val EMULATOR_API_KEY = "emulator-only-api-key"

    /** Deletes all documents in the emulator (its REST reset endpoint). */
    fun clearData() {
        val url = URL("http://$HOST:$PORT/emulator/v1/projects/${projectId()}/databases/(default)/documents")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = HTTP_DELETE
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Clearing the Firestore emulator (project ${projectId()}) failed with HTTP ${connection.responseCode}. " +
                    "Is it running with the same project id and is scripts/device-setup.sh done?"
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun projectId(): String =
        InstrumentationRegistry.getArguments().getString(PROJECT_ID_ARGUMENT)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_PROJECT_ID

    fun newIsolatedFirestore(): FirebaseFirestore {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = FirebaseOptions.Builder()
            .setProjectId(projectId())
            .setApplicationId(EMULATOR_APP_ID)
            .setApiKey(EMULATOR_API_KEY)
            .build()
        val app = FirebaseApp.initializeApp(context, options, "contract-test-${Uuid.random()}")
        return FirebaseFirestore.getInstance(app).apply {
            useEmulator(HOST, PORT)
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }
}