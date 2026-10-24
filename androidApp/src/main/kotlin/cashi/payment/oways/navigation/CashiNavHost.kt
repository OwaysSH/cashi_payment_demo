package cashi.payment.oways.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import cashi.payment.oways.ui.history.HistoryScreen
import cashi.payment.oways.ui.send.SendScreen

@Composable
fun CashiNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = SendRoute, modifier = modifier) {
        composable<SendRoute> { SendScreen(snackbarHostState) }
        composable<HistoryRoute> { HistoryScreen() }
    }
}

/**
 * Bottom-bar navigation: no back-stack growth when switching tabs, one instance per tab, and each
 * tab's state is saved and restored. Back from any tab returns to the start destination.
 */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}