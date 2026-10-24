package cashi.payment.oways.server.payment

import cashi.payment.oways.server.error.IDEMPOTENCY_KEY_MISMATCH_MESSAGE
import cashi.payment.oways.server.error.INVALID_IDEMPOTENCY_KEY_MESSAGE
import cashi.payment.oways.server.error.errorResponse
import cashi.payment.oways.server.error.toErrorResponse
import cashi.payment.oways.shared.api.ApiErrorCode
import cashi.payment.oways.shared.api.ApiHeaders
import cashi.payment.oways.shared.api.PaymentRequest
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.result.Outcome
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

private const val PAYMENTS_PATH = "/payments"
private const val REPLAYED_HEADER_VALUE = "true"

fun Route.paymentRoutes(paymentService: PaymentService) {
    post(PAYMENTS_PATH) {
        val rawKey = call.request.headers[ApiHeaders.IDEMPOTENCY_KEY]
        val key = rawKey?.let { IdempotencyKey.parse(it) }
        if (rawKey != null && key == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                errorResponse(ApiErrorCode.INVALID_IDEMPOTENCY_KEY, INVALID_IDEMPOTENCY_KEY_MESSAGE),
            )
            return@post
        }

        val request = call.receive<PaymentRequest>()
        when (val outcome = paymentService.process(request.toDraft(), key)) {
            is Outcome.Success -> call.respondProcessed(outcome.value)
            is Outcome.Failure -> call.respondFailure(outcome.error)
        }
    }
}

private suspend fun ApplicationCall.respondProcessed(processed: ProcessedPayment) {
    if (processed.replayed) response.header(ApiHeaders.IDEMPOTENT_REPLAYED, REPLAYED_HEADER_VALUE)
    respond(HttpStatusCode.Created, processed.payment.toResponse())
}

private suspend fun ApplicationCall.respondFailure(failure: ProcessPaymentFailure) = when (failure) {
    is ProcessPaymentFailure.Invalid -> respond(HttpStatusCode.BadRequest, failure.error.toErrorResponse())
    ProcessPaymentFailure.IdempotencyKeyMismatch -> respond(
        HttpStatusCode.UnprocessableEntity,
        errorResponse(ApiErrorCode.IDEMPOTENCY_KEY_MISMATCH, IDEMPOTENCY_KEY_MISMATCH_MESSAGE),
    )
}