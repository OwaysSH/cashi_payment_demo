package cashi.payment.oways.server.payment

import cashi.payment.oways.shared.domain.error.PaymentError
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.PaymentDraft
import cashi.payment.oways.shared.domain.result.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PaymentServiceTest {

    private val idGenerator = SequentialIdGenerator()
    private val service = testPaymentService(idGenerator = idGenerator)
    private val key = checkNotNull(IdempotencyKey.parse("key-1"))

    @Test
    fun process_returnsProcessedPayment_forValidDraft() {
        val result = service.process(PaymentDraft(" alice@example.com ", 10.5, "EUR"), key = null)

        val expected = Payment(
            id = "payment-1",
            recipientEmail = "alice@example.com",
            amountMinor = 1050L,
            currency = Currency.EUR,
            createdAt = Instant.parse(FIXED_NOW),
        )
        assertEquals(Outcome.Success(ProcessedPayment(expected, replayed = false)), result)
    }

    @Test
    fun process_passesValidationErrorThrough() {
        val result = service.process(PaymentDraft("alice@example.com", 0.0, "USD"), key)

        assertEquals(Outcome.Failure(ProcessPaymentFailure.Invalid(PaymentError.InvalidAmount)), result)
        assertEquals(0, idGenerator.generatedCount)
    }

    @Test
    fun process_withoutKey_createsNewPaymentEachTime() {
        val draft = PaymentDraft("alice@example.com", 10.5, "USD")

        service.process(draft, key = null)
        service.process(draft, key = null)

        assertEquals(2, idGenerator.generatedCount)
    }

    @Test
    fun process_withSameKeyAndPayment_replaysOriginal() {
        val first = service.process(PaymentDraft("alice@example.com", 10.5, "USD"), key)
        val second = service.process(PaymentDraft(" alice@example.com", 10.50, "USD"), key)

        check(first is Outcome.Success && second is Outcome.Success)
        assertEquals(first.value.payment, second.value.payment)
        assertEquals(false, first.value.replayed)
        assertEquals(true, second.value.replayed)
        assertEquals(1, idGenerator.generatedCount)
    }

    @Test
    fun process_withSameKeyAndDifferentPayment_fails() {
        service.process(PaymentDraft("alice@example.com", 10.5, "USD"), key)

        val result = service.process(PaymentDraft("alice@example.com", 99.0, "USD"), key)

        assertEquals(Outcome.Failure(ProcessPaymentFailure.IdempotencyKeyMismatch), result)
        assertEquals(1, idGenerator.generatedCount)
    }
}