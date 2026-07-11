package com.shopmandate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Cta,
    onSecondary = Color.White,
    background = AppBg,
    onBackground = Ink,
    surface = AppSurface,
    onSurface = Ink,
)

// Phase 1 is light-only (matches the Stitch design). Dark theme = later.
@Composable
fun ShopMandateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
