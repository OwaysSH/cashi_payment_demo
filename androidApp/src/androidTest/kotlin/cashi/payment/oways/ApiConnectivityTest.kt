package cashi.payment.oways

import androidx.test.ext.junit.runners.AndroidJUnit4
import cashi.payment.oways.shared.data.remote.KtorPaymentApi
import cashi.payment.oways.shared.data.remote.createOkHttpEngine
import cashi.payment.oways.shared.data.remote.createPaymentHttpClient
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.PaymentDraft
import cashi.payment.oways.shared.domain.result.Outcome
import cashi.payment.oways.shared.domain.validation.PaymentValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-demand device smoke test: proves the OkHttp engine, [BuildConfig.API_BASE_URL], the debug
 * network security config, the INTERNET permission and `adb reverse` work together.
 *
 * Prerequisites: the backend is running and `scripts/device-setup.sh` has been run.
 * Run with `./gradlew :androidApp:connectedDebugAndroidTest`. Not part of `allTests` or `check`.
 */
@RunWith(AndroidJUnit4::class)
class ApiConnectivityTest {

    @Test
    fun createPayment_reachesLocalBackend(): Unit = runBlocking {
        val api = KtorPaymentApi(createPaymentHttpClient(createOkHttpEngine(), BuildConfig.API_BASE_URL))
        val validated = PaymentValidator().validate(PaymentDraft("smoke-test@example.com", 1.0, "USD"))
        check(validated is Outcome.Success) { "smoke-test draft must be valid" }

        val result = api.createPayment(validated.value, IdempotencyKey.random())

        assertTrue(
            "Expected success from ${BuildConfig.API_BASE_URL} but was $result. " +
                "Is the server running and scripts/device-setup.sh done?",
            result is Outcome.Success,
        )
    }
}