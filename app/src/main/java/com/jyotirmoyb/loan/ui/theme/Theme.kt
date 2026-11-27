package com.jyotirmoyb.loan.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = SageGreenDark,
    onPrimary = Color.White,
    primaryContainer = SageContainerLight,
    onPrimaryContainer = SageGreenDark,
    secondary = TealPrimaryDark,
    onSecondary = Color.White,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = TealPrimaryDark,
    tertiary = InterestAmber,
    onTertiary = Color.White,
    tertiaryContainer = SoftAmberTile,
    onTertiaryContainer = Color(0xFF8A4209),
    background = SurfaceTintLight,
    onBackground = TextPrimaryLight,
    surface = CardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = WarmIvoryTile,
    onSurfaceVariant = TextSecondaryLight,
    outline = CardBorderLight,
    outlineVariant = Color(0xFFE6E2D6),
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerRedBg,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColors = darkColorScheme(
    primary = SageGreenLight,
    onPrimary = Color(0xFF102A11),
    primaryContainer = SageContainerDark,
    onPrimaryContainer = Color(0xFFD2ECD1),
    secondary = TealPrimaryLight,
    onSecondary = Color(0xFF003730),
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = Color(0xFFB5F5EA),
    tertiary = InterestAmberLight,
    onTertiary = Color(0xFF422006),
    tertiaryContainer = Color(0xFF4B3012),
    onTertiaryContainer = Color(0xFFFDE68A),
    background = SurfaceDark,
    onBackground = TextPrimaryDark,
    surface = CardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateNavySurface,
    onSurfaceVariant = TextSecondaryDark,
    outline = CardBorderDark,
    outlineVariant = Color(0xFF283328),
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
fun LoanJBTheme(themeMode: AppThemeMode = AppThemeMode.LIGHT, content: @Composable () -> Unit) {
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDark
                controller.isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    CompositionLocalProvider(LocalIsDarkTheme provides useDark) {
        MaterialTheme(
            colorScheme = if (useDark) DarkColors else LightColors,
            typography = LoanJBTypography,
            shapes = LoanJBShapes,
            content = content
        )
    }
}
