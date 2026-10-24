package cashi.payment.oways.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class IdempotencyKeyTest {

    @Test
    fun parse_acceptsValidKeys() {
        listOf(
            "3f1c2b9e-7a4d-4e8b-9c1a-2d5e6f708192",
            "order-42:retry",
            "a b",
            "k".repeat(255),
        ).forEach { raw ->
            assertEquals(raw, IdempotencyKey.parse(raw)?.value, "key '$raw'")
        }
    }

    @Test
    fun parse_rejectsInvalidKeys() {
        listOf("", "   ", " key", "key ", "k".repeat(256), "key\u0001", "clé", "key\n").forEach { raw ->
            assertNull(IdempotencyKey.parse(raw), "key '$raw'")
        }
    }

    @Test
    fun random_producesDistinctValidKeys() {
        val first = IdempotencyKey.random()
        val second = IdempotencyKey.random()

        assertNotEquals(first, second)
        assertEquals(first, IdempotencyKey.parse(first.value))
    }
}