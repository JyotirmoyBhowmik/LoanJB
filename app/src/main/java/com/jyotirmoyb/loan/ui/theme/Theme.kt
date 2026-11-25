package com.jyotirmoyb.loan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = TealPrimaryDark,
    secondary = SlateNavy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3ECF2),
    onSecondaryContainer = SlateNavy,
    tertiary = InterestAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background = SurfaceTintLight,
    onBackground = TextPrimaryLight,
    surface = CardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFEBF3F1),
    onSurfaceVariant = TextSecondaryLight,
    outline = CardBorderLight,
    outlineVariant = Color(0xFFE6EFED),
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerRedBg,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryLight,
    onPrimary = Color(0xFF003731),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = Color(0xFFA7F3E8),
    secondary = Color(0xFF93B7D4),
    onSecondary = Color(0xFF0D2336),
    secondaryContainer = SlateNavyLight,
    onSecondaryContainer = Color(0xFFD5E6F5),
    tertiary = InterestAmberLight,
    onTertiary = Color(0xFF422006),
    tertiaryContainer = Color(0xFF533108),
    onTertiaryContainer = Color(0xFFFDE68A),
    background = SurfaceDark,
    onBackground = TextPrimaryDark,
    surface = CardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateNavySurface,
    onSurfaceVariant = TextSecondaryDark,
    outline = CardBorderDark,
    outlineVariant = Color(0xFF1D2F3D),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1515),
    onErrorContainer = Color(0xFFFECACA)
)

val LoanJBShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LoanJBTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = LoanJBTypography,
        shapes = LoanJBShapes,
        content = content
    )
}
