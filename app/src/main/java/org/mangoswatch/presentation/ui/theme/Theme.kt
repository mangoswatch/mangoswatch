package org.mangoswatch.presentation.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CyberPrimaryDark,
    onPrimary = CyberOnPrimaryDark,
    primaryContainer = CyberPrimaryContainerDark,
    onPrimaryContainer = CyberOnPrimaryContainerDark,
    secondary = CyberSecondaryDark,
    onSecondary = CyberOnSecondaryDark,
    secondaryContainer = CyberSecondaryContainerDark,
    onSecondaryContainer = CyberOnSecondaryContainerDark,
    tertiary = CyberTertiaryDark,
    background = CyberBackgroundDark,
    surface = CyberSurfaceDark,
    onSurface = CyberOnSurfaceDark,
    surfaceVariant = CyberSurfaceVariantDark,
    onSurfaceVariant = CyberOnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = CyberPrimaryLight,
    onPrimary = CyberOnPrimaryLight,
    primaryContainer = CyberPrimaryContainerLight,
    onPrimaryContainer = CyberOnPrimaryContainerLight,
    secondary = CyberSecondaryLight,
    onSecondary = CyberOnSecondaryLight,
    secondaryContainer = CyberSecondaryContainerLight,
    onSecondaryContainer = CyberOnSecondaryContainerLight,
    tertiary = CyberTertiaryLight,
    background = CyberBackgroundLight,
    surface = CyberSurfaceLight,
    onSurface = CyberOnSurfaceLight,
    surfaceVariant = CyberSurfaceVariantLight,
    onSurfaceVariant = CyberOnSurfaceVariantLight
)

// Sharp, tactical terminal shapes (eliminating soft/playful curves)
val CyberShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(6.dp)
)

@Composable
fun MangosWatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = CyberShapes,
        content = content
    )
}
