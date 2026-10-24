package cashi.payment.oways.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level

/** Logs method, path, status and duration only; request bodies contain personal data (emails). */
fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val status = call.response.status()?.value
            val durationMs = call.processingTimeMillis()
            "${call.request.httpMethod.value} ${call.request.path()} -> $status (${durationMs}ms)"
        }
    }
}