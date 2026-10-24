package cashi.payment.oways.shared.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ErrorContractTest {

    @Test
    fun everyErrorCode_roundTripsThroughWireValue() {
        ApiErrorCode.entries.forEach { code ->
            assertEquals(code, ApiErrorCode.fromWireValue(code.wireValue))
        }
    }

    @Test
    fun fromWireValue_returnsNull_forUnknownCode() {
        assertNull(ApiErrorCode.fromWireValue("RATE_LIMITED"))
    }

    @Test
    fun errorResponse_encodesToCodeAndMessage() {
        val json = Json.parseToJsonElement(
            ApiJson.encodeToString(ErrorResponse(code = "INVALID_EMAIL", message = "Invalid recipient email"))
        ).jsonObject

        val expected = JsonObject(
            mapOf(
                "code" to JsonPrimitive("INVALID_EMAIL"),
                "message" to JsonPrimitive("Invalid recipient email"),
            )
        )
        assertEquals(expected, json)
    }

    @Test
    fun errorResponse_decodesUnknownCode() {
        val body = """{"code":"RATE_LIMITED","message":"Slow down","retryAfter":5}"""

        val error = ApiJson.decodeFromString<ErrorResponse>(body)

        assertEquals(ErrorResponse(code = "RATE_LIMITED", message = "Slow down"), error)
    }
}