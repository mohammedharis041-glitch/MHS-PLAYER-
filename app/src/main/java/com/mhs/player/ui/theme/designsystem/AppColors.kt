package com.mhs.player.ui.theme.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

object AppColors {
    // Cinematic Theme - Dark / AMOLED
    val CinematicBlack = Color(0xFF060609)
    val MidnightCharcoal = Color(0xFF101016)
    val GlassOverlayBase = Color(0xFF0F0F1A)
    
    // Core brand accents
    val ElectricBlue = Color(0xFF2979FF)
    val CyanGlow = Color(0xFF00E5FF)
    val AccentViolet = Color(0xFF7B61FF)
    val AccentPink = Color(0xFFFF2D78)
    val AccentTeal = Color(0xFF03DAC6)
    val AccentAmber = Color(0xFFFFB74D)
    
    // Semantic Colors
    val Error = Color(0xFFCF6679)
    val OnError = Color(0xFF690020)
    val Success = Color(0xFF4CAF50)
    
    // Text and Subtitles
    val SubtitleWhite = Color(0xFFFBFBF6)
    val SubtitleTextShadow = Color(0xCC000000)
    val OnBackground = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFFE6E1E5)
    val OnSurfaceVariant = Color(0xFFCAC4D0)
    val OnSurfaceDim = Color(0xFF938F99)
    
    // Gradient stops (cinematic background)
    val BackgroundGradientStart = Color(0xFF070712)
    val BackgroundGradientEnd = Color(0xFF000000)
    
    val PrimaryAccentGradient = Brush.linearGradient(
        listOf(CyanGlow, AccentViolet, AccentPink)
    )
    
    val CyanBlueGradient = Brush.linearGradient(
        listOf(CyanGlow, ElectricBlue)
    )

    val VioletPinkGradient = Brush.linearGradient(
        listOf(AccentViolet, AccentPink)
    )
}
