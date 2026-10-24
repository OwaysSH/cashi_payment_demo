package cashi.payment.oways.shared.api

import kotlinx.serialization.Serializable

/**
 * Body of `POST /payments`. Fields are deliberately raw (unvalidated) so the server can report
 * specific validation errors instead of failing during deserialization. Only [amount]'s JSON type
 * is enforced: it must be a JSON number, not a quoted string.
 */
@Serializable
data class PaymentRequest(
    val recipientEmail: String,
    @Serializable(with = StrictJsonDoubleSerializer::class)
    val amount: Double,
    val currency: String,
)