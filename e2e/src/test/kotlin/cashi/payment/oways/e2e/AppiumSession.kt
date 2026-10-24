package cashi.payment.oways.e2e

import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.net.URI
import java.time.Duration

/**
 * One Appium (UiAutomator2) session on the real device, with the app freshly launched on its start
 * screen. All lookups go by resource id and wait explicitly (no fixed sleeps).
 *
 * Resource ids are the app's plain Compose test tags (e.g. `send_email_field`, exposed with
 * `testTagsAsResourceId`), without the `<package>:id/` prefix of XML views. By default UiAutomator2
 * auto-completes an id without a package to `cashi.payment.oways:id/send_email_field`, which matches
 * nothing, so the session sets `appium:disableIdLocatorAutocompletion` to look up ids exactly.
 *
 * The on-screen keyboard is disabled for the session with `appium:hideKeyboard` (Appium's invisible
 * `io.appium.settings` keyboard; the original one is restored afterwards), so it can never cover a
 * target. The driver's `hideKeyboard()` is deliberately not used: when it can't close the keyboard
 * otherwise, UiAutomator2 presses Back, and Back on the Send screen closes the app. That happened on
 * an Android emulator with a hardware keyboard, where `isKeyboardShown` reports `true` regardless.
 */
internal class AppiumSession private constructor(private val driver: AndroidDriver) : AutoCloseable {

    private val wait = WebDriverWait(driver, WAIT_TIMEOUT)

    /** Waits until the element with resource [id] is visible. */
    fun element(id: String): WebElement = wait.until(ExpectedConditions.visibilityOfElementLocated(AppiumBy.id(id)))

    fun tap(id: String) = element(id).click()

    /**
     * Types into a text field: into the tagged element if it is the `EditText`, otherwise into its
     * first `EditText` descendant (in case Compose puts the tag on a container around the editable
     * node). No keyboard needs hiding; see the class KDoc.
     */
    fun type(id: String, text: String) {
        val field = element(id)
        val editable = field.takeIf { it.getAttribute(CLASS_NAME_ATTRIBUTE) == EDIT_TEXT_CLASS }
            ?: field.findElements(By.className(EDIT_TEXT_CLASS)).firstOrNull()
            ?: field
        editable.click()
        editable.sendKeys(text)
    }

    fun isPresent(id: String): Boolean = driver.findElements(AppiumBy.id(id)).isNotEmpty()

    /**
     * Waits until an element with resource [id] shows [expected], either as its own text or as the
     * text of a descendant (e.g. the snackbar container holds its message in a child node).
     */
    fun waitForText(id: String, expected: String): WebElement = wait.until {
        driver.findElements(AppiumBy.id(id)).firstOrNull { it.textWithDescendants().any { text -> text.contains(expected) } }
    }

    /** Waits for a list row ([rowId]) whose child [childId] has exactly [text]; returns that row. */
    fun waitForRow(rowId: String, childId: String, text: String): WebElement = wait.until {
        driver.findElements(AppiumBy.id(rowId)).firstOrNull { row ->
            row.findElements(AppiumBy.id(childId)).any { it.text == text }
        }
    }

    private fun WebElement.textWithDescendants(): List<String> =
        listOf(text) + findElements(By.xpath(DESCENDANTS)).map { it.text }

    override fun close() = driver.quit()

    companion object {
        private const val APP_PACKAGE = "cashi.payment.oways"
        private const val APP_ACTIVITY = ".MainActivity"
        private const val APPIUM_URL_ENV = "APPIUM_URL"
        private const val DEFAULT_APPIUM_URL = "http://127.0.0.1:4723"
        private const val DESCENDANTS = ".//*"
        private const val FORCE_APP_LAUNCH = "appium:forceAppLaunch"
        private const val SHOULD_TERMINATE_APP = "appium:shouldTerminateApp"
        private const val DISABLE_ID_AUTOCOMPLETION = "appium:disableIdLocatorAutocompletion"
        private const val HIDE_KEYBOARD = "appium:hideKeyboard"
        private const val CLASS_NAME_ATTRIBUTE = "className"
        private const val EDIT_TEXT_CLASS = "android.widget.EditText"
        private val WAIT_TIMEOUT: Duration = Duration.ofSeconds(15)
        private val NEW_COMMAND_TIMEOUT: Duration = Duration.ofSeconds(60)

        /** Starts a session on the installed debug app (never reinstalled or cleared). */
        fun start(): AppiumSession {
            val udid = DeviceSelector.udid()
            check(DeviceSelector.isAppInstalled(udid, APP_PACKAGE)) {
                "App $APP_PACKAGE is not installed on $udid. connectedDebugAndroidTest uninstalls it when it " +
                    "finishes; run ./gradlew :androidApp:installDebug first."
            }
            val options = UiAutomator2Options()
                .setUdid(udid)
                .setAppPackage(APP_PACKAGE)
                .setAppActivity(APP_ACTIVITY)
                .setNoReset(true)
                .setNewCommandTimeout(NEW_COMMAND_TIMEOUT)
                // No typed setters in java-client 11: stop the app at session end and relaunch it
                // at session start even if it's already running, so each test starts on Send.
                .amend(SHOULD_TERMINATE_APP, true)
                .amend(FORCE_APP_LAUNCH, true)
                // Compose resource ids have no package prefix; see the class KDoc.
                .amend(DISABLE_ID_AUTOCOMPLETION, true)
                // No on-screen keyboard during the session; see the class KDoc.
                .amend(HIDE_KEYBOARD, true)
            val appiumUrl = System.getenv(APPIUM_URL_ENV) ?: DEFAULT_APPIUM_URL
            return AppiumSession(AndroidDriver(URI(appiumUrl).toURL(), options))
        }
    }
}