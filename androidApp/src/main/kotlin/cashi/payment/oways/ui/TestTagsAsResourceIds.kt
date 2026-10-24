package cashi.payment.oways.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Exposes the `testTag`s of this subtree as Android resource ids, so UiAutomator2 (Appium) can find
 * them. Inherited by children, but not across windows: apply it at the app root and again inside
 * each dialog or popup.
 */
fun Modifier.testTagsAsResourceIds(): Modifier = semantics { testTagsAsResourceId = true }