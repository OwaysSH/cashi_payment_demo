package cashi.payment.oways.server.idempotency

import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.ValidatedPayment
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Single-instance store: entries expire after [ttl] and the least recently used entry is evicted
 * beyond [maxEntries]. One lock guards the map; the critical section only builds a payment
 * in memory, so contention is negligible. Keys are lost on restart.
 */
class InMemoryIdempotencyStore(
    private val clock: Clock,
    private val ttl: Duration = DEFAULT_TTL,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : IdempotencyStore {

    private val lock = Any()
    private val entries = object : LinkedHashMap<IdempotencyKey, Entry>(INITIAL_CAPACITY, LOAD_FACTOR, ACCESS_ORDER) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<IdempotencyKey, Entry>): Boolean =
            size > maxEntries
    }

    override fun getOrCreate(
        key: IdempotencyKey,
        request: ValidatedPayment,
        create: () -> Payment,
    ): IdempotencyResult = synchronized(lock) {
        val now = clock.now()
        val existing = entries[key]?.takeUnless { it.isExpiredAt(now) }
        when {
            existing == null -> {
                val payment = create()
                entries[key] = Entry(request, payment, storedAt = now)
                IdempotencyResult.Created(payment)
            }
            existing.request == request -> IdempotencyResult.Replayed(existing.payment)
            else -> IdempotencyResult.Mismatch
        }
    }

    private fun Entry.isExpiredAt(now: Instant): Boolean = now - storedAt >= ttl

    private data class Entry(val request: ValidatedPayment, val payment: Payment, val storedAt: Instant)

    private companion object {
        val DEFAULT_TTL = 24.hours
        const val DEFAULT_MAX_ENTRIES = 10_000
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
        const val ACCESS_ORDER = true
    }
}