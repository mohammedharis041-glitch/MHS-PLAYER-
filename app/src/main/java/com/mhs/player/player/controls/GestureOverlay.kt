package com.mhs.player.player.controls

import android.util.Log
import com.mhs.player.player.gestures.GestureController
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mhs.player.ui.screens.PlayerViewModel
import com.mhs.player.ui.theme.*
import com.mhs.player.ui.theme.designsystem.AppColors
import com.mhs.player.ui.theme.designsystem.AppTypography
import com.mhs.player.ui.theme.designsystem.AppAnimations
import com.mhs.player.ui.theme.designsystem.GlassStyles.glassOverlay
import com.mhs.player.ui.theme.designsystem.GlassStyles.glassCard
import com.mhs.player.ui.theme.designsystem.PlayerIcons
import com.mhs.player.ui.theme.designsystem.rememberHaptics
import com.mhs.player.player.controls.SeekPreviewPopup
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.roundToInt

private enum class GestureDirection { HORIZONTAL, VERTICAL }
private enum class DoubleTapSide { LEFT, RIGHT }

@Composable
fun GestureOverlay(
    viewModel: PlayerViewModel,
    onZoom: (delta: Float) -> Unit = {},
    onMinimize: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gestureState by viewModel.gestureController.gestureState.collectAsState()
    val config = LocalConfiguration.current
    val screenWidthPx = config.screenWidthDp * config.densityDpi / 160f
    val screenHeightPx = config.screenHeightDp * config.densityDpi / 160f
    val previewBitmap by viewModel.previewFrameManager.previewBitmap.collectAsState()
    val isPreviewVisible by viewModel.previewFrameManager.isVisible.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val seekMs = (settings.seekDurationPreset * 1000L).coerceAtLeast(5000L)

    var rippleSide by remember { mutableStateOf<DoubleTapSide?>(null) }
    var rippleTrigger by remember { mutableLongStateOf(0L) }
    var rippleOffset by remember { mutableStateOf(Offset.Zero) }
    var showPlayPauseIndicator by remember { mutableStateOf<Boolean?>(null) }
    var playPauseTrigger by remember { mutableLongStateOf(0L) }
    var accumulatedSeekSecs by remember { mutableIntStateOf(0) }
    val haptics = rememberHaptics()

    val activity = (androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity)
    DisposableEffect(activity) {
        if (activity != null) viewModel.gestureController.attachActivity(activity)
        onDispose { viewModel.gestureController.detachActivity() }
    }
    LaunchedEffect(settings.seekSensitivity, settings.swipeSensitivity) {
        viewModel.gestureController.setSensitivities(
            seek = settings.seekSensitivity,
            swipe = settings.swipeSensitivity
        )
    }

    val uiState by viewModel.uiState.collectAsState()

    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }
    var gestureDirection by remember { mutableStateOf<GestureDirection?>(null) }
    var gestureStartPosition by remember { mutableLongStateOf(0L) }

    LaunchedEffect(gestureState.activeGesture, gestureState.seekDelta) {
        if (gestureState.activeGesture == GestureController.GestureType.SEEK) {
            val liveDuration = viewModel.playerController.playbackState.value.duration
            val maxDur = if (liveDuration <= 0L) Long.MAX_VALUE else liveDuration
            val targetPos = (gestureStartPosition + gestureState.seekDelta).coerceIn(0, maxDur)
            viewModel.onSeekPreviewStart(targetPos)
        } else if (gestureState.activeGesture == GestureController.GestureType.NONE) {
            viewModel.onSeekPreviewEnd()
        }
    }

    Box(
        modifier = modifier
            .pointerInput(uiState.isControlsVisible, uiState.isLocked) {
                awaitEachGesture {
                    var isPinching = false
                    var prevSpan = 0f
                    try {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        dragStartX = firstDown.position.x
                        dragStartY = firstDown.position.y
                        totalDragX = 0f
                        totalDragY = 0f
                        gestureDirection = null

                        if (!uiState.isLocked) {
                            gestureStartPosition = viewModel.playerController.playbackState.value.currentPosition
                            viewModel.gestureController.onGestureStart(
                                dragStartX, dragStartY, screenWidthPx, screenHeightPx
                            )
                        }

                        var movedFar = false

                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            val pointerCount = pressed.size

                            when {
                                pointerCount >= 2 -> {
                                    if (!isPinching) {
                                        isPinching = true
                                        val p1 = pressed[0].position
                                        val p2 = pressed[1].position
                                        prevSpan = sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y))
                                    } else {
                                        val p1 = pressed[0].position
                                        val p2 = pressed[1].position
                                        val span = sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y))
                                        
                                        if (prevSpan > 0f) {
                                            val delta = span / prevSpan
                                            if (abs(delta - 1f) > 0.0001f) {
                                                if (!uiState.isLocked) {
                                                    onZoom(delta)
                                                }
                                                prevSpan = span
                                            }
                                        } else {
                                            prevSpan = span
                                        }
                                    }
                                    pressed.forEach { it.consume() }
                                    movedFar = true
                                }

                                pointerCount == 1 && !isPinching -> {
                                    val dragDelta = pressed[0].positionChange()
                                    if (!uiState.isLocked) {
                                        totalDragX += dragDelta.x
                                        totalDragY += dragDelta.y

                                        if (gestureDirection == null && (abs(totalDragX) > 20f || abs(totalDragY) > 20f)) {
                                            gestureDirection = if (abs(totalDragX) > abs(totalDragY) * 1.5f) GestureDirection.HORIZONTAL else GestureDirection.VERTICAL
                                            if (uiState.isControlsVisible) viewModel.hideControls()
                                        }

                                        when (gestureDirection) {
                                            GestureDirection.HORIZONTAL -> viewModel.gestureController.onHorizontalScroll(totalDragX, size.width.toFloat())
                                            GestureDirection.VERTICAL -> {
                                                viewModel.gestureController.onVerticalScroll(totalDragY, dragStartX, size.width.toFloat(), size.height.toFloat())
                                            }
                                            else -> {}
                                        }

                                        if (abs(totalDragX) > 15f || abs(totalDragY) > 15f) {
                                            movedFar = true
                                            pressed[0].consume()
                                        }
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        if (!uiState.isLocked) {
                            val wasBrightness = gestureState.activeGesture == GestureController.GestureType.BRIGHTNESS
                            val currentBrightness = gestureState.brightnessPercent / 100f

                            viewModel.gestureController.onGestureEnd(gestureStartPosition)
                            viewModel.scheduleHideControls()

                            if (wasBrightness) {
                                viewModel.saveBrightness(currentBrightness)
                            }
                        }
                    }
                }
            }
            .pointerInput(uiState.isControlsVisible, uiState.isLocked) {
                if (uiState.isLocked) return@pointerInput

                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = true)
                    val half = size.width / 2f
                    val isRightSide = firstDown.position.x > half * 1.3f

                    var dragged = false
                    val firstUp = withTimeoutOrNull(400) {
                        var upEvent: PointerInputChange? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                upEvent = change
                                break
                            }
                            if (change.isConsumed || 
                                abs(change.position.x - firstDown.position.x) > 15f || 
                                abs(change.position.y - firstDown.position.y) > 15f) {
                                dragged = true
                                break
                            }
                        }
                        upEvent
                    }

                    if (firstUp == null) {
                        if (dragged) return@awaitEachGesture
                        
                        if (isRightSide) {
                            val originalSpeed = playbackState.playbackSpeed
                            viewModel.setPlaybackSpeed(2.0f)
                            
                            rippleSide = DoubleTapSide.RIGHT
                            accumulatedSeekSecs = -1 
                            rippleOffset = firstDown.position
                            rippleTrigger = System.currentTimeMillis()
                            
                            try {
                                do {
                                    awaitPointerEvent()
                                } while (currentEvent.changes.any { it.pressed })
                            } finally {
                                viewModel.setPlaybackSpeed(originalSpeed)
                            }
                        }
                        return@awaitEachGesture
                    }

                    var tapCount = 1
                    var lastOffset = firstUp.position

                    while (true) {
                        val nextDown = withTimeoutOrNull(250) {
                            awaitFirstDown(requireUnconsumed = true)
                        }
                        if (nextDown == null) break 
                        
                        val nextUp = withTimeoutOrNull(300) {
                            waitForUpOrCancellation()
                        }
                        if (nextUp == null) break 
                        
                        tapCount++
                        lastOffset = nextUp.position

                        val accumulatedSeekMs = seekMs * (tapCount - 1)
                        accumulatedSeekSecs = (accumulatedSeekMs / 1000L).toInt()

                        when {
                            lastOffset.x < half * 0.7f -> {
                                viewModel.gestureController.onDoubleTapLeft(seekMs)
                                haptics.click()
                                rippleSide = DoubleTapSide.LEFT
                                rippleOffset = lastOffset
                                rippleTrigger = System.currentTimeMillis()
                            }
                            lastOffset.x > half * 1.3f -> {
                                viewModel.gestureController.onDoubleTapRight(seekMs)
                                haptics.click()
                                rippleSide = DoubleTapSide.RIGHT
                                rippleOffset = lastOffset
                                rippleTrigger = System.currentTimeMillis()
                            }
                            else -> {
                                haptics.heavyClick()
                                viewModel.togglePlayPause()
                                showPlayPauseIndicator = !playbackState.isPlaying
                                playPauseTrigger = System.currentTimeMillis()
                            }
                        }
                    }

                    if (tapCount == 1) {
                        viewModel.toggleControls()
                    }
                }
            }
    ) {
        DoubleTapRipple(
            side = rippleSide,
            offset = rippleOffset,
            trigger = rippleTrigger,
            seekSeconds = accumulatedSeekSecs,
            modifier = Modifier.fillMaxSize()
        )

        // ── Double Tap Center Play/Pause Indicator ──────────────────────────────
        var centralVisible by remember { mutableStateOf(false) }
        var isPlayIcon by remember { mutableStateOf(true) }

        LaunchedEffect(playPauseTrigger) {
            if (playPauseTrigger > 0L) {
                isPlayIcon = showPlayPauseIndicator == true
                centralVisible = true
                delay(650)
                centralVisible = false
            }
        }

        AnimatedVisibility(
            visible = centralVisible,
            enter = scaleIn(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(150)),
            exit = scaleOut(tween(250)) + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .glassOverlay(shape = CircleShape)
                    .size(90.dp)
                    .accentGlow(color = AppColors.CyanGlow, radius = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlayIcon) PlayerIcons.Play else PlayerIcons.Pause,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        val activeGesture = gestureState.activeGesture
        val lastGestureTypeState = remember { mutableStateOf<GestureController.GestureType?>(null) }
        if (activeGesture == GestureController.GestureType.VOLUME ||
            activeGesture == GestureController.GestureType.BRIGHTNESS) {
            lastGestureTypeState.value = activeGesture
        }
        val lastGestureType = lastGestureTypeState.value

        // ── Premium Centered Floating Circular HUD Gesture Indicators ─────────────────
        AnimatedVisibility(
            visible = activeGesture == GestureController.GestureType.VOLUME ||
                      activeGesture == GestureController.GestureType.BRIGHTNESS,
            enter = scaleIn(animationSpec = AppAnimations.TactileSpringSpec) + fadeIn(animationSpec = AppAnimations.CinematicFadeSpec),
            exit = scaleOut(animationSpec = AppAnimations.TactileSpringSpec) + fadeOut(animationSpec = AppAnimations.CinematicFadeSpec),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val currentGestureType = if (activeGesture != GestureController.GestureType.NONE) {
                activeGesture
            } else {
                lastGestureType ?: GestureController.GestureType.VOLUME
            }
            val isVolume = currentGestureType == GestureController.GestureType.VOLUME
            val percent = if (isVolume) gestureState.volumePercent else gestureState.brightnessPercent.toFloat()
            val color = if (isVolume) AppColors.CyanGlow else AppColors.AccentAmber
            val icon = if (isVolume) {
                if (gestureState.volumePercent <= 0.1f) PlayerIcons.VolumeOff else PlayerIcons.VolumeUp
            } else {
                PlayerIcons.Brightness
            }
            
            CircularGestureHud(
                icon = icon,
                percent = percent,
                color = color
            )
        }

        if (gestureState.isLocked) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
        }

        SeekPreviewPopup(
            isVisible = isPreviewVisible && gestureState.activeGesture == GestureController.GestureType.SEEK,
            thumbnail = previewBitmap,
            time = "${formatDuration(
                if (gestureState.activeGesture == GestureController.GestureType.SEEK)
                    (playbackState.currentPosition + gestureState.seekDelta).coerceAtLeast(0)
                else playbackState.currentPosition
            )} / ${formatDuration(playbackState.duration)}",
            delta = if (gestureState.activeGesture == GestureController.GestureType.SEEK) {
                val secs = gestureState.seekDelta / 1000
                if (secs >= 0) "+${secs}s" else "${secs}s"
            } else null,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun CircularGestureHud(
    icon: ImageVector,
    percent: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val animatedPercent by animateFloatAsState(
        targetValue = percent.coerceIn(0f, 100f),
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "circular_hud_progress"
    )

    Box(
        modifier = modifier
            .size(96.dp)
            .glassOverlay(shape = CircleShape)
            .accentGlow(color = color.copy(alpha = 0.35f), radius = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val strokeW = 4.dp.toPx()
            
            // Background thin track
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                style = Stroke(width = strokeW)
            )
            
            // Active progress arc with smooth sweep
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(color, color.copy(alpha = 0.6f), color)
                ),
                startAngle = -90f,
                sweepAngle = (animatedPercent / 100f) * 360f,
                useCenter = false,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${percent.roundToInt()}%",
                color = Color.White,
                style = AppTypography.CodecInfo.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val h = totalSecs / 3600
    val m = (totalSecs % 3600) / 60
    val s = totalSecs % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

@Composable
private fun DoubleTapRipple(
    side: DoubleTapSide?,
    offset: Offset,
    trigger: Long,
    seekSeconds: Int,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    var currentSide by remember { mutableStateOf<DoubleTapSide?>(null) }

    LaunchedEffect(trigger) {
        if (trigger > 0L) {
            currentSide = side
            visible = true
            delay(700)
            visible = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing)),
        label = "r1"
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 150, easing = FastOutSlowInEasing)),
        label = "r2"
    )
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 300, easing = FastOutSlowInEasing)),
        label = "r3"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(80)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        val color = if (currentSide == DoubleTapSide.RIGHT) AppColors.CyanGlow else AppColors.AccentViolet
        val label = if (seekSeconds == -1) "2x Forward" else if (currentSide == DoubleTapSide.RIGHT) "+${seekSeconds}s" else "-${seekSeconds}s"
        val icon = if (currentSide == DoubleTapSide.RIGHT) PlayerIcons.Forward10 else PlayerIcons.Replay10

        Box(modifier = Modifier.fillMaxSize().graphicsLayer()) {
            val density = LocalDensity.current
            
            Box(
                modifier = Modifier
                    .offset {
                        val x = (offset.x / density.density).dp - 60.dp
                        val y = (offset.y / density.density).dp - 60.dp
                        androidx.compose.ui.unit.IntOffset(
                            x.toPx().toInt(),
                            y.toPx().toInt()
                        )
                    }
                    .size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    listOf(ring1, ring2, ring3).forEachIndexed { i, progress ->
                        val radius = (30f + progress * 60f).dp.toPx()
                        val alpha = (1f - progress) * 0.6f
                        drawCircle(
                            color = color.copy(alpha = alpha),
                            radius = radius,
                            center = Offset(cx, cy),
                            style = Stroke(width = (3f - progress * 2f).dp.toPx())
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        icon, null,
                        tint = color,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        label,
                        color = Color.White,
                        style = AppTypography.CodecInfo.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
