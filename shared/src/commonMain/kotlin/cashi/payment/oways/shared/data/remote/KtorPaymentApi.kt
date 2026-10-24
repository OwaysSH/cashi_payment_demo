package cashi.payment.oways.shared.data.remote

import cashi.payment.oways.shared.api.ApiErrorCode
import cashi.payment.oways.shared.api.ApiHeaders
import cashi.payment.oways.shared.api.ErrorResponse
import cashi.payment.oways.shared.api.PaymentResponse
import cashi.payment.oways.shared.domain.error.PaymentError
import cashi.payment.oways.shared.domain.gateway.PaymentApi
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.ValidatedPayment
import cashi.payment.oways.shared.domain.result.Outcome
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.ContentConvertException
import kotlinx.io.IOException

/**
 * [PaymentApi] over HTTP. Expects a client from [createPaymentHttpClient] (base URL, JSON, timeouts).
 * Transport failures and timeouts (all [IOException]s) become [PaymentError.Network]; cancellation
 * and unexpected exceptions propagate.
 */
class KtorPaymentApi(private val httpClient: HttpClient) : PaymentApi {

    override suspend fun createPayment(
        payment: ValidatedPayment,
        idempotencyKey: IdempotencyKey,
    ): Outcome<Payment, PaymentError> =
        try {
            httpClient.post(PAYMENTS_PATH) {
                contentType(ContentType.Application.Json)
                header(ApiHeaders.IDEMPOTENCY_KEY, idempotencyKey.value)
                setBody(payment.toRequest())
            }.toOutcome()
        } catch (_: IOException) {
            Outcome.Failure(PaymentError.Network)
        }

    private suspend fun HttpResponse.toOutcome(): Outcome<Payment, PaymentError> = when (status) {
        HttpStatusCode.Created -> decodeOrNull<PaymentResponse>()?.toDomain()
            ?.let { Outcome.Success(it) }
            ?: Outcome.Failure(PaymentError.Server(status.value))
        else -> {
            val code = decodeOrNull<ErrorResponse>()?.code?.let(ApiErrorCode::fromWireValue)
            Outcome.Failure(code.toPaymentError(status.value))
        }
    }

    /** `null` if the body is not the expected JSON (e.g. a proxy's HTML error page or an empty body). */
    private suspend inline fun <reified T> HttpResponse.decodeOrNull(): T? =
        try {
            body<T>()
        } catch (_: ContentConvertException) {
            null
        } catch (_: NoTransformationFoundException) {
            null
        }

    private companion object {
        const val PAYMENTS_PATH = "payments"
    }
}