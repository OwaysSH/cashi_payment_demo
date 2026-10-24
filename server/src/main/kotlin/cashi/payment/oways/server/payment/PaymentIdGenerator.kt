package cashi.payment.oways.server.payment

import kotlin.uuid.Uuid

/** Produces unique payment IDs; injectable so tests can use fixed IDs. */
fun interface PaymentIdGenerator {
    fun next(): String

    companion object {
        val Random = PaymentIdGenerator { Uuid.random().toString() }
    }
}