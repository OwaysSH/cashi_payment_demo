package cashi.payment.oways.ui.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cashi.payment.oways.R
import cashi.payment.oways.shared.domain.model.Currency
import cashi.payment.oways.ui.AppTestTags
import cashi.payment.oways.ui.testTagsAsResourceIds
import org.koin.androidx.compose.koinViewModel

/** Send Payment screen: connects [SendPaymentViewModel] to the stateless [SendPaymentContent]. */
@Composable
fun SendScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: SendPaymentViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current

    LaunchedEffect(state.status) {
        val finished = state.status as? SubmissionStatus.Finished ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resources.getString(finished.messageRes))
        viewModel.onStatusShown()
    }

    SendPaymentContent(
        state = state,
        actions = SendPaymentActions(
            onEmailChange = viewModel::onEmailChange,
            onAmountChange = viewModel::onAmountChange,
            onCurrencyChange = viewModel::onCurrencyChange,
            onSendClick = viewModel::onSendClick,
            onConfirm = viewModel::onConfirm,
            onDismissConfirmation = viewModel::onDismissConfirmation,
        ),
        modifier = modifier,
    )
}

/** User intents of the Send form, hoisted out of [SendPaymentContent]. */
data class SendPaymentActions(
    val onEmailChange: (String) -> Unit = {},
    val onAmountChange: (String) -> Unit = {},
    val onCurrencyChange: (Currency) -> Unit = {},
    val onSendClick: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismissConfirmation: () -> Unit = {},
)

@Composable
fun SendPaymentContent(state: SendPaymentUiState, actions: SendPaymentActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(dimensionResource(R.dimen.screen_padding))
            .testTag(AppTestTags.SCREEN_SEND),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.form_spacing)),
    ) {
        Text(text = stringResource(R.string.send_title), style = MaterialTheme.typography.headlineSmall)

        FormTextField(
            value = state.recipientEmail,
            onValueChange = actions.onEmailChange,
            labelRes = R.string.send_email_label,
            error = state.fieldError?.takeIf { it.field == FormField.EMAIL },
            keyboardType = KeyboardType.Email,
            enabled = !state.isSending,
            testTag = AppTestTags.SEND_EMAIL_FIELD,
            errorTestTag = AppTestTags.SEND_EMAIL_ERROR,
        )
        FormTextField(
            value = state.amountText,
            onValueChange = actions.onAmountChange,
            labelRes = R.string.send_amount_label,
            error = state.fieldError?.takeIf { it.field == FormField.AMOUNT },
            keyboardType = KeyboardType.Decimal,
            enabled = !state.isSending,
            testTag = AppTestTags.SEND_AMOUNT_FIELD,
            errorTestTag = AppTestTags.SEND_AMOUNT_ERROR,
        )
        CurrencyDropdown(
            selected = state.currency,
            onSelect = actions.onCurrencyChange,
            error = state.fieldError?.takeIf { it.field == FormField.CURRENCY },
            enabled = !state.isSending,
        )

        Button(
            onClick = actions.onSendClick,
            enabled = !state.isSending,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AppTestTags.SEND_BUTTON),
        ) {
            if (state.isSending) {
                CircularProgressIndicator(modifier = Modifier.size(dimensionResource(R.dimen.button_progress_size)))
            } else {
                Text(stringResource(R.string.send_button))
            }
        }
    }

    state.confirmation?.let { confirmation ->
        ConfirmationDialog(confirmation = confirmation, onConfirm = actions.onConfirm, onDismiss = actions.onDismissConfirmation)
    }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    error: FieldError?,
    keyboardType: KeyboardType,
    enabled: Boolean,
    testTag: String,
    errorTestTag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        isError = error != null,
        supportingText = error?.let { { ErrorText(it, errorTestTag) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(selected: Currency, onSelect: (Currency) -> Unit, error: FieldError?, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selected.code,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.send_currency_label)) },
            isError = error != null,
            supportingText = error?.let { { ErrorText(it, AppTestTags.SEND_CURRENCY_ERROR) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .testTag(AppTestTags.SEND_CURRENCY_FIELD),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.testTagsAsResourceIds(), // the menu is a separate popup window
        ) {
            Currency.entries.forEach { currency ->
                DropdownMenuItem(
                    text = { Text(currency.code) },
                    onClick = {
                        onSelect(currency)
                        expanded = false
                    },
                    modifier = Modifier.testTag(AppTestTags.SEND_CURRENCY_OPTION_PREFIX + currency.code),
                )
            }
        }
    }
}

@Composable
private fun ErrorText(error: FieldError, testTag: String) {
    Text(stringResource(error.messageRes), modifier = Modifier.testTag(testTag))
}

@Composable
private fun ConfirmationDialog(confirmation: PaymentConfirmation, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTagsAsResourceIds(), // the dialog is a separate window
        title = { Text(stringResource(R.string.confirm_title)) },
        text = {
            Text(
                text = stringResource(R.string.confirm_message, confirmation.formattedAmount, confirmation.recipientEmail),
                modifier = Modifier.testTag(AppTestTags.CONFIRM_MESSAGE),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(AppTestTags.CONFIRM_SEND_BUTTON)) {
                Text(stringResource(R.string.confirm_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(AppTestTags.CONFIRM_CANCEL_BUTTON)) {
                Text(stringResource(R.string.confirm_cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SendPaymentEmptyPreview() {
    MaterialTheme { SendPaymentContent(SendPaymentUiState(), SendPaymentActions()) }
}

@Preview(showBackground = true)
@Composable
private fun SendPaymentErrorPreview() {
    MaterialTheme {
        SendPaymentContent(
            SendPaymentUiState(
                recipientEmail = "not-an-email",
                amountText = "10.50",
                fieldError = FieldError(FormField.EMAIL, R.string.error_invalid_recipient),
            ),
            SendPaymentActions(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SendPaymentSendingPreview() {
    MaterialTheme {
        SendPaymentContent(
            SendPaymentUiState(recipientEmail = "alice@example.com", amountText = "10.50", status = SubmissionStatus.Sending),
            SendPaymentActions(),
        )
    }
}