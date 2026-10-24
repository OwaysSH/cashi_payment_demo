package cashi.payment.oways.server.payment

import cashi.payment.oways.server.idempotency.IdempotencyResult
import cashi.payment.oways.server.idempotency.IdempotencyStore
import cashi.payment.oways.shared.domain.error.PaymentError
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.PaymentDraft
import cashi.payment.oways.shared.domain.model.ValidatedPayment
import cashi.payment.oways.shared.domain.result.Outcome
import cashi.payment.oways.shared.domain.validation.PaymentValidator
import kotlin.time.Clock

data class ProcessedPayment(val payment: Payment, val replayed: Boolean)

/** Why a payment request was not processed. Server-local: key reuse is an HTTP-API concern. */
sealed interface ProcessPaymentFailure {
    data class Invalid(val error: PaymentError.Validation) : ProcessPaymentFailure
    data object IdempotencyKeyMismatch : ProcessPaymentFailure
}

/**
 * Validates a payment draft with the shared validator and turns it into a processed payment.
 * With an [IdempotencyKey], a repeated request returns the original payment instead of a new one.
 */
class PaymentService(
    private val validator: PaymentValidator,
    private val clock: Clock,
    private val idGenerator: PaymentIdGenerator,
    private val idempotencyStore: IdempotencyStore,
) {
    fun process(draft: PaymentDraft, key: IdempotencyKey?): Outcome<ProcessedPayment, ProcessPaymentFailure> =
        when (val validation = validator.validate(draft)) {
            is Outcome.Failure -> Outcome.Failure(ProcessPaymentFailure.Invalid(validation.error))
            is Outcome.Success -> when (key) {
                null -> Outcome.Success(ProcessedPayment(createPayment(validation.value), replayed = false))
                else -> processIdempotently(key, validation.value)
            }
        }

    private fun processIdempotently(
        key: IdempotencyKey,
        validated: ValidatedPayment,
    ): Outcome<ProcessedPayment, ProcessPaymentFailure> =
        when (val result = idempotencyStore.getOrCreate(key, validated) { createPayment(validated) }) {
            is IdempotencyResult.Created -> Outcome.Success(ProcessedPayment(result.payment, replayed = false))
            is IdempotencyResult.Replayed -> Outcome.Success(ProcessedPayment(result.payment, replayed = true))
            IdempotencyResult.Mismatch -> Outcome.Failure(ProcessPaymentFailure.IdempotencyKeyMismatch)
        }

    private fun createPayment(validated: ValidatedPayment): Payment = Payment(
        id = idGenerator.next(),
        recipientEmail = validated.recipientEmail,
        amountMinor = validated.amountMinor,
        currency = validated.currency,
        createdAt = clock.now(),
    )
}