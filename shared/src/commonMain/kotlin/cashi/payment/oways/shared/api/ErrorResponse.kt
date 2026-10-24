package cashi.payment.oways.shared.api

import kotlinx.serialization.Serializable

/**
 * Body of every error response. [code] is an [ApiErrorCode] wire value, kept as a String so
 * clients still decode codes they don't know yet. [message] is for developers, not end users.
 */
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
)