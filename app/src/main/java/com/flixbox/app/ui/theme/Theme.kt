package com.flixbox.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// FlixBox color palette
val FxRed       = Color(0xFFE50914)
val FxRedDark   = Color(0xFFB20710)
val FxBlack     = Color(0xFF0F0F0F)
val FxDark      = Color(0xFF1A1A1A)
val FxCard      = Color(0xFF222222)
val FxGray      = Color(0xFF808080)
val FxLightGray = Color(0xFFAAAAAA)
val FxWhite     = Color(0xFFFFFFFF)
val FxOverlay   = Color(0x99000000)

private val ColorScheme = darkColorScheme(
    primary       = FxRed,
    background    = FxBlack,
    surface       = FxDark,
    onPrimary     = FxWhite,
    onBackground  = FxWhite,
    onSurface     = FxWhite,
    secondary     = FxGray
)

@Composable
fun FlixBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, content = content)
}
