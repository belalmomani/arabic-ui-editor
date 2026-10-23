package com.arabicuieditor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5E8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E6F7),
    onPrimaryContainer = Color(0xFF00243B),
    secondary = Color(0xFF4F6070),
    background = Color(0xFFF7FAFC),
    surface = Color(0xFFF7FAFC)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BCBF0),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF004B71),
    onPrimaryContainer = Color(0xFFD0E6F7),
    secondary = Color(0xFFB7C8D9),
    background = Color(0xFF101417),
    surface = Color(0xFF101417)
)

/** ثيم التطبيق: يفرض اتجاه RTL على كل المحتوى. */
@Composable
fun ArabicUiEditorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content
        )
    }
}
