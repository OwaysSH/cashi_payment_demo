package cashi.payment.oways.ui.history

import cashi.payment.oways.R
import cashi.payment.oways.shared.domain.error.PaymentStoreError
import cashi.payment.oways.shared.domain.error.PaymentStoreException
import cashi.payment.oways.shared.domain.gateway.PaymentStore
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.result.Outcome
import cashi.payment.oways.shared.domain.usecase.ObservePaymentsUseCase
import cashi.payment.oways.ui.format.AmountFormatter
import cashi.payment.oways.ui.format.TimestampFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.Locale
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = ControllableStore()
    private lateinit var viewModel: HistoryViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = HistoryViewModel(
            observePayments = ObservePaymentsUseCase(store),
            amountFormatter = AmountFormatter(Locale.US),
            timestampFormatter = TimestampFormatter(Locale.US, TimeZone.getTimeZone("UTC")),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startsLoading() {
        assertEquals(HistoryUiState.Loading, viewModel.state.value)
    }

    @Test
    fun emptyHistory_isEmpty() = runTest(dispatcher) {
        collectState()
        advanceUntilIdle()

        assertEquals(HistoryUiState.Empty, viewModel.state.value)
    }

    @Test
    fun payments_areShownNewestFirst_withAmountCurrencyCodeAndTimestamp() = runTest(dispatcher) {
        store.payments.value = listOf(
            payment("p-2", "second@example.com", 2_550L, Currency.EUR, "2026-10-23T09:20:00Z"),
            payment("p-1", "alice@example.com", 1_050L, Currency.USD, "2026-10-23T09:15:00Z"),
        )
        collectState()
        advanceUntilIdle()

        val expected = HistoryUiState.Content(
            listOf(
                PaymentItem("p-2", "second@example.com", "€25.50", "EUR", "Oct 23, 2026, 9:20 AM"),
                PaymentItem("p-1", "alice@example.com", "$10.50", "USD", "Oct 23, 2026, 9:15 AM"),
            ),
        )
        assertEquals(expected, viewModel.state.value.withPlainSpaces())
    }

    @Test
    fun newPayment_appearsLive() = runTest(dispatcher) {
        collectState()
        advanceUntilIdle()

        store.payments.value = listOf(payment("p-1", "alice@example.com", 1_050L, Currency.USD, "2026-10-23T09:15:00Z"))
        advanceUntilIdle()

        val content = viewModel.state.value as HistoryUiState.Content
        assertEquals(listOf("p-1"), content.items.map { it.id })
        assertEquals("USD", content.items.single().currencyCode)
    }

    @Test
    fun storeFailure_showsError_andRetryRecovers() = runTest(dispatcher) {
        store.failing = true
        collectState()
        advanceUntilIdle()
        assertEquals(HistoryUiState.Error(R.string.history_error), viewModel.state.value)

        store.failing = false
        store.payments.value = listOf(payment("p-1", "alice@example.com", 1_050L, Currency.USD, "2026-10-23T09:15:00Z"))
        viewModel.onRetry()
        advanceUntilIdle()

        val content = viewModel.state.value as HistoryUiState.Content
        assertEquals("p-1", content.items.single().id)
    }

    @Test
    fun stopsObserving_fiveSecondsAfterTheLastCollectorLeaves() = runTest(dispatcher) {
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()
        assertEquals(1, store.payments.subscriptionCount.value)

        collector.cancel()
        advanceTimeBy(STOP_TIMEOUT_MILLIS - 1)
        assertEquals(1, store.payments.subscriptionCount.value)
        advanceTimeBy(2)
        assertEquals(0, store.payments.subscriptionCount.value)
    }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    /** Some JDKs format "9:20 AM" with a narrow no-break space; compare with plain spaces. */
    private fun HistoryUiState.withPlainSpaces(): HistoryUiState = when (this) {
        is HistoryUiState.Content -> copy(items = items.map { it.copy(formattedTimestamp = it.formattedTimestamp.replace(NARROW_NBSP, ' ')) })
        else -> this
    }

    private fun payment(id: String, email: String, amountMinor: Long, currency: Currency, timestamp: String) =
        Payment(id, email, amountMinor, currency, Instant.parse(timestamp))

    /** Observation-only store whose history and failure can be changed by the test. */
    private class ControllableStore : PaymentStore {
        val payments = MutableStateFlow<List<Payment>>(emptyList())
        var failing = false

        override suspend fun save(payment: Payment): Outcome<Unit, PaymentStoreError> = error("not used")

        override fun observePayments(): Flow<List<Payment>> = flow {
            if (failing) throw PaymentStoreException(PaymentStoreError.Unavailable)
            emitAll(payments)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val NARROW_NBSP = ' '
    }
}