package cashi.payment.oways.shared.data.remote

import cashi.payment.oways.shared.api.PaymentRequest
import cashi.payment.oways.shared.api.PaymentResponse
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.ValidatedPayment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class PaymentApiMappersTest {

    private val response = PaymentResponse(
        id = "p-1",
        recipientEmail = "alice@example.com",
        amount = 10.5,
        currency = "EUR",
        createdAt = "2026-10-23T09:15:30Z",
    )

    @Test
    fun toRequest_convertsMinorUnitsToMajorAmount() {
        val request = ValidatedPayment("alice@example.com", 1050L, Currency.EUR).toRequest()

        assertEquals(PaymentRequest("alice@example.com", 10.5, "EUR"), request)
    }

    @Test
    fun toDomain_mapsValidResponse() {
        val expected = Payment(
            id = "p-1",
            recipientEmail = "alice@example.com",
            amountMinor = 1050L,
            currency = Currency.EUR,
            createdAt = Instant.parse("2026-10-23T09:15:30Z"),
        )

        assertEquals(expected, response.toDomain())
    }

    @Test
    fun toDomain_returnsNull_forContractViolations() {
        listOf(
            response.copy(currency = "GBP"),
            response.copy(amount = 10.123),
            response.copy(amount = Double.NaN),
            response.copy(createdAt = "not-a-timestamp"),
        ).forEach { invalid ->
            assertNull(invalid.toDomain(), "expected null for $invalid")
        }
    }
}