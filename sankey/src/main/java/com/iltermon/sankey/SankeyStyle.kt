package com.iltermon.sankey

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visual parameters for [SankeyDiagram]. Every color, dimension, and formatter is a parameter so the
 * library stays app-agnostic (no hardcoded strings, currency, or theme). Build one with
 * [SankeyDefaults.style] and `.copy(...)` the pieces that should follow the host theme.
 */
@Immutable
data class SankeyStyle(
    val nodeWidth: Dp = 12.dp,
    val nodeGap: Dp = 10.dp,
    val nodeCornerRadius: Dp = 4.dp,
    val minNodeHeight: Dp = 4.dp,
    val labelSpacing: Dp = 8.dp,
    val maxLabelWidthFraction: Float = 0.30f,
    val labelTextStyle: TextStyle,
    val valueTextStyle: TextStyle,
    val labelColor: Color,
    val valueColor: Color,
    val palette: List<Color>,
    val neutralColor: Color,
    val remainderColor: Color,
    val ribbonAlpha: Float = 0.35f,
    val highlightRibbonAlpha: Float = 0.60f,
    val dimmedAlpha: Float = 0.12f,
    val valueFormatter: (Double) -> String = { "%.2f".format(it) },
)

object SankeyDefaults {
    // Pre-validated categorical palettes (see dataviz validator): 8 fixed slots. Labels are always
    // drawn beside nodes, which supplies the secondary encoding the CVD/contrast floor requires.
    private val LightPalette = listOf(
        Color(0xFF2A78D6), Color(0xFF1BAF7A), Color(0xFFEDA100), Color(0xFF008300),
        Color(0xFF4A3AA7), Color(0xFFE34948), Color(0xFFE87BA4), Color(0xFFEB6834),
    )
    private val DarkPalette = listOf(
        Color(0xFF3987E5), Color(0xFF199E70), Color(0xFFC98500), Color(0xFF008300),
        Color(0xFF9085E9), Color(0xFFE66767), Color(0xFFD55181), Color(0xFFD95926),
    )

    /** The 8-slot categorical palette for the given theme. */
    fun palette(darkTheme: Boolean): List<Color> = if (darkTheme) DarkPalette else LightPalette

    /** Neutral gray for the synthetic hub/deficit balancing node. */
    fun neutral(darkTheme: Boolean): Color = Color(0xFF898781)

    /** Muted success green for the "unspent" remainder node — surplus reads as positive, not filler. */
    fun remainder(darkTheme: Boolean): Color = if (darkTheme) Color(0xFF5FBF95) else Color(0xFF2E7D57)

    /**
     * A default style. The caller is expected to `.copy(...)` in host theme values (label/value
     * text styles and colors, currency [SankeyStyle.valueFormatter]); the defaults here only exist
     * so previews and tests can render without a Material theme.
     */
    @Composable
    fun style(darkTheme: Boolean = isSystemInDarkTheme()): SankeyStyle = SankeyStyle(
        labelTextStyle = TextStyle.Default,
        valueTextStyle = TextStyle.Default,
        labelColor = if (darkTheme) Color(0xFFE6E1E5) else Color(0xFF1C1B1F),
        valueColor = if (darkTheme) Color(0xFFCAC4D0) else Color(0xFF49454F),
        palette = palette(darkTheme),
        neutralColor = neutral(darkTheme),
        remainderColor = remainder(darkTheme),
    )
}
