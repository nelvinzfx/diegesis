package dev.diegesis.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Base theme tokens from docs/ui-theme.md. Dark only — no light scheme,
 * no system follow, no toggle. Phase 1 applies only the base tokens.
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
