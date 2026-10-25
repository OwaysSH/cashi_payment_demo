package cashi.payment.oways.shared.api

/** Machine-readable error codes of the payments API. [wireValue] is what appears in JSON. */
enum class ApiErrorCode(val wireValue: String) {
    INVALID_EMAIL("INVALID_EMAIL"),
    INVALID_AMOUNT("INVALID_AMOUNT"),
    UNSUPPORTED_CURRENCY("UNSUPPORTED_CURRENCY"),
    MALFORMED_REQUEST("MALFORMED_REQUEST"),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE"),
    NOT_FOUND("NOT_FOUND"),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED"),
    INVALID_IDEMPOTENCY_KEY("INVALID_IDEMPOTENCY_KEY"),
    IDEMPOTENCY_KEY_MISMATCH("IDEMPOTENCY_KEY_MISMATCH"),
    INTERNAL_ERROR("INTERNAL_ERROR");

    companion object {
        /** `null` for codes this client doesn't know (e.g. added by a newer server). */
        fun fromWireValue(value: String): ApiErrorCode? = entries.firstOrNull { it.wireValue == value }
    }
}