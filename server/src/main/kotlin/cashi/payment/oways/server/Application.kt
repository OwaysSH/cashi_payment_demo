package cashi.payment.oways.server

import cashi.payment.oways.server.config.ServerConfig
import cashi.payment.oways.server.health.healthRoutes
import cashi.payment.oways.server.idempotency.InMemoryIdempotencyStore
import cashi.payment.oways.server.payment.PaymentIdGenerator
import cashi.payment.oways.server.payment.PaymentService
import cashi.payment.oways.server.payment.paymentRoutes
import cashi.payment.oways.server.plugins.configureMonitoring
import cashi.payment.oways.server.plugins.configureSerialization
import cashi.payment.oways.server.plugins.configureStatusPages
import cashi.payment.oways.shared.domain.validation.PaymentValidator
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlin.time.Clock

fun main() {
    val config = ServerConfig.fromEnvironment()
    val paymentService = PaymentService(
        validator = PaymentValidator(),
        clock = Clock.System,
        idGenerator = PaymentIdGenerator.Random,
        idempotencyStore = InMemoryIdempotencyStore(Clock.System),
    )
    embeddedServer(Netty, host = config.host, port = config.port) {
        module(paymentService)
    }.start(wait = true)
}

/** Composition of plugins and routes; dependencies are passed in so tests can supply fakes. */
fun Application.module(paymentService: PaymentService) {
    configureSerialization()
    configureMonitoring()
    configureStatusPages()
    routing {
        healthRoutes()
        paymentRoutes(paymentService)
    }
}