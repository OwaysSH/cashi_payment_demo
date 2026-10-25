package cashi.payment.oways.shared.domain.model

import kotlin.time.Instant

/** A payment the backend has processed. Amounts are kept in minor units (e.g. cents). */
data class Payment(
    val id: String,
    val recipientEmail: String,
    val amountMinor: Long,
    val currency: Currency,
    val createdAt: Instant,
)