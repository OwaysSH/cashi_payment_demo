package cashi.payment.oways.shared.data.firestore

import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.shared.domain.model.Payment
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import kotlin.time.Instant

/** Firestore layout of `payments/{paymentId}`; must match `firestore.rules` exactly. */
internal object PaymentDocument {
    const val COLLECTION = "payments"
    const val FIELD_RECIPIENT_EMAIL = "recipientEmail"
    const val FIELD_AMOUNT_MINOR = "amountMinor"
    const val FIELD_CURRENCY = "currency"
    const val FIELD_TIMESTAMP = "timestamp"
}

/** Document fields for a new payment. The timestamp is assigned by Firestore (required by the rules). */
internal fun Payment.toDocumentData(): Map<String, Any> = mapOf(
    PaymentDocument.FIELD_RECIPIENT_EMAIL to recipientEmail,
    PaymentDocument.FIELD_AMOUNT_MINOR to amountMinor,
    PaymentDocument.FIELD_CURRENCY to currency.code,
    PaymentDocument.FIELD_TIMESTAMP to FieldValue.serverTimestamp(),
)

/** `null` if the document doesn't have the expected shape (missing field, unknown currency). */
internal fun paymentFromDocument(id: String, data: Map<String, Any?>): Payment? {
    val recipientEmail = data[PaymentDocument.FIELD_RECIPIENT_EMAIL] as? String ?: return null
    val amountMinor = data[PaymentDocument.FIELD_AMOUNT_MINOR] as? Long ?: return null
    val currency = (data[PaymentDocument.FIELD_CURRENCY] as? String)?.let(Currency::fromCode) ?: return null
    val timestamp = data[PaymentDocument.FIELD_TIMESTAMP] as? Timestamp ?: return null
    return Payment(
        id = id,
        recipientEmail = recipientEmail,
        amountMinor = amountMinor,
        currency = currency,
        createdAt = timestamp.toKotlinInstant(),
    )
}

/** Whether a stored document holds the same payment content (the timestamp is not compared). */
internal fun Map<String, Any?>.hasSameContentAs(payment: Payment): Boolean =
    this[PaymentDocument.FIELD_RECIPIENT_EMAIL] == payment.recipientEmail &&
        this[PaymentDocument.FIELD_AMOUNT_MINOR] == payment.amountMinor &&
        this[PaymentDocument.FIELD_CURRENCY] == payment.currency.code

/** Not `toInstant()`: Timestamp has a member of that name returning `java.time.Instant` (API 26+). */
private fun Timestamp.toKotlinInstant(): Instant = Instant.fromEpochSeconds(seconds, nanoseconds)