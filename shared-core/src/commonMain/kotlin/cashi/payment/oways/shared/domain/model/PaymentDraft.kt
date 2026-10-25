package cashi.payment.oways.shared.domain.model

/** Unvalidated payment input, as typed in the form or received by the API. */
data class PaymentDraft(
    val recipientEmail: String,
    val amount: Double,
    val currencyCode: String,
)