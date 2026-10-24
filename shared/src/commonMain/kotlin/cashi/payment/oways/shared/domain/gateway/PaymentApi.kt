package cashi.payment.oways.shared.domain.gateway

import cashi.payment.oways.shared.domain.error.PaymentError
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.ValidatedPayment
import cashi.payment.oways.shared.domain.result.Outcome

/** Backend that processes payments. Takes only validated input; returns the processed payment. */
interface PaymentApi {
    suspend fun createPayment(
        payment: ValidatedPayment,
        idempotencyKey: IdempotencyKey,
    ): Outcome<Payment, PaymentError>
}