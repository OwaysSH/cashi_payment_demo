package cashi.payment.oways.shared.api

/** HTTP header names shared by the payments API client and server. */
object ApiHeaders {
    /** Client-chosen key that makes `POST /payments` safe to retry. Optional in this demo. */
    const val IDEMPOTENCY_KEY = "Idempotency-Key"

    /** Set to `true` by the server when a response is a replay of an earlier request. */
    const val IDEMPOTENT_REPLAYED = "Idempotent-Replayed"
}