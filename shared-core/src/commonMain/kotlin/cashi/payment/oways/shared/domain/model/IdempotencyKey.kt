package cashi.payment.oways.shared.domain.model

import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/**
 * Identifies one payment submission so it can be retried safely: the server returns the original
 * result for a repeated key. Opaque, 1–255 printable ASCII characters without surrounding
 * whitespace. Shared so the client and server agree on what a valid key is.
 */
@JvmInline
value class IdempotencyKey private constructor(val value: String) {

    companion object {
        private const val MAX_LENGTH = 255
        private val PRINTABLE_ASCII = ' '..'~'

        /** A new random key (UUID); create one per submission and reuse it for that submission's retries. */
        fun random(): IdempotencyKey = IdempotencyKey(Uuid.random().toString())

        fun parse(raw: String): IdempotencyKey? =
            raw.takeIf(::isValid)?.let(::IdempotencyKey)

        private fun isValid(raw: String): Boolean =
            raw.isNotEmpty() &&
                raw.length <= MAX_LENGTH &&
                raw == raw.trim() &&
                raw.all { it in PRINTABLE_ASCII }
    }
}