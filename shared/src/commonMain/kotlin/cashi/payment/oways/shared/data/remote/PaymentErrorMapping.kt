package cashi.payment.oways.shared.data.remote

import cashi.payment.oways.shared.api.ApiErrorCode
import cashi.payment.oways.shared.domain.error.PaymentError

/**
 * Maps an API error code to the domain error. Validation codes become the same errors the shared
 * validator produces, so the UI treats client- and server-side rejections alike. Codes the user
 * can't act on, and unknown codes (`null`), become [PaymentError.Server]. Listed exhaustively so a
 * new [ApiErrorCode] forces a decision here.
 */
internal fun ApiErrorCode?.toPaymentError(statusCode: Int): PaymentError = when (this) {
    ApiErrorCode.INVALID_EMAIL -> PaymentError.InvalidEmail
    ApiErrorCode.INVALID_AMOUNT -> PaymentError.InvalidAmount
    ApiErrorCode.UNSUPPORTED_CURRENCY -> PaymentError.UnsupportedCurrency
    ApiErrorCode.MALFORMED_REQUEST,
    ApiErrorCode.UNSUPPORTED_MEDIA_TYPE,
    ApiErrorCode.NOT_FOUND,
    ApiErrorCode.METHOD_NOT_ALLOWED,
    ApiErrorCode.INVALID_IDEMPOTENCY_KEY,
    ApiErrorCode.IDEMPOTENCY_KEY_MISMATCH,
    ApiErrorCode.INTERNAL_ERROR,
    null,
    -> PaymentError.Server(statusCode)
}