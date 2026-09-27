package com.parvez.booker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BookerColorScheme = darkColorScheme(
    primary = AccentGold,
    secondary = AccentGold,
    tertiary = SuccessGreen,
    background = BgDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceAlt,
    onPrimary = BgDark,
    onSecondary = BgDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = HairlineBorder
)

@Composable
fun BookerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BookerColorScheme,
        typography = Typography,
        content = content
    )
}