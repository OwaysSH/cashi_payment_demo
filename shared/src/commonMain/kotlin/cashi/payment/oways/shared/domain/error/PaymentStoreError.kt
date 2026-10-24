package cashi.payment.oways.shared.domain.error

/**
 * Failures of the payment history store. Separate from [PaymentError]: a store error happens after
 * the backend accepted the payment, so the payment itself succeeded.
 */
sealed interface PaymentStoreError {
    /** The store couldn't be reached (offline, timeout, backend down). */
    data object Unavailable : PaymentStoreError

    /** The store's security rules rejected the operation. */
    data object PermissionDenied : PaymentStoreError

    /** A payment with the same id is already stored with different data. */
    data object Conflict : PaymentStoreError
}

/** Ends a [cashi.payment.oways.shared.domain.gateway.PaymentStore.observePayments] flow on failure. */
class PaymentStoreException(val error: PaymentStoreError) : Exception("Payment store failure: $error")