package com.example.ui.theme

import android.app.Activity
import android.os.Build
import android.util.DisplayMetrics
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.ThemeMode

private val FloatXLightColorScheme = lightColorScheme(
    primary = FloatXCobaltLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = FloatXIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF312E81),
    tertiary = WindowFreeformGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF065F46),
    background = LightCanvas,
    onBackground = LightInkPrimary,
    surface = LightSurface,
    onSurface = LightInkPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightInkSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurfaceElevated,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineSubtle,
    error = WindowCloseRed,
    onError = Color.White
)

private val FloatXDarkColorScheme = darkColorScheme(
    primary = FloatXCobaltDark,
    onPrimary = Color(0xFF071936),
    primaryContainer = Color(0xFF172E54),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF131642),
    secondaryContainer = Color(0xFF25295C),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = WindowFreeformGreen,
    onTertiary = Color(0xFF032B1D),
    tertiaryContainer = Color(0xFF0B3B2B),
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = DarkCanvas,
    onBackground = DarkInkPrimary,
    surface = DarkSurface,
    onSurface = DarkInkPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkSecondary,
    surfaceContainerLowest = Color(0xFF06080E),
    surfaceContainerLow = Color(0xFF0F141F),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceElevated,
    outline = DarkOutline,
    outlineVariant = DarkOutlineSubtle,
    error = WindowCloseRed,
    onError = Color.White
)

val FloatXShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun FloatXTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (useDarkTheme) FloatXDarkColorScheme else FloatXLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    // Lock density and fontScale = 1.0f so even if phone font or display size is 3.0x, the app stays fixed
    val currentDensity = LocalDensity.current
    val fixedDensity = remember(currentDensity.density) {
        val stableDpi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            DisplayMetrics.DENSITY_DEVICE_STABLE
        } else {
            0
        }
        val lockedScale = if (stableDpi > 0) {
            stableDpi / 160f
        } else {
            currentDensity.density.coerceIn(1.5f, 2.75f)
        }
        Density(density = lockedScale, fontScale = 1.0f)
    }

    CompositionLocalProvider(LocalDensity provides fixedDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FloatXTypography,
            shapes = FloatXShapes,
            content = content
        )
    }
}
