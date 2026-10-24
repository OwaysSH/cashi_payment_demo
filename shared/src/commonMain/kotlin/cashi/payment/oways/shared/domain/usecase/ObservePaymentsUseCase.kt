package cashi.payment.oways.shared.domain.usecase

import cashi.payment.oways.shared.domain.error.PaymentStoreException
import cashi.payment.oways.shared.domain.gateway.PaymentStore
import cashi.payment.oways.shared.domain.model.Payment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Real-time transaction history, newest first, as guaranteed by the [PaymentStore] contract.
 * Equal consecutive lists are dropped (e.g. a snapshot whose data didn't change). A store failure
 * ends the flow with a [PaymentStoreException]; callers map it to their error state.
 */
class ObservePaymentsUseCase(private val paymentStore: PaymentStore) {
    operator fun invoke(): Flow<List<Payment>> = paymentStore.observePayments().distinctUntilChanged()
}