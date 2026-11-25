package com.jyotirmoyb.loan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = TealPrimaryDark,
    secondary = SageGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EFE3),
    onSecondaryContainer = SageGreenDark,
    tertiary = InterestAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF0E1),
    onTertiaryContainer = Color(0xFF7D3C08),
    background = SurfaceTintLight,
    onBackground = TextPrimaryLight,
    surface = CardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFEDF5F3),
    onSurfaceVariant = TextSecondaryLight,
    outline = CardBorderLight,
    outlineVariant = Color(0xFFEAF2F0),
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerRedBg,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryLight,
    onPrimary = Color(0xFF003830),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = Color(0xFFB3F7EC),
    secondary = SageGreenLight,
    onSecondary = Color(0xFF153315),
    secondaryContainer = Color(0xFF223B22),
    onSecondaryContainer = Color(0xFFCDE7CC),
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
    outlineVariant = Color(0xFF1E2D42),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1515),
    onErrorContainer = Color(0xFFFECACA)
)

val LoanJBShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
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
