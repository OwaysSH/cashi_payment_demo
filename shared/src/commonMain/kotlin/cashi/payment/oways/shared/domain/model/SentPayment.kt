package cashi.payment.oways.shared.domain.model

import cashi.payment.oways.shared.domain.error.PaymentStoreError

/**
 * A payment the backend accepted. [history] says whether it was also recorded in the transaction
 * history; a history problem never means the payment failed.
 */
data class SentPayment(val payment: Payment, val history: HistoryStatus)

sealed interface HistoryStatus {
    data object Recorded : HistoryStatus

    /** The payment was sent, but saving it to the history failed with [error]. */
    data class NotRecorded(val error: PaymentStoreError) : HistoryStatus
}