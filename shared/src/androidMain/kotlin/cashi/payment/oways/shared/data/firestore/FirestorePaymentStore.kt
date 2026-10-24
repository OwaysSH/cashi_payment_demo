package cashi.payment.oways.shared.data.firestore

import android.util.Log
import cashi.payment.oways.shared.domain.error.PaymentStoreError
import cashi.payment.oways.shared.domain.error.PaymentStoreException
import cashi.payment.oways.shared.domain.gateway.PaymentStore
import cashi.payment.oways.shared.domain.model.Payment
import cashi.payment.oways.shared.domain.model.PaymentOrdering
import cashi.payment.oways.shared.domain.result.Outcome
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [PaymentStore] backed by Firestore `payments/{paymentId}`.
 *
 * - [save] waits for the server's acknowledgement up to [operationTimeout]; on timeout it returns
 *   [PaymentStoreError.Unavailable] while Firestore keeps the write queued and syncs it later
 *   (harmless: the document id is the payment id, so a later retry is idempotent).
 * - The rules deny updates, so writing an existing document fails with PERMISSION_DENIED; [save]
 *   then reads it back: same content → success (retry), different → conflict, absent → a real
 *   rules rejection.
 * - [observePayments] streams a snapshot listener. Pending local writes appear immediately with an
 *   estimated server timestamp. Malformed documents are skipped and logged by id only.
 *
 * [ioDispatcher] runs the awaits (and their timeouts) off the caller's dispatcher.
 */
class FirestorePaymentStore(
    private val firestore: FirebaseFirestore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val operationTimeout: Duration = DEFAULT_OPERATION_TIMEOUT,
) : PaymentStore {

    private val payments get() = firestore.collection(PaymentDocument.COLLECTION)

    override suspend fun save(payment: Payment): Outcome<Unit, PaymentStoreError> = withContext(ioDispatcher) {
        try {
            // Task<Void>.await() yields null, so the block returns Unit to tell success from timeout.
            val written = withTimeoutOrNull(operationTimeout) {
                payments.document(payment.id).set(payment.toDocumentData()).await()
                Unit
            }
            if (written == null) Outcome.Failure(PaymentStoreError.Unavailable) else Outcome.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                resolveExisting(payment)
            } else {
                Outcome.Failure(e.code.toStoreError())
            }
        }
    }

    override fun observePayments(): Flow<List<Payment>> = callbackFlow {
        val registration = payments
            .orderBy(PaymentDocument.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(PaymentStoreException(error.code.toStoreError()))
                    snapshot != null -> trySend(snapshot.toPayments())
                }
            }
        awaitClose { registration.remove() }
    }.conflate()

    private suspend fun resolveExisting(payment: Payment): Outcome<Unit, PaymentStoreError> =
        try {
            val snapshot = withTimeoutOrNull(operationTimeout) {
                payments.document(payment.id).get(Source.SERVER).await()
            }
            when {
                snapshot == null -> Outcome.Failure(PaymentStoreError.Unavailable)
                !snapshot.exists() -> Outcome.Failure(PaymentStoreError.PermissionDenied)
                snapshot.data.orEmpty().hasSameContentAs(payment) -> Outcome.Success(Unit)
                else -> Outcome.Failure(PaymentStoreError.Conflict)
            }
        } catch (e: FirebaseFirestoreException) {
            Outcome.Failure(e.code.toStoreError())
        }

    private fun QuerySnapshot.toPayments(): List<Payment> =
        documents.mapNotNull { it.toPaymentOrNull() }.sortedWith(PaymentOrdering.NewestFirst)

    private fun DocumentSnapshot.toPaymentOrNull(): Payment? {
        val data = getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).orEmpty()
        return paymentFromDocument(id, data) ?: run {
            Log.w(LOG_TAG, "Skipping malformed payment document $id")
            null
        }
    }

    private companion object {
        const val LOG_TAG = "FirestorePaymentStore"
        val DEFAULT_OPERATION_TIMEOUT = 10.seconds
    }
}