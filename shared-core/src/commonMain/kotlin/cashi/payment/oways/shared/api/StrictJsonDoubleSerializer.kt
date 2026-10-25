package cashi.payment.oways.shared.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Decodes a Double only from a JSON number. kotlinx.serialization otherwise also accepts quoted
 * numbers such as `"10.5"`, which the API contract (`amount: Double`) does not allow.
 */
object StrictJsonDoubleSerializer : KSerializer<Double> {

    private const val NOT_A_NUMBER_MESSAGE = "Expected a JSON number"

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("cashi.payment.oways.StrictJsonDouble", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)

    override fun deserialize(decoder: Decoder): Double {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val primitive = jsonDecoder.decodeJsonElement() as? JsonPrimitive
        if (primitive == null || primitive.isString) throw SerializationException(NOT_A_NUMBER_MESSAGE)
        return primitive.doubleOrNull ?: throw SerializationException(NOT_A_NUMBER_MESSAGE)
    }
}