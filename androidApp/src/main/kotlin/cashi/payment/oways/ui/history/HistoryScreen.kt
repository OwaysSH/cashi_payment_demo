package cashi.payment.oways.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cashi.payment.oways.R
import cashi.payment.oways.ui.AppTestTags
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
            .padding(dimensionResource(R.dimen.screen_padding))
            .testTag(AppTestTags.SCREEN_HISTORY),
    ) {
        Text(text = stringResource(R.string.history_title), style = MaterialTheme.typography.headlineSmall)
        when (state) {
            HistoryUiState.Loading -> CenteredBox { CircularProgressIndicator() }
            HistoryUiState.Empty -> CenteredBox {
                Text(stringResource(R.string.history_empty), modifier = Modifier.testTag(AppTestTags.HISTORY_EMPTY))
            }
            is HistoryUiState.Error -> CenteredBox { ErrorMessage(state.messageRes, onRetry) }
            is HistoryUiState.Content -> PaymentList(state.items)
        }
    }
}

@Composable
private fun PaymentList(items: List<PaymentItem>) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag(AppTestTags.HISTORY_LIST)) {
        items(items, key = { it.id }) { item ->
            PaymentRow(item)
            HorizontalDivider()
        }
    }
}

@Composable
private fun PaymentRow(item: PaymentItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensionResource(R.dimen.list_item_vertical_padding))
            .testTag(AppTestTags.HISTORY_ITEM),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.recipientEmail,
                style = MaterialTheme.typography.bodyLarge,
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
            text = stringResource(R.string.history_amount_with_currency, item.formattedAmount, item.currencyCode),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(AppTestTags.HISTORY_ITEM_AMOUNT),
        )
    }
}

@Composable
private fun ErrorMessage(messageRes: Int, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag(AppTestTags.HISTORY_ERROR)) {
        Text(stringResource(messageRes))
        Button(onClick = onRetry, modifier = Modifier.testTag(AppTestTags.HISTORY_RETRY)) {
            Text(stringResource(R.string.history_retry))
        }
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

@Preview(showBackground = true)
@Composable
private fun HistoryContentPreview() {
    MaterialTheme { HistoryContent(HistoryUiState.Content(previewItems), onRetry = {}) }
}

@Preview(showBackground = true)
@Composable
private fun HistoryEmptyPreview() {
    MaterialTheme { HistoryContent(HistoryUiState.Empty, onRetry = {}) }
}

@Preview(showBackground = true)
@Composable
private fun HistoryLoadingPreview() {
    MaterialTheme { HistoryContent(HistoryUiState.Loading, onRetry = {}) }
}

@Preview(showBackground = true)
@Composable
private fun HistoryErrorPreview() {
    MaterialTheme { HistoryContent(HistoryUiState.Error(R.string.history_error), onRetry = {}) }
}