package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = IndigoPrimaryContainerDark,
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = CyanSecondaryDark,
    onSecondary = Color(0xFF082F49),
    secondaryContainer = CyanSecondaryContainerDark,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = PurpleTertiaryDark,
    onTertiary = Color(0xFF2E1065),
    tertiaryContainer = PurpleTertiaryContainerDark,
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = IndigoPrimaryContainerLight,
    onPrimaryContainer = Color(0xFF312E81),
    secondary = CyanSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = CyanSecondaryContainerLight,
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = PurpleTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = PurpleTertiaryContainerLight,
    onTertiaryContainer = Color(0xFF4C1D95),
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our handcrafted rich palette by default
    content: @Composable () -> Unit,
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
        content = content
    )
}
