package cashi.payment.oways.shared.testing

import cashi.payment.oways.shared.domain.error.PaymentError
import cashi.payment.oways.shared.domain.gateway.PaymentApi
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.ValidatedPayment
import cashi.payment.oways.shared.domain.result.Outcome
import kotlin.time.Clock

/**
 * Test fake of [PaymentApi]. By default it "processes" every payment (ids `payment-1`, `payment-2`,
 * … and the [clock]'s time); [failWith] makes every following call fail. Records all calls.
 */
class FakePaymentApi(private val clock: Clock) : PaymentApi {

    data class Call(val payment: ValidatedPayment, val idempotencyKey: IdempotencyKey)

    private val recordedCalls = mutableListOf<Call>()
    private var failure: PaymentError? = null

    val calls: List<Call> get() = recordedCalls.toList()

    override suspend fun createPayment(
        payment: ValidatedPayment,
        idempotencyKey: IdempotencyKey,
    ): Outcome<Payment, PaymentError> {
        recordedCalls += Call(payment, idempotencyKey)
        failure?.let { return Outcome.Failure(it) }
        return Outcome.Success(
            Payment(
                id = "payment-${recordedCalls.size}",
                recipientEmail = payment.recipientEmail,
                amountMinor = payment.amountMinor,
                currency = payment.currency,
                createdAt = clock.now(),
            ),
        )
    }

    /** Every following call fails with [error]; `null` restores success. */
    fun failWith(error: PaymentError?) {
        failure = error
    }
}