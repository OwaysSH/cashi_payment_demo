package cashi.payment.oways.shared.domain.gateway

import cashi.payment.oways.shared.domain.error.PaymentStoreError
import cashi.payment.oways.shared.domain.error.PaymentStoreException
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Transaction history of processed payments.
 *
 * The store assigns its own timestamp: [save] ignores [Payment.createdAt], and payments emitted by
 * [observePayments] carry the store's timestamp in `createdAt`. That timestamp is the source of
 * truth for the history.
 */
interface PaymentStore {

    /**
     * Records a processed payment. Idempotent per payment id: saving the same payment again
     * succeeds without a duplicate; the same id with different data fails with
     * [PaymentStoreError.Conflict].
     */
    suspend fun save(payment: Payment): Outcome<Unit, PaymentStoreError>

    /**
     * Real-time history, newest first (ties by id). Emits the current list immediately, then on
     * every change. A listener failure ends the flow with a [PaymentStoreException].
     */
    fun observePayments(): Flow<List<Payment>>
}