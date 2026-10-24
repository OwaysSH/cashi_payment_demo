package cashi.payment.oways.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The Cashi theme: fixed brand colors in light and dark mode (no wallpaper-based dynamic color, so
 * the app looks the same on every device), the tuned type scale and rounded shapes.
 */
@Composable
fun CashiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CashiTypography,
        shapes = CashiShapes,
        content = content,
    )
}