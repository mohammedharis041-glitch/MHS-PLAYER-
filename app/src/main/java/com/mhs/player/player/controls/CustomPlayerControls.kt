package com.mhs.player.player.controls

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mhs.player.media.model.MediaItemModel
import com.mhs.player.media.model.PlaybackState
import com.mhs.player.media.model.ResizeMode
import com.mhs.player.player.controller.PlayerController
import com.mhs.player.ui.screens.PlayerUiState
import com.mhs.player.ui.screens.PlayerViewModel
import com.mhs.player.settings.SettingsRepository
import com.mhs.player.settings.SettingsRepository.OrientationMode
import androidx.compose.ui.graphics.graphicsLayer
import com.mhs.player.ui.theme.*
import com.mhs.player.ui.theme.designsystem.AppColors
import com.mhs.player.ui.theme.designsystem.AppAnimations
import com.mhs.player.ui.theme.designsystem.AppTypography
import com.mhs.player.ui.theme.designsystem.AppShapes
import com.mhs.player.ui.theme.designsystem.GlassStyles.glassOverlay
import com.mhs.player.ui.theme.designsystem.GlassStyles.glassCard
import com.mhs.player.ui.theme.designsystem.PlayerIcons
import com.mhs.player.ui.theme.designsystem.rememberHaptics
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

import kotlinx.coroutines.flow.StateFlow

@Composable
fun CustomPlayerControls(
    viewModel: PlayerViewModel,
    currentMedia: MediaItemModel?,
    playbackStateFlow: StateFlow<PlaybackState>,
    onBack: () -> Unit,
    onPip: () -> Unit,
    onSwitchDecoder: () -> Unit = {},
    onRotate: () -> Unit = {},
    rotationLabel: String = "Auto"
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by playbackStateFlow.collectAsState()
    val isFirstFrameRendered by viewModel.playerController.isFirstFrameRendered.collectAsState()
    var showEqualizer by remember { mutableStateOf(false) }
    val settings by viewModel.settings.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim background for better control readability
        AnimatedVisibility(
            visible = uiState.isControlsVisible,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )
        }

        if (showEqualizer) {
            val currentPlayer by viewModel.playerController.player.collectAsState()
            val audioSessionId = currentPlayer?.audioSessionId ?: 0
            if (audioSessionId != 0) {
                EqualizerSheet(
                    audioSessionId = audioSessionId,
                    onDismiss = { 
                        showEqualizer = false 
                        viewModel.setSheetOpen(false)
                    },
                    viewModel = viewModel
                )
            } else {
                // Player not ready yet — show a brief toast and dismiss
                LaunchedEffect(Unit) {
                    android.widget.Toast.makeText(context, "Audio session not ready", android.widget.Toast.LENGTH_SHORT).show()
                    showEqualizer = false
                    viewModel.setSheetOpen(false)
                }
            }
        }

        // Top gradient overlay
        AnimatedVisibility(
            visible = uiState.isControlsVisible && !uiState.isLocked && !uiState.isSheetOpen,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopControlsBar(
                title = currentMedia?.title ?: "MHS Player",
                isFavorite = uiState.isFavorite,
                currentResizeMode = uiState.currentResizeMode,
                isRotationLocked = uiState.isRotationLocked,
                isDimActive = uiState.isDimActive,
                onBack = onBack,
                onFavorite = viewModel::toggleFavorite,
                onResize = viewModel::cycleResizeMode,
                onOrientation = { 
                    val nextMode = when (settings.orientationMode) {
                        OrientationMode.AUTO -> OrientationMode.LANDSCAPE
                        OrientationMode.LANDSCAPE -> OrientationMode.PORTRAIT
                        OrientationMode.PORTRAIT -> OrientationMode.SYSTEM
                        OrientationMode.SYSTEM -> OrientationMode.AUTO
                    }
                    viewModel.setOrientationMode(nextMode)
                    val modeLabel = when (nextMode) {
                        OrientationMode.AUTO -> "Auto"
                        OrientationMode.LANDSCAPE -> "Landscape"
                        OrientationMode.PORTRAIT -> "Portrait"
                        OrientationMode.SYSTEM -> "System"
                    }
                    Toast.makeText(context, "Orientation: $modeLabel", Toast.LENGTH_SHORT).show()
                },
                onEqualizer = { 
                    viewModel.setSheetOpen(true)
                    showEqualizer = true 
                },
                onPip = onPip,
                onSwitchDecoder = onSwitchDecoder,
                onMore = { viewModel.showSpeedMenu(true) },
                onInfo = { viewModel.toggleDiagnostics() },
                isDiagnosticsEnabled = uiState.isDiagnosticsEnabled,
                onNightMode = viewModel::toggleNightMode,
                onScreenshot = { viewModel.captureScreenshot(context) },
                onToggleEnhanced = { viewModel.showEnhancedSettings(true) },
                isEnhancedActive = settings.enhancedPlaybackMode,
                audioDecoder = playbackState.audioDecoderName,
                isAudioSoftware = playbackState.isAudioSoftware
            )
        }

        // Center controls (prev / play / next)
        AnimatedVisibility(
            visible = uiState.isControlsVisible && !uiState.isLocked && !uiState.isSheetOpen,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialScale = 0.85f
            ),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) + scaleOut(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                targetScale = 0.85f
            ),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CenterPlaybackControls(
                isPlaying = playbackState.isPlaying,
                isLoading = playbackState.isLoading && isFirstFrameRendered,
                onPlayPause = viewModel::togglePlayPause,
                onPrevious = viewModel::skipToPrevious,
                onNext = viewModel::skipToNext
            )
        }

        // Bottom gradient overlay + seekbar + bottom icons
        AnimatedVisibility(
            visible = uiState.isControlsVisible && !uiState.isLocked && !uiState.isSheetOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomControlsBar(
                playbackState = playbackState,
                isLocked = uiState.isLocked,
                uiState = uiState,
                settings = settings,
                onSeek = viewModel::seekTo,
                onSeekPreviewStart = viewModel::onSeekPreviewStart,
                onSeekPreviewEnd = viewModel::onSeekPreviewEnd,
                onSubtitleSettings = { viewModel.showSubtitleSettings(true) },
                onAudioSettings = { viewModel.showAudioSettings(true) },
                onLock = viewModel::toggleLock
            )
        }



        // Speed menu dialog
        if (uiState.showSpeedMenu) {
            SpeedMenuDialog(
                currentSpeed = playbackState.playbackSpeed,
                onSelect = { speed ->
                    viewModel.setPlaybackSpeed(speed)
                    viewModel.showSpeedMenu(false)
                },
                onDismiss = { viewModel.showSpeedMenu(false) }
            )
        }

        if (uiState.showEnhancedSettings) {
            EnhancedSettingsDialog(
                settings = settings,
                onToggleEnhancedPlaybackMode = { enabled ->
                    viewModel.setEnhancedPlaybackMode(enabled)
                },
                onToggleLowLatency = { enabled ->
                    viewModel.setLowLatencyMode(enabled)
                    Toast.makeText(context, "Low Latency Buffering: ${if (enabled) "ON (Requires Restart)" else "OFF"}", Toast.LENGTH_SHORT).show()
                },
                onToggleHardwareScaling = { enabled ->
                    viewModel.setHardwareScaling(enabled)
                    Toast.makeText(context, "GPU Scaling: ${if (enabled) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                },
                onToggleFasterFullscreen = { enabled ->
                    viewModel.setFasterFullscreen(enabled)
                    Toast.makeText(context, "Faster Fullscreen: ${if (enabled) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                },
                onToggleSurfaceStabilization = { enabled ->
                    viewModel.setSurfaceStabilization(enabled)
                    Toast.makeText(context, "Surface Stabilizer: ${if (enabled) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                },
                onDismiss = {
                    viewModel.showEnhancedSettings(false)
                }
            )
        }

        // Resize mode: no dialog — button cycles modes, overlay shows current
        AnimatedVisibility(
            visible = uiState.resizeModeOverlay != null,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialScale = 0.85f
            ),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) + scaleOut(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                targetScale = 0.85f
            ),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .accentGlow(color = AppColors.CyanGlow, radius = 24.dp, offsetY = 0.dp)
                    .glassCard(shape = AppShapes.RoundedMD, isPlaybackActive = true)
                    .padding(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        PlayerIcons.Resize, null,
                        tint = AppColors.CyanGlow,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        uiState.resizeModeOverlay ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }



        // Lock indicator
        if (uiState.isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
            ) {
                IconButton(
                    onClick = viewModel::toggleLock,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .size(48.dp)
                ) {
                    Icon(Icons.Default.Lock, "Unlock", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun DecoderBadge(audioDecoder: String?, isSoftware: Boolean = false) {
    if (audioDecoder == null) return
    
    val (label, color) = when {
        audioDecoder.contains("ffmpeg", ignoreCase = true) -> "FFmpeg SW" to Color(0xFFFF9800)
        isSoftware -> "SW Decoder" to Color(0xFF4CAF50)
        else -> "HW Decoder" to Color(0xFF00E5FF)
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSoftware) {
                Icon(
                    Icons.Default.Speed, null,
                    tint = color,
                    modifier = Modifier.size(10.dp).padding(end = 4.dp)
                )
            }
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun TopControlsBar(
    title: String,
    isFavorite: Boolean,
    currentResizeMode: ResizeMode,
    isRotationLocked: Boolean,
    isDimActive: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onResize: () -> Unit,
    onOrientation: () -> Unit,
    onEqualizer: () -> Unit,
    onPip: () -> Unit,
    onSwitchDecoder: () -> Unit,
    onMore: () -> Unit,
    onInfo: () -> Unit,
    isDiagnosticsEnabled: Boolean = false,
    onNightMode: () -> Unit,
    onScreenshot: () -> Unit,
    onToggleEnhanced: () -> Unit,
    isEnhancedActive: Boolean = false,
    audioDecoder: String? = null,
    isAudioSoftware: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentTime = remember {
        derivedStateOf {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }
    }

    var isExpanded by remember { mutableStateOf(false) }
    val rotationLabel = if (isRotationLocked) "Locked" else "Unlock"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp) // Extra top margin for better accessibility
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Back & Minimize & Title
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.KeyboardArrowDown, "Minimize to Miniplayer", tint = AccentCyan, modifier = Modifier.size(28.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 4.dp)
                )
                DecoderBadge(audioDecoder, isAudioSoftware)
            }

            // Right: Essential Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentTime.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(end = 8.dp)
                )

                // Resize
                IconButton(onClick = onResize) {
                    Icon(Icons.Default.AspectRatio, "Resize", tint = Color.White)
                }

                // PIP
                IconButton(onClick = onPip) {
                    Icon(Icons.Default.PictureInPicture, "PIP", tint = Color.White)
                }

                // EQ
                IconButton(onClick = onEqualizer) {
                    Icon(Icons.Default.Equalizer, "EQ", tint = Color.White)
                }


                // Expandable Arrow
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowLeft,
                        "More",
                        tint = AccentCyan
                    )
                }
            }
        }

        // Expanded secondary controls
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(top = 56.dp)
        ) {
            Row(
                modifier = Modifier
                    .glassCard(shape = AppShapes.RoundedMD, isPlaybackActive = true)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Favorite
                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (isFavorite) PlayerIcons.Favorite else PlayerIcons.FavoriteBorder,
                        null,
                        tint = if (isFavorite) FavoriteColor else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Speed
                IconButton(
                    onClick = onMore,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Speed, 
                        null, 
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Rotation Lock
                IconButton(
                    onClick = onOrientation,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (isRotationLocked) Icons.Default.ScreenLockRotation else PlayerIcons.Orientation,
                        null,
                        tint = if (isRotationLocked) AppColors.CyanGlow else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Night Dim Toggle
                IconButton(
                    onClick = onNightMode,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.DarkMode,
                        "Night Mode",
                        tint = if (isDimActive) AppColors.CyanGlow else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Screenshot
                IconButton(
                    onClick = onScreenshot,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        "Take Screenshot",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // HW+ Settings
                IconButton(
                    onClick = onToggleEnhanced,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        "HW+ Playback Settings",
                        tint = if (isEnhancedActive) AccentCyan else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Info / Diagnostics
                IconButton(
                    onClick = onInfo,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        "Playback Diagnostics",
                        tint = if (isDiagnosticsEnabled) AccentCyan else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
@Composable
private fun CenterPlaybackControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val haptics = rememberHaptics()

        // Previous
        PlayerIconButton(
            icon = PlayerIcons.SkipPrevious,
            contentDescription = "Previous",
            size = 44.dp,
            onClick = { haptics.click(); onPrevious() },
            tint = Color.White
        )

        Box(
            modifier = Modifier
                .size(64.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { haptics.heavyClick(); onPlayPause() },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = AppColors.CyanGlow,
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) PlayerIcons.Pause else PlayerIcons.Play,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .size(48.dp)
                        .accentGlow(AppColors.CyanGlow, radius = 24.dp)
                )
            }
        }

        // Next
        PlayerIconButton(
            icon = PlayerIcons.SkipNext,
            contentDescription = "Next",
            size = 44.dp,
            onClick = { haptics.click(); onNext() },
            tint = Color.White
        )
    }
}

@Composable
private fun BottomControlsBar(
    playbackState: PlaybackState,
    isLocked: Boolean,
    uiState: PlayerUiState,
    settings: com.mhs.player.settings.SettingsRepository.AppSettings,
    onSeek: (Long) -> Unit,
    onSeekPreviewStart: (Long) -> Unit,
    onSeekPreviewEnd: () -> Unit,
    onSubtitleSettings: () -> Unit,
    onAudioSettings: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    // -1L means duration is not yet known (C.TIME_UNSET for 4K/live streams)
    val durationKnown = playbackState.duration > 0L
    val duration = if (durationKnown) playbackState.duration else 0L
    val durationForSlider = if (durationKnown) playbackState.duration else 1L // avoid division by zero
    val position = playbackState.currentPosition.coerceAtLeast(0L)
    val buffer = playbackState.bufferedPosition.coerceAtLeast(0L)
    val progress = if (durationKnown) (position.toFloat() / durationForSlider.toFloat()).coerceIn(0f, 1f) else 0f
    val bufferedProgress = if (durationKnown) (buffer.toFloat() / durationForSlider.toFloat()).coerceIn(0f, 1f) else 0f

    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 20.dp)
            // Removed glassCard background
            .padding(bottom = 8.dp, top = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Time + futuristic seekbar row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = formatDuration(if (isSeeking) (seekValue * durationForSlider).toLong() else position),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = Offset(2f, 2f),
                            blurRadius = 4f
                        )
                    ),
                    color = Color.White,
                    fontSize = 11.sp
                )

                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val maxWidth = maxWidth
                    FuturisticSeekbar(
                        activeProgress = if (isSeeking) seekValue else progress.coerceIn(0f, 1f),
                        bufferedProgress = bufferedProgress,
                        duration = duration,
                        isSeeking = isSeeking,
                        onValueChange = { v ->
                            if (!isLocked) {
                                isSeeking = true
                                seekValue = v
                                onSeekPreviewStart((v * durationForSlider).toLong())
                            }
                        },
                        onValueChangeFinished = {
                            if (!isLocked) {
                                onSeek((seekValue * durationForSlider).toLong())
                                onSeekPreviewEnd()
                                isSeeking = false
                            }
                        },
                        enabled = !isLocked,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Seek Preview Popup
                    val popupWidth = 240.dp
                    val margin = 16.dp
                    val xOffset = remember(seekValue, maxWidth) {
                        val centerX = (seekValue * maxWidth.value).dp
                        val halfWidth = popupWidth / 2
                        val minX = halfWidth + margin
                        val maxX = (maxWidth - halfWidth - margin).coerceAtLeast(minX)
                        centerX.coerceIn(minX, maxX) - halfWidth
                    }

                    SeekPreviewPopup(
                        isVisible = isSeeking,
                        thumbnail = uiState.previewFrame,
                        time = "${formatDuration((seekValue * durationForSlider).toLong())} / ${if (durationKnown) formatDuration(duration) else "--:--"}",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(y = (-80).dp) // Even lower to be near progress bar
                            .offset(x = xOffset)
                    )
                }

                Text(
                    text = if (durationKnown) formatDuration(duration) else "--:--",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = Offset(2f, 2f),
                            blurRadius = 4f
                        )
                    ),
                    color = Color.White,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            // Premium glass-pill action buttons
            if (!isLocked) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PremiumPlayerButton(
                            icon = if (settings.subtitleTranslationEnabled) Icons.Default.AutoAwesome else Icons.Default.Subtitles,
                            label = if (settings.subtitleTranslationEnabled) "AI Subs" else "Subs",
                            onClick = onSubtitleSettings,
                            isActive = settings.subtitleTranslationEnabled
                        )
                        PremiumPlayerButton(
                            icon = Icons.Default.Audiotrack,
                            label = "Audio",
                            onClick = onAudioSettings
                        )
                    }
                    
                    PremiumPlayerButton(
                        icon = Icons.Default.LockOpen,
                        label = "Lock Controls",
                        onClick = onLock
                    )
                }
            }
        }
    }
}

/** Neon gradient seekbar drawn on Canvas with a pulsing glow thumb. */
@Composable
private fun FuturisticSeekbar(
    activeProgress: Float,
    bufferedProgress: Float,
    duration: Long,
    isSeeking: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val ticks = listOf(0.15f, 0.35f, 0.55f, 0.8f) // Standard cinematic chapter ticks

    // Neon gradient: electric cyan → violet → neon pink
    val gradientColors = listOf(
        AppColors.CyanGlow,
        AppColors.ElectricBlue,
        AppColors.AccentViolet,
        AppColors.AccentPink
    )
    val inactiveTrack = Color.White.copy(alpha = 0.12f)

    // Glowing breathe effect for thumb
    val infiniteTransition = rememberInfiniteTransition(label = "thumb_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Soft spring scale expansion on dragging
    val thumbScale by animateFloatAsState(
        targetValue = if (isSeeking) 1.40f else 1.0f,
        animationSpec = AppAnimations.TactileSpringSpec,
        label = "thumb_scale"
    )

    // Haptic feedback trigger on tick crossing
    var lastCrossedTick by remember { mutableStateOf(-1f) }
    LaunchedEffect(activeProgress) {
        if (isSeeking) {
            ticks.forEach { tick ->
                if (abs(activeProgress - tick) < 0.012f) {
                    if (lastCrossedTick != tick) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        lastCrossedTick = tick
                    }
                    return@LaunchedEffect
                }
            }
            lastCrossedTick = -1f
        }
    }

    Box(
        modifier = modifier.height(44.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            val trackH = 5.dp.toPx()
            val trackY = center.y - trackH / 2f
            val w = size.width
            val cr = CornerRadius(trackH / 2f)

            // ── Background Track ───────────────────────────────────────
            drawRoundRect(
                color = inactiveTrack,
                topLeft = Offset(0f, trackY),
                size = Size(w, trackH),
                cornerRadius = cr
            )

            // ── Subtle Tick Indicators ────────────────────────────────
            ticks.forEach { tick ->
                val tickX = tick * w
                drawRoundRect(
                    color = if (activeProgress >= tick) AppColors.SubtitleWhite.copy(0.6f) else Color.White.copy(0.22f),
                    topLeft = Offset(tickX - 1.dp.toPx(), trackY - trackH),
                    size = Size(2.dp.toPx(), trackH * 3.0f),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }

            // ── Buffer Track ───────────────────────────────────────────
            val bufW = w * bufferedProgress.coerceIn(0f, 1f)
            if (bufW > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.24f),
                    topLeft = Offset(0f, trackY),
                    size = Size(bufW, trackH),
                    cornerRadius = cr
                )
            }

            // ── Active Track Gradient ──────────────────────────────────
            val actW = (w * activeProgress).coerceAtLeast(0f)
            if (actW > 2f) {
                // Glow sweep underlay
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = gradientColors.map { it.copy(alpha = 0.20f) },
                        startX = 0f, endX = w
                    ),
                    topLeft = Offset(0f, trackY - trackH * 2f),
                    size = Size(actW, trackH * 5f),
                    cornerRadius = CornerRadius(trackH * 2.5f)
                )

                // Main active progress line
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = gradientColors,
                        startX = 0f, endX = w
                    ),
                    topLeft = Offset(0f, trackY),
                    size = Size(actW, trackH),
                    cornerRadius = cr
                )
            }

            // ── Premium Outer Glowed Thumb ────────────────────────────
            val thumbX = actW.coerceIn(0f, w)
            val baseRadius = 8.dp.toPx()
            val radius = baseRadius * thumbScale

            // Floating radial shadow aura
            drawCircle(
                color = AppColors.AccentViolet.copy(alpha = glowAlpha),
                radius = radius * 2.5f,
                center = Offset(thumbX, center.y)
            )

            // Solid outer white capsule ring
            drawCircle(
                color = Color.White,
                radius = radius,
                center = Offset(thumbX, center.y)
            )

            // Dynamic inner core sweep
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AppColors.CyanGlow, AppColors.AccentPink),
                    center = Offset(thumbX, center.y),
                    radius = radius * 0.52f
                ),
                radius = radius * 0.52f,
                center = Offset(thumbX, center.y)
            )
        }
        // Invisible Slider handles all touch interaction
        Slider(
            value = activeProgress,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent
            )
        )
    }
}

/** Glass-pill button with icon + label used in the player bottom bar. */
@Composable
private fun PremiumPlayerButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cornerShape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clip(cornerShape)
            .border(
                width = 1.dp,
                brush = if (isActive)
                    Brush.verticalGradient(listOf(AppColors.CyanGlow, AppColors.AccentViolet))
                else
                    Brush.verticalGradient(listOf(Color.White.copy(0.18f), Color.White.copy(0.06f))),
                shape = cornerShape
            )
            .background(
                if (isActive)
                    Brush.verticalGradient(listOf(AppColors.ElectricBlue.copy(0.35f), AppColors.MidnightCharcoal.copy(0.15f)))
                else
                    Brush.verticalGradient(listOf(Color.White.copy(0.10f), Color.White.copy(0.03f)))
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Icon(
            icon, null,
            tint = if (isActive) AppColors.CyanGlow else Color.White,
            modifier = Modifier
                .size(18.dp)
                .then(if (isActive) Modifier.accentGlow(AppColors.CyanGlow, radius = 8.dp) else Modifier)
        )
        Text(
            label,
            color = if (isActive) AppColors.CyanGlow else Color.White,
            style = AppTypography.CodecInfo.copy(
                fontWeight = FontWeight.Bold,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.8f),
                    offset = Offset(1f, 1f),
                    blurRadius = 3f
                )
            ),
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun SpeedMenuDialog(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 240.dp)
                .fillMaxWidth()
                .glassCard(shape = AppShapes.RoundedLG, isPlaybackActive = true)
                .padding(20.dp)
        ) {
            Text(
                "Playback Speed",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 250.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                speeds.forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(speed) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (speed == 1.0f) "Normal" else "${speed}x",
                            color = if (speed == currentSpeed) AppColors.CyanGlow else Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal
                        )
                        if (speed == currentSpeed) {
                            Icon(Icons.Default.Check, null, tint = AppColors.CyanGlow, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResizeMenuDialog(
    currentMode: ResizeMode,
    onSelect: (ResizeMode) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 240.dp)
                .fillMaxWidth()
                .glassCard(shape = AppShapes.RoundedLG, isPlaybackActive = true)
                .padding(20.dp)
        ) {
            Text(
                "Resize Mode",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Column {
                ResizeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = mode.label,
                            color = if (mode == currentMode) AppColors.CyanGlow else Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (mode == currentMode) FontWeight.Bold else FontWeight.Normal
                        )
                        if (mode == currentMode) {
                            Icon(Icons.Default.Check, null, tint = AppColors.CyanGlow, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(size * 0.8f)
                .accentGlow(tint, radius = 10.dp)
        )
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%d:%02d", minutes, seconds)
}

@Composable
fun TrackPickerDialog(
    title: String,
    tracks: List<PlayerController.TrackInfo>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .fillMaxWidth()
                .glassCard(shape = AppShapes.RoundedLG, isPlaybackActive = true)
                .padding(20.dp)
        ) {
            Text(
                title, 
                color = Color.White, 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (tracks.isEmpty()) {
                Text(
                    "No tracks available", 
                    color = Color.White.copy(alpha = 0.6f), 
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.CyanGlow),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    tracks.forEach { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(track.index) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = track.label,
                                color = if (track.isSelected) AppColors.CyanGlow else Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (track.isSelected) {
                                Icon(Icons.Default.Check, null, tint = AppColors.CyanGlow, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CodecInfoPanel(
    playbackState: PlaybackState,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onDismiss() }
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .glassCard(shape = AppShapes.RoundedLG, isPlaybackActive = true)
                .padding(24.dp)
                .width(300.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Codec Information", style = MaterialTheme.typography.titleMedium, color = AppColors.CyanGlow, fontWeight = FontWeight.Black)
            
            CodecInfoRow("Video Codec", playbackState.videoCodec ?: "Unknown")
            CodecInfoRow("Video Decoder", playbackState.videoDecoderName ?: "None", isSW = playbackState.isVideoSoftware)
            
            HorizontalDivider(color = Color.White.copy(0.1f))
            
            CodecInfoRow("Audio Codec", playbackState.audioCodec ?: "Unknown")
            CodecInfoRow("Audio Decoder", playbackState.audioDecoderName ?: "None", isSW = playbackState.isAudioSoftware)
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.CyanGlow)
            ) {
                Text("Close", color = Color.Black, fontWeight = FontWeight.Bold)
                }
        }
    }
}

@Composable
fun CodecInfoRow(label: String, value: String, isSW: Boolean? = null) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.6f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value.replace("audio/", "").replace("video/", ""), 
                style = MaterialTheme.typography.bodyMedium, 
                color = Color.White, 
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isSW != null) {
                Spacer(Modifier.width(8.dp))
                val badgeColor = if (isSW) Color(0xFFFF9800) else Color(0xFF4CAF50)
                Text(
                    if (isSW) "SW" else "HW",
                    modifier = Modifier
                        .background(badgeColor.copy(0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    color = badgeColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun EnhancedSettingsDialog(
    settings: com.mhs.player.settings.SettingsRepository.AppSettings,
    onToggleEnhancedPlaybackMode: (Boolean) -> Unit,
    onToggleLowLatency: (Boolean) -> Unit,
    onToggleHardwareScaling: (Boolean) -> Unit,
    onToggleFasterFullscreen: (Boolean) -> Unit,
    onToggleSurfaceStabilization: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 350.dp)
                .fillMaxWidth()
                .glassCard(shape = AppShapes.RoundedLG, isPlaybackActive = true)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = AppColors.CyanGlow,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "HW+ Playback Tweaks",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                EnhancedSettingToggleRow(
                    title = "Enhanced Playback Mode",
                    subtitle = "Force high-performance hardware decoders.",
                    checked = settings.enhancedPlaybackMode,
                    onCheckedChange = onToggleEnhancedPlaybackMode
                )
                EnhancedSettingToggleRow(
                    title = "Low Latency Buffering",
                    subtitle = "Accelerates play startup thresholds.",
                    checked = settings.lowLatencyMode,
                    onCheckedChange = onToggleLowLatency
                )
                EnhancedSettingToggleRow(
                    title = "GPU Hardware Scaling",
                    subtitle = "Offloads frame fitting to GPU scaling context.",
                    checked = settings.hardwareScaling,
                    onCheckedChange = onToggleHardwareScaling
                )
                EnhancedSettingToggleRow(
                    title = "Faster Fullscreen Rendering",
                    subtitle = "Speeds up orientation change frame updates.",
                    checked = settings.fasterFullscreen,
                    onCheckedChange = onToggleFasterFullscreen
                )
                EnhancedSettingToggleRow(
                    title = "Surface Stabilization",
                    subtitle = "Preserves surface aspect lock on zoom/pinch gestures.",
                    checked = settings.surfaceStabilization,
                    onCheckedChange = onToggleSurfaceStabilization
                )

                Spacer(Modifier.height(8.dp))

                // Close Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onDismiss() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Dismiss",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


@Composable
private fun EnhancedSettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                lineHeight = 13.sp
            )
        }
        Switch(
            checked = checked && enabled,
            onCheckedChange = if (enabled) onCheckedChange else null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = AppColors.CyanGlow,
                uncheckedTrackColor = Color.White.copy(0.1f),
                uncheckedThumbColor = Color.White.copy(0.7f)
            ),
            modifier = Modifier.graphicsLayer { scaleX = 0.8f; scaleY = 0.8f }
        )
    }
}


