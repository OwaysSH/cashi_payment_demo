package cashi.payment.oways.ui.history

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cashi.payment.oways.R
import cashi.payment.oways.ui.AppTestTags
import cashi.payment.oways.ui.theme.CashiTheme
import cashi.payment.oways.ui.theme.amountRow
import org.koin.androidx.compose.koinViewModel

/** Transaction History screen: connects [HistoryViewModel] to the stateless [HistoryContent]. */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier, viewModel: HistoryViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryContent(state = state, onRetry = viewModel::onRetry, modifier = modifier)
}

@Composable
fun HistoryContent(state: HistoryUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = dimensionResource(R.dimen.screen_padding))
            .testTag(AppTestTags.SCREEN_HISTORY),
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = dimensionResource(R.dimen.screen_padding)),
        )
        when (state) {
            HistoryUiState.Loading -> CenteredBox { CircularProgressIndicator() }
            HistoryUiState.Empty -> CenteredBox { EmptyMessage() }
            is HistoryUiState.Error -> CenteredBox { ErrorMessage(state.messageRes, onRetry) }
            is HistoryUiState.Content -> PaymentList(state.items)
        }
    }
}

@Composable
private fun PaymentList(items: List<PaymentItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(AppTestTags.HISTORY_LIST),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_item_spacing)),
        contentPadding = PaddingValues(bottom = dimensionResource(R.dimen.screen_padding)),
    ) {
        items(items, key = { it.id }) { item -> PaymentRow(item) }
    }
}

/** One payment as a card: recipient initial, recipient and timestamp, and the amount with its currency code. */
@Composable
private fun PaymentRow(item: PaymentItem) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(AppTestTags.HISTORY_ITEM),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimensionResource(R.dimen.card_padding),
                vertical = dimensionResource(R.dimen.list_item_vertical_padding),
            ),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_item_vertical_padding)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecipientAvatar(item.recipientEmail)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.recipientEmail,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(AppTestTags.HISTORY_ITEM_RECIPIENT),
                )
                Text(
                    item.formattedTimestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(AppTestTags.HISTORY_ITEM_TIMESTAMP),
                )
            }
            Text(
                text = amountWithCurrency(item),
                style = MaterialTheme.typography.amountRow,
                modifier = Modifier.testTag(AppTestTags.HISTORY_ITEM_AMOUNT),
            )
        }
    }
}

/** First letter of the recipient in a tinted circle. Decorative: hidden from accessibility and UI tests. */
@Composable
private fun RecipientAvatar(recipientEmail: String) {
    Box(
        modifier = Modifier
            .size(dimensionResource(R.dimen.avatar_size))
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = recipientEmail.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * The row's amount text, e.g. "$10.50 USD", from the same string resource as before; only the
 * currency code is styled (lighter and muted), so the text itself is unchanged.
 */
@Composable
private fun amountWithCurrency(item: PaymentItem): AnnotatedString {
    val text = stringResource(R.string.history_amount_with_currency, item.formattedAmount, item.currencyCode)
    val codeStart = text.lastIndexOf(item.currencyCode)
    val codeStyle = SpanStyle(fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
    return buildAnnotatedString {
        append(text)
        if (codeStart >= 0) addStyle(codeStyle, codeStart, codeStart + item.currencyCode.length)
    }
}

@Composable
private fun EmptyMessage() {
    StateMessage(iconRes = R.drawable.ic_receipt, iconTint = MaterialTheme.colorScheme.primary) {
        Text(
            stringResource(R.string.history_empty),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(AppTestTags.HISTORY_EMPTY),
        )
        Text(
            stringResource(R.string.history_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorMessage(messageRes: Int, onRetry: () -> Unit) {
    StateMessage(
        iconRes = R.drawable.ic_error,
        iconTint = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(AppTestTags.HISTORY_ERROR),
    ) {
        Text(stringResource(messageRes), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        FilledTonalButton(onClick = onRetry, modifier = Modifier.testTag(AppTestTags.HISTORY_RETRY)) {
            Text(stringResource(R.string.history_retry))
        }
    }
}

/** A large icon in a tinted circle above a short message, for the empty and error states. */
@Composable
private fun StateMessage(
    iconRes: Int,
    iconTint: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.form_spacing)),
        modifier = modifier,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                .padding(dimensionResource(R.dimen.state_icon_padding))
                .size(dimensionResource(R.dimen.state_icon_size)),
        )
        content()
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private val previewItems = listOf(
    PaymentItem("p-2", "second@example.com", "€25.50", "EUR", "Oct 23, 2026, 9:20 AM"),
    PaymentItem("p-1", "alice@example.com", "$10.50", "USD", "Oct 23, 2026, 9:15 AM"),
)

@Preview(name = "Content", showBackground = true)
@Preview(name = "Content, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryContentPreview() {
    CashiTheme { HistoryContent(HistoryUiState.Content(previewItems), onRetry = {}) }
}

@Preview(name = "Empty", showBackground = true)
@Preview(name = "Empty, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryEmptyPreview() {
    CashiTheme { HistoryContent(HistoryUiState.Empty, onRetry = {}) }
}

@Preview(name = "Loading", showBackground = true)
@Composable
private fun HistoryLoadingPreview() {
    CashiTheme { HistoryContent(HistoryUiState.Loading, onRetry = {}) }
}

@Preview(name = "Error", showBackground = true)
@Preview(name = "Error, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryErrorPreview() {
    CashiTheme { HistoryContent(HistoryUiState.Error(R.string.history_error), onRetry = {}) }
}