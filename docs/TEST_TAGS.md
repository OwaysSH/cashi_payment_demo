# UI test ids

Every element below has a Compose `testTag`, defined once in
`androidApp/src/main/kotlin/cashi/payment/oways/ui/AppTestTags.kt`. The app enables
`testTagsAsResourceId`, so each tag is also the element's Android **resource-id**, exactly as
written (no package prefix). UiAutomator2 (Appium) finds them with `AppiumBy.id("<tag>")`, or
`new UiSelector().resourceId("<tag>")`.

These ids are a contract with the Appium tests in `e2e/`: don't rename them without updating the
tests.

## Navigation and app shell

| Resource id | Element | Notes |
|---|---|---|
| `nav_send` | Bottom bar item "Send" | Selected on start |
| `nav_history` | Bottom bar item "History" | |
| `screen_send` | Send screen container | Present when the Send screen is shown |
| `screen_history` | History screen container | Present when the History screen is shown |
| `snackbar_message` | Snackbar | Text: "Payment sent", "Payment sent, but it couldn't be added to history yet", "No connection. Check your network and try again.", "Something went wrong. Please try again." Shown for a few seconds; wait for it explicitly |

## Send screen

| Resource id | Element | Notes |
|---|---|---|
| `send_email_field` | Recipient email text field | |
| `send_email_error` | Inline error under the email field | "Invalid recipient"; only present while shown |
| `send_amount_field` | Amount text field | Accepts `.` or `,` as decimal separator |
| `send_amount_error` | Inline error under the amount field | "Invalid amount"; only present while shown |
| `send_currency_field` | Currency dropdown (read-only field) | Tap to open the menu |
| `send_currency_option_USD`, `send_currency_option_EUR` | Menu items of the open dropdown | |
| `send_currency_error` | Inline error under the currency field | "Unsupported currency"; only present while shown |
| `send_button` | Send button | Disabled while sending |
| `confirm_message` | Confirmation dialog text | e.g. "Send $10.50 to alice@example.com?" |
| `confirm_send_button` | Dialog "Send" | Sends the payment |
| `confirm_cancel_button` | Dialog "Cancel" | Back to the form |

## History screen

| Resource id | Element | Notes |
|---|---|---|
| `history_list` | List of payments | Newest first |
| `history_item` | One payment row | Same id for every row; find all and match by text |
| `history_item_recipient` | Recipient email inside a row | |
| `history_item_amount` | Amount with currency code inside a row | e.g. "$10.50 USD" |
| `history_item_timestamp` | Firestore timestamp inside a row | Locale/time-zone dependent; don't assert exact text |
| `history_empty` | "No payments yet" | Only when the history is empty |
| `history_error` | Error message container | Only on a load failure |
| `history_retry` | "Try again" button | Inside `history_error` |

## Inspecting the ids on a device

With the app open on the screen you want to check:

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml \
  && grep -o 'resource-id="[a-zA-Z_]*"' /tmp/ui.xml | sort -u
```

The dropdown menu and the confirmation dialog are separate windows; open them first, then dump.