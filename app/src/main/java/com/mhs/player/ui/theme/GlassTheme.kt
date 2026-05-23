package com.mhs.player.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─── Glass palette ─────────────────────────────────────────────────────────────
val GlassWhite         = Color.White.copy(alpha = 0.10f)
val GlassBorder        = Color.White.copy(alpha = 0.25f)
val GlassHighlight     = Color.White.copy(alpha = 0.40f)
val GlassDeep          = Color(0xFF0A0A1E).copy(alpha = 0.70f)
val GlassButtonBg      = Color.White.copy(alpha = 0.13f)
val GlassButtonBorder  = Color.White.copy(alpha = 0.28f)

// Accent gradient colors (matches PixelSeekBar)
val AccentCyan    = Color(0xFF00E5FF)
val AccentViolet  = Color(0xFF7B61FF)
val AccentMagenta = Color(0xFFFF61D2)

val accentGradient = Brush.linearGradient(
    listOf(AccentCyan, AccentViolet, AccentMagenta)
)

// ─── Modifier extensions ────────────────────────────────────────────────────────

/**
 * Applies a frosted-glass surface effect:
 * semi-transparent white fill + gradient top-lit border + inner highlight line.
 */
@Composable
fun Modifier.glassCard(
    cornerRadius: Dp = 16.dp, // Reduced default for sharper look
    fillAlpha: Float = 0.05f,
    borderAlpha: Float = 0.12f, // Reduced default
    useDarkBg: Boolean = true
): Modifier {
    val isDark = isSystemInDarkTheme()
    val baseColor = if (isDark) {
        if (useDarkBg) Color(0xFF0F0F16) else Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val actualCornerRadius = if (isDark) cornerRadius else cornerRadius * 0.8f // Sharper corners in light mode
    
    return this
        .clip(RoundedCornerShape(actualCornerRadius))
        .background(
            if (isDark) {
                Brush.verticalGradient(
                    colors = listOf(
                        baseColor.copy(alpha = fillAlpha * 1.5f),
                        baseColor.copy(alpha = fillAlpha)
                    ),
                    startY = 0f,
                    endY = 500f
                )
            } else {
                // Flatter surface for light mode
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            }
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    baseColor.copy(alpha = borderAlpha),
                    baseColor.copy(alpha = borderAlpha * 0.5f)
                ),
                startY = 0f,
                endY = 500f
            ),
            shape = RoundedCornerShape(actualCornerRadius)
        )
}

/**
 * Circular glass button surface — white semi-transparent fill + rim border.
 */
@Composable
fun Modifier.glassButton(
    cornerRadius: Dp = 50.dp,
    fillAlpha: Float = 0.08f
): Modifier {
    val isDark = isSystemInDarkTheme()
    val baseColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(
            if (isDark) baseColor.copy(alpha = fillAlpha)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    baseColor.copy(0.25f),
                    baseColor.copy(0.08f)
                ),
                startY = 0f,
                endY = 100f
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
}

/**
 * Adds a colorful accent glow shadow behind an element.
 */
@Composable
fun Modifier.accentGlow(
    color: Color = AccentViolet,
    radius: Dp = 12.dp, // Reduced default for sharper look
    offsetY: Dp = 3.dp
): Modifier {
    val isDark = isSystemInDarkTheme()
    val actualRadius = if (isDark) radius else radius * 0.5f // Less blur in light mode
    val actualAlpha = if (isDark) 0.55f else 0.25f // Less intense in light mode
    
    return this.drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint().also {
                it.asFrameworkPaint().apply {
                    isAntiAlias = true
                    this.color  = android.graphics.Color.TRANSPARENT
                    setShadowLayer(actualRadius.toPx(), 0f, offsetY.toPx(), color.copy(actualAlpha).toArgb())
                }
            }
            canvas.drawRoundRect(0f, 0f, size.width, size.height, actualRadius.toPx() / 2, actualRadius.toPx() / 2, paint)
        }
    }
}
