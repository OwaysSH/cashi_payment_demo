package cashi.payment.oways.shared.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Empty on purpose: the JVM target exists for the Ktor server, which uses the shared domain and
 * validator directly without Koin. A JVM client app would provide an HTTP engine and a store here.
 */
actual fun platformModule(): Module = module { }