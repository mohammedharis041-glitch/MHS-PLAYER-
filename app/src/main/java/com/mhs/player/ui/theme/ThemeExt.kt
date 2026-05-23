package com.mhs.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mhs.player.ui.theme.designsystem.AppColors

/** True when the active Material color scheme is dark (AMOLED). */
@Composable
fun isDarkTheme(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Primary brand accent — cyan in dark mode, deep violet in light mode for contrast. */
@Composable
fun themeAccent(): Color =
    if (isDarkTheme()) AppColors.CyanGlow else Color(0xFF5046E5)

@Composable
fun themeAccentDeep(): Color =
    if (isDarkTheme()) AppColors.ElectricBlue else Color(0xFF3D36B5)

/** Text/icon color on top of [themeAccent] filled surfaces. */
@Composable
fun themeOnAccent(): Color =
    if (isDarkTheme()) Color(0xFF001A1F) else Color.White

@Composable
fun themeAccentMuted(): Color = themeAccent().copy(alpha = if (isDarkTheme()) 0.65f else 0.85f)

@Composable
fun themeAccentGradient(): Brush = Brush.linearGradient(
    colors = if (isDarkTheme()) {
        listOf(AppColors.CyanGlow, AppColors.AccentViolet)
    } else {
        listOf(Color(0xFF5046E5), Color(0xFF7B61FF))
    }
)

@Composable
fun themeOverlayScrim(): Color =
    if (isDarkTheme()) Color.Black.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f)
