package cashi.payment.oways.server.config

/** Network binding of the server. Defaults to localhost so it isn't exposed on the LAN. */
data class ServerConfig(
    val host: String,
    val port: Int,
) {
    companion object {
        private const val HOST_ENV = "SERVER_HOST"
        private const val PORT_ENV = "SERVER_PORT"
        private const val DEFAULT_HOST = "127.0.0.1"
        private const val DEFAULT_PORT = 8080

        fun fromEnvironment(env: (String) -> String? = System::getenv): ServerConfig = ServerConfig(
            host = env(HOST_ENV) ?: DEFAULT_HOST,
            port = env(PORT_ENV)?.toIntOrNull() ?: DEFAULT_PORT,
        )
    }
}