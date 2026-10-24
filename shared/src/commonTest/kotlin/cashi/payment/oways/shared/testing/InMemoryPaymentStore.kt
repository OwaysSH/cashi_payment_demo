package cashi.payment.oways.shared.testing

import cashi.payment.oways.shared.domain.error.PaymentStoreError
import cashi.payment.oways.shared.domain.error.PaymentStoreException
import cashi.payment.oways.shared.domain.gateway.PaymentStore
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.PaymentOrdering
import cashi.payment.oways.shared.domain.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.getAndUpdate
import kotlin.time.Clock

/**
 * Test fake of [PaymentStore] that satisfies the same contract as the real store
 * (see `PaymentStoreContractTest`). Timestamps come from the injected [clock].
 * [failNextSaveWith] and [failObservationWith] simulate store failures.
 */
class InMemoryPaymentStore(private val clock: Clock) : PaymentStore {

    private val payments = MutableStateFlow<Map<String, Payment>>(emptyMap())
    private val observationFailure = MutableStateFlow<PaymentStoreError?>(null)
    private var nextSaveFailure: PaymentStoreError? = null

    override suspend fun save(payment: Payment): Outcome<Unit, PaymentStoreError> {
        nextSaveFailure?.let { failure ->
            nextSaveFailure = null
            return Outcome.Failure(failure)
        }
        val stored = payment.copy(createdAt = clock.now())
        val before = payments.getAndUpdate { current ->
            if (payment.id in current) current else current + (payment.id to stored)
        }
        val existing = before[payment.id] ?: return Outcome.Success(Unit)
        return if (existing.hasSameContentAs(payment)) {
            Outcome.Success(Unit)
        } else {
            Outcome.Failure(PaymentStoreError.Conflict)
        }
    }

    override fun observePayments(): Flow<List<Payment>> =
        combine(payments, observationFailure) { current, failure ->
            if (failure != null) throw PaymentStoreException(failure)
            current.values.sortedWith(PaymentOrdering.NewestFirst)
        }

    /** The next [save] fails with [error] without storing anything. */
    fun failNextSaveWith(error: PaymentStoreError) {
        nextSaveFailure = error
    }

    /** Active and future [observePayments] flows end with a [PaymentStoreException] for [error]. */
    fun failObservationWith(error: PaymentStoreError) {
        observationFailure.value = error
    }

    private fun Payment.hasSameContentAs(other: Payment): Boolean =
        recipientEmail == other.recipientEmail &&
            amountMinor == other.amountMinor &&
            currency == other.currency
}