package cashi.payment.oways.ui.send

import androidx.annotation.StringRes
import cashi.payment.oways.shared.domain.model.Currency

/** The whole Send screen state: the form, an inline field error, a pending confirmation and the submission status. */
data class SendPaymentUiState(
    val recipientEmail: String = "",
    val amountText: String = "",
    val currency: Currency = Currency.USD,
    val fieldError: FieldError? = null,
    val confirmation: PaymentConfirmation? = null,
    val status: SubmissionStatus = SubmissionStatus.Idle,
) {
    val isSending: Boolean get() = status == SubmissionStatus.Sending
}

enum class FormField { EMAIL, AMOUNT, CURRENCY }

/** A validation error shown inline under [field]. */
data class FieldError(val field: FormField, @param:StringRes val messageRes: Int)

/** What the confirmation dialog shows before the payment is sent. */
data class PaymentConfirmation(val recipientEmail: String, val formattedAmount: String)

sealed interface SubmissionStatus {
    data object Idle : SubmissionStatus
    data object Sending : SubmissionStatus

    /** A finished submission whose message is shown once in a snackbar. */
    sealed interface Finished : SubmissionStatus {
        @get:StringRes val messageRes: Int
    }

    data class Sent(@param:StringRes override val messageRes: Int) : Finished
    data class Failed(@param:StringRes override val messageRes: Int) : Finished
}