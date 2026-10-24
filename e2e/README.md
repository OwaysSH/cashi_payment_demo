# End-to-end UI tests (Appium)

Appium (UiAutomator2) tests that drive the real app on a **real Android device**, through the whole
stack: Compose UI → ViewModels → shared use case → Ktor server (via `adb reverse` 8080) → Firestore
emulator (via `adb reverse` 8085).

| Test | What it checks |
|---|---|
| `sendPayment_appearsInHistory` | Sends 12.34 EUR to a unique recipient (`e2e-<millis>-<random>@example.com`) via the confirmation dialog, waits for the "Payment sent" snackbar, opens History and finds the row with that recipient showing `12.34` (or `12,34`, per device locale) and `EUR`. |
| `invalidRecipient_showsInlineError` | Enters `not-an-email`, taps Send, and checks the inline "Invalid recipient" error with no confirmation dialog. |

Elements are found by **resource id** (the app's Compose test tags; see
[docs/TEST_TAGS.md](../docs/TEST_TAGS.md), mirrored in `TestIds.kt`) and every step **waits
explicitly** (`WebDriverWait`, 15 s) for its element or text. There are no fixed sleeps. The unique
recipient lets the test find its own payment among earlier ones in the emulator.

**Why `appium:disableIdLocatorAutocompletion=true`:** Compose's `testTagsAsResourceId` exposes the
plain tag as the resource id (`send_email_field`), with no `<package>:id/` prefix. By default
UiAutomator2 auto-completes an id without a package to `cashi.payment.oways:id/send_email_field`,
which matches nothing, so every lookup would time out. The session disables that auto-completion so
ids are matched exactly. Typing goes to the tagged `EditText`, or to its first `EditText` descendant
if the tag sits on a container.

**Why `appium:hideKeyboard=true` (and no `driver.hideKeyboard()`):** the capability swaps in
Appium's invisible keyboard (`io.appium.settings`) for the session and restores the original one
afterwards, so no on-screen keyboard can cover a target. The tests never call `hideKeyboard()`:
when UiAutomator2 can't close the keyboard otherwise, it presses **Back**, and Back on the Send
screen closes the app. That happened on an Android emulator with a hardware keyboard
(`hw.keyboard=yes`), where `isKeyboardShown` reports `true` regardless. On a phone, Back only closed
the real keyboard, which is why it went unnoticed there.

### Running on an Android emulator

Works the same way as on a phone; no extra setup is needed in the emulator. Make sure the app can
reach the backend and the Firestore emulator on your Mac: either run `./scripts/device-setup.sh`
(adb reverse works for emulators too), or set `api.baseUrl=http://10.0.2.2:8080` and
`firestore.emulatorHost=10.0.2.2:8085` in `local.properties` and reinstall the app (see the README's
[Connection options](../README.md#connection-options)). With several devices or emulators
connected, set `ANDROID_SERIAL` (e.g. `emulator-5554`).

## Why a standalone build

`e2e/` is its own Gradle build (`e2e/settings.gradle.kts`), **not included** in the app's build, so
`./gradlew build` / `allTests` never need a device or Appium. It uses the root Gradle wrapper
(`./gradlew -p e2e …`) and the root version catalog (`gradle/libs.versions.toml`). To edit it with
code completion in Android Studio / IntelliJ, link `e2e/build.gradle.kts` as an additional Gradle
project (Gradle tool window → **+** → select the file).

## Prerequisites (once)

- Node.js `^20.19`, `^22.12` or `>=24`, then Appium 3 and the UiAutomator2 driver:
  ```bash
  npm install -g appium@3.8.0
  appium driver install uiautomator2@8.7.0
  appium driver doctor uiautomator2      # optional: checks ANDROID_HOME, JAVA_HOME, adb
  ```
- `ANDROID_HOME` (Android SDK) and `JAVA_HOME` set in the shell that starts Appium.
- A device connected over USB with USB debugging enabled. On **Samsung/Xiaomi** devices also enable
  "Install via USB" / "USB debugging (Security settings)" in Developer options, or the UiAutomator2
  helper apps can't be installed.

## Run

```bash
./gradlew :server:installDist && server/build/install/server/bin/server   # terminal 1: backend
firebase emulators:start --only firestore --project demo-cashi           # terminal 2: Firestore emulator
                                    # (use the project_id of your google-services.json; the example file uses demo-cashi;
                                    #  not needed if the app was built with firestore.useEmulator=false)
appium                                                                    # terminal 3: Appium (127.0.0.1:4723)
./scripts/device-setup.sh                                                 # adb reverse 8080 + 8085
./gradlew :androidApp:installDebug                                        # app under test (uses the emulator)
./gradlew -p e2e test --rerun                                             # the tests
```

`--rerun` makes Gradle run the tests even when nothing changed since the last run; without it,
Gradle can report the `test` task as up to date and skip it, so nothing happens on the device. The
report is at `e2e/build/reports/tests/test/index.html`. The app is used as installed
(`noReset`): it's never reinstalled or cleared, so you always test the build you just installed.
Each test gets a fresh session and the app is relaunched on the Send screen.

### Settings

| Environment variable | Default | Meaning |
|---|---|---|
| `ANDROID_SERIAL` | the single ready device from `adb devices` | Device udid; required when several devices are connected |
| `APPIUM_URL` | `http://127.0.0.1:4723` | Appium server address |

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Could not start a new session … Connection refused` | Appium isn't running (start `appium`), or `APPIUM_URL` points elsewhere |
| "No Android device is connected and ready" / "Several devices are connected" | Connect one device, or set `ANDROID_SERIAL` |
| Snackbar shows "No connection…" | Server not running or `adb reverse` missing: rerun `./scripts/device-setup.sh` |
| Payment never appears in History | Firestore emulator not running, or `adb reverse tcp:8085` missing |
| Every first lookup times out (`NoSuchElementException` for `send_email_field`) | Ids are being auto-prefixed with the package; the session must set `appium:disableIdLocatorAutocompletion=true` (see above) |
| The email is typed, then the next lookup times out and the launcher is in front | The app was closed by a Back press from `hideKeyboard()`; the session must set `appium:hideKeyboard=true` and the tests must not call `driver.hideKeyboard()` (see above) |
| An element id isn't found | App build out of date: rerun `./gradlew :androidApp:installDebug` (ids are listed in docs/TEST_TAGS.md) |
| UiAutomator2 server install fails on the device | Enable "Install via USB" / security settings (see Prerequisites) |