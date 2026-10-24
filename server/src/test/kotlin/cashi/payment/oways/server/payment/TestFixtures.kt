package cashi.payment.oways.server.payment

import cashi.payment.oways.server.idempotency.IdempotencyStore
import cashi.payment.oways.server.idempotency.InMemoryIdempotencyStore
import cashi.payment.oways.shared.domain.model.PaymentDraft
import cashi.payment.oways.shared.domain.model.ValidatedPayment
import cashi.payment.oways.shared.domain.result.Outcome
import cashi.payment.oways.shared.domain.validation.PaymentValidator
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

const val FIXED_PAYMENT_ID = "3f1c2b9e-7a4d-4e8b-9c1a-2d5e6f708192"
const val FIXED_NOW = "2026-10-23T09:15:30Z"

class FixedClock(private val instant: Instant = Instant.parse(FIXED_NOW)) : Clock {
    override fun now(): Instant = instant
}

class MutableClock(private var instant: Instant = Instant.parse(FIXED_NOW)) : Clock {
    override fun now(): Instant = instant

    fun advanceBy(duration: Duration) {
        instant += duration
    }
}

/** Thread-safe generator producing `payment-1`, `payment-2`, ... so tests can count creations. */
class SequentialIdGenerator : PaymentIdGenerator {
    private val counter = AtomicInteger()

    val generatedCount: Int get() = counter.get()

    override fun next(): String = "payment-${counter.incrementAndGet()}"
}

fun testPaymentService(
    idGenerator: PaymentIdGenerator = PaymentIdGenerator { FIXED_PAYMENT_ID },
    idempotencyStore: IdempotencyStore = InMemoryIdempotencyStore(FixedClock()),
): PaymentService = PaymentService(
    validator = PaymentValidator(),
    clock = FixedClock(),
    idGenerator = idGenerator,
    idempotencyStore = idempotencyStore,
)

/** [ValidatedPayment]'s constructor is internal to `shared`, so tests obtain one via the validator. */
fun validatedPayment(email: String = "alice@example.com", amount: Double = 10.5): ValidatedPayment {
    val result = PaymentValidator().validate(PaymentDraft(email, amount, "USD"))
    check(result is Outcome.Success) { "test draft must be valid but was $result" }
    return result.value
}