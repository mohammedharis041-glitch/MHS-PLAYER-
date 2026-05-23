package com.mhs.player.player.audio

import android.media.audiofx.Visualizer
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.mhs.player.ui.theme.Primary
import com.mhs.player.ui.theme.Secondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun AudioVisualizer(
    audioSessionId: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = Primary,
    barCount: Int = 32
) {
    var waveform by remember { mutableStateOf(ByteArray(barCount)) }
    DisposableEffect(audioSessionId) {
        val viz = if (audioSessionId != 0) {
            try {
                Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1]
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                v: Visualizer?,
                                waveformData: ByteArray?,
                                samplingRate: Int
                            ) {
                                if (waveformData != null) {
                                    val sampled = ByteArray(barCount)
                                    val step = waveformData.size / barCount
                                    for (i in 0 until barCount) {
                                        sampled[i] = waveformData[i * step]
                                    }
                                    waveform = sampled
                                }
                            }
                            override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {}
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        true,
                        false
                    )
                    enabled = true
                }
            } catch (e: Exception) {
                null
            }
        } else null

        onDispose {
            viz?.enabled = false
            viz?.release()
        }
    }

    // Animated idle bars when not playing
    val infiniteTransition = rememberInfiniteTransition(label = "idle")
    val animatedPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier.fillMaxWidth().height(80.dp)) {
        val barWidth = (size.width / barCount) * 0.6f
        val barSpacing = size.width / barCount
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val barHeight = if (isPlaying && waveform.isNotEmpty()) {
                val sample = (waveform[i].toInt() and 0xFF) / 255f
                (sample * size.height * 0.8f).coerceAtLeast(4f)
            } else {
                // Idle animation
                val phase = (animatedPhase + i * (360f / barCount)) % 360f
                val radians = Math.toRadians(phase.toDouble()).toFloat()
                (kotlin.math.sin(radians) * 0.3f + 0.15f) * size.height
            }

            val x = i * barSpacing + barSpacing / 2f
            val alpha = if (isPlaying) 1f else 0.4f

            drawLine(
                color = barColor.copy(alpha = alpha),
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
