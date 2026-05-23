package com.mhs.player.player.controls

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.mhs.player.ui.theme.designsystem.rememberHaptics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mhs.player.ui.theme.designsystem.AppColors
import com.mhs.player.ui.theme.designsystem.AppAnimations
import kotlin.math.abs

/**
 * Premium Walkman-style animated seekbar featuring custom tick marks,
 * tactile haptic snapping, active gradient sweeps, and soft glow layers.
 */
@Composable
fun PixelSeekBar(
    position: Float,            // 0f..1f current playback progress
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,    // live preview while dragging
    onSeekEnd: (Float) -> Unit, // final value on finger-up or tap
    modifier: Modifier = Modifier,
    ticks: List<Float> = listOf(0.2f, 0.4f, 0.6f, 0.8f), // Standard chapter/subtitle ticks
    trackHeight: Dp = 6.dp,
    thumbRadius: Dp = 8.dp
) {
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val trackHeightPx = with(density) { trackHeight.toPx() }
    val thumbRadiusPx  = with(density) { thumbRadius.toPx() }

    val gradientColors = listOf(
        AppColors.CyanGlow,
        AppColors.ElectricBlue,
        AppColors.AccentViolet,
        AppColors.AccentPink
    )
    val inactiveTrack = Color.White.copy(alpha = 0.12f)

    // Thumb pulse animation while active playing
    val infiniteTransition = rememberInfiniteTransition(label = "breathingPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(1000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val displayed = if (isDragging) dragValue else position.coerceIn(0f, 1f)

    // Butter smooth thumb scaling via customized springs
    val thumbScale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.40f
            isPlaying -> pulse
            else -> 1.0f
        },
        animationSpec = AppAnimations.TactileSpringSpec,
        label = "seekThumbScale"
    )

    // Track tick-crossing to trigger haptic feedback
    var lastCrossedTick by remember { mutableStateOf(-1f) }

    LaunchedEffect(displayed) {
        if (isDragging) {
            ticks.forEach { tick ->
                if (abs(displayed - tick) < 0.012f) {
                    if (lastCrossedTick != tick) {
                        haptics.tick()
                        lastCrossedTick = tick
                    }
                    return@LaunchedEffect
                }
            }
            lastCrossedTick = -1f
        }
    }

    Box(
        modifier = modifier.height(36.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek(dragValue)
                            haptics.heavyClick()
                        },
                        onHorizontalDrag = { change, _ ->
                            dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                            onSeek(dragValue)
                        },
                        onDragEnd = { 
                            onSeekEnd(dragValue)
                            isDragging = false 
                        },
                        onDragCancel = { isDragging = false }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekEnd(frac)
                        haptics.click()
                    }
                }
        ) {
            val cy = size.height / 2f
            val activeW = displayed * size.width

            // ── Inactive Track background ──────────────────────────────
            drawRoundRect(
                color = inactiveTrack,
                topLeft = Offset(0f, cy - trackHeightPx / 2f),
                size = Size(size.width, trackHeightPx),
                cornerRadius = CornerRadius(trackHeightPx / 2f)
            )

            // ── Subtle Tick Indicators ────────────────────────────────
            ticks.forEach { tick ->
                val tickX = tick * size.width
                // Draw a vertical subtle pill
                drawRoundRect(
                    color = if (displayed >= tick) AppColors.SubtitleWhite.copy(0.6f) else Color.White.copy(0.25f),
                    topLeft = Offset(tickX - 1.dp.toPx(), cy - (trackHeightPx * 1.5f)),
                    size = Size(2.dp.toPx(), trackHeightPx * 3.0f),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }

            if (activeW > 0f) {
                // ── Radial Backing Glow ───────────────────────────────
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = gradientColors.map { it.copy(alpha = 0.22f) },
                        startX = 0f,
                        endX = size.width
                    ),
                    topLeft = Offset(0f, cy - trackHeightPx * 2.5f),
                    size = Size(activeW, trackHeightPx * 5.0f),
                    cornerRadius = CornerRadius(trackHeightPx * 2.5f)
                )

                // ── Premium Active Gradient Track ─────────────────────
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = gradientColors,
                        startX = 0f,
                        endX = size.width
                    ),
                    topLeft = Offset(0f, cy - trackHeightPx / 2f),
                    size = Size(activeW, trackHeightPx),
                    cornerRadius = CornerRadius(trackHeightPx / 2f)
                )
            }

            // ── Premium Outer Glowed Thumb ────────────────────────────
            val thumbX = displayed * size.width
            val radius = thumbRadiusPx * thumbScale

            // Multi-layered radial glowing aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AppColors.AccentViolet.copy(alpha = 0.5f), Color.Transparent),
                    center = Offset(thumbX, cy),
                    radius = radius * 2.5f
                ),
                radius = radius * 2.5f,
                center = Offset(thumbX, cy)
            )

            // Solid Outer White Ring
            drawCircle(
                color = Color.White,
                radius = radius,
                center = Offset(thumbX, cy)
            )

            // Vibrant Cyan-to-Pink gradient inner core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AppColors.CyanGlow, AppColors.AccentPink),
                    center = Offset(thumbX, cy),
                    radius = radius * 0.5f
                ),
                radius = radius * 0.5f,
                center = Offset(thumbX, cy)
            )
        }
    }
}
