package cashi.payment.oways.server.payment

import cashi.payment.oways.shared.api.PaymentRequest
import cashi.payment.oways.shared.api.PaymentResponse
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.PaymentDraft
import cashi.payment.oways.shared.domain.money.toMajorUnits

fun PaymentRequest.toDraft(): PaymentDraft = PaymentDraft(
    recipientEmail = recipientEmail,
    amount = amount,
    currencyCode = currency,
)

fun Payment.toResponse(): PaymentResponse = PaymentResponse(
    id = id,
    recipientEmail = recipientEmail,
    amount = currency.toMajorUnits(amountMinor),
    currency = currency.code,
    createdAt = createdAt.toString(),
)