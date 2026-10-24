package cashi.payment.oways.shared.di

import org.koin.core.module.Module

/**
 * Platform-specific dependencies for [sharedModule]: the HTTP engine and the `PaymentStore`
 * implementation. Android provides OkHttp and Firestore; another platform (e.g. iOS) would provide
 * its own engine and store here.
 */
expect fun platformModule(): Module