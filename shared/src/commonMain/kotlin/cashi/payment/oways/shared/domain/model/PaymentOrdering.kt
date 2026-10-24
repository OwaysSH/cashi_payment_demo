package cashi.payment.oways.shared.domain.model

/** Canonical history order shared by every store: newest first, ties broken by id. */
object PaymentOrdering {
    val NewestFirst: Comparator<Payment> = compareByDescending<Payment> { it.createdAt }.thenBy { it.id }
}