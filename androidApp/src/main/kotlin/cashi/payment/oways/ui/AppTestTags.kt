package cashi.payment.oways.ui

/**
 * Stable identifiers for UI tests. They are also the Android resource ids Appium uses (see
 * [testTagsAsResourceIds] and docs/TEST_TAGS.md), so the values are a contract with the e2e tests:
 * don't rename them without updating e2e/.
 */
object AppTestTags {
    const val NAV_SEND = "nav_send"
    const val NAV_HISTORY = "nav_history"
    const val SCREEN_SEND = "screen_send"
    const val SCREEN_HISTORY = "screen_history"
    const val SNACKBAR_MESSAGE = "snackbar_message"

    const val SEND_EMAIL_FIELD = "send_email_field"
    const val SEND_AMOUNT_FIELD = "send_amount_field"
    const val SEND_CURRENCY_FIELD = "send_currency_field"
    const val SEND_CURRENCY_OPTION_PREFIX = "send_currency_option_"
    const val SEND_BUTTON = "send_button"
    const val SEND_EMAIL_ERROR = "send_email_error"
    const val SEND_AMOUNT_ERROR = "send_amount_error"
    const val SEND_CURRENCY_ERROR = "send_currency_error"
    const val CONFIRM_MESSAGE = "confirm_message"
    const val CONFIRM_SEND_BUTTON = "confirm_send_button"
    const val CONFIRM_CANCEL_BUTTON = "confirm_cancel_button"

    const val HISTORY_LIST = "history_list"
    const val HISTORY_ITEM = "history_item"
    const val HISTORY_ITEM_RECIPIENT = "history_item_recipient"
    const val HISTORY_ITEM_AMOUNT = "history_item_amount"
    const val HISTORY_ITEM_TIMESTAMP = "history_item_timestamp"
    const val HISTORY_EMPTY = "history_empty"
    const val HISTORY_ERROR = "history_error"
    const val HISTORY_RETRY = "history_retry"
}