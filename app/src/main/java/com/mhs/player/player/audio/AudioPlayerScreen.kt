package com.mhs.player.player.audio

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.media.AudioManager
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.mhs.player.media.model.MediaItemModel
import com.mhs.player.ui.screens.MediaViewModel
import com.mhs.player.ui.screens.PlayerViewModel
import com.mhs.player.ui.theme.accentGlow
import com.mhs.player.ui.theme.designsystem.AppColors
import com.mhs.player.ui.theme.designsystem.AppShapes
import com.mhs.player.ui.theme.designsystem.AppAnimations
import com.mhs.player.ui.theme.designsystem.AppTypography
import com.mhs.player.ui.theme.designsystem.GlassStyles.glassCard as dsGlassCard
import com.mhs.player.ui.theme.designsystem.rememberHaptics
import android.app.Activity
import com.mhs.player.player.controls.EqualizerSheet
import com.mhs.player.player.controls.PixelSeekBar

@Composable
fun AudioPlayerScreen(
    mediaId: Long,
    queueIndex: Int,
    navController: NavController,
    viewModel: PlayerViewModel = hiltViewModel(),
    mediaViewModel: MediaViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentMedia by viewModel.currentMedia.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentQueueIndex.collectAsStateWithLifecycle()

    var showQueue by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredQueue = remember(queue, searchQuery) {
        if (searchQuery.isBlank()) queue
        else queue.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    // Lock to portrait for audio player
    LaunchedEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    // Load audio
    val mediaUiState by mediaViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(mediaId, queueIndex, mediaUiState.filteredAudios) {
        val audios = mediaUiState.filteredAudios
        if (audios.isNotEmpty()) {
            val safeIndex = queueIndex.coerceIn(0, audios.size - 1)
            val targetItem = audios.find { it.id == mediaId } ?: audios.getOrNull(safeIndex)
            if (targetItem != null) {
                val actualIndex = audios.indexOf(targetItem).coerceAtLeast(0)
                val currentPlayingId = viewModel.currentMedia.value?.id
                if (currentPlayingId != targetItem.id) {
                    viewModel.openMedia(targetItem, audios, actualIndex)
                }
            }
        }
    }

    BackHandler {
        navController.popBackStack()
    }

    // ── Gesture State ────────────────────────────────────────────────
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volumeLevel by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }
    var isDraggingVolume by remember { mutableStateOf(false) }
    var isDraggingHorizontal by remember { mutableStateOf(false) }
    var cumulativeX by remember { mutableFloatStateOf(0f) }
    var cumulativeY by remember { mutableFloatStateOf(0f) }
    var skipText by remember { mutableStateOf("") }

    val haptics = rememberHaptics()

    val smoothVolume by animateFloatAsState(
        targetValue = volumeLevel,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "volume_smooth"
    )

    // ── Walkman disc spring scale animation ─────────────────────────
    val discScale by animateFloatAsState(
        targetValue = if (playbackState.isPlaying) 1.08f else 1.0f,
        animationSpec = AppAnimations.TactileSpringSpec,
        label = "disc_scale"
    )

    val gestureModifier = if (!showQueue && !showEqualizer && !showSearch) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    cumulativeX = 0f
                    cumulativeY = 0f
                    isDraggingVolume = false
                    isDraggingHorizontal = false
                    volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                },
                onDragEnd = {
                    if (isDraggingHorizontal) {
                        if (cumulativeX > 250f) viewModel.skipToNext()
                        else if (cumulativeX < -250f) viewModel.skipToPrevious()
                    }
                    isDraggingVolume = false
                    isDraggingHorizontal = false
                    skipText = ""
                },
                onDragCancel = {
                    isDraggingVolume = false
                    isDraggingHorizontal = false
                    skipText = ""
                }
            ) { change, dragAmount ->
                cumulativeX += dragAmount.x
                cumulativeY += dragAmount.y

                if (!isDraggingVolume && !isDraggingHorizontal) {
                    val absX = kotlin.math.abs(cumulativeX)
                    val absY = kotlin.math.abs(cumulativeY)
                    if (absY > 50f && absY > absX * 2f) {
                        isDraggingVolume = true
                    } else if (absX > 50f && absX > absY * 2f) {
                        isDraggingHorizontal = true
                    }
                }

                if (isDraggingVolume) {
                    val sensitivity = 0.0015f
                    val newVolFloat = (volumeLevel - (dragAmount.y * sensitivity)).coerceIn(0f, 1f)
                    volumeLevel = newVolFloat
                    val newVolInt = (newVolFloat * maxVolume).toInt()
                    if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != newVolInt) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolInt, 0)
                    }
                    change.consume()
                } else if (isDraggingHorizontal) {
                    skipText = when {
                        cumulativeX > 250f  -> "Next Track"
                        cumulativeX < -250f -> "Previous Track"
                        else                -> "Slide to skip"
                    }
                    change.consume()
                }
            }
        }
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(gestureModifier)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AppColors.BackgroundGradientStart,
                        AppColors.MidnightCharcoal,
                        AppColors.BackgroundGradientEnd
                    ),
                    startY = 0f,
                    endY = 2200f
                )
            )
    ) {
        // ── Equalizer sheet ──────────────────────────────────────────
        if (showEqualizer) {
            val audioSessionId = viewModel.playerController.player.value?.audioSessionId ?: 0
            EqualizerSheet(
                audioSessionId = audioSessionId,
                onDismiss = { showEqualizer = false },
                viewModel = viewModel
            )
        }

        if (showQueue) {
            QueuePanel(
                queue = filteredQueue,
                currentIndex = currentIndex,
                onItemClick = { index ->
                    val item = filteredQueue.getOrNull(index)
                    if (item != null) {
                        val originalIndex = queue.indexOf(item).coerceAtLeast(0)
                        viewModel.playerController.queueManager.jumpTo(originalIndex)
                        viewModel.playerController.play(item)
                    }
                },
                onClose = { showQueue = false }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(48.dp))

                // ── Top bar ──────────────────────────────────────────
                if (showSearch) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            showSearch = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                        }
                        com.mhs.player.ui.screens.SearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.KeyboardArrowDown, "Back", tint = Color.White)
                        }
                        Text(
                            "Now Playing",
                            style = AppTypography.StandardTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = AppColors.OnSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        Row {
                            IconButton(onClick = { showSearch = true }) {
                                Icon(Icons.Default.Search, "Search", tint = AppColors.OnSurfaceVariant)
                            }
                            IconButton(onClick = { showQueue = true }) {
                                Icon(Icons.Default.PlaylistPlay, "Playlist", tint = AppColors.OnSurfaceVariant)
                            }
                            IconButton(onClick = { showEqualizer = true }) {
                                Icon(Icons.Default.Equalizer, "Equalizer", tint = AppColors.CyanGlow)
                            }
                        }
                    }
                }

                if (showSearch) {
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(filteredQueue) { index, item ->
                            val originalIndex = queue.indexOf(item).coerceAtLeast(0)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (originalIndex == currentIndex)
                                            AppColors.CyanGlow.copy(alpha = 0.12f)
                                        else Color.Transparent,
                                        AppShapes.RoundedMD
                                    )
                                    .clickable {
                                        viewModel.playerController.queueManager.jumpTo(originalIndex)
                                        viewModel.playerController.play(item)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (originalIndex == currentIndex) {
                                    Icon(Icons.Default.VolumeUp, null,
                                        tint = AppColors.CyanGlow, modifier = Modifier.size(24.dp))
                                } else {
                                    Icon(Icons.Default.MusicNote, null,
                                        tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.title,
                                        style = AppTypography.StandardTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (originalIndex == currentIndex) AppColors.CyanGlow else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.artist,
                                        style = AppTypography.StandardTypography.bodySmall,
                                        color = AppColors.OnSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                } else {
                    Spacer(Modifier.height(32.dp))

                    // ── Walkman rotating disc — battery-safe ──────────
                    // Only consumes GPU when actually playing; freezes when paused
                    val discRotation = remember { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(playbackState.isPlaying) {
                        if (playbackState.isPlaying) {
                            // Resume from where we stopped, complete a full 360° cycle slowly
                            discRotation.animateTo(
                                targetValue = discRotation.value + 360f,
                                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                    animation = tween(20000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                )
                            )
                        }
                        // When isPlaying = false, we simply don't call animate → disc freezes
                    }

                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .scale(discScale)  // Spring-driven Walkman disc pulse
                            .accentGlow(color = AppColors.CyanGlow, radius = 36.dp, offsetY = 14.dp)
                            .clip(CircleShape)
                            .border(
                                width = 3.dp,
                                brush = Brush.sweepGradient(
                                    listOf(
                                        AppColors.CyanGlow.copy(alpha = 0.8f),
                                        AppColors.AccentViolet.copy(alpha = 0.5f),
                                        AppColors.CyanGlow.copy(alpha = 0.2f),
                                        AppColors.CyanGlow.copy(alpha = 0.8f)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .rotate(discRotation.value),
                        contentAlignment = Alignment.Center
                    ) {
                        val artContext = LocalContext.current
                        val media = currentMedia

                        // Placeholder background
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AppColors.MidnightCharcoal),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                null,
                                tint = AppColors.CyanGlow.copy(alpha = 0.5f),
                                modifier = Modifier.size(80.dp)
                            )
                        }

                        // Album art overlay
                        val artData: Any? = when {
                            media?.albumArtUri != null -> media.albumArtUri
                            media?.uri != null -> com.mhs.player.media.scanner.AudioArtFetcher.AudioArtKey(media.uri)
                            else -> null
                        }
                        if (artData != null) {
                            val artRequest = remember(artData) {
                                ImageRequest.Builder(artContext)
                                    .data(artData)
                                    .allowHardware(false)
                                    .crossfade(400)
                                    .build()
                            }
                            SubcomposeAsyncImage(
                                model = artRequest,
                                contentDescription = "Album Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                when (painter.state) {
                                    is coil.compose.AsyncImagePainter.State.Success ->
                                        SubcomposeAsyncImageContent()
                                    else -> {}
                                }
                            }
                        }

                        // Centre spindle ring (Walkman detail)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AppColors.CinematicBlack)
                                .border(2.dp, AppColors.CyanGlow.copy(alpha = 0.4f), CircleShape)
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // ── Audio Visualizer ──────────────────────────────
                    val audioSessionId = viewModel.playerController.player.collectAsState().value?.audioSessionId ?: 0
                    AudioVisualizer(
                        audioSessionId = audioSessionId,
                        isPlaying = playbackState.isPlaying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        barColor = AppColors.CyanGlow
                    )

                    Spacer(Modifier.height(20.dp))

                    // ── Title & Artist ────────────────────────────────
                    Text(
                        text = currentMedia?.title ?: "Unknown",
                        style = AppTypography.StandardTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = AppColors.OnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = currentMedia?.artist ?: "Unknown Artist",
                        style = AppTypography.StandardTypography.bodyMedium,
                        color = AppColors.OnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = currentMedia?.album ?: "",
                        style = AppTypography.StandardTypography.bodySmall,
                        color = AppColors.OnSurfaceDim,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(28.dp))

                    // ── Pixel seekbar ─────────────────────────────────
                    val duration = playbackState.duration.coerceAtLeast(1L)
                    val position = playbackState.currentPosition.coerceAtLeast(0L)
                    var isSeeking by remember { mutableStateOf(false) }
                    var seekValue by remember { mutableFloatStateOf(0f) }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        PixelSeekBar(
                            position = if (isSeeking) seekValue
                                       else (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                            isPlaying = playbackState.isPlaying,
                            onSeek = { frac ->
                                isSeeking = true
                                seekValue = frac
                            },
                            onSeekEnd = { frac ->
                                viewModel.seekTo((frac * duration).toLong())
                                isSeeking = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            trackHeight = 5.dp,
                            thumbRadius = 9.dp
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                MediaItemModel.formatDuration(
                                    if (isSeeking) (seekValue * duration).toLong() else position
                                ),
                                style = AppTypography.StandardTypography.labelSmall,
                                color = AppColors.OnSurfaceDim
                            )
                            Text(
                                MediaItemModel.formatDuration(duration),
                                style = AppTypography.StandardTypography.labelSmall,
                                color = AppColors.OnSurfaceDim
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                // ── Controls row ──────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    val shuffleMode by viewModel.queueManager.shuffleMode.collectAsStateWithLifecycle()
                    IconButton(onClick = { haptics.tick(); viewModel.queueManager.toggleShuffle() }) {
                        Icon(
                            Icons.Default.Shuffle,
                            "Shuffle",
                            tint = if (shuffleMode) AppColors.CyanGlow else AppColors.OnSurfaceDim
                        )
                    }

                    // Previous
                    IconButton(
                        onClick = { haptics.click(); viewModel.skipToPrevious() },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious, "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play/Pause — glass button with press-scale spring
                    val playPressSource = remember { MutableInteractionSource() }
                    val isPlayPressed by playPressSource.collectIsPressedAsState()
                    val playButtonScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isPlayPressed) 0.88f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessHigh
                        ),
                        label = "play_btn_scale"
                    )
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(playButtonScale)
                            .accentGlow(color = AppColors.CyanGlow, radius = 28.dp, offsetY = 0.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        AppColors.CyanGlow.copy(alpha = 0.22f),
                                        AppColors.ElectricBlue.copy(alpha = 0.10f),
                                        Color.White.copy(alpha = 0.04f)
                                    )
                                )
                            )
                            .border(2.dp,
                                Brush.linearGradient(
                                    listOf(AppColors.CyanGlow.copy(alpha = 0.7f), AppColors.AccentViolet.copy(alpha = 0.4f))
                                ),
                                CircleShape
                            )
                            .clickable(
                                interactionSource = playPressSource,
                                indication = null
                            ) { haptics.heavyClick(); viewModel.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Next
                    IconButton(
                        onClick = { haptics.click(); viewModel.skipToNext() },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext, "Next",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Repeat
                    val repeatMode by viewModel.queueManager.repeatMode.collectAsStateWithLifecycle()
                    IconButton(onClick = { haptics.tick(); viewModel.queueManager.cycleRepeatMode() }) {
                        Icon(
                            when (repeatMode) {
                                com.mhs.player.player.controller.QueueManager.RepeatMode.ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            "Repeat",
                            tint = when (repeatMode) {
                                com.mhs.player.player.controller.QueueManager.RepeatMode.NONE -> AppColors.OnSurfaceDim
                                else -> AppColors.CyanGlow
                            }
                        )
                    }
                }
            }
        }

        // ── Gesture Visual Feedback Overlay ───────────────────────────
        AnimatedVisibility(
            visible = isDraggingVolume || skipText.isNotEmpty(),
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    scaleIn(spring(stiffness = Spring.StiffnessMediumLow), initialScale = 0.82f),
            exit = fadeOut(tween(280)) + scaleOut(tween(280), targetScale = 0.82f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
        ) {
            Box(
                modifier = Modifier
                    .dsGlassCard(shape = AppShapes.RoundedLG, isPlaybackActive = false)
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isDraggingVolume) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (smoothVolume <= 0.05f) Icons.Default.VolumeOff
                            else if (smoothVolume < 0.5f) Icons.Default.VolumeDown
                            else Icons.Default.VolumeUp,
                            null,
                            tint = AppColors.CyanGlow,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${(smoothVolume * 100).toInt()}%",
                            style = AppTypography.StandardTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                } else if (skipText.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (cumulativeX > 250f) Icons.Default.SkipPrevious
                            else if (cumulativeX < -250f) Icons.Default.SkipNext
                            else Icons.Default.SyncAlt,
                            null,
                            tint = AppColors.CyanGlow,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            skipText,
                            style = AppTypography.StandardTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueuePanel(
    queue: List<MediaItemModel>,
    currentIndex: Int,
    onItemClick: (Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Queue",
                style = AppTypography.StandardTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = AppColors.OnSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(queue) { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (index == currentIndex)
                                AppColors.CyanGlow.copy(alpha = 0.10f)
                            else Color.Transparent,
                            AppShapes.RoundedSM
                        )
                        .clickable { onItemClick(index) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (index == currentIndex) {
                        Icon(Icons.Default.VolumeUp, null,
                            tint = AppColors.CyanGlow, modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            "${index + 1}",
                            style = AppTypography.StandardTypography.labelMedium,
                            color = AppColors.OnSurfaceDim,
                            modifier = Modifier.width(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = AppTypography.StandardTypography.bodyMedium,
                            color = if (index == currentIndex) AppColors.CyanGlow else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            item.artist,
                            style = AppTypography.StandardTypography.bodySmall,
                            color = AppColors.OnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        item.formattedDuration,
                        style = AppTypography.StandardTypography.labelSmall,
                        color = AppColors.OnSurfaceDim
                    )
                }
            }
        }
    }
}
