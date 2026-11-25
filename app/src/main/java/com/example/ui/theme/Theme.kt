package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NseStreamColorScheme = darkColorScheme(
    primary = NseCrimson,
    onPrimary = CrispWhite,
    primaryContainer = NseDeepCrimson,
    onPrimaryContainer = CrispWhite,
    secondary = NseRubyAccent,
    onSecondary = CrispWhite,
    secondaryContainer = GraphiteElevated,
    onSecondaryContainer = CrispWhite,
    tertiary = SignalEmerald,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = CrispWhite,
    surface = CharcoalSurface,
    onSurface = CrispWhite,
    surfaceVariant = GraphiteElevated,
    onSurfaceVariant = MutedSilver,
    surfaceContainer = CharcoalSurface,
    surfaceContainerHigh = GraphiteElevated,
    outline = SlateCardBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NseStreamColorScheme,
        typography = Typography,
        content = content
    )
}
