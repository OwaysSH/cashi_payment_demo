package cashi.payment.oways.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cashi.payment.oways.R
import cashi.payment.oways.shared.domain.error.PaymentStoreException
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.usecase.ObservePaymentsUseCase
import cashi.payment.oways.ui.format.AmountFormatter
import cashi.payment.oways.ui.format.TimestampFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Maps the live, newest-first history from [ObservePaymentsUseCase] to [HistoryUiState]. The
 * Firestore listener only runs while the screen is collecting (plus a short grace period, e.g. for
 * rotation). A store failure becomes [HistoryUiState.Error]; [onRetry] subscribes again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val observePayments: ObservePaymentsUseCase,
    private val amountFormatter: AmountFormatter,
    private val timestampFormatter: TimestampFormatter,
) : ViewModel() {

    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = RETRY_BUFFER)

    val state: StateFlow<HistoryUiState> = retries
        .onStart { emit(Unit) }
        .flatMapLatest {
            observePayments()
                .map(::toState)
                .onStart { emit(HistoryUiState.Loading) }
                .catch { error ->
                    if (error !is PaymentStoreException) throw error
                    emit(HistoryUiState.Error(R.string.history_error))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HistoryUiState.Loading)

    fun onRetry() {
        retries.tryEmit(Unit)
    }

    private fun toState(payments: List<Payment>): HistoryUiState =
        if (payments.isEmpty()) HistoryUiState.Empty else HistoryUiState.Content(payments.map(::toItem))

    private fun toItem(payment: Payment) = PaymentItem(
        id = payment.id,
        recipientEmail = payment.recipientEmail,
        formattedAmount = amountFormatter.format(payment.amountMinor, payment.currency),
        currencyCode = payment.currency.code,
        formattedTimestamp = timestampFormatter.format(payment.createdAt),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val RETRY_BUFFER = 1
    }
}