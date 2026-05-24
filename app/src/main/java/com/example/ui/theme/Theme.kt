package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrandBlueDark,
    secondary = BrandSecondaryDark,
    tertiary = BrandAccentDark,
    background = BrandBackgroundDark,
    surface = BrandSurfaceDark,
    onPrimary = Color(0xFF0F1216),
    onSecondary = Color(0xFF15191E),
    onTertiary = Color.White,
    onBackground = BrandOnSurfaceDark,
    onSurface = BrandOnSurfaceDark,
    outlineVariant = BrandGridDark
)

private val LightColorScheme = lightColorScheme(
    primary = BrandBlueLight,
    secondary = BrandSecondaryLight,
    tertiary = BrandAccentLight,
    background = BrandBackgroundLight,
    surface = BrandSurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = BrandOnSurfaceLight,
    onSurface = BrandOnSurfaceLight,
    outlineVariant = Color(0xFFE0E2EC)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Enforce unified corporate colors
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

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
