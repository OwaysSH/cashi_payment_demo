package cashi.payment.oways.server.idempotency

import cashi.payment.oways.server.payment.MutableClock
import cashi.payment.oways.server.payment.SequentialIdGenerator
import cashi.payment.oways.server.payment.validatedPayment
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.Payment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class InMemoryIdempotencyStoreTest {

    private val clock = MutableClock()
    private val idGenerator = SequentialIdGenerator()
    private val store = InMemoryIdempotencyStore(clock, ttl = TTL, maxEntries = MAX_ENTRIES)
    private val request = validatedPayment()

    @Test
    fun firstUse_creates_andRepeat_replaysSamePayment() {
        val created = store.getOrCreate(key("a"), request, ::newPayment)
        val replayed = store.getOrCreate(key("a"), request, ::newPayment)

        assertIs<IdempotencyResult.Created>(created)
        assertEquals(IdempotencyResult.Replayed(created.payment), replayed)
        assertEquals(1, idGenerator.generatedCount)
    }

    @Test
    fun sameKeyWithDifferentRequest_isMismatch() {
        store.getOrCreate(key("a"), request, ::newPayment)

        val result = store.getOrCreate(key("a"), validatedPayment(amount = 99.0), ::newPayment)

        assertEquals(IdempotencyResult.Mismatch, result)
        assertEquals(1, idGenerator.generatedCount)
    }

    @Test
    fun expiredKey_isTreatedAsNew() {
        store.getOrCreate(key("a"), request, ::newPayment)
        clock.advanceBy(TTL + 1.seconds)

        val result = store.getOrCreate(key("a"), validatedPayment(amount = 99.0), ::newPayment)

        assertIs<IdempotencyResult.Created>(result)
        assertEquals(2, idGenerator.generatedCount)
    }

    @Test
    fun leastRecentlyUsedKey_isEvicted_beyondMaxEntries() {
        store.getOrCreate(key("a"), request, ::newPayment)
        store.getOrCreate(key("b"), request, ::newPayment)
        store.getOrCreate(key("a"), request, ::newPayment) // touch "a": "b" is now least recent
        store.getOrCreate(key("c"), request, ::newPayment)

        assertIs<IdempotencyResult.Replayed>(store.getOrCreate(key("a"), request, ::newPayment))
        assertIs<IdempotencyResult.Created>(store.getOrCreate(key("b"), request, ::newPayment))
    }

    @Test
    fun concurrentRequestsWithSameKey_createExactlyOnePayment() {
        val executor = Executors.newFixedThreadPool(CONCURRENT_CALLERS)
        val start = CountDownLatch(1)
        val results = (1..CONCURRENT_CALLERS).map {
            executor.submit<IdempotencyResult> {
                start.await()
                store.getOrCreate(key("shared"), request, ::newPayment)
            }
        }

        start.countDown()
        val payments = results.map { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).payment() }
        executor.shutdown()

        assertEquals(1, idGenerator.generatedCount)
        assertEquals(1, payments.toSet().size)
    }

    private fun newPayment(): Payment = Payment(
        id = idGenerator.next(),
        recipientEmail = request.recipientEmail,
        amountMinor = request.amountMinor,
        currency = request.currency,
        createdAt = clock.now(),
    )

    private fun IdempotencyResult.payment(): Payment = when (this) {
        is IdempotencyResult.Created -> payment
        is IdempotencyResult.Replayed -> payment
        IdempotencyResult.Mismatch -> error("unexpected mismatch")
    }

    private fun key(raw: String): IdempotencyKey = checkNotNull(IdempotencyKey.parse(raw))

    private companion object {
        val TTL = 24.hours
        const val MAX_ENTRIES = 2
        const val CONCURRENT_CALLERS = 50
        const val TIMEOUT_SECONDS = 5L
    }
}