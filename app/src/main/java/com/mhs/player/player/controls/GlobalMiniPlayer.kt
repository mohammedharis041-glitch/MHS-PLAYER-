package com.mhs.player.player.controls

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mhs.player.ui.screens.PlayerViewModel
import com.mhs.player.ui.theme.designsystem.AppAnimations
import com.mhs.player.ui.theme.designsystem.AppColors
import com.mhs.player.ui.theme.designsystem.AppShapes
import com.mhs.player.ui.theme.designsystem.AppTypography
import com.mhs.player.ui.theme.designsystem.GlassStyles.glassCard
import com.mhs.player.ui.theme.designsystem.rememberHaptics
import kotlin.math.absoluteValue

@OptIn(UnstableApi::class)
@Composable
fun GlobalMiniPlayer(
    viewModel: PlayerViewModel = hiltViewModel(),
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMedia by viewModel.currentMedia.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val player by viewModel.playerController.player.collectAsStateWithLifecycle()

    val isVisible = currentMedia != null

    // Horizontal drag for swipe-to-dismiss gesture
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    var verticalDrag by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    val haptics = rememberHaptics()

    // Animated translation for swipe rubber-band feedback
    val dragOffsetX by animateFloatAsState(
        targetValue = if (isDismissing) horizontalDrag else horizontalDrag * 0.4f,
        animationSpec = if (isDismissing)
            tween(200)
        else
            spring(stiffness = Spring.StiffnessMediumLow),
        label = "mini_player_drag_x"
    )

    // Fading opacity as user swipes away
    val contentAlpha by animateFloatAsState(
        targetValue = if (horizontalDrag.absoluteValue > 60f)
            (1f - (horizontalDrag.absoluteValue - 60f) / 160f).coerceIn(0f, 1f)
        else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "mini_player_alpha"
    )

    // Play button scale pulse
    val playBtnScale by animateFloatAsState(
        targetValue = if (playbackState.isPlaying) 1.08f else 1.0f,
        animationSpec = AppAnimations.TactileSpringSpec,
        label = "play_btn_scale"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(350)) +
                fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(280)) +
               fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .graphicsLayer {
                    translationX = dragOffsetX
                    alpha = contentAlpha
                }
        ) {
            // ── Pill-shaped glass card ─────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = AppShapes.RoundedXL, isPlaybackActive = false)
                    // Horizontal swipe gesture (dismiss) with slight rubberbanding
                    .draggable(
                        state = rememberDraggableState { delta ->
                            horizontalDrag += delta
                        },
                        orientation = Orientation.Horizontal,
                        onDragStarted = {
                            horizontalDrag = 0f
                            isDismissing = false
                        },
                        onDragStopped = { velocity ->
                            if (horizontalDrag.absoluteValue > 160f || velocity.absoluteValue > 600f) {
                                haptics.heavyClick()
                                isDismissing = true
                                viewModel.closeMiniPlayer()
                            } else {
                                horizontalDrag = 0f
                            }
                            isDismissing = false
                        }
                    )
                    // Vertical swipe up → open player
                    .draggable(
                        state = rememberDraggableState { delta ->
                            verticalDrag += delta
                        },
                        orientation = Orientation.Vertical,
                        onDragStarted = { verticalDrag = 0f },
                        onDragStopped = { velocity ->
                            if (verticalDrag < -80f || velocity < -300f) {
                                haptics.click()
                                onNavigateToPlayer()
                            }
                            verticalDrag = 0f
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onNavigateToPlayer
                    )
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ── Album art thumbnail ───────────────────────
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(AppShapes.RoundedSM)
                                .background(AppColors.MidnightCharcoal)
                                .border(
                                    1.dp,
                                    AppColors.CyanGlow.copy(alpha = 0.25f),
                                    AppShapes.RoundedSM
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentMedia?.isVideo == true) {
                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { ctx ->
                                        android.view.SurfaceView(ctx).also {
                                            viewModel.playerController.setVideoSurfaceView(it)
                                        }
                                    },
                                    onRelease = { view ->
                                        viewModel.playerController.releaseVideoSurfaceView(view)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val context = LocalContext.current
                                val artData = remember(currentMedia) {
                                    currentMedia?.albumArtUri
                                        ?: currentMedia?.let {
                                            com.mhs.player.media.scanner.AudioArtFetcher.AudioArtKey(it.uri)
                                        }
                                }
                                if (artData != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(artData)
                                            .size(176, 176)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Media Artwork",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = AppColors.CyanGlow.copy(alpha = 0.6f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        // ── Track info ────────────────────────────────
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = currentMedia?.title ?: "Unknown",
                                style = AppTypography.StandardTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = AppColors.OnBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentMedia?.artist ?: "Unknown Artist",
                                style = AppTypography.StandardTypography.bodySmall,
                                color = AppColors.OnSurfaceDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        // ── Play / Pause ──────────────────────────────
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .scale(playBtnScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            AppColors.CyanGlow.copy(alpha = 0.30f),
                                            AppColors.ElectricBlue.copy(alpha = 0.12f)
                                        )
                                    )
                                )
                                .border(
                                    1.dp,
                                    AppColors.CyanGlow.copy(alpha = 0.50f),
                                    CircleShape
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { haptics.heavyClick(); viewModel.togglePlayPause() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // ── Skip Next ─────────────────────────────────
                        IconButton(
                            onClick = { haptics.click(); viewModel.skipToNext() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = AppColors.OnSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        // ── Close ─────────────────────────────────────
                        IconButton(
                            onClick = { haptics.tick(); viewModel.closeMiniPlayer() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = AppColors.OnSurfaceDim,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // ── Gradient progress bar at bottom ───────────────
                    val progress = if (playbackState.duration > 0) {
                        (playbackState.currentPosition.toFloat() / playbackState.duration.toFloat())
                            .coerceIn(0f, 1f)
                    } else 0f

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color.White.copy(alpha = 0.06f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(AppColors.CyanBlueGradient)
                        )
                    }
                }
            }

            // ── Swipe-hint drag indicator ──────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .size(width = 32.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
            )
        }
    }
}
