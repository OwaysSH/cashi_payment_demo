package cashi.payment.oways.shared.data.firestore

import cashi.payment.oways.shared.domain.error.PaymentStoreError
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Rules rejections become [PaymentStoreError.PermissionDenied]; every other Firestore failure
 * (UNAVAILABLE, DEADLINE_EXCEEDED, ABORTED, INTERNAL, ...) means the store couldn't complete the
 * operation now, i.e. [PaymentStoreError.Unavailable].
 */
internal fun FirebaseFirestoreException.Code.toStoreError(): PaymentStoreError = when (this) {
    FirebaseFirestoreException.Code.PERMISSION_DENIED -> PaymentStoreError.PermissionDenied
    else -> PaymentStoreError.Unavailable
}