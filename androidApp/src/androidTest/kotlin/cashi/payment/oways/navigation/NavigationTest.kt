package cashi.payment.oways.navigation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cashi.payment.oways.CashiApp
import cashi.payment.oways.ui.AppTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bottom-bar navigation between Send and History. Runs on a device on demand; needs no server or
 * emulator. Run with `./gradlew :androidApp:connectedDebugAndroidTest
 * -Pandroid.testInstrumentationRunnerArguments.class=cashi.payment.oways.navigation.NavigationTest`.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun showApp() {
        composeRule.setContent { CashiApp() }
    }

    @Test
    fun startsOnSend() {
        composeRule.onNodeWithTag(AppTestTags.SCREEN_SEND).assertIsDisplayed()
        composeRule.onNodeWithTag(AppTestTags.NAV_SEND).assertIsSelected()
        composeRule.onNodeWithTag(AppTestTags.NAV_HISTORY).assertIsNotSelected()
    }

    @Test
    fun tappingHistory_showsHistory() {
        composeRule.onNodeWithTag(AppTestTags.NAV_HISTORY).performClick()

        composeRule.onNodeWithTag(AppTestTags.SCREEN_HISTORY).assertIsDisplayed()
        composeRule.onAllNodesWithTag(AppTestTags.SCREEN_SEND).assertCountEquals(0)
        composeRule.onNodeWithTag(AppTestTags.NAV_HISTORY).assertIsSelected()
    }

    @Test
    fun tappingSend_returnsToSend() {
        composeRule.onNodeWithTag(AppTestTags.NAV_HISTORY).performClick()

        composeRule.onNodeWithTag(AppTestTags.NAV_SEND).performClick()

        composeRule.onNodeWithTag(AppTestTags.SCREEN_SEND).assertIsDisplayed()
        composeRule.onAllNodesWithTag(AppTestTags.SCREEN_HISTORY).assertCountEquals(0)
        composeRule.onNodeWithTag(AppTestTags.NAV_SEND).assertIsSelected()
    }

    @Test
    fun reselectingCurrentTab_keepsASingleScreen() {
        composeRule.onNodeWithTag(AppTestTags.NAV_HISTORY).performClick()
        composeRule.onNodeWithTag(AppTestTags.NAV_HISTORY).performClick()

        composeRule.onAllNodesWithTag(AppTestTags.SCREEN_HISTORY).assertCountEquals(1)
    }
}