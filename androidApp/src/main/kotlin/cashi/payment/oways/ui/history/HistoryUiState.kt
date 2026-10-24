package cashi.payment.oways.ui.history

import androidx.annotation.StringRes

/** The whole History screen state. */
sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data object Empty : HistoryUiState
    data class Content(val items: List<PaymentItem>) : HistoryUiState
    data class Error(@param:StringRes val messageRes: Int) : HistoryUiState
}

/**
 * One transaction row, ready to display. [formattedTimestamp] is Firestore's server timestamp (the
 * source of truth), not the API's `createdAt`. [currencyCode] is shown explicitly next to the amount.
 */
data class PaymentItem(
    val id: String,
    val recipientEmail: String,
    val formattedAmount: String,
    val currencyCode: String,
    val formattedTimestamp: String,
)