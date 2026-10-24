package cashi.payment.oways.shared.data.remote

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android HTTP engine for [createPaymentHttpClient]. Timeouts come from Ktor's `HttpTimeout`.
 * OkHttp may transparently retry a request on a stale pooled connection; that is safe here because
 * every payment request carries an idempotency key.
 */
fun createOkHttpEngine(): HttpClientEngine = OkHttp.create()