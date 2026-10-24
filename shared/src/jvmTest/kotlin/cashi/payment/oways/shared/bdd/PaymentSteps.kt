package cashi.payment.oways.shared.bdd

import cashi.payment.oways.shared.domain.error.PaymentError
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.HistoryStatus
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.PaymentDraft
import cashi.payment.oways.shared.domain.model.SentPayment
import cashi.payment.oways.shared.domain.money.toMinorUnits
import cashi.payment.oways.shared.domain.result.Outcome
import cashi.payment.oways.shared.domain.usecase.ObservePaymentsUseCase
import cashi.payment.oways.shared.domain.usecase.SendPaymentUseCase
import cashi.payment.oways.shared.domain.validation.AmountParser
import cashi.payment.oways.shared.domain.validation.PaymentValidator
import cashi.payment.oways.shared.testing.FakePaymentApi
import cashi.payment.oways.shared.testing.InMemoryPaymentStore
import io.cucumber.datatable.DataTable
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Step definitions for the payment features. Cucumber creates a new instance per scenario, so every
 * scenario gets fresh fakes. The use cases and the validator are the real shared code; only the
 * backend ([FakePaymentApi]) and Firestore ([InMemoryPaymentStore]) are faked.
 */
class PaymentSteps {

    private val clock = TickingClock()
    private val paymentApi = FakePaymentApi(clock)
    private val paymentStore = InMemoryPaymentStore(clock)
    private val sendPayment = SendPaymentUseCase(PaymentValidator(), paymentApi, paymentStore)
    private val observePayments = ObservePaymentsUseCase(paymentStore)

    /** Collects the live history while a scenario is "viewing" it; cancelled after each scenario. */
    private val viewingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val viewedHistory = MutableStateFlow<List<Payment>?>(null)

    private var lastResult: Outcome<SentPayment, PaymentError>? = null

    @After
    fun stopViewingHistory() {
        viewingScope.cancel()
    }

    @When("I send {word} {string} to {string}")
    fun iSend(amount: String, currencyCode: String, recipientEmail: String) {
        lastResult = send(amount, currencyCode, recipientEmail)
    }

    @Given("I have sent {word} {string} to {string}")
    fun iHaveSent(amount: String, currencyCode: String, recipientEmail: String) {
        assertIs<Outcome.Success<SentPayment>>(send(amount, currencyCode, recipientEmail))
    }

    @When("I send {word} {string} to {string} while viewing the history")
    fun iSendWhileViewing(amount: String, currencyCode: String, recipientEmail: String) {
        check(viewedHistory.value != null) { "the history must be open first" }
        lastResult = send(amount, currencyCode, recipientEmail)
    }

    @Then("the payment is processed")
    fun thePaymentIsProcessed() {
        assertIs<Outcome.Success<SentPayment>>(lastResult)
    }

    @Then("the backend processed {long} minor units in {string} for {string}")
    fun theBackendProcessed(amountMinor: Long, currencyCode: String, recipientEmail: String) {
        val call = paymentApi.calls.single()
        assertEquals(amountMinor, call.payment.amountMinor)
        assertEquals(currencyCode, call.payment.currency.code)
        assertEquals(recipientEmail, call.payment.recipientEmail)
    }

    @Then("it is saved to the transaction history")
    fun itIsSavedToTheHistory() {
        val sent = assertIs<Outcome.Success<SentPayment>>(lastResult).value
        assertEquals(HistoryStatus.Recorded, sent.history)
    }

    @Then("the transaction history shows {int} payment to {string} of {word} {string}")
    fun theHistoryShows(count: Int, recipientEmail: String, amount: String, currencyCode: String) {
        val history = currentHistory()
        assertEquals(count, history.size)
        val payment = history.single()
        assertEquals(recipientEmail, payment.recipientEmail)
        assertEquals(minorUnits(amount, currencyCode), payment.amountMinor)
        assertEquals(currencyCode, payment.currency.code)
    }

    @Then("the payment is rejected as an invalid recipient")
    fun thePaymentIsRejectedAsInvalidRecipient() {
        assertEquals(Outcome.Failure(PaymentError.InvalidEmail), lastResult)
    }

    @Then("the backend was not called")
    fun theBackendWasNotCalled() {
        assertTrue(paymentApi.calls.isEmpty(), "backend calls: ${paymentApi.calls}")
    }

    @Then("the transaction history is empty")
    fun theHistoryIsEmpty() {
        assertEquals(emptyList(), currentHistory())
    }

    @When("I open the transaction history")
    fun iOpenTheHistory() {
        viewingScope.launch {
            observePayments().collect { viewedHistory.value = it }
        }
        awaitViewedHistory { true }
    }

    @Then("I see these payments, newest first:")
    fun iSeeThesePayments(expected: DataTable) {
        val expectedRows = expected.asMaps().map { row ->
            val currency = row.cell("currency")
            Triple(row.cell("recipient"), minorUnits(row.cell("amount"), currency), currency)
        }
        val shownRows = awaitViewedHistory { it.size == expectedRows.size }
            .map { Triple(it.recipientEmail, it.amountMinor, it.currency.code) }
        assertEquals(expectedRows, shownRows)
    }

    @Then("{string} appears at the top of the history")
    fun appearsAtTheTop(recipientEmail: String) {
        awaitViewedHistory { it.firstOrNull()?.recipientEmail == recipientEmail }
    }

    private fun send(amount: String, currencyCode: String, recipientEmail: String): Outcome<SentPayment, PaymentError> {
        val parsedAmount = checkNotNull(AmountParser.parse(amount, fractionDigitsOf(currencyCode))) {
            "unparsable amount '$amount' in the scenario"
        }
        val draft = PaymentDraft(recipientEmail = recipientEmail, amount = parsedAmount, currencyCode = currencyCode)
        return runBlocking { sendPayment(draft, IdempotencyKey.random()) }
    }

    private fun currentHistory(): List<Payment> = runBlocking { observePayments().first() }

    private fun awaitViewedHistory(predicate: (List<Payment>) -> Boolean): List<Payment> = runBlocking {
        withTimeout(AWAIT_TIMEOUT) { viewedHistory.filterNotNull().first(predicate) }
    }

    private fun minorUnits(amount: String, currencyCode: String): Long {
        val currency = checkNotNull(Currency.fromCode(currencyCode)) { "unsupported currency '$currencyCode'" }
        val parsed = checkNotNull(AmountParser.parse(amount, currency.fractionDigits)) { "unparsable amount '$amount'" }
        return checkNotNull(currency.toMinorUnits(parsed)) { "amount '$amount' not representable" }
    }

    private fun Map<String?, String?>.cell(column: String): String =
        checkNotNull(this[column]) { "missing '$column' column in the scenario table" }

    private fun fractionDigitsOf(currencyCode: String): Int =
        Currency.fromCode(currencyCode)?.fractionDigits ?: DEFAULT_FRACTION_DIGITS

    /** Advances one second per reading, so consecutive payments get increasing timestamps. */
    private class TickingClock(private var now: Instant = Instant.parse("2026-10-23T09:00:00Z")) : Clock {
        @Synchronized
        override fun now(): Instant = now.also { now += 1.seconds }
    }

    private companion object {
        val AWAIT_TIMEOUT = 5.seconds
        const val DEFAULT_FRACTION_DIGITS = 2
    }
}