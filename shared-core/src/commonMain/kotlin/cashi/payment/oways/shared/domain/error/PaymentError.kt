package cashi.payment.oways.shared.domain.error

/** Every way sending a payment can fail. User-facing text is mapped in the UI, not here. */
sealed interface PaymentError {

    /** Input rejected by [cashi.payment.oways.shared.domain.validation.PaymentValidator]. */
    sealed interface Validation : PaymentError

    data object InvalidEmail : Validation
    data object InvalidAmount : Validation
    data object UnsupportedCurrency : Validation

    /** The backend could not be reached (no connection, timeout). */
    data object Network : PaymentError

    /** The backend answered with an unexpected error status. */
    data class Server(val statusCode: Int) : PaymentError
}