package cashi.payment.oways.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/** Tabular figures, so digits of different amounts line up. */
private const val TABULAR_FIGURES = "tnum"

private val base = Typography()

/** The Material 3 scale on the system font, with heavier titles and labels. */
internal val CashiTypography: Typography = base.copy(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Large amount input on the Send screen. */
val Typography.amountInput: TextStyle
    get() = headlineMedium.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR_FIGURES)

/** Amount in a History row. */
val Typography.amountRow: TextStyle
    get() = titleMedium.copy(fontFeatureSettings = TABULAR_FIGURES)