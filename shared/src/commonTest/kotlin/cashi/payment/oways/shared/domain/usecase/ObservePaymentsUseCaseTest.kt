package cashi.payment.oways.shared.domain.usecase

import app.cash.turbine.test
import cashi.payment.oways.shared.domain.error.PaymentStoreError
import cashi.payment.oways.shared.domain.error.PaymentStoreException
import cashi.payment.oways.shared.domain.gateway.PaymentStore
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.result.Outcome
import cashi.payment.oways.shared.testing.InMemoryPaymentStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ObservePaymentsUseCaseTest {

    @Test
    fun emitsEmptyHistory_thenLiveUpdatesNewestFirst() = runTest {
        val store = InMemoryPaymentStore(TickingClock())
        val observePayments = ObservePaymentsUseCase(store)

        observePayments().test {
            assertEquals(emptyList(), awaitItem())

            store.save(payment("p-1"))
            assertEquals(listOf("p-1"), awaitItem().map { it.id })

            store.save(payment("p-2"))
            assertEquals(listOf("p-2", "p-1"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun equalConsecutiveLists_areEmittedOnce() = runTest {
        val first = listOf(payment("p-1"))
        val second = listOf(payment("p-2"), payment("p-1"))
        val observePayments = ObservePaymentsUseCase(StubStore(flowOf(first, first, second)))

        observePayments().test {
            assertEquals(first, awaitItem())
            assertEquals(second, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun storeFailure_endsFlowWithTypedException() = runTest {
        val store = InMemoryPaymentStore(TickingClock())
        val observePayments = ObservePaymentsUseCase(store)

        observePayments().test {
            awaitItem()
            store.failObservationWith(PaymentStoreError.Unavailable)

            val error = assertIs<PaymentStoreException>(awaitError())
            assertEquals(PaymentStoreError.Unavailable, error.error)
        }
    }

    private fun payment(id: String) = Payment(
        id = id,
        recipientEmail = "alice@example.com",
        amountMinor = 1_050L,
        currency = Currency.USD,
        createdAt = Instant.fromEpochSeconds(0),
    )

    /** Observation-only stub; saving isn't part of these tests. */
    private class StubStore(private val history: Flow<List<Payment>>) : PaymentStore {
        override suspend fun save(payment: Payment): Outcome<Unit, PaymentStoreError> = error("not used")
        override fun observePayments(): Flow<List<Payment>> = history
    }

    private class TickingClock(private var now: Instant = Instant.parse("2026-10-23T09:00:00Z")) : Clock {
        override fun now(): Instant = now.also { now += 1.seconds }
    }
}