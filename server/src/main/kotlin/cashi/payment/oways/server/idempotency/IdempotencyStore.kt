package cashi.payment.oways.server.idempotency

import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.ValidatedPayment

/**
 * Remembers which payment each idempotency key produced. Implementations must make the
 * check-and-create in [getOrCreate] atomic, so concurrent duplicates create one payment.
 * Production would back this with Redis or a database table with a unique key constraint.
 */
interface IdempotencyStore {
    fun getOrCreate(key: IdempotencyKey, request: ValidatedPayment, create: () -> Payment): IdempotencyResult
}

sealed interface IdempotencyResult {
    /** First use of the key: [payment] was just created. */
    data class Created(val payment: Payment) : IdempotencyResult

    /** The key was seen with the same request: [payment] is the original result. */
    data class Replayed(val payment: Payment) : IdempotencyResult

    /** The key was seen with a different request; nothing was created. */
    data object Mismatch : IdempotencyResult
}