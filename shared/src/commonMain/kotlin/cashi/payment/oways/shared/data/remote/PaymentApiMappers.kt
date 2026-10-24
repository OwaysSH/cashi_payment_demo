package cashi.payment.oways.shared.data.remote

import cashi.payment.oways.shared.api.PaymentRequest
import cashi.payment.oways.shared.api.PaymentResponse
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.ValidatedPayment
import cashi.payment.oways.shared.domain.money.toMajorUnits
import cashi.payment.oways.shared.domain.money.toMinorUnits
import kotlin.time.Instant

internal fun ValidatedPayment.toRequest(): PaymentRequest = PaymentRequest(
    recipientEmail = recipientEmail,
    amount = currency.toMajorUnits(amountMinor),
    currency = currency.code,
)

/** `null` if the response breaks the API contract (unknown currency, invalid amount or timestamp). */
internal fun PaymentResponse.toDomain(): Payment? {
    val currency = Currency.fromCode(currency) ?: return null
    val amountMinor = currency.toMinorUnits(amount) ?: return null
    val createdAt = parseInstantOrNull(createdAt) ?: return null
    return Payment(
        id = id,
        recipientEmail = recipientEmail,
        amountMinor = amountMinor,
        currency = currency,
        createdAt = createdAt,
    )
}

private fun parseInstantOrNull(value: String): Instant? =
    try {
        Instant.parse(value)
    } catch (_: IllegalArgumentException) {
        null
    }