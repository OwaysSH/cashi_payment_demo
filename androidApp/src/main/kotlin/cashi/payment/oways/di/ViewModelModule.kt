package cashi.payment.oways.di

import cashi.payment.oways.ui.format.AmountFormatter
import cashi.payment.oways.ui.format.TimestampFormatter
import cashi.payment.oways.ui.history.HistoryViewModel
import cashi.payment.oways.ui.send.SendPaymentViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Android presentation layer: ViewModels and UI helpers, on top of the shared graph. */
val viewModelModule = module {
    factory { AmountFormatter() }
    factory { TimestampFormatter() }
    viewModel { SendPaymentViewModel(get(), get(), get()) }
    viewModel { HistoryViewModel(get(), get(), get()) }
}