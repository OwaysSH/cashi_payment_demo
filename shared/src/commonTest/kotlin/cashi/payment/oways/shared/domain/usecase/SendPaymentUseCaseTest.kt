package cashi.payment.oways.shared.domain.usecase

import app.cash.turbine.test
import cashi.payment.oways.shared.domain.error.PaymentError
import cashi.payment.oways.shared.domain.error.PaymentStoreError
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.HistoryStatus
import cashi.payment.oways.shared.domain.model.IdempotencyKey
import cashi.payment.oways.shared.domain.model.PaymentDraft
import cashi.payment.oways.shared.domain.model.SentPayment
import cashi.payment.oways.shared.domain.result.Outcome
import cashi.payment.oways.shared.domain.validation.PaymentValidator
import cashi.payment.oways.shared.testing.FakePaymentApi
import cashi.payment.oways.shared.testing.InMemoryPaymentStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant

class SendPaymentUseCaseTest {

    private val clock = FixedClock()
    private val api = FakePaymentApi(clock)
    private val store = InMemoryPaymentStore(clock)
    private val sendPayment = SendPaymentUseCase(PaymentValidator(), api, store)
    private val key = IdempotencyKey.random()
    private val validDraft = PaymentDraft(" alice@example.com ", 10.5, "EUR")

    @Test
    fun invalidDraft_failsWithoutCallingApiOrStore() = runTest {
        val result = sendPayment(validDraft.copy(recipientEmail = "not-an-email"), key)

        assertEquals(Outcome.Failure(PaymentError.InvalidEmail), result)
        assertEquals(emptyList(), api.calls)
        assertHistoryIsEmpty()
    }

    @Test
    fun validDraft_sendsValidatedPaymentWithKey_andRecordsIt() = runTest {
        val result = sendPayment(validDraft, key)

        val call = api.calls.single()
        assertEquals("alice@example.com", call.payment.recipientEmail)
        assertEquals(1_050L, call.payment.amountMinor)
        assertEquals(Currency.EUR, call.payment.currency)
        assertEquals(key, call.idempotencyKey)

        val sent = assertIs<Outcome.Success<SentPayment>>(result).value
        assertEquals(HistoryStatus.Recorded, sent.history)
        assertEquals("payment-1", sent.payment.id)
        store.observePayments().test {
            assertEquals(listOf("payment-1"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun apiFailure_isReturned_andNothingIsRecorded() = runTest {
        listOf(PaymentError.InvalidEmail, PaymentError.Network, PaymentError.Server(500)).forEach { error ->
            api.failWith(error)

            assertEquals(Outcome.Failure(error), sendPayment(validDraft, key), "$error")
        }
        assertHistoryIsEmpty()
    }

    @Test
    fun storeFailureAfterApiSuccess_isStillSent_butNotRecorded() = runTest {
        store.failNextSaveWith(PaymentStoreError.Unavailable)

        val result = sendPayment(validDraft, key)

        val sent = assertIs<Outcome.Success<SentPayment>>(result).value
        assertEquals(HistoryStatus.NotRecorded(PaymentStoreError.Unavailable), sent.history)
        assertEquals("payment-1", sent.payment.id)
    }

    @Test
    fun retryWithSameKey_sendsSameKeyAgain() = runTest {
        api.failWith(PaymentError.Network)
        sendPayment(validDraft, key)
        api.failWith(null)

        sendPayment(validDraft, key)

        assertEquals(listOf(key, key), api.calls.map { it.idempotencyKey })
    }

    private suspend fun assertHistoryIsEmpty() {
        store.observePayments().test {
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FixedClock : Clock {
        override fun now(): Instant = Instant.parse("2026-10-23T09:00:00Z")
    }
}