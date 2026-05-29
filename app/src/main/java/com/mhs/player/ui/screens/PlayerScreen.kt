package com.mhs.player.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import android.net.Uri
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.mhs.player.player.controller.PlayerController
import com.mhs.player.player.controls.CustomPlayerControls
import com.mhs.player.player.controls.GestureOverlay
import com.mhs.player.player.controls.ResumePromptOverlay
import com.mhs.player.player.controls.SubtitleSettingsSheet
import com.mhs.player.player.controls.AudioSettingsSheet
import com.mhs.player.player.controls.SubtitleSearchSheet
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.LinearOutSlowInEasing
import com.mhs.player.ui.theme.*
import com.mhs.player.settings.SettingsRepository.OrientationMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PlayerScreen(
    mediaId: Long,
    queueIndex: Int,
    navController: NavController,
    viewModel: PlayerViewModel = hiltViewModel(),
    mediaViewModel: MediaViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentMedia by viewModel.currentMedia.collectAsStateWithLifecycle()
    val player by viewModel.playerController.player.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isFirstFrameRendered by viewModel.playerController.isFirstFrameRendered.collectAsStateWithLifecycle()
    val isInPipMode by viewModel.playerController.isInPipMode.collectAsStateWithLifecycle()

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { safeUri ->
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(safeUri) ?: return@launch
                    val cacheDir = File(context.cacheDir, "subtitles")
                    if (!cacheDir.exists()) cacheDir.mkdirs()
                    
                    var extension = "srt"
                    val type = context.contentResolver.getType(safeUri)
                    if (type != null) {
                        if (type.contains("vtt")) extension = "vtt"
                        else if (type.contains("ass") || type.contains("ssa")) extension = "ass"
                    } else {
                        val uriPath = safeUri.path
                        if (uriPath != null) {
                            if (uriPath.endsWith(".vtt", ignoreCase = true)) extension = "vtt"
                            else if (uriPath.endsWith(".ass", ignoreCase = true)) extension = "ass"
                            else if (uriPath.endsWith(".ssa", ignoreCase = true)) extension = "ssa"
                        }
                    }
                    
                    val fileName = "local_subtitle_${System.currentTimeMillis()}.$extension"
                    val destFile = File(cacheDir, fileName)
                    
                    destFile.outputStream().use { output ->
                        inputStream.use { input ->
                            input.copyTo(output)
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        viewModel.loadExternalSubtitle(destFile, "en")
                        viewModel.showSubtitleSettings(false)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MHSPlayer-LocalSub", "Failed to load local subtitle file", e)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            
            // Safe restore of orientation when leaving the player to ensure library is not locked
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(settings.lastBrightness) {
        val window = activity?.window ?: return@LaunchedEffect
        if (settings.lastBrightness != -1f && window.attributes.screenBrightness != settings.lastBrightness) {
            val lp = window.attributes
            lp.screenBrightness = settings.lastBrightness
            window.attributes = lp
        }
    }

    LaunchedEffect(uiState.isControlsVisible) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (!uiState.isControlsVisible) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val videoSize by viewModel.playerController.videoSize.collectAsStateWithLifecycle()
    var videoScale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(uiState.currentResizeMode) { videoScale = 1f }
    LaunchedEffect(currentMedia) { videoScale = 1f }

    LaunchedEffect(videoSize, currentMedia, settings.orientationMode, uiState.isRotationLocked) {
        if (activity == null || uiState.isRotationLocked) return@LaunchedEffect
        
        val rotation = videoSize.unappliedRotationDegrees
        val isRotated = (rotation == 90 || rotation == 270)
        
        val rawW = if (videoSize.width > 0) videoSize.width else currentMedia?.width ?: 0
        val rawH = if (videoSize.height > 0) videoSize.height else currentMedia?.height ?: 0
        
        val trueW = if (isRotated) rawH else rawW
        val trueH = if (isRotated) rawW else rawH
        
        if (trueW == 0 || trueH == 0) return@LaunchedEffect
        
        android.util.Log.d(
            "MHSPlayer-Orientation",
            "True dimensions: ${trueW}x${trueH} (raw: ${rawW}x${rawH}, rotation: ${rotation}°). Resolved Orientation Mode: ${settings.orientationMode}"
        )
        
        val targetOrientation = when (settings.orientationMode) {
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationMode.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationMode.AUTO -> {
                val aspect = trueW.toFloat() / trueH.toFloat()
                if (aspect >= 1.2f) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else if (aspect <= 0.83f) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR
                }
            }
        }
        
        if (activity.requestedOrientation != targetOrientation) {
            activity.requestedOrientation = targetOrientation
        }
    }

    DisposableEffect(Unit) {
        viewModel.playerController.setAutoAdvance(true)
        onDispose {
            viewModel.playerController.setAutoAdvance(false)
            viewModel.stopPlayer()
        }
    }

    val mediaUiState by mediaViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(mediaId, queueIndex, mediaUiState.filteredVideos) {
        val allVideos = mediaUiState.filteredVideos
        if (allVideos.isNotEmpty()) {
            val safeIndex = queueIndex.coerceIn(0, allVideos.size - 1)
            val targetItem = allVideos.find { it.id == mediaId } ?: allVideos.getOrNull(safeIndex)
            if (targetItem != null && viewModel.currentMedia.value?.id != targetItem.id) {
                viewModel.openMedia(targetItem, allVideos, allVideos.indexOf(targetItem).coerceAtLeast(0))
            }
        }
    }

    val handleBack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            navController.popBackStack()
        } else {
            (activity as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
        }
    }

    BackHandler(onBack = handleBack)

    val diagnosticsInfo by viewModel.playerController.diagnosticsInfo.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // --- Native Media3 Video Player View (VLC/MX Player Grade Aspect Ratios) ---
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val playerView = remember(context) {
                PlayerView(context).apply {
                    useController = false
                    // Completely disable built-in subtitle rendering as we handle it custom in SubtitleOverlay
                    subtitleView?.alpha = 0f
                    
                    val aspectMode = when (uiState.currentResizeMode) {
                        com.mhs.player.media.model.ResizeMode.TRUE_CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> uiState.currentResizeMode.exoMode
                    }
                    resizeMode = aspectMode
                    
                    // Bind to the underlying player instance
                    this.player = player
                    
                    // Retrieve the internal SurfaceView dynamically and register it with the controller
                    post {
                        fun findSurfaceView(view: android.view.View): SurfaceView? {
                            if (view is SurfaceView) return view
                            if (view is android.view.ViewGroup) {
                                for (i in 0 until view.childCount) {
                                    val sv = findSurfaceView(view.getChildAt(i))
                                    if (sv != null) return sv
                                }
                            }
                            return null
                        }
                        findSurfaceView(this)?.let { surfaceView ->
                            viewModel.playerController.setVideoSurfaceView(surfaceView)
                        }
                    }
                }
            }

            LaunchedEffect(player) {
                playerView.player = player
            }

            LaunchedEffect(uiState.currentResizeMode) {
                val aspectMode = when (uiState.currentResizeMode) {
                    com.mhs.player.media.model.ResizeMode.TRUE_CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> uiState.currentResizeMode.exoMode
                }
                playerView.resizeMode = aspectMode
            }

            DisposableEffect(playerView) {
                onDispose {
                    fun findSurfaceView(view: android.view.View): SurfaceView? {
                        if (view is SurfaceView) return view
                        if (view is android.view.ViewGroup) {
                            for (i in 0 until view.childCount) {
                                val sv = findSurfaceView(view.getChildAt(i))
                                if (sv != null) return sv
                            }
                        }
                        return null
                    }
                    findSurfaceView(playerView)?.let { surfaceView ->
                        viewModel.playerController.releaseVideoSurfaceView(surfaceView)
                    }
                }
            }

            AndroidView(
                factory = { playerView },
                update = { },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = videoScale
                        scaleY = videoScale
                    }
            )
        }


        // --- Night Mode / Dim Overlay ---
        AnimatedVisibility(
            visible = uiState.isDimActive && !isInPipMode,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (diagnosticsInfo.isHeavyVideoMode) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.65f))
            )
        }

        // --- Premium Elegant Loading Overlay ---
        AnimatedVisibility(
            visible = !isFirstFrameRendered,
            enter = fadeIn(),
            exit = fadeOut(tween(600)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = AccentCyan,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        }


        // Custom Subtitle Overlay (Translated or Media3 Cues) - Isolated to shield PlayerScreen
        SubtitleOverlay(
            playerController = viewModel.playerController,
            settingsFlow = viewModel.settings,
            resizeMode = uiState.currentResizeMode,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // --- Playback Diagnostics Overlay HUD ---
        AnimatedVisibility(
            visible = uiState.isDiagnosticsEnabled && !isInPipMode,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 80.dp, end = 16.dp)
                .width(280.dp)
        ) {
            Column(
                modifier = Modifier
                    .accentGlow(color = AccentCyan, radius = if (diagnosticsInfo.isHeavyVideoMode) 0.dp else 16.dp, offsetY = 0.dp)
                    .glassCard(cornerRadius = 12.dp, fillAlpha = if (diagnosticsInfo.isHeavyVideoMode) 0.5f else 0.35f, borderAlpha = 0.5f)
                    .padding(12.dp)
            ) {
                Text(
                    text = "PLAYBACK DIAGNOSTICS",
                    color = AccentCyan,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                val items = listOf(
                    "Decoder" to "${diagnosticsInfo.decoderName} (${if (diagnosticsInfo.isHardware) "HW" else "SW"})",
                    "Codec / Profile" to "${diagnosticsInfo.codec} (${diagnosticsInfo.codecProfile} | ${diagnosticsInfo.bitDepth})",
                    "Format" to "${diagnosticsInfo.resolution} @ ${java.lang.String.format(java.util.Locale.US, "%.2f", diagnosticsInfo.bitrate)} Mbps",
                    "HDR / Color" to diagnosticsInfo.hdrType,
                    "Dropped Frames" to "${diagnosticsInfo.droppedFrames}",
                    "Buffering Reason" to diagnosticsInfo.bufferingReason,
                    "AI Translate" to diagnosticsInfo.subtitleTranslationActivity
                )
                
                items.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp
                        )
                        Text(
                            text = value,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }


        if (isFirstFrameRendered && !isInPipMode) {
            GestureOverlay(
                viewModel = viewModel,
                onZoom = { delta ->
                    val newScale = (videoScale * delta).coerceIn(0.25f, 4f)
                    videoScale = newScale
                },
                onMinimize = handleBack,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Decoder Switch Message
        AnimatedVisibility(
            visible = uiState.decoderMessage != null && !isInPipMode,
            enter = fadeIn(tween(300)) + scaleIn(tween(300, easing = LinearOutSlowInEasing), initialScale = 0.9f),
            exit = fadeOut(tween(500)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .accentGlow(color = AccentCyan, radius = if (diagnosticsInfo.isHeavyVideoMode) 0.dp else 20.dp, offsetY = 0.dp)
                    .glassCard(cornerRadius = 14.dp, fillAlpha = if (diagnosticsInfo.isHeavyVideoMode) 0.5f else 0.25f, borderAlpha = 0.6f)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = uiState.decoderMessage ?: "",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isFirstFrameRendered && !isInPipMode) {
            CustomPlayerControls(
                viewModel = viewModel,
                currentMedia = currentMedia,
                playbackStateFlow = viewModel.playbackState,
                onBack = handleBack,
                onPip = { (activity as? com.mhs.player.MainActivity)?.enterPipMode() },
                onSwitchDecoder = { viewModel.toggleDecoder() },
                onRotate = viewModel::toggleRotationLock,
                rotationLabel = if (uiState.isRotationLocked) "Locked" else "Unlock"
            )
        }

        // Sheet Logic (Simplified for clarity)
        AnimatedVisibility(
            visible = uiState.showSubtitleSettings && !isInPipMode,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut(tween(300))
        ) {
            val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
            var subtitleTracks by remember { mutableStateOf(emptyList<PlayerController.TrackInfo>()) }
            LaunchedEffect(uiState.showSubtitleSettings) {
                if (uiState.showSubtitleSettings) {
                    subtitleTracks = viewModel.getSubtitleTracks()
                }
            }
            SubtitleSettingsSheet(
                settings = settings,
                currentTracks = subtitleTracks,
                onSelectTrack = { viewModel.selectSubtitleTrack(it); subtitleTracks = viewModel.getSubtitleTracks() },
                onSearchOnline = { 
                    viewModel.showSubtitleSettings(false)
                    viewModel.showSubtitleSearch(true)
                },
                onSelectLocalFile = { subtitlePickerLauncher.launch("*/*") },
                onUpdateSubtitleSize = viewModel::updateSubtitleSize,
                onUpdateSubtitleOpacity = viewModel::updateSubtitleOpacity,
                onUpdateSubtitlePosition = viewModel::updateSubtitlePosition,
                subtitleDelayMs = playbackState.subtitleDelay,
                onUpdateDelay = viewModel::updateSubtitleDelay,
                subtitleSpeed = playbackState.subtitleSpeed,
                onUpdateSpeed = viewModel::updateSubtitleSpeed,
                onUpdateTranslationEnabled = viewModel::updateSubtitleTranslationEnabled,
                onUpdateTargetLang = viewModel::updateSubtitleTargetLang,
                onUpdateApiKey = viewModel::setSubtitleApiKey,
                onDismiss = { viewModel.showSubtitleSettings(false) }
            )
        }

        if (uiState.showSubtitleSearch && !isInPipMode) {
            SubtitleSearchSheet(
                videoFilename = currentMedia?.displayName ?: "Video",
                videoPath = currentMedia?.path,
                apiKey = settings.subtitleApiKey,
                preferredLanguage = settings.subtitleLanguage,
                onSubtitleSelected = { file: java.io.File, lang: String ->
                    viewModel.loadExternalSubtitle(file, lang)
                    viewModel.showSubtitleSearch(false)
                },
                onDismiss = { viewModel.showSubtitleSearch(false) }
            )
        }

        AnimatedVisibility(
            visible = uiState.showAudioSettings && !isInPipMode,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut(tween(300))
        ) {
            var audioTracks by remember { mutableStateOf(emptyList<PlayerController.TrackInfo>()) }
            LaunchedEffect(uiState.showAudioSettings) {
                if (uiState.showAudioSettings) {
                    audioTracks = viewModel.getAudioTracks()
                }
            }
            AudioSettingsSheet(
                currentTracks = audioTracks,
                audioDelayMs = uiState.audioDelayMs,
                onSelectTrack = { viewModel.selectAudioTrack(it); audioTracks = viewModel.getAudioTracks() },
                onUpdateDelay = viewModel::updateAudioDelay,
                onDismiss = { viewModel.showAudioSettings(false) }
            )
        }

        ResumePromptOverlay(
            isVisible = uiState.resumePromptPosition != null && !isInPipMode,
            savedPositionMs = uiState.resumePromptPosition ?: 0L,
            onResume = { rem -> uiState.resumePromptPosition?.let { viewModel.resumePlayback(it, rem) } },
            onStartOver = { rem -> viewModel.startFromBeginning(rem) }
        )
    }
}

@Composable
private fun SubtitleOverlay(
    playerController: PlayerController,
    settingsFlow: kotlinx.coroutines.flow.StateFlow<com.mhs.player.settings.SettingsRepository.AppSettings>,
    resizeMode: com.mhs.player.media.model.ResizeMode,
    modifier: Modifier = Modifier
) {
    val cues by playerController.currentCues.collectAsStateWithLifecycle()
    val settings by settingsFlow.collectAsStateWithLifecycle()

    if (cues.isNotEmpty()) {
        val bottomPadding = remember(resizeMode, settings.subtitlePosition) {
            if (resizeMode == com.mhs.player.media.model.ResizeMode.ZOOM) {
                (settings.subtitlePosition * 400).dp
            } else {
                (settings.subtitlePosition * 300).dp
            }
        }

        AndroidView(
            factory = { ctx -> 
                androidx.media3.ui.SubtitleView(ctx).apply { 
                    setUserDefaultStyle()
                    setUserDefaultTextSize() 
                } 
            },
            update = { view ->
                view.setCues(cues)
                view.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, settings.subtitleSize)
                view.alpha = settings.subtitleOpacity
                
                val systemStyle = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    val captioningManager = view.context.getSystemService(android.content.Context.CAPTIONING_SERVICE) as? android.view.accessibility.CaptioningManager
                    if (captioningManager != null) {
                        androidx.media3.ui.CaptionStyleCompat.createFromCaptionStyle(captioningManager.userStyle)
                    } else {
                        androidx.media3.ui.CaptionStyleCompat.DEFAULT
                    }
                } else {
                    androidx.media3.ui.CaptionStyleCompat.DEFAULT
                }
                val fgColor = try {
                    android.graphics.Color.parseColor(settings.subtitleColor)
                } catch (e: Exception) {
                    systemStyle.foregroundColor
                }
                val bgColor = if (settings.subtitleBackground) {
                    if (systemStyle.backgroundColor != android.graphics.Color.TRANSPARENT) {
                        systemStyle.backgroundColor
                    } else {
                        android.graphics.Color.parseColor("#80000000")
                    }
                } else {
                    android.graphics.Color.TRANSPARENT
                }
                val edgeType = if (settings.subtitleShadowEnabled) {
                    if (systemStyle.edgeType != androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE) systemStyle.edgeType else androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
                } else {
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                }
                val customStyle = androidx.media3.ui.CaptionStyleCompat(
                    fgColor,
                    bgColor,
                    android.graphics.Color.TRANSPARENT,
                    edgeType,
                    systemStyle.edgeColor,
                    systemStyle.typeface
                )
                view.setStyle(customStyle)
            },
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = bottomPadding)
                .height(150.dp)
        )
    }
}


