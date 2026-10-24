package cashi.payment.oways.e2e

import io.appium.java_client.AppiumBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end tests on a real device: app → Ktor server (adb reverse 8080) → Firestore emulator
 * (adb reverse 8085). Prerequisites and how to run: e2e/README.md.
 */
class SendPaymentE2ETest {

    private lateinit var app: AppiumSession

    @BeforeEach
    fun launchApp() {
        app = AppiumSession.start()
    }

    @AfterEach
    fun closeSession() {
        app.close()
    }

    @Test
    fun sendPayment_appearsInHistory() {
        val recipient = uniqueRecipient()

        app.type(TestIds.SEND_EMAIL_FIELD, recipient)
        app.type(TestIds.SEND_AMOUNT_FIELD, AMOUNT)
        app.tap(TestIds.SEND_CURRENCY_FIELD)
        app.tap(TestIds.currencyOption(CURRENCY))
        app.tap(TestIds.SEND_BUTTON)

        app.waitForText(TestIds.CONFIRM_MESSAGE, recipient)
        app.tap(TestIds.CONFIRM_SEND_BUTTON)
        app.waitForText(TestIds.SNACKBAR_MESSAGE, PAYMENT_SENT)

        app.tap(TestIds.NAV_HISTORY)
        val row = app.waitForRow(TestIds.HISTORY_ITEM, TestIds.HISTORY_ITEM_RECIPIENT, recipient)
        val amount = row.findElement(AppiumBy.id(TestIds.HISTORY_ITEM_AMOUNT)).text
        assertTrue(AMOUNT_DIGITS.any { amount.contains(it) }, "amount '$amount' should show $AMOUNT")
        assertTrue(amount.contains(CURRENCY), "amount '$amount' should show the currency code $CURRENCY")
    }

    @Test
    fun invalidRecipient_showsInlineError() {
        app.type(TestIds.SEND_EMAIL_FIELD, INVALID_RECIPIENT)
        app.type(TestIds.SEND_AMOUNT_FIELD, AMOUNT)
        app.tap(TestIds.SEND_BUTTON)

        app.waitForText(TestIds.SEND_EMAIL_ERROR, INVALID_RECIPIENT_MESSAGE)
        assertFalse(app.isPresent(TestIds.CONFIRM_MESSAGE), "no confirmation dialog for an invalid recipient")
    }

    /** Unique per run, so the test finds its own payment among earlier ones in the emulator. */
    private fun uniqueRecipient(): String =
        "e2e-${System.currentTimeMillis()}-${Random.nextInt(RANDOM_SUFFIX_BOUND).toString().padStart(4, '0')}@example.com"

    private companion object {
        const val AMOUNT = "12.34"
        val AMOUNT_DIGITS = listOf("12.34", "12,34") // depends on the device locale
        const val CURRENCY = "EUR"
        const val PAYMENT_SENT = "Payment sent"
        const val INVALID_RECIPIENT = "not-an-email"
        const val INVALID_RECIPIENT_MESSAGE = "Invalid recipient"
        const val RANDOM_SUFFIX_BOUND = 10_000
    }
}