package com.mhs.player.ui.theme.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GlassStyles {
    /**
     * A performance-safe modifier to apply a frosted glass card appearance.
     * During active video playback (where high-blur GPU rendering can cause frames to drop),
     * we bypass expensive drawing in favor of translucent cinematic overlays.
     */
    fun Modifier.glassCard(
        shape: Shape = AppShapes.RoundedMD,
        isPlaybackActive: Boolean = false,
        borderWidth: Dp = 1.dp
    ): Modifier {
        return if (isPlaybackActive) {
            // Performance-safe mode
            this
                .clip(shape)
                .background(Color(0x59060609)) // Highly translucent deep cinematic black (35% opacity)
                .border(
                    width = borderWidth,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x33FFFFFF),
                            Color(0x12FFFFFF)
                        )
                    ),
                    shape = shape
                )
        } else {
            // High-fidelity immersive frosted glass overlay
            this
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x24FFFFFF), // Sleek frosted highlight
                            Color(0x0AFFFFFF)  // Semi-transparent base
                        )
                    )
                )
                .background(Color(0x120F0F1A)) // Tinted overlay
                .border(
                    width = borderWidth,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x40FFFFFF),
                            Color(0x0FFFFFFF),
                            Color(0x267B61FF) // Subtle neon violet edge reflections
                        )
                    ),
                    shape = shape
                )
        }
    }

    /**
     * Highly transparent background for overlays (e.g. gesture indicators, bottom sheets)
     * which retains user visibility into the live coordinates behind the panels.
     */
    fun Modifier.glassOverlay(
        shape: Shape = AppShapes.RoundedLG,
        isPlaybackActive: Boolean = false
    ): Modifier {
        return if (isPlaybackActive) {
            this
                .clip(shape)
                .background(Color(0x59060609)) // Highly translucent 35% dark tinting overlay
                .border(
                    width = 1.dp,
                    color = Color(0x26FFFFFF),
                    shape = shape
                )
        } else {
            this
                .clip(shape)
                .background(Color(0xCC0F0F1A)) // Warm dark glass overlay
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x33FFFFFF),
                            Color(0x1F7B61FF)
                        )
                    ),
                    shape = shape
                )
        }
    }
}
