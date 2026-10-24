package cashi.payment.oways.shared.domain.gateway

import app.cash.turbine.test
import cashi.payment.oways.shared.domain.error.PaymentStoreError
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.result.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Behaviour every [PaymentStore] implementation must have. Subclasses provide a fresh, empty store.
 * Assertions avoid exact timestamps so the same contract can run against a real backend.
 */
abstract class PaymentStoreContractTest {

    /** A new, empty store; consecutive saves must get non-decreasing timestamps. */
    protected abstract fun createStore(): PaymentStore

    /** How long to wait for each flow event; real backends override this with a longer value. */
    protected open val flowTimeout: Duration = DEFAULT_FLOW_TIMEOUT

    @Test
    fun observePayments_emitsEmptyListFirst() = runTest {
        createStore().observePayments().test(timeout = flowTimeout) {
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun savedPayment_appearsInLiveHistory() = runTest {
        val store = createStore()
        store.observePayments().test(timeout = flowTimeout) {
            assertEquals(emptyList(), awaitItem())

            assertEquals(Outcome.Success(Unit), store.save(payment("p-1")))

            assertEquals(listOf("p-1"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun history_isOrderedNewestFirst() = runTest {
        val store = createStore()
        store.save(payment("p-1"))
        store.save(payment("p-2"))
        store.save(payment("p-3"))

        store.observePayments().test(timeout = flowTimeout) {
            val history = awaitItem()
            assertEquals(listOf("p-3", "p-2", "p-1"), history.map { it.id })
            assertTrue(history.zipWithNext().all { (newer, older) -> newer.createdAt >= older.createdAt })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun savingSamePaymentTwice_isIdempotent() = runTest {
        val store = createStore()

        assertEquals(Outcome.Success(Unit), store.save(payment("p-1")))
        assertEquals(Outcome.Success(Unit), store.save(payment("p-1")))

        store.observePayments().test(timeout = flowTimeout) {
            assertEquals(listOf("p-1"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sameIdWithDifferentData_isConflict() = runTest {
        val store = createStore()
        store.save(payment("p-1"))

        val result = store.save(payment("p-1", amountMinor = 9_900L))

        assertEquals(Outcome.Failure(PaymentStoreError.Conflict), result)
    }

    @Test
    fun storedTimestamp_comesFromStore_notFromPayment() = runTest {
        val store = createStore()
        store.save(payment("p-1"))

        store.observePayments().test(timeout = flowTimeout) {
            val stored = awaitItem().single()
            assertNotEquals(CLIENT_TIMESTAMP, stored.createdAt)
            assertEquals(payment("p-1").copy(createdAt = stored.createdAt), stored)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun payment(id: String, amountMinor: Long = 1_050L) = Payment(
        id = id,
        recipientEmail = "alice@example.com",
        amountMinor = amountMinor,
        currency = Currency.USD,
        createdAt = CLIENT_TIMESTAMP,
    )

    private companion object {
        /** A timestamp no store would assign, to prove the store uses its own. */
        val CLIENT_TIMESTAMP: Instant = Instant.fromEpochSeconds(0)
        val DEFAULT_FLOW_TIMEOUT: Duration = 3.seconds
    }
}