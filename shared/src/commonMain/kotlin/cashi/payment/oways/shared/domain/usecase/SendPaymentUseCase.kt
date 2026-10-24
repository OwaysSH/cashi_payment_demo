package cashi.payment.oways.shared.domain.usecase

import cashi.payment.oways.shared.domain.error.PaymentError
import cashi.payment.oways.shared.domain.gateway.PaymentApi
import cashi.payment.oways.shared.domain.gateway.PaymentStore
import cashi.payment.oways.shared.domain.model.HistoryStatus
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.PaymentDraft
import cashi.payment.oways.shared.domain.model.SentPayment
import cashi.payment.oways.shared.domain.result.Outcome
import cashi.payment.oways.shared.domain.validation.PaymentValidator

/**
 * Sends a payment: validate locally (no network on invalid input) → backend → record in history.
 *
 * A failure is a [PaymentError] and means nothing was sent. Once the backend accepted the payment
 * the result is a success, even if recording it in the history fails ([HistoryStatus.NotRecorded]).
 * The caller supplies the [IdempotencyKey]: one per submission, reused when retrying it.
 */
class SendPaymentUseCase(
    private val validator: PaymentValidator,
    private val paymentApi: PaymentApi,
    private val paymentStore: PaymentStore,
) {
    suspend operator fun invoke(draft: PaymentDraft, idempotencyKey: IdempotencyKey): Outcome<SentPayment, PaymentError> {
        val validated = when (val validation = validator.validate(draft)) {
            is Outcome.Failure -> return validation
            is Outcome.Success -> validation.value
        }
        return when (val sent = paymentApi.createPayment(validated, idempotencyKey)) {
            is Outcome.Failure -> sent
            is Outcome.Success -> Outcome.Success(SentPayment(sent.value, recordInHistory(sent.value)))
        }
    }

    private suspend fun recordInHistory(payment: Payment): HistoryStatus =
        when (val saved = paymentStore.save(payment)) {
            is Outcome.Success -> HistoryStatus.Recorded
            is Outcome.Failure -> HistoryStatus.NotRecorded(saved.error)
        }
}