package cashi.payment.oways.shared.testing

import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.PaymentDraft
import cashi.payment.oways.shared.domain.model.ValidatedPayment
import cashi.payment.oways.shared.domain.result.Outcome
import cashi.payment.oways.shared.domain.validation.PaymentValidator

/** [ValidatedPayment]'s constructor is internal to `shared-core`, so tests obtain one via the validator. */
fun validatedPayment(
    email: String = "alice@example.com",
    amount: Double = 10.5,
    currency: Currency = Currency.USD,
): ValidatedPayment {
    val result = PaymentValidator().validate(PaymentDraft(email, amount, currency.code))
    check(result is Outcome.Success) { "test draft must be valid but was $result" }
    return result.value
}