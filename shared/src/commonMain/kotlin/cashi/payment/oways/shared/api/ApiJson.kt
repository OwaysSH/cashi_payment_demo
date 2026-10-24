package cashi.payment.oways.shared.api

import kotlinx.serialization.json.Json

/**
 * JSON configuration shared by the API client and server. Unknown keys are ignored so either side
 * can add fields without breaking the other; missing required fields still fail to decode.
 */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
}