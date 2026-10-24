package cashi.payment.oways.server.health

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

private const val HEALTH_PATH = "/health"
private const val STATUS_UP = "UP"

@Serializable
data class HealthResponse(val status: String)

fun Route.healthRoutes() {
    get(HEALTH_PATH) {
        call.respond(HealthResponse(STATUS_UP))
    }
}