package cashi.payment.oways.ui.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cashi.payment.oways.R
import cashi.payment.oways.shared.domain.error.PaymentError
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.HistoryStatus
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.PaymentDraft
import cashi.payment.oways.shared.domain.model.SentPayment
import cashi.payment.oways.shared.domain.model.ValidatedPayment
import cashi.payment.oways.shared.domain.result.Outcome
import cashi.payment.oways.shared.domain.usecase.SendPaymentUseCase
import cashi.payment.oways.shared.domain.validation.AmountParser
import cashi.payment.oways.shared.domain.validation.PaymentValidator
import cashi.payment.oways.ui.format.AmountFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the Send form and turns shared results into [SendPaymentUiState]. Validation, parsing and
 * sending are shared code; this class only orchestrates and maps to messages.
 *
 * Idempotency: the key of the last unfinished submission is reused while the validated content
 * (trimmed email, minor-unit amount, currency) is the same, so retrying after a timeout can't
 * create a duplicate even if the form was edited back to the same values. A different payment, or a
 * success, gets a new key.
 */
class SendPaymentViewModel(
    private val sendPayment: SendPaymentUseCase,
    private val validator: PaymentValidator,
    private val amountFormatter: AmountFormatter,
) : ViewModel() {

    private val mutableState = MutableStateFlow(SendPaymentUiState())
    val state: StateFlow<SendPaymentUiState> = mutableState.asStateFlow()

    private var awaitingConfirmation: Submission? = null
    private var pending: PendingSubmission? = null

    fun onEmailChange(value: String) = mutableState.update {
        it.copy(recipientEmail = value, fieldError = it.fieldError.clearedFor(FormField.EMAIL))
    }

    fun onAmountChange(value: String) = mutableState.update {
        it.copy(amountText = value, fieldError = it.fieldError.clearedFor(FormField.AMOUNT))
    }

    fun onCurrencyChange(value: Currency) = mutableState.update {
        it.copy(currency = value, fieldError = it.fieldError.clearedFor(FormField.CURRENCY))
    }

    fun onSendClick() {
        val form = mutableState.value
        if (form.isSending) return
        // Unparsable text becomes NaN, which the validator rejects as an invalid amount after
        // checking email and currency, so the shared validation order is kept.
        val amount = AmountParser.parse(form.amountText, form.currency.fractionDigits) ?: Double.NaN
        val draft = PaymentDraft(form.recipientEmail, amount, form.currency.code)
        when (val validation = validator.validate(draft)) {
            is Outcome.Failure -> mutableState.update { it.copy(fieldError = validation.error.toFieldError()) }
            is Outcome.Success -> askForConfirmation(Submission(draft, validation.value))
        }
    }

    fun onDismissConfirmation() {
        awaitingConfirmation = null
        mutableState.update { it.copy(confirmation = null) }
    }

    fun onConfirm() {
        val submission = awaitingConfirmation ?: return
        awaitingConfirmation = null
        val key = pending?.takeIf { it.payment == submission.payment }?.key ?: IdempotencyKey.random()
        pending = PendingSubmission(submission.payment, key)
        mutableState.update { it.copy(confirmation = null, status = SubmissionStatus.Sending) }
        viewModelScope.launch {
            onResult(sendPayment(submission.draft, key))
        }
    }

    /** The snackbar for a finished submission has been shown. */
    fun onStatusShown() = mutableState.update {
        if (it.status is SubmissionStatus.Finished) it.copy(status = SubmissionStatus.Idle) else it
    }

    private fun askForConfirmation(submission: Submission) {
        awaitingConfirmation = submission
        val confirmation = PaymentConfirmation(
            recipientEmail = submission.payment.recipientEmail,
            formattedAmount = amountFormatter.format(submission.payment.amountMinor, submission.payment.currency),
        )
        mutableState.update { it.copy(fieldError = null, confirmation = confirmation) }
    }

    private fun onResult(result: Outcome<SentPayment, PaymentError>) {
        when (result) {
            is Outcome.Success -> {
                pending = null
                mutableState.update {
                    SendPaymentUiState(currency = it.currency, status = SubmissionStatus.Sent(result.value.messageRes()))
                }
            }
            is Outcome.Failure -> when (val error = result.error) {
                is PaymentError.Validation -> {
                    pending = null
                    mutableState.update { it.copy(fieldError = error.toFieldError(), status = SubmissionStatus.Idle) }
                }
                PaymentError.Network -> fail(R.string.error_no_connection)
                is PaymentError.Server -> fail(R.string.error_generic)
            }
        }
    }

    /** Keeps [pending], so a retry of the same payment reuses its idempotency key. */
    private fun fail(messageRes: Int) = mutableState.update { it.copy(status = SubmissionStatus.Failed(messageRes)) }

    private fun SentPayment.messageRes(): Int = when (history) {
        HistoryStatus.Recorded -> R.string.payment_sent
        is HistoryStatus.NotRecorded -> R.string.payment_sent_not_recorded
    }

    private fun PaymentError.Validation.toFieldError(): FieldError = when (this) {
        PaymentError.InvalidEmail -> FieldError(FormField.EMAIL, R.string.error_invalid_recipient)
        PaymentError.InvalidAmount -> FieldError(FormField.AMOUNT, R.string.error_invalid_amount)
        PaymentError.UnsupportedCurrency -> FieldError(FormField.CURRENCY, R.string.error_unsupported_currency)
    }

    private fun FieldError?.clearedFor(field: FormField): FieldError? = this?.takeUnless { it.field == field }

    private data class Submission(val draft: PaymentDraft, val payment: ValidatedPayment)

    private data class PendingSubmission(val payment: ValidatedPayment, val key: IdempotencyKey)
}