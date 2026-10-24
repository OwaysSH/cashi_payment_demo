package cashi.payment.oways

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cashi.payment.oways.navigation.CashiNavHost
import cashi.payment.oways.navigation.TopLevelDestination
import cashi.payment.oways.navigation.navigateToTopLevel
import cashi.payment.oways.ui.AppTestTags
import cashi.payment.oways.ui.testTagsAsResourceIds
import cashi.payment.oways.ui.theme.CashiTheme

/** App root: bottom navigation between the Send and History screens. */
@Composable
fun CashiApp() {
    CashiTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination
        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            modifier = Modifier.testTagsAsResourceIds(),
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(data, modifier = Modifier.testTag(AppTestTags.SNACKBAR_MESSAGE))
                }
            },
            bottomBar = {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination.isOn(destination),
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = { Icon(painterResource(destination.iconRes), contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                            modifier = Modifier.testTag(destination.testTag),
                        )
                    }
                }
            },
        ) { innerPadding ->
            CashiNavHost(
                navController = navController,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

@Preview
@Composable
private fun CashiAppPreview() {
    CashiApp()
}