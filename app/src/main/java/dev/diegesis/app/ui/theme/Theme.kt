package dev.diegesis.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Base theme tokens from docs/ui-theme.md. Dark only — no light scheme,
 * no system follow, no toggle.
 */
object DiegesisColors {
    val Bg = Color(0xFF0A0A0B)        // app background
    val Surface = Color(0xFF121214)   // cards, sheets, input bar
    val Surface2 = Color(0xFF18181B)  // elevated elements, dialogs
    val Border = Color(0xFF1E1E20)    // hairline dividers, outlines
    val Text = Color(0xFFE8E8EA)      // primary
    val TextDim = Color(0xFF9A9AA0)   // secondary, metadata
    val TextFaint = Color(0xFF5C5C62) // timestamps, hints

    // Vibrant accents — only when they carry meaning.
    val Amber = Color(0xFFFFB020)
    val Cyan = Color(0xFF22D3EE)
    val Red = Color(0xFFF87171)
    val Green = Color(0xFF34D399)
}

val DiegesisDarkColorScheme = darkColorScheme(
    background = DiegesisColors.Bg,
    surface = DiegesisColors.Surface,
    surfaceVariant = DiegesisColors.Surface2,
    surfaceContainer = DiegesisColors.Surface,
    surfaceContainerHigh = DiegesisColors.Surface2,
    outline = DiegesisColors.Border,
    outlineVariant = DiegesisColors.Border,
    onBackground = DiegesisColors.Text,
    onSurface = DiegesisColors.Text,
    onSurfaceVariant = DiegesisColors.TextDim,
    primary = DiegesisColors.Text,
    onPrimary = DiegesisColors.Bg,
    secondary = DiegesisColors.TextDim,
    onSecondary = DiegesisColors.Bg,
    error = DiegesisColors.Red,
)

val DiegesisTypography = Typography(
    // Body text: 16sp / 1.5 line height
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    // Headings
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    // Labels
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun DiegesisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DiegesisDarkColorScheme,
        typography = DiegesisTypography,
        content = content
    )
}
