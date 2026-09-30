package com.mhs.player.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AmoledDarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = OnError,
    outline = Outline,
    scrim = Scrim,
)

private val MHSLightColorScheme = lightColorScheme(
    primary = Color(0xFF5046E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8E4FF),
    onPrimaryContainer = Color(0xFF2D2868),
    secondary = Color(0xFF00796B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF004D40),
    tertiary = Color(0xFFE65100),
    onTertiary = Color(0xFFFFFFFF),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    outline = LightOutline,
    scrim = LightScrim,
)

@Composable
fun MHSPlayerTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    themePresetId: String = "AMOLED",
    accentColorHex: String = "#5046E5",
    content: @Composable () -> Unit
) {
    val preset = ThemePresets.getById(themePresetId)
    val accent = AccentColors.parse(accentColorHex)
    
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.2f),
            onPrimaryContainer = accent,
            secondary = accent.copy(alpha = 0.8f),
            onSecondary = Color.White,
            secondaryContainer = accent.copy(alpha = 0.15f),
            onSecondaryContainer = Color.White,
            tertiary = accent,
            onTertiary = Color.White,
            background = preset.background,
            onBackground = Color.White,
            surface = preset.surface,
            onSurface = Color.White,
            surfaceVariant = preset.surfaceVariant,
            onSurfaceVariant = Color(0xFFB0B0B0),
            error = Error,
            onError = OnError,
            outline = Outline,
            scrim = Scrim,
        )
        else -> MHSLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MHSTypography,
        shapes = MHSShapes,
        content = content
    )
}
