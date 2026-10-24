package cashi.payment.oways.e2e

/**
 * Android resource ids of the app's UI, i.e. its Compose test tags exposed with
 * `testTagsAsResourceId`. Mirrors `AppTestTags` in androidApp; the contract is docs/TEST_TAGS.md.
 */
internal object TestIds {
    const val NAV_HISTORY = "nav_history"
    const val SNACKBAR_MESSAGE = "snackbar_message"

    const val SEND_EMAIL_FIELD = "send_email_field"
    const val SEND_EMAIL_ERROR = "send_email_error"
    const val SEND_AMOUNT_FIELD = "send_amount_field"
    const val SEND_CURRENCY_FIELD = "send_currency_field"
    const val SEND_BUTTON = "send_button"
    const val CONFIRM_MESSAGE = "confirm_message"
    const val CONFIRM_SEND_BUTTON = "confirm_send_button"

    const val HISTORY_ITEM = "history_item"
    const val HISTORY_ITEM_RECIPIENT = "history_item_recipient"
    const val HISTORY_ITEM_AMOUNT = "history_item_amount"

    private const val SEND_CURRENCY_OPTION_PREFIX = "send_currency_option_"

    fun currencyOption(code: String): String = SEND_CURRENCY_OPTION_PREFIX + code
}