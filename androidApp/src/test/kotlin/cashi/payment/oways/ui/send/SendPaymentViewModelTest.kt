package cashi.payment.oways.ui.send

import cashi.payment.oways.R
import cashi.payment.oways.shared.domain.error.PaymentError
import cashi.payment.oways.shared.domain.error.PaymentStoreError
import cashi.payment.oways.shared.domain.gateway.PaymentApi
import cashi.payment.oways.shared.domain.gateway.PaymentStore
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.ValidatedPayment
import cashi.payment.oways.shared.domain.result.Outcome
import cashi.payment.oways.shared.domain.usecase.SendPaymentUseCase
import cashi.payment.oways.shared.domain.validation.PaymentValidator
import cashi.payment.oways.ui.format.AmountFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SendPaymentViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val api = RecordingPaymentApi()
    private val store = ScriptedPaymentStore()
    private lateinit var viewModel: SendPaymentViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val validator = PaymentValidator()
        viewModel = SendPaymentViewModel(
            sendPayment = SendPaymentUseCase(validator, api, store),
            validator = validator,
            amountFormatter = AmountFormatter(Locale.US),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isAnEmptyUsdForm() {
        assertEquals(SendPaymentUiState(), viewModel.state.value)
    }

    @Test
    fun edits_updateTheForm_andClearThatFieldsError() {
        viewModel.onEmailChange("bad")
        viewModel.onSendClick()
        assertEquals(FormField.EMAIL, viewModel.state.value.fieldError?.field)

        viewModel.onEmailChange("alice@example.com")
        viewModel.onAmountChange("10.50")
        viewModel.onCurrencyChange(Currency.EUR)

        val state = viewModel.state.value
        assertEquals("alice@example.com", state.recipientEmail)
        assertEquals("10.50", state.amountText)
        assertEquals(Currency.EUR, state.currency)
        assertNull(state.fieldError)
    }

    @Test
    fun unparsableAmount_showsAmountError_withoutCallingTheApi() = runTest(dispatcher) {
        fillForm(amount = "1,500")

        viewModel.onSendClick()
        advanceUntilIdle()

        assertEquals(FieldError(FormField.AMOUNT, R.string.error_invalid_amount), viewModel.state.value.fieldError)
        assertNull(viewModel.state.value.confirmation)
        assertEquals(emptyList(), api.keys)
    }

    @Test
    fun invalidEmail_showsEmailError_beforeAmountError() {
        fillForm(email = "not-an-email", amount = "abc")

        viewModel.onSendClick()

        assertEquals(FieldError(FormField.EMAIL, R.string.error_invalid_recipient), viewModel.state.value.fieldError)
    }

    @Test
    fun validForm_asksForConfirmation_andDismissGoesBack() {
        fillForm(email = " alice@example.com ", amount = "10,5")

        viewModel.onSendClick()
        assertEquals(PaymentConfirmation("alice@example.com", "$10.50"), viewModel.state.value.confirmation)

        viewModel.onDismissConfirmation()
        assertNull(viewModel.state.value.confirmation)
        assertEquals(emptyList(), api.keys)
    }

    @Test
    fun confirm_sends_thenShowsSent_andClearsTheForm() = runTest(dispatcher) {
        fillForm(currency = Currency.EUR)
        viewModel.onSendClick()

        viewModel.onConfirm()
        assertEquals(SubmissionStatus.Sending, viewModel.state.value.status)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SubmissionStatus.Sent(R.string.payment_sent), state.status)
        assertEquals("", state.recipientEmail)
        assertEquals("", state.amountText)
        assertEquals(Currency.EUR, state.currency)
        assertEquals(1, api.keys.size)
    }

    @Test
    fun storeFailure_isStillSent_withHistoryWarning() = runTest(dispatcher) {
        store.saveResult = Outcome.Failure(PaymentStoreError.Unavailable)

        sendValidPayment()

        assertEquals(SubmissionStatus.Sent(R.string.payment_sent_not_recorded), viewModel.state.value.status)
    }

    @Test
    fun networkFailure_showsMessage_andRetryReusesTheKey() = runTest(dispatcher) {
        api.failWith = PaymentError.Network
        sendValidPayment()
        assertEquals(SubmissionStatus.Failed(R.string.error_no_connection), viewModel.state.value.status)
        viewModel.onStatusShown()

        api.failWith = null
        sendValidPayment()

        assertEquals(2, api.keys.size)
        assertEquals(api.keys[0], api.keys[1])
    }

    @Test
    fun editingAFieldAndRestoringTheSameValue_afterNetworkFailure_reusesTheKey() = runTest(dispatcher) {
        api.failWith = PaymentError.Network
        sendValidPayment()
        viewModel.onStatusShown()

        viewModel.onAmountChange("10.505")
        viewModel.onAmountChange("10.50")
        api.failWith = null
        viewModel.onSendClick()
        viewModel.onConfirm()
        advanceUntilIdle()

        assertEquals(api.keys[0], api.keys[1])
    }

    @Test
    fun differentPaymentAfterFailure_getsANewKey() = runTest(dispatcher) {
        api.failWith = PaymentError.Network
        sendValidPayment()
        viewModel.onStatusShown()

        viewModel.onAmountChange("11.00")
        viewModel.onSendClick()
        viewModel.onConfirm()
        advanceUntilIdle()

        assertNotEquals(api.keys[0], api.keys[1])
    }

    @Test
    fun samePaymentAfterSuccess_getsANewKey() = runTest(dispatcher) {
        sendValidPayment()
        viewModel.onStatusShown()

        sendValidPayment()

        assertNotEquals(api.keys[0], api.keys[1])
    }

    @Test
    fun serverError_showsGenericMessage() = runTest(dispatcher) {
        api.failWith = PaymentError.Server(500)

        sendValidPayment()

        assertEquals(SubmissionStatus.Failed(R.string.error_generic), viewModel.state.value.status)
    }

    @Test
    fun serverValidationError_showsFieldError() = runTest(dispatcher) {
        api.failWith = PaymentError.InvalidEmail

        sendValidPayment()

        val state = viewModel.state.value
        assertEquals(SubmissionStatus.Idle, state.status)
        assertEquals(FieldError(FormField.EMAIL, R.string.error_invalid_recipient), state.fieldError)
    }

    @Test
    fun secondConfirmWhileSending_isIgnored() = runTest(dispatcher) {
        fillForm()
        viewModel.onSendClick()

        viewModel.onConfirm()
        viewModel.onConfirm()
        advanceUntilIdle()

        assertEquals(1, api.keys.size)
    }

    @Test
    fun onStatusShown_returnsToIdle() = runTest(dispatcher) {
        sendValidPayment()

        viewModel.onStatusShown()

        assertEquals(SubmissionStatus.Idle, viewModel.state.value.status)
    }

    private fun fillForm(email: String = "alice@example.com", amount: String = "10.50", currency: Currency = Currency.USD) {
        viewModel.onEmailChange(email)
        viewModel.onAmountChange(amount)
        viewModel.onCurrencyChange(currency)
    }

    private fun kotlinx.coroutines.test.TestScope.sendValidPayment() {
        fillForm()
        viewModel.onSendClick()
        viewModel.onConfirm()
        advanceUntilIdle()
    }

    /** Records the idempotency key of every call; fails with [failWith] when set. */
    private class RecordingPaymentApi : PaymentApi {
        val keys = mutableListOf<IdempotencyKey>()
        var failWith: PaymentError? = null

        override suspend fun createPayment(payment: ValidatedPayment, idempotencyKey: IdempotencyKey): Outcome<Payment, PaymentError> {
            keys += idempotencyKey
            failWith?.let { return Outcome.Failure(it) }
            return Outcome.Success(
                Payment("payment-${keys.size}", payment.recipientEmail, payment.amountMinor, payment.currency, Instant.fromEpochSeconds(0)),
            )
        }
    }

    private class ScriptedPaymentStore : PaymentStore {
        var saveResult: Outcome<Unit, PaymentStoreError> = Outcome.Success(Unit)

        override suspend fun save(payment: Payment): Outcome<Unit, PaymentStoreError> = saveResult
        override fun observePayments(): Flow<List<Payment>> = flowOf(emptyList())
    }
}