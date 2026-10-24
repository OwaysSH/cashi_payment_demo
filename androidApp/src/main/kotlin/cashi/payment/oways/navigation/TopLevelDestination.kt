package cashi.payment.oways.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import cashi.payment.oways.R
import cashi.payment.oways.ui.AppTestTags

/** The screens reachable from the bottom navigation bar, in display order. */
enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    val testTag: String,
) {
    SEND(SendRoute, R.string.nav_send, R.drawable.ic_pay, AppTestTags.NAV_SEND),
    HISTORY(HistoryRoute, R.string.nav_history, R.drawable.ic_history, AppTestTags.NAV_HISTORY),
}