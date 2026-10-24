package cashi.payment.oways.shared.testing

import app.cash.turbine.test
import cashi.payment.oways.shared.domain.error.PaymentStoreError
import cashi.payment.oways.shared.domain.error.PaymentStoreException
import cashi.payment.oways.shared.domain.gateway.PaymentStore
import cashi.payment.oways.shared.domain.gateway.PaymentStoreContractTest
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.result.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Runs the [PaymentStore] contract against the fake, plus the fake's failure switches. */
class InMemoryPaymentStoreTest : PaymentStoreContractTest() {

    override fun createStore(): PaymentStore = InMemoryPaymentStore(TickingClock())

    @Test
    fun failNextSaveWith_failsOnce_withoutStoring() = runTest {
        val store = InMemoryPaymentStore(TickingClock())
        store.failNextSaveWith(PaymentStoreError.Unavailable)

        assertEquals(Outcome.Failure(PaymentStoreError.Unavailable), store.save(payment()))
        assertEquals(Outcome.Success(Unit), store.save(payment()))
    }

    @Test
    fun failObservationWith_endsFlowWithTypedException() = runTest {
        val store = InMemoryPaymentStore(TickingClock())

        store.observePayments().test {
            awaitItem()
            store.failObservationWith(PaymentStoreError.PermissionDenied)

            val error = assertIs<PaymentStoreException>(awaitError())
            assertEquals(PaymentStoreError.PermissionDenied, error.error)
        }
    }

    private fun payment() = Payment(
        id = "p-1",
        recipientEmail = "alice@example.com",
        amountMinor = 1_050L,
        currency = Currency.USD,
        createdAt = Instant.fromEpochSeconds(0),
    )

    /** Advances one second per reading, so consecutive saves get increasing timestamps. */
    private class TickingClock(private var now: Instant = Instant.parse("2026-10-23T09:00:00Z")) : Clock {
        override fun now(): Instant = now.also { now += 1.seconds }
    }
}