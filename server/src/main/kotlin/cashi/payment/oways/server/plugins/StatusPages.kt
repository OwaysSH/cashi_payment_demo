package cashi.payment.oways.server.plugins

import cashi.payment.oways.server.error.INTERNAL_ERROR_MESSAGE
import cashi.payment.oways.server.error.MALFORMED_REQUEST_MESSAGE
import cashi.payment.oways.server.error.METHOD_NOT_ALLOWED_MESSAGE
import cashi.payment.oways.server.error.NOT_FOUND_MESSAGE
import cashi.payment.oways.server.error.UNSUPPORTED_MEDIA_TYPE_MESSAGE
import cashi.payment.oways.server.error.errorResponse
import cashi.payment.oways.shared.api.ApiErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException

/** Gives every non-validation error the same JSON body as validation errors. */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respond(
                HttpStatusCode.BadRequest,
                errorResponse(ApiErrorCode.MALFORMED_REQUEST, MALFORMED_REQUEST_MESSAGE),
            )
        }
        // Thrown when no converter accepts the request's Content-Type (e.g. text/plain);
        // covers UnsupportedMediaTypeException and CannotTransformContentToTypeException.
        exception<ContentTransformationException> { call, _ ->
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                errorResponse(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_MEDIA_TYPE_MESSAGE),
            )
        }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            call.application.log.error("Unhandled error on ${call.request.local.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                errorResponse(ApiErrorCode.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE),
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, errorResponse(ApiErrorCode.NOT_FOUND, NOT_FOUND_MESSAGE))
        }
        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respond(status, errorResponse(ApiErrorCode.METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED_MESSAGE))
        }
    }
}