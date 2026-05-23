package com.mhs.player.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp)
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val base = MaterialTheme.colorScheme.onSurface
    val shimmerColors = if (isDark) {
        listOf(
            base.copy(alpha = 0.05f),
            base.copy(alpha = 0.15f),
            base.copy(alpha = 0.05f),
        )
    } else {
        listOf(
            base.copy(alpha = 0.06f),
            base.copy(alpha = 0.14f),
            base.copy(alpha = 0.06f),
        )
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translation"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun MediaCardShimmer() {
    Column(modifier = Modifier.width(160.dp)) {
        ShimmerPlaceholder(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )
        Spacer(Modifier.height(8.dp))
        ShimmerPlaceholder(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(16.dp)
        )
        Spacer(Modifier.height(4.dp))
        ShimmerPlaceholder(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(12.dp)
        )
    }
}
