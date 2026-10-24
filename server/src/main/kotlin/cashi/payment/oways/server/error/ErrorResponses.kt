package cashi.payment.oways.server.error

import cashi.payment.oways.shared.api.ApiErrorCode
import cashi.payment.oways.shared.api.ErrorResponse
import cashi.payment.oways.shared.domain.error.PaymentError

private const val INVALID_EMAIL_MESSAGE = "Invalid recipient email"
private const val INVALID_AMOUNT_MESSAGE =
    "Amount must be greater than 0, have at most 2 decimals and not exceed 1,000,000.00"
private const val UNSUPPORTED_CURRENCY_MESSAGE = "Supported currencies: USD, EUR"

const val MALFORMED_REQUEST_MESSAGE = "Request body is missing, malformed or has invalid field types"
const val UNSUPPORTED_MEDIA_TYPE_MESSAGE = "Content-Type must be application/json"
const val NOT_FOUND_MESSAGE = "Resource not found"
const val METHOD_NOT_ALLOWED_MESSAGE = "HTTP method not allowed for this resource"
const val INTERNAL_ERROR_MESSAGE = "Unexpected server error"
const val INVALID_IDEMPOTENCY_KEY_MESSAGE =
    "Idempotency-Key must be 1-255 printable ASCII characters without surrounding whitespace"
const val IDEMPOTENCY_KEY_MISMATCH_MESSAGE = "Idempotency-Key was already used with a different payment"

fun errorResponse(code: ApiErrorCode, message: String): ErrorResponse =
    ErrorResponse(code = code.wireValue, message = message)

/** Exhaustive: a new validation error won't compile until it has an API code. */
fun PaymentError.Validation.toErrorResponse(): ErrorResponse = when (this) {
    PaymentError.InvalidEmail -> errorResponse(ApiErrorCode.INVALID_EMAIL, INVALID_EMAIL_MESSAGE)
    PaymentError.InvalidAmount -> errorResponse(ApiErrorCode.INVALID_AMOUNT, INVALID_AMOUNT_MESSAGE)
    PaymentError.UnsupportedCurrency ->
        errorResponse(ApiErrorCode.UNSUPPORTED_CURRENCY, UNSUPPORTED_CURRENCY_MESSAGE)
}