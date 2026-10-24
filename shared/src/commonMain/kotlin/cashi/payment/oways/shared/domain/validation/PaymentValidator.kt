package cashi.payment.oways.shared.domain.validation

import cashi.payment.oways.shared.domain.error.PaymentError
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.PaymentDraft
import cashi.payment.oways.shared.domain.model.ValidatedPayment
import cashi.payment.oways.shared.domain.money.toMinorUnits
import cashi.payment.oways.shared.domain.result.Outcome

/**
 * Validates payment input for both the app and the server. Rules run in a fixed order and the
 * first failure wins: email → currency → amount (the amount needs the currency's decimals).
 */
class PaymentValidator {

    fun validate(draft: PaymentDraft): Outcome<ValidatedPayment, PaymentError.Validation> {
        val email = draft.recipientEmail.trim()
        if (!isValidEmail(email)) return Outcome.Failure(PaymentError.InvalidEmail)

        val currency = Currency.fromCode(draft.currencyCode)
            ?: return Outcome.Failure(PaymentError.UnsupportedCurrency)

        val amountMinor = toPositiveMinorUnits(draft.amount, currency)
            ?: return Outcome.Failure(PaymentError.InvalidAmount)

        return Outcome.Success(ValidatedPayment(email, amountMinor, currency))
    }

    private fun isValidEmail(email: String): Boolean =
        email.length <= MAX_EMAIL_LENGTH &&
            email.substringBefore(EMAIL_SEPARATOR).length <= MAX_LOCAL_PART_LENGTH &&
            EMAIL_PATTERN.matches(email)

    private fun toPositiveMinorUnits(amount: Double, currency: Currency): Long? =
        currency.toMinorUnits(amount)?.takeIf { it > 0 }

    private companion object {
        const val MAX_EMAIL_LENGTH = 254
        const val MAX_LOCAL_PART_LENGTH = 64
        const val EMAIL_SEPARATOR = '@'

        /** Practical (not full RFC 5322) shape: local@domain.tld, TLD of 2+ letters. */
        val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")
    }
}