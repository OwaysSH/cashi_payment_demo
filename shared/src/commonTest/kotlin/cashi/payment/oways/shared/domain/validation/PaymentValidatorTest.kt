package cashi.payment.oways.shared.domain.validation

import cashi.payment.oways.shared.domain.error.PaymentError
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.PaymentDraft
import cashi.payment.oways.shared.domain.model.ValidatedPayment
import cashi.payment.oways.shared.domain.result.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentValidatorTest {

    private val validator = PaymentValidator()

    @Test
    fun validDraft_producesValidatedPayment() {
        val result = validator.validate(draft())

        assertEquals(Outcome.Success(ValidatedPayment(VALID_EMAIL, 1050L, Currency.USD)), result)
    }

    @Test
    fun email_isTrimmed() {
        val result = validator.validate(draft(email = "  $VALID_EMAIL \n"))

        assertEquals(Outcome.Success(ValidatedPayment(VALID_EMAIL, 1050L, Currency.USD)), result)
    }

    @Test
    fun validEmails_areAccepted() {
        listOf("a.b+tag@mail.example.co.uk", "x_y-z@sub-domain.io", "${"a".repeat(64)}@example.com")
            .forEach { email -> assertSuccess(draft(email = email), "email '$email'") }
    }

    @Test
    fun invalidEmails_areRejected() {
        val tooLong = "a".repeat(64) + "@" + "b".repeat(186) + ".com"
        listOf(
            "",
            "alice.example.com",
            "alice@@example.com",
            "alice@bob@example.com",
            "alice@",
            "@example.com",
            "alice@.com",
            "alice@example",
            "alice@example.c",
            "ali ce@example.com",
            "${"a".repeat(65)}@example.com",
            tooLong,
        ).forEach { email ->
            assertFailure(PaymentError.InvalidEmail, draft(email = email), "email '$email'")
        }
    }

    @Test
    fun invalidAmounts_areRejected() {
        listOf(0.0, -10.5, Double.NaN, Double.POSITIVE_INFINITY, 10.123, 1_000_000.01).forEach { amount ->
            assertFailure(PaymentError.InvalidAmount, draft(amount = amount), "amount $amount")
        }
    }

    @Test
    fun unsupportedCurrencies_areRejected() {
        listOf("usd", "GBP", "").forEach { code ->
            assertFailure(PaymentError.UnsupportedCurrency, draft(currencyCode = code), "currency '$code'")
        }
    }

    @Test
    fun firstFailingRule_wins_inOrderEmailCurrencyAmount() {
        assertFailure(PaymentError.InvalidEmail, draft(email = "bad", amount = 0.0, currencyCode = "GBP"))
        assertFailure(PaymentError.UnsupportedCurrency, draft(amount = 10.123, currencyCode = "GBP"))
    }

    private fun draft(
        email: String = VALID_EMAIL,
        amount: Double = 10.5,
        currencyCode: String = "USD",
    ) = PaymentDraft(recipientEmail = email, amount = amount, currencyCode = currencyCode)

    private fun assertSuccess(draft: PaymentDraft, message: String) {
        val result = validator.validate(draft)
        check(result is Outcome.Success) { "$message: expected success but was $result" }
    }

    private fun assertFailure(expected: PaymentError.Validation, draft: PaymentDraft, message: String? = null) {
        assertEquals(Outcome.Failure(expected), validator.validate(draft), message)
    }

    private companion object {
        const val VALID_EMAIL = "alice@example.com"
    }
}