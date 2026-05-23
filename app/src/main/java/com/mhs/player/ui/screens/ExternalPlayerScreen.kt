package com.mhs.player.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.mhs.player.player.controls.CustomPlayerControls
import com.mhs.player.player.controls.GestureOverlay
import com.mhs.player.player.controller.PlayerController
import com.mhs.player.settings.SettingsRepository.OrientationMode

@Composable
fun ExternalPlayerScreen(
    uriString: String,
    navController: NavController,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentMedia by viewModel.currentMedia.collectAsStateWithLifecycle()
    val player by viewModel.playerController.player.collectAsStateWithLifecycle()
    val videoSize by viewModel.playerController.videoSize.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isFirstFrameRendered by viewModel.playerController.isFirstFrameRendered.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val isPlayerReady = remember(videoSize) { videoSize.width > 0 && videoSize.height > 0 }
    var videoScale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(uiState.currentResizeMode) { videoScale = 1f }
    LaunchedEffect(currentMedia) { videoScale = 1f }

    // Configure keeping screen on and cutout styling immediately
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
            
            // Restore orientation when leaving the external player activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Dynamic adaptive orientation based on video structure & user preferences
    LaunchedEffect(isPlayerReady, videoSize, currentMedia, settings.orientationMode, uiState.isRotationLocked) {
        if (activity == null || uiState.isRotationLocked || !isPlayerReady) return@LaunchedEffect
        
        val rotation = videoSize.unappliedRotationDegrees
        val isRotated = (rotation == 90 || rotation == 270)
        
        val rawW = if (videoSize.width > 0) videoSize.width else currentMedia?.width ?: 0
        val rawH = if (videoSize.height > 0) videoSize.height else currentMedia?.height ?: 0
        
        val trueW = if (isRotated) rawH else rawW
        val trueH = if (isRotated) rawW else rawH
        
        if (trueW == 0 || trueH == 0) return@LaunchedEffect
        
        android.util.Log.d(
            "MHSPlayer-Orientation-External",
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

    // Delayed and synced immersive system UI transition
    LaunchedEffect(uiState.isControlsVisible, isPlayerReady) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (!uiState.isControlsVisible && isPlayerReady) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Load media from URI only if it's different from the currently playing one to allow seamless miniplayer resumption
    LaunchedEffect(uriString) {
        if (viewModel.currentMedia.value?.uri?.toString() != uriString) {
            viewModel.openMediaByUri(Uri.parse(uriString))
        }
    }

    val handleBack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            navController.popBackStack()
        } else {
            (activity as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
        }
    }

    BackHandler(onBack = handleBack)

    val cues by viewModel.currentCues.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- Hybrid Video Surface (Unified Raw SurfaceView) ---
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val containerWidth = constraints.maxWidth.toFloat()
            val containerHeight = constraints.maxHeight.toFloat()
            
            val videoAspectRatio = remember(videoSize, currentMedia) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val sar = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
                    (videoSize.width.toFloat() * sar) / videoSize.height.toFloat()
                } else {
                    val media = currentMedia
                    if (media != null && media.width > 0 && media.height > 0) {
                        media.width.toFloat() / media.height.toFloat()
                    } else {
                        16f / 9f
                    }
                }
            }

            val gpuScale = remember(videoAspectRatio, uiState.currentResizeMode, containerWidth, containerHeight) {
                val containerAspectRatio = containerWidth / containerHeight
                var baseScaleX = 1f
                var baseScaleY = 1f
                
                when (uiState.currentResizeMode) {
                    com.mhs.player.media.model.ResizeMode.FIT -> {
                        if (containerAspectRatio > videoAspectRatio) {
                            baseScaleX = videoAspectRatio / containerAspectRatio
                            baseScaleY = 1f
                        } else {
                            baseScaleX = 1f
                            baseScaleY = containerAspectRatio / videoAspectRatio
                        }
                    }
                    com.mhs.player.media.model.ResizeMode.FILL -> {
                        baseScaleX = 1f
                        baseScaleY = 1f
                    }
                    com.mhs.player.media.model.ResizeMode.ZOOM -> {
                        if (containerAspectRatio > videoAspectRatio) {
                            baseScaleX = 1f
                            baseScaleY = containerAspectRatio / videoAspectRatio
                        } else {
                            baseScaleX = videoAspectRatio / containerAspectRatio
                            baseScaleY = 1f
                        }
                    }
                    com.mhs.player.media.model.ResizeMode.TRUE_CROP -> {
                        val baseZoomX = if (containerAspectRatio > videoAspectRatio) 1f else (videoAspectRatio / containerAspectRatio)
                        val baseZoomY = if (containerAspectRatio > videoAspectRatio) (containerAspectRatio / videoAspectRatio) else 1f
                        baseScaleX = baseZoomX * 1.15f
                        baseScaleY = baseZoomY * 1.15f
                    }
                    else -> {
                        if (containerAspectRatio > videoAspectRatio) {
                            baseScaleX = videoAspectRatio / containerAspectRatio
                            baseScaleY = 1f
                        } else {
                            baseScaleX = 1f
                            baseScaleY = containerAspectRatio / videoAspectRatio
                        }
                    }
                }
                baseScaleX to baseScaleY
            }

            val baseScaleX = gpuScale.first
            val baseScaleY = gpuScale.second

            // Log scale changes for debugging
            LaunchedEffect(baseScaleX, baseScaleY, uiState.currentResizeMode, videoAspectRatio, videoScale) {
                android.util.Log.d(
                    "MHSPlayer-External-UI",
                    "GPU Scale Applied: ${baseScaleX}x${baseScaleY} (Mode: ${uiState.currentResizeMode}, AspectRatio: $videoAspectRatio, GestureZoomScale: $videoScale)"
                )
            }

            val surfaceView = remember(context) {
                SurfaceView(context).apply {
                    if (settings.fasterFullscreen) {
                        holder.setFormat(android.graphics.PixelFormat.RGBX_8888)
                    }
                    viewModel.playerController.setVideoSurfaceView(this)
                }
            }

            DisposableEffect(surfaceView) {
                onDispose {
                    android.util.Log.d("MHSPlayer-External-UI", "ExternalPlayerScreen: Disposing surfaceView")
                    viewModel.playerController.releaseVideoSurfaceView(surfaceView)
                }
            }

            AndroidView(
                factory = { surfaceView },
                update = { /* Pure GPU transformation architecture: surface remains static and layout loops are eliminated */ },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = baseScaleX * videoScale
                        scaleY = baseScaleY * videoScale
                    }
            )
        }

        // --- Night Mode / Dim Overlay ---
        AnimatedVisibility(
            visible = uiState.isDimActive,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
            )
        }

        // --- Premium Elegant Loading Overlay ---
        AnimatedVisibility(
            visible = !isFirstFrameRendered && importState is ImportState.Idle,
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
                    color = Color(0xFF00E5FF),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // --- Content Import Progress Overlay ---
        AnimatedVisibility(
            visible = importState is ImportState.Importing,
            enter = fadeIn(),
            exit = fadeOut(tween(600)),
            modifier = Modifier.fillMaxSize()
        ) {
            val progress = (importState as? ImportState.Importing)?.progress ?: 0f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Text(
                        text = "Preparing media... ${(progress * 100).toInt()}%",
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // --- Content Import Error Dialog / Overlay ---
        AnimatedVisibility(
            visible = importState is ImportState.Error,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val errorMsg = (importState as? ImportState.Error)?.message ?: "Unknown Error"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(24.dp)
                        .background(Color(0xFF1E1E1E), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    androidx.compose.material3.Text(
                        text = "Access Failed",
                        color = Color.Red,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.Text(
                        text = errorMsg,
                        color = Color.LightGray,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    androidx.compose.material3.Button(
                        onClick = handleBack,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935),
                            contentColor = Color.White
                        )
                    ) {
                        androidx.compose.material3.Text("Go Back")
                    }
                }
            }
        }

        // AI Subtitle Overlay for External Player
        if (cues.isNotEmpty()) {
            val bottomPadding = (settings.subtitlePosition * 300).dp
            AndroidView(
                factory = { ctx -> androidx.media3.ui.SubtitleView(ctx).apply { setUserDefaultStyle(); setUserDefaultTextSize() } },
                update = { view ->
                    view.setCues(cues)
                    view.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, settings.subtitleSize)
                    view.alpha = settings.subtitleOpacity
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding)
                    .height(150.dp)
            )
        }

        if (isFirstFrameRendered) {
            GestureOverlay(
                viewModel = viewModel,
                onZoom = { delta ->
                    val newScale = (videoScale * delta).coerceIn(0.25f, 4f)
                    videoScale = newScale
                },
                onMinimize = handleBack,
                modifier = Modifier.fillMaxSize()
            )

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

        // Subtitle and Audio Settings Sheets
        if (uiState.showSubtitleSettings) {
            val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
            var subtitleTracks by remember { mutableStateOf<List<PlayerController.TrackInfo>>(emptyList()) }
            LaunchedEffect(Unit) { subtitleTracks = viewModel.getSubtitleTracks() }
            com.mhs.player.player.controls.SubtitleSettingsSheet(
                settings = settings,
                currentTracks = subtitleTracks,
                onSelectTrack = { viewModel.selectSubtitleTrack(it); subtitleTracks = viewModel.getSubtitleTracks() },
                onSearchOnline = { 
                    viewModel.showSubtitleSettings(false)
                    viewModel.showSubtitleSearch(true)
                },
                onSelectLocalFile = { /* Picker logic */ },
                onUpdateSubtitleSize = viewModel::updateSubtitleSize,
                onUpdateSubtitleOpacity = viewModel::updateSubtitleOpacity,
                onUpdateSubtitlePosition = viewModel::updateSubtitlePosition,
                onUpdateTargetLang = viewModel::updateSubtitleTargetLang,
                onUpdateApiKey = viewModel::setSubtitleApiKey,
                onUpdateTranslationEnabled = viewModel::updateSubtitleTranslationEnabled,
                subtitleDelayMs = playbackState.subtitleDelay,
                onUpdateDelay = viewModel::updateSubtitleDelay,
                subtitleSpeed = playbackState.subtitleSpeed,
                onUpdateSpeed = viewModel::updateSubtitleSpeed,
                onDismiss = { viewModel.showSubtitleSettings(false) }
            )
        }

        if (uiState.showSubtitleSearch) {
            com.mhs.player.player.controls.SubtitleSearchSheet(
                videoFilename = currentMedia?.displayName ?: "Video",
                videoPath = currentMedia?.path,
                apiKey = settings.subtitleApiKey,
                preferredLanguage = settings.subtitleTargetLang,
                onSubtitleSelected = { file: java.io.File, lang: String ->
                    viewModel.loadExternalSubtitle(file, lang)
                    viewModel.showSubtitleSearch(false)
                },
                onDismiss = { viewModel.showSubtitleSearch(false) }
            )
        }
    }
}


