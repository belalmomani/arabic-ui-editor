package com.arabicuieditor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** لوحة ألوان المحرر الموحّدة (واجهة التطبيق نفسه، لا تصاميم المستخدم). */
object Palette {
    val Primary = Color(0xFF23866B)
    val PrimaryPressed = Color(0xFF196B55)
    val Background = Color(0xFFF3F5F7)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF202B35)
    val TextSecondary = Color(0xFF77838D)
    val Border = Color(0xFFDCE2E6)
    val Selection = Color(0xFFE5F3EE)
    val Warning = Color(0xFFD99A36)
    val Error = Color(0xFFD9534F)
    /** أخضر فاتح لخطوط المحاذاة والحاويات الإرشادية. */
    val GuideGreen = Color(0xFF7CCBB1)
}

private val LightColors = lightColorScheme(
    primary = Palette.Primary,
    onPrimary = Color.White,
    primaryContainer = Palette.Selection,
    onPrimaryContainer = Palette.PrimaryPressed,
    secondary = Palette.TextSecondary,
    onSecondary = Color.White,
    secondaryContainer = Palette.Selection,
    onSecondaryContainer = Palette.PrimaryPressed,
    tertiary = Palette.Warning,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.Background,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceTint = Palette.Primary,
    surfaceContainerLowest = Palette.Surface,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface,
    surfaceContainerHighest = Palette.Background,
    outline = Palette.Border,
    outlineVariant = Palette.Border,
    error = Palette.Error,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5CC4A4),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF14533F),
    onPrimaryContainer = Color(0xFFCDEEE3),
    secondary = Color(0xFFA9B6BF),
    secondaryContainer = Color(0xFF14533F),
    onSecondaryContainer = Color(0xFFCDEEE3),
    tertiary = Color(0xFFE5B661),
    background = Color(0xFF14181B),
    surface = Color(0xFF1B2024),
    surfaceContainer = Color(0xFF1B2024),
    surfaceContainerHigh = Color(0xFF232A2F),
    outline = Color(0xFF39434A),
    outlineVariant = Color(0xFF39434A),
    error = Color(0xFFE8847F)
)

/** زوايا موحّدة 10–12dp: الأزرار والشرائح والبطاقات. */
private val AppShapeSet = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

object AppShapes {
    val control = RoundedCornerShape(10.dp)
}

/** ثيم التطبيق: يفرض اتجاه RTL على كل المحتوى. */
@Composable
fun ArabicUiEditorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = AppShapeSet,
            content = content
        )
    }
}
