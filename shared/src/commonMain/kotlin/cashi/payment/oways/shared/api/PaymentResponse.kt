package cashi.payment.oways.shared.api

import kotlinx.serialization.Serializable

/** Processed payment returned by `POST /payments`. [createdAt] is an ISO-8601 UTC timestamp. */
@Serializable
data class PaymentResponse(
    val id: String,
    val recipientEmail: String,
    val amount: Double,
    val currency: String,
    val createdAt: String,
)