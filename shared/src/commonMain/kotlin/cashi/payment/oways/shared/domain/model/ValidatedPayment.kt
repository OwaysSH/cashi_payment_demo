package cashi.payment.oways.shared.domain.model

/**
 * Payment input that passed [cashi.payment.oways.shared.domain.validation.PaymentValidator].
 * The constructor and [copy] are internal, so other modules can only obtain one through the
 * validator; holding a [ValidatedPayment] proves the input was checked.
 */
@ConsistentCopyVisibility
data class ValidatedPayment internal constructor(
    val recipientEmail: String,
    val amountMinor: Long,
    val currency: Currency,
)