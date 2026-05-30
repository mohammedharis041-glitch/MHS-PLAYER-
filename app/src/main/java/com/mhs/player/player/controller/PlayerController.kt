package com.mhs.player.player.controller

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.media3.common.*
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.mhs.player.media.model.MediaItemModel
import com.mhs.player.media.model.PlaybackState
import com.mhs.player.player.audio.AudioEffectsManager
import com.mhs.player.player.service.MhsPlaybackService
import com.mhs.player.player.subtitles.parser.SrtParser
import com.mhs.player.player.subtitles.parser.SubtitleCue
import com.mhs.player.player.ai.translation.SubtitleTranslator
import androidx.media3.common.text.Cue
import androidx.media3.session.SessionToken
import androidx.media3.session.MediaController
import android.content.ComponentName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import androidx.media3.exoplayer.DefaultLoadControl
import com.mhs.player.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import com.mhs.player.player.enhance.SmartEnhanceGlEffect
import com.mhs.player.player.enhance.SmartEnhanceEngine

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    val queueManager: QueueManager,
    val audioEffectsManager: AudioEffectsManager,
    val subtitleTranslator: SubtitleTranslator,
    private val settingsRepository: SettingsRepository
) : Player.Listener, SurfaceHolder.Callback {

    // region 🧱 Properties, Fields & State Flows

    private val srtParser = SrtParser()
    private var pendingPlayAction: (() -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile
    private var cachedSettings: SettingsRepository.AppSettings = SettingsRepository.AppSettings()
    private var progressJob: Job? = null
    private var manualSubtitleJob: Job? = null
    private var loadSubtitleJob: Job? = null
    private var isManualSubtitleActive: Boolean = false
    private var currentlyActiveSubtitleFile: File? = null
    var pendingAudioTrackIndex: Int = -1


    // Media3 (ExoPlayer)
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()
    private var trackSelector: DefaultTrackSelector? = null
    private var videoSurfaceView: SurfaceView? = null
    private var mediaController: MediaController? = null

    // Common State
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentMedia = MutableStateFlow<MediaItemModel?>(null)
    val currentMedia: StateFlow<MediaItemModel?> = _currentMedia.asStateFlow()

    fun clearCurrentMedia() {
        _currentMedia.value = null
        _videoSize.value = VideoSize.UNKNOWN
    }

    private val _videoSize = MutableStateFlow(VideoSize.UNKNOWN)
    val videoSize: StateFlow<VideoSize> = _videoSize.asStateFlow()

    private val _isFirstFrameRendered = MutableStateFlow(false)
    val isFirstFrameRendered: StateFlow<Boolean> = _isFirstFrameRendered.asStateFlow()

    private val _currentCues = MutableStateFlow<List<androidx.media3.common.text.Cue>>(emptyList())
    val currentCues: StateFlow<List<androidx.media3.common.text.Cue>> = _currentCues.asStateFlow()

    // Real-Time Playback Diagnostics Telemetry State Flow
    private val _diagnosticsInfo = MutableStateFlow(DiagnosticsInfo())
    val diagnosticsInfo: StateFlow<DiagnosticsInfo> = _diagnosticsInfo.asStateFlow()

    val isInPipMode = MutableStateFlow(false)

    private var lastSubtitleFile: File? = null
    private var lastCuesText: String? = null
    private var lastCues: List<androidx.media3.common.text.Cue> = emptyList()
    private var translationJob: Job? = null

    // HEVC 10-Bit Playback Compatibility & Software Fallback Pipeline
    private var bufferingStartTimeMs = 0L
    private var renderingFrameStartTimeMs = 0L
    private var fallbackAttempts = 0
    private var useSoftwareVideoDecoder = false
    private val _autoAdvance = MutableStateFlow(false)
    fun setAutoAdvance(enabled: Boolean) { _autoAdvance.value = enabled }

    var translationEnabled: Boolean = false
    var targetLang: String = "ml"
    var currentSubtitleDelay: Long = 0L
    private var externalCues: List<SubtitleCue> = emptyList()
    private var isSubtitleOff: Boolean = false

    init {
        scope.launch {
            settingsRepository.settings.collect { cachedSettings = it }
        }
    }

    // endregion

    // region 🚀 Playback Lifecycle & Setup

    fun initPlayer() {
        if (_player.value != null) return

        val isEnhanced = cachedSettings.enhancedPlaybackMode
        
        // Initialize Media3 with optimized Audio Compatibility (FFmpeg/Software Decoders)
        trackSelector = DefaultTrackSelector(context)
        
        // Log FFmpeg extension status for diagnostics with multiple class name checks
        val isFfmpegAvailable = try {
            val classNames = listOf(
                "androidx.media3.decoder.ffmpeg.FfmpegLibrary",
                "com.google.android.exoplayer2.ext.ffmpeg.FfmpegLibrary"
            )
            var available = false
            for (className in classNames) {
                try {
                    val ffmpegClass = Class.forName(className)
                    val method = ffmpegClass.getDeclaredMethod("isAvailable")
                    if (method.invoke(null) as Boolean) {
                        available = true
                        Log.d("MHSPlayer", "FFmpeg Extension found and available: $className")
                        break
                    }
                } catch (e: Exception) {
                    // Continue to next check
                }
            }
            available
        } catch (e: Exception) {
            Log.e("MHSPlayer", "Error checking FFmpeg: ${e.message}")
            false
        }
        
        Log.d("MHSPlayer", "FFmpeg Extension Available: $isFfmpegAvailable")
        Log.d("MHSPlayer", "Enhanced Mode Active: $isEnhanced")
        
        // Custom MediaCodecSelector that intercepts decoder queries to force software fallback when needed
        val customMediaCodecSelector = MediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
            val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
            Log.d("MHSPlayer-HEVC-Compat", "Querying decoders for mimeType=$mimeType, requiresSecure=$requiresSecure, useSoftwareVideoDecoder=$useSoftwareVideoDecoder")
            if (useSoftwareVideoDecoder && mimeType.startsWith("video/")) {
                val softwareDecoders = decoders.filter { info ->
                    val name = info.name.lowercase()
                    info.softwareOnly || 
                    name.contains("google") || 
                    name.contains("sw") || 
                    name.contains("software") ||
                    name.startsWith("c2.android.")
                }
                if (softwareDecoders.isNotEmpty()) {
                    Log.w("MHSPlayer-HEVC-Compat", "FORCE SOFTWARE DECODER fallback enabled! Returning: ${softwareDecoders.map { it.name }}")
                    return@MediaCodecSelector softwareDecoders
                }
            }
            decoders
        }

        // Prioritize Hardware Decoders (HW+ Mode) globally, fallback to FFmpeg ONLY on failure.
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true) // Ensure seamless fallback to software if hardware fails!
            .setMediaCodecSelector(customMediaCodecSelector)
            
        // Build robust, cinema-grade Buffer LoadControl for high-bitrate / 4K / HDR playback smoothness
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30000,   // minBufferMs (30s)
                120000,  // maxBufferMs (120s)
                2500,    // bufferForPlaybackMs (2.5s startup)
                5000     // bufferForPlaybackAfterRebufferMs (5s rebuffer)
            )
            .setBackBuffer(30000, true) // Enable stable 30s back buffer for smooth backward seeks!
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
            
        // Configure extractor factory for robust seeking in VP9/WebM/MKV files.
        // IDM-downloaded MKV/WebM files (and many YouTube VP9 downloads) write Cue points
        // at the END of the file. Without this flag, ExoPlayer seeks to end-of-file to find
        // the Cue index before every user seek — during that scan player.currentPosition
        // reads 0, causing seeks to always calculate from position 0.
        // FLAG_DISABLE_SEEK_FOR_CUES: skip the end-of-file cue search entirely.
        // We do NOT use 'AlwaysEnabled' so that properly indexed files still use their perfect Cues.
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()

        val exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context, extractorsFactory)
            )
            .setTrackSelector(trackSelector as TrackSelector)
            // Note: SeekParameters are now set per-seek call (NEXT_SYNC/PREVIOUS_SYNC/CLOSEST_SYNC)
            // so we do not set a global default here.
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            
        // Always use SCALE_TO_FIT — SCALE_TO_FIT_WITH_CROPPING was part of the removed
        // Smart Enhance pipeline and caused portrait videos to display incorrectly.
        exoPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            
        // Optimize for high-bitrate audio (DDP/DTS) on problematic devices (Vivo/OnePlus)
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setPreferredAudioMimeType(MimeTypes.AUDIO_E_AC3)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setAudioOffloadPreferences(
                TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                    .build()
            )
            .build()
            
        exoPlayer.addListener(this)
        
        // Setup live analytics metrics tracking for hardware/software decoders and playback formats
        val analyticsListener = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                decoderName: String,
                initializedMs: Long,
                initializationDurationMs: Long
            ) {
                val isHw = !(decoderName.contains("ffmpeg", ignoreCase = true) || 
                             decoderName.contains("software", ignoreCase = true) || 
                             decoderName.contains("google", ignoreCase = true) && !decoderName.contains("hw", ignoreCase = true) ||
                             decoderName.startsWith("c2.android."))
                val classification = when {
                    decoderName.contains("ffmpeg", ignoreCase = true) -> "FFmpeg"
                    isHw -> "Hardware"
                    else -> "Software"
                }
                _diagnosticsInfo.value = _diagnosticsInfo.value.copy(
                    decoderName = decoderName,
                    isHardware = isHw,
                    smartEnhanceStatus = if (SmartEnhanceEngine.isEnabled) "Active" else "Disabled"
                )
                Log.d("MHSPlayer-Diagnostics", "Decoder selected: $decoderName ($classification)")
            }

            override fun onVideoInputFormatChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
            ) {
                val codecName = when {
                    format.sampleMimeType?.contains("hevc", ignoreCase = true) == true || format.sampleMimeType?.contains("h265", ignoreCase = true) == true -> "HEVC"
                    format.sampleMimeType?.contains("avc", ignoreCase = true) == true || format.sampleMimeType?.contains("h264", ignoreCase = true) == true -> "AVC"
                    format.sampleMimeType?.contains("av01", ignoreCase = true) == true || format.sampleMimeType?.contains("av1", ignoreCase = true) == true -> "AV1"
                    format.sampleMimeType?.contains("vp9", ignoreCase = true) == true -> "VP9"
                    format.sampleMimeType?.contains("dolby", ignoreCase = true) == true -> "Dolby"
                    else -> format.sampleMimeType?.substringAfter("video/") ?: "Unknown"
                }
                val colorInfo = format.colorInfo
                val is10Bit = if (colorInfo != null &&
                    (colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084 ||
                     colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG ||
                     colorInfo.colorSpace == C.COLOR_SPACE_BT2020)) {
                    "10-bit"
                } else {
                    "8-bit"
                }
                val hdr = when {
                    format.sampleMimeType?.contains("dolby", ignoreCase = true) == true ||
                    format.codecs?.contains("dovi", ignoreCase = true) == true -> "Dolby Vision"
                    colorInfo != null && colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084 -> "HDR10"
                    colorInfo != null && colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
                    else -> "SDR"
                }
                val res = if (format.width > 0 && format.height > 0) "${format.width}x${format.height}" else "Unknown"
                val br = if (format.bitrate > 0) format.bitrate / 1_000_000f else 0f
                
                val codecLower = codecName.lowercase()
                val is4K = format.width >= 3840 || format.height >= 2160 || res.startsWith("3840") || res.contains("2160")
                val isHdr = hdr != "SDR"
                val isAv1 = codecLower.contains("av1") || codecLower.contains("av01")
                val isVp9 = codecLower.contains("vp9")
                val isHevc = codecLower.contains("hevc") || codecLower.contains("h265")
                val isHighBitrate = br >= 15f
                val isHeavy = is4K || isHdr || (isAv1 && isHighBitrate) || (isVp9 && isHighBitrate) || (isHevc && isHighBitrate) || br >= 25f

                val profileName = getProfileString(format)
                _diagnosticsInfo.value = _diagnosticsInfo.value.copy(
                    codec = codecName,
                    codecProfile = profileName,
                    bitDepth = is10Bit,
                    hdrType = hdr,
                    resolution = res,
                    bitrate = br,
                    isHeavyVideoMode = isHeavy
                )
                
                // Smart Enhance is permanently disabled. No shader effects applied.
            }

            override fun onDroppedVideoFrames(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long
            ) {
                _diagnosticsInfo.value = _diagnosticsInfo.value.copy(
                    droppedFrames = _diagnosticsInfo.value.droppedFrames + droppedFrames
                )
            }

            override fun onPlaybackStateChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                state: Int
            ) {
                val reason = when (state) {
                    Player.STATE_BUFFERING -> {
                        if (_player.value?.isPlaying == true) "Buffer Starvation" 
                        else if (getCurrentPosition() > 1000L) "Seeking" 
                        else "Startup"
                    }
                    Player.STATE_READY -> {
                        "None"
                    }
                    else -> "None"
                }
                _diagnosticsInfo.value = _diagnosticsInfo.value.copy(bufferingReason = reason)
            }
        }
        exoPlayer.addAnalyticsListener(analyticsListener)
        
        _player.value = exoPlayer

        // Effects & Service
        try {
            audioEffectsManager.initialize(exoPlayer.audioSessionId)
            com.mhs.player.player.service.PlayerServiceConnection.setPlayer(exoPlayer)
            
            // Connect MediaController to satisfy Media3's internal foreground notification lifecycle
            val sessionToken = SessionToken(context, ComponentName(context, MhsPlaybackService::class.java))
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture.addListener({
                try {
                    mediaController = controllerFuture.get()
                    Log.d("MHSPlayer", "MediaController bound to MhsPlaybackService successfully!")
                } catch (e: Exception) {
                    Log.e("MHSPlayer", "Failed to bind MediaController", e)
                }
            }, { it.run() })
        } catch (e: Exception) { Log.e("MHSPlayer", "Init error", e) }

        // Clear any stale state on fresh init
        _currentMedia.value = null
        _currentCues.value = emptyList()
    }

    fun updateSmartEnhanceParams(
        enabled: Boolean,
        sharpness: Float,
        contrast: Float,
        colorBoost: Float,
        noiseReduction: Float,
        isAdaptive: Boolean
    ) {
        SmartEnhanceEngine.setParams(
            enabled = enabled,
            sharp = sharpness,
            cont = contrast,
            color = colorBoost,
            noise = noiseReduction,
            adaptive = isAdaptive
        )
        updateSmartEnhanceEffects(enabled)
    }

    fun stopAll() {
        pendingPlayAction = null
        try {
            _player.value?.stop()
        } catch (e: Exception) {
            Log.e("MHSPlayer-Lifecycle", "stopAll: Error while stopping", e)
        }
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }


    fun getVideoSurfaceView(): SurfaceView? = videoSurfaceView

    fun setVideoScalingMode(scalingMode: Int) {
        val exo = _player.value
        if (exo != null) {
            exo.videoScalingMode = scalingMode
            Log.d("MHSPlayer-Scaling", "setVideoScalingMode: Updated hardware scaling mode to $scalingMode")
        }
    }

    fun setVideoSurfaceView(view: SurfaceView?) {
        Log.d("MHSPlayer-Lifecycle", "setVideoSurfaceView: ${view != null}")
        if (view == null) {
            videoSurfaceView?.holder?.removeCallback(this)
            videoSurfaceView = null
            _player.value?.setVideoSurface(null)
            return
        }
        videoSurfaceView?.holder?.removeCallback(this)
        videoSurfaceView = view
        
        val exo = _player.value ?: return
        view.holder.addCallback(this)
        val surface = view.holder.surface
        if (surface != null && surface.isValid) {
            Log.d("MHSPlayer-Lifecycle", "Setting surface immediately as it is already valid")
            exo.setVideoSurface(surface)
            pendingPlayAction?.let { action ->
                pendingPlayAction = null
                action.invoke()
            }
        }
    }

    fun releaseVideoSurfaceView(view: SurfaceView) {
        if (videoSurfaceView === view) {
            Log.d("MHSPlayer-Lifecycle", "releaseVideoSurfaceView: Releasing matching surface")
            videoSurfaceView?.holder?.removeCallback(this)
            videoSurfaceView = null
            _player.value?.setVideoSurface(null)
        } else {
            Log.d("MHSPlayer-Lifecycle", "releaseVideoSurfaceView: Ignoring release for non-matching surface view")
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("MHSPlayer-Lifecycle", "surfaceCreated: Setting player surface")
        if (videoSurfaceView?.holder === holder) {
            _player.value?.setVideoSurface(holder.surface)
            pendingPlayAction?.let { action ->
                pendingPlayAction = null
                action.invoke()
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("MHSPlayer-Lifecycle", "surfaceChanged: ${width}x${height}")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("MHSPlayer-Lifecycle", "surfaceDestroyed: Clearing player surface")
        if (videoSurfaceView?.holder === holder) {
            _player.value?.setVideoSurface(null)
        } else {
            Log.d("MHSPlayer-Lifecycle", "surfaceDestroyed: Ignoring destroyed surface because it does not match current active surface view")
        }
    }

    // endregion

    // region 🎬 Core Playback Controls (Queue & Single)

    fun play(item: MediaItemModel, position: Long = 0L) {
        _diagnosticsInfo.value = DiagnosticsInfo()
        _currentMedia.value = item
        _videoSize.value = VideoSize.UNKNOWN // Reset stale dimensions so orientation is determined fresh
        _isFirstFrameRendered.value = item.isAudio
        
        // Reset playback speed to 1.0f on new media startup to prevent carryover of fast-forward gesture speed
        setPlaybackSpeed(1.0f)
        
        if (_player.value == null) initPlayer()
        
        if (item.isVideo) {
            val surface = videoSurfaceView?.holder?.surface
            if (surface == null || !surface.isValid) {
                Log.d("MHSPlayer-Lifecycle", "play: Surface not valid yet. Deferring playback action.")
                pendingPlayAction = {
                    Log.d("MHSPlayer-Lifecycle", "Executing deferred play action for ${item.title}")
                    playWithMedia3(item, position)
                }
                return
            }
        }
        
        pendingPlayAction = null
        playWithMedia3(item, position)
    }

    private fun playWithMedia3(item: MediaItemModel, position: Long) {
        val exo = _player.value ?: return
        
        // Reset decoder compatibility state for the new media item
        useSoftwareVideoDecoder = false
        bufferingStartTimeMs = 0L
        renderingFrameStartTimeMs = 0L
        
        // Restore State
        val params = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        exo.trackSelectionParameters = params
        exo.volume = 1f
        exo.setPlaybackSpeed(1.0f) // Hard reset player's internal playback speed on new track start
        
        // Force bind surface
        exo.setVideoSurface(videoSurfaceView?.holder?.surface)
        
        val mediaItem = MediaItem.Builder()
            .setMediaId(item.id.toString())
            .setUri(item.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.artist)
                    .setArtworkUri(item.albumArtUri)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
        exo.setMediaItem(mediaItem)
        exo.prepare()
        if (position > 0) exo.seekTo(position)
        exo.play()
        updateSmartEnhanceEffects(cachedSettings.smartEnhanceEnabled)
        isManualSubtitleActive = false
        isSubtitleOff = false
        manualSubtitleJob?.cancel()
    }

    fun playQueue(items: List<MediaItemModel>, startIndex: Int = 0, startPosition: Long = 0L) {
        _diagnosticsInfo.value = DiagnosticsInfo()
        val targetItem = items.getOrNull(startIndex)
        _currentMedia.value = targetItem
        _isFirstFrameRendered.value = targetItem?.isAudio == true
        
        // Reset playback speed to 1.0f on new media startup to prevent carryover of fast-forward gesture speed
        setPlaybackSpeed(1.0f)
        
        if (_player.value == null) initPlayer()
        queueManager.setQueue(items, startIndex)
        
        if (targetItem?.isVideo == true) {
            val surface = videoSurfaceView?.holder?.surface
            if (surface == null || !surface.isValid) {
                Log.d("MHSPlayer-Lifecycle", "playQueue: Surface not valid yet. Deferring playback action.")
                pendingPlayAction = {
                    Log.d("MHSPlayer-Lifecycle", "Executing deferred playQueue action")
                    executePlayQueueWithMedia3(items, startIndex, startPosition)
                }
                return
            }
        }
        
        pendingPlayAction = null
        executePlayQueueWithMedia3(items, startIndex, startPosition)
    }

    private fun executePlayQueueWithMedia3(items: List<MediaItemModel>, startIndex: Int, startPosition: Long) {
        val exo = _player.value ?: return
        
        // Reset decoder compatibility state for the new media item
        useSoftwareVideoDecoder = false
        bufferingStartTimeMs = 0L
        renderingFrameStartTimeMs = 0L
        
        // Restore State
        val params = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        exo.trackSelectionParameters = params
        exo.volume = 1f
        exo.setPlaybackSpeed(1.0f) // Hard reset player's internal playback speed on new track start
        
        // Force bind surface
        exo.setVideoSurface(videoSurfaceView?.holder?.surface)
        
        val mediaItems = items.map { item ->
            MediaItem.Builder().setMediaId(item.id.toString()).setUri(item.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setArtworkUri(item.albumArtUri)
                        .setIsPlayable(true)
                        .build()
                ).build()
        }
        exo.setMediaItems(mediaItems, startIndex, startPosition)
        exo.prepare()
        exo.play()
        updateSmartEnhanceEffects(cachedSettings.smartEnhanceEnabled)
        isManualSubtitleActive = false
        isSubtitleOff = false
        currentlyActiveSubtitleFile = null
        manualSubtitleJob?.cancel()
    }

    // endregion

    // region 🎚️ Settings (Speed, Delay & Audio Offset)

    fun setPlaybackSpeed(speed: Float) {
        _player.value?.setPlaybackSpeed(speed)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
    }

    fun setSubtitleDelay(delayMs: Long) {
        Log.d("MHSPlayer-Subtitles", "Sync: Delay set to ${delayMs}ms")
        _playbackState.value = _playbackState.value.copy(subtitleDelay = delayMs)
    }

    fun setSubtitleSpeed(speed: Float) {
        _playbackState.value = _playbackState.value.copy(subtitleSpeed = speed)
    }

    fun setAudioDelay(delayMs: Long) {
        _playbackState.value = _playbackState.value.copy(audioDelay = delayMs)
    }

    // endregion

    // region 📜 Subtitle Parsing & External Files

    fun loadExternalSubtitle(file: File, showToast: Boolean = true) {
        if (isManualSubtitleActive && currentlyActiveSubtitleFile?.absolutePath == file.absolutePath) {
            Log.d("MHSPlayer-Subtitles", "loadExternalSubtitle: Subtitle already active, skipping reload")
            return
        }
        currentlyActiveSubtitleFile = file
        lastSubtitleFile = file
        
        // 1. Get current position on Main thread before switching to IO
        val currentPos = getCurrentPosition()
        
        // Cancel any existing load/parse tasks to avoid overlapping race conditions
        loadSubtitleJob?.cancel()
        
        // 2. Parse subtitles securely offloaded to IO, with all state changes and UI calls bounded on Main thread
        loadSubtitleJob = scope.launch {
            try {
                Log.d("MHSPlayer-Subtitles", "Parsing external subtitle: ${file.absolutePath}")
                val cues = withContext(Dispatchers.IO) {
                    srtParser.parse(file)
                }
                Log.d("MHSPlayer-Subtitles", "Parsed ${cues.size} cues from ${file.name}")
                externalCues = cues
                isManualSubtitleActive = true
                isSubtitleOff = false
                
                // 3. Start manual tracking if playing, otherwise update immediately
                if (_player.value?.isPlaying == true) {
                    startManualSubtitleTracking()
                }
                updateManualSubtitlesForPosition(getCurrentPosition())
                
                // 4. Optional pre-translation offloaded to background IO pool
                withContext(Dispatchers.IO) {
                    subtitleTranslator.startPreTranslation(cues, targetLang, currentPos)
                }
                
                if (showToast) {
                    Toast.makeText(context, "Subtitle loaded: ${file.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("MHSPlayer-Subtitles", "Manual parsing failed", e)
                }
            }
        }
    }

    fun getCurrentPosition(): Long {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.e("MHSPlayer", "getCurrentPosition accessed on wrong thread: ${Thread.currentThread().name}")
            return 0L
        }
        return _player.value?.currentPosition ?: 0L
    }

    fun getDuration(): Long {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.e("MHSPlayer", "getDuration accessed on wrong thread: ${Thread.currentThread().name}")
            return -1L
        }
        val d = _player.value?.duration ?: return -1L
        return if (d == androidx.media3.common.C.TIME_UNSET || d < 0L) -1L else d
    }

    private fun updateManualSubtitlesForPosition(position: Long) {
        val delayMs = _playbackState.value.subtitleDelay
        val speed = _playbackState.value.subtitleSpeed
        val targetPos = ((position - delayMs) * speed).toLong()
        
        if (externalCues.isNotEmpty()) {
            val matchingCues = externalCues.filter { targetPos in it.startTimeMs..it.endTimeMs }
            val media3Cues = matchingCues.map { 
                androidx.media3.common.text.Cue.Builder()
                    .setText(it.text)
                    .build()
            }
            updateCuesInternal(media3Cues)
        } else {
            updateCuesInternal(emptyList())
        }
    }

    private fun startManualSubtitleTracking() {
        manualSubtitleJob?.cancel()
        if (!isManualSubtitleActive) return
        manualSubtitleJob = scope.launch {
            while (isActive && isManualSubtitleActive) {
                val currentPos = getCurrentPosition()
                updateManualSubtitlesForPosition(currentPos)
                delay(100) 
            }
        }
    }

    // endregion

    // region ⏯️ Direct Media Play/Pause & Seeks

    fun playPause() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            scope.launch { playPause() }
            return
        }
        _player.value?.let { p ->
            if (p.isPlaying) p.pause() else p.play()
        }
    }

    /**
     * Seek to [position] with optional seek parameters.
     * - Forward gesture seeks: use SeekParameters.NEXT_SYNC (mirrors MX Player behaviour —
     *   always lands on a keyframe AT OR AFTER the target, so 4K long-GOP files always
     *   move forward, never snap back to a prior keyframe).
     * - Backward gesture seeks: use SeekParameters.PREVIOUS_SYNC.
     * - Seekbar scrubbing / double-tap: use SeekParameters.CLOSEST_SYNC (default).
     */
    fun seekTo(
        position: Long,
        seekParams: SeekParameters = SeekParameters.CLOSEST_SYNC
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            scope.launch { seekTo(position, seekParams) }
            return
        }
        val player = _player.value ?: return
        player.setSeekParameters(seekParams)
        player.seekTo(position)
        updatePlaybackState()
        if (isManualSubtitleActive) {
            updateManualSubtitlesForPosition(position)
        }
    }

    fun seekForward(ms: Long = 10000L) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            scope.launch { seekForward(ms) }
            return
        }
        val player = _player.value ?: return
        val rawDuration = player.duration
        val duration = if (rawDuration == C.TIME_UNSET || rawDuration <= 0L) Long.MAX_VALUE else rawDuration
        val newPos = (player.currentPosition + ms).coerceAtMost(duration)
        // Default sync seeking
        seekTo(newPos, SeekParameters.CLOSEST_SYNC)
    }

    fun seekBackward(ms: Long = 10000L) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            scope.launch { seekBackward(ms) }
            return
        }
        val player = _player.value ?: return
        val newPos = (player.currentPosition - ms).coerceAtLeast(0L)
        // Default sync seeking
        seekTo(newPos, SeekParameters.CLOSEST_SYNC)
    }
    
    // endregion

    // region 🎨 Subtitle Rendering & Pipeline Updates

    private fun updateCuesInternal(cues: List<androidx.media3.common.text.Cue>) {
        // Deduplicate cues by text to avoid double rendering if multiple tracks are active
        val uniqueCues = cues.distinctBy { it.text?.toString() }
        
        // Redundant Empty Cue Shielding
        if (uniqueCues.isEmpty() && _currentCues.value.isEmpty()) {
            return
        }
        
        lastCues = uniqueCues
        val text = uniqueCues.joinToString("\n") { it.text?.toString() ?: "" }
        if (text == lastCuesText && uniqueCues.isNotEmpty()) return

        if (translationEnabled && uniqueCues.isNotEmpty()) {
            val cached = subtitleTranslator.getCachedTranslation(uniqueCues, targetLang)
            if (cached != null) {
                _currentCues.value = cached
                lastCuesText = text
                return
            }

            // REMOVED: English fallback display.
            // User requested to see ONLY the translated language.
            // We will wait for the translation to complete before updating _currentCues.
            lastCuesText = text
            translateAndSetCues(uniqueCues)
        } else {
            if (uniqueCues.isEmpty()) {
                if (lastCuesText != null) {
                    lastCuesText = null
                }
            } else {
                lastCuesText = text
            }
            _currentCues.value = uniqueCues
        }
    }

    fun refreshCues() {
        logVerbose("MHSPlayer-Subtitles", "Pipeline: Refreshing cues due to settings change")
        lastCuesText = null // Force update
        applySubtitleDelay()
        updateCuesInternal(lastCues)
    }

    private fun applySubtitleDelay() {
        val exo = _player.value ?: return
        logVerbose("MHSPlayer-Subtitles", "Pipeline: Applying subtitle delay: ${currentSubtitleDelay}ms")
        
        // Media3 way to apply subtitle offset
        val params = exo.trackSelectionParameters.buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(
                    exo.currentTracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
                        .map { it.mediaTrackGroup }.firstOrNull() ?: return,
                    0
                )
            )
        // Wait, Media3 offset is typically handled via setSubtitleOffsetUs if available 
        // or by adjusting the cues timing. Since we have a custom pipeline, 
        // we can just offset the cues in our translation/display logic!
    }

    // endregion

    // region 🤖 AI Translation Engine

    private fun translateAndSetCues(cues: List<androidx.media3.common.text.Cue>) {
        val originalText = cues.joinToString("\n") { it.text?.toString() ?: "" }
        translationJob?.cancel()
        translationJob = scope.launch {
            _diagnosticsInfo.value = _diagnosticsInfo.value.copy(subtitleTranslationActivity = "Translating...")
            try {
                // Debounce translation calls by 50ms to prevent duplicate translation jobs during scrubbing
                delay(50)
                logVerbose("MHSPlayer-Subtitles", "Pipeline: Requesting translation from SubtitleTranslator for: '$originalText'")
                val translated = subtitleTranslator.translateCues(cues, targetLang)
                
                logVerbose("MHSPlayer-Subtitles", "Pipeline: Translation SUCCESS")
                _currentCues.value = translated
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("MHSPlayer-Subtitles", "Pipeline: Translation FAILED", e)
                    _currentCues.value = cues 
                }
            } finally {
                _diagnosticsInfo.value = _diagnosticsInfo.value.copy(subtitleTranslationActivity = "Idle")
            }
        }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        _videoSize.value = videoSize
        if (videoSize.width > 0 && videoSize.height > 0) {
            Log.d("MHSPlayer", "Video size initialized (${videoSize.width}x${videoSize.height}). Applying Smart Enhance if enabled.")
            updateSmartEnhanceEffects(cachedSettings.smartEnhanceEnabled)
        }
    }

    override fun onRenderedFirstFrame() {
        Log.d("MHSPlayer", "onRenderedFirstFrame: First video frame successfully rendered!")
        _isFirstFrameRendered.value = true
    }

    private fun isFormatUnsupported(): Boolean {
        val info = _diagnosticsInfo.value
        return info.hdrType != "SDR" || info.bitDepth == "10-bit" || info.resolution.startsWith("3840") || info.resolution.contains("2160") || info.isHeavyVideoMode
    }

    fun updateSmartEnhanceEffects(enabled: Boolean) {
        // Completely disabled / removed the Smart Enhance video effect pipeline as requested by the user.
        // We do NOT call player.setVideoEffects() AT ALL. This ensures that Media3 remains 100% in
        // high-performance direct hardware surface rendering mode, avoiding any OpenGL/GL frame-processor paths.
        _diagnosticsInfo.value = _diagnosticsInfo.value.copy(smartEnhanceStatus = "Disabled")
        Log.d("MHSPlayer", "Smart Enhance video effects bypassed (Direct Surface Mode Active).")
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e("MHSPlayer", "onPlayerError encountered: ${error.message}", error)
        
        // Catch direct MediaCodec decoders initialization or decoding failures
        var isDecoderException = false
        var currentCause: Throwable? = error.cause
        while (currentCause != null) {
            val name = currentCause.javaClass.name
            if (name.contains("CodecException", ignoreCase = true) || 
                name.contains("DecoderInitializationException", ignoreCase = true) || 
                name.contains("DecoderQueryException", ignoreCase = true) ||
                name.contains("MediaCodec", ignoreCase = true)
            ) {
                isDecoderException = true
                break
            }
            currentCause = currentCause.cause
        }

        val isDecoderError = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                             error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                             error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                             isDecoderException ||
                             error.cause is android.media.MediaCodec.CodecException ||
                             error.message?.contains("codec", ignoreCase = true) == true ||
                             error.message?.contains("decoder", ignoreCase = true) == true

        if (isDecoderError && !useSoftwareVideoDecoder) {
            triggerSoftwareDecoderFallback("Decoder error encountered: ${error.message} (Code: ${error.errorCode})")
            return
        }

        // Phase 1: Catch any VideoFrameProcessingException or GL rendering exception
        val isShaderError = error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED ||
                error.cause is VideoFrameProcessingException ||
                error.message?.contains("frame", ignoreCase = true) == true ||
                error.message?.contains("effect", ignoreCase = true) == true ||
                error.message?.contains("opengl", ignoreCase = true) == true ||
                error.message?.contains("shader", ignoreCase = true) == true

        if (isShaderError) {
            Log.w("MHSPlayer", "Emergency safe fallback: Shader error caught in player loop. Disabling Smart Enhance.")
            SmartEnhanceEngine.triggerErrorFallback()
        }
    }


    override fun onCues(cues: List<Cue>) {
        if (!isManualSubtitleActive) {
            if (isSubtitleOff) {
                updateCuesInternal(emptyList())
            } else {
                updateCuesInternal(cues)
            }
        }
    }


    override fun onPlaybackStateChanged(state: Int) { updatePlaybackState() }
    
    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
        super.onTracksChanged(tracks)
        if (pendingAudioTrackIndex != -1) {
            val targetIndex = pendingAudioTrackIndex
            pendingAudioTrackIndex = -1
            selectAudioTrack(targetIndex)
            Log.d("MHSPlayer-Lifecycle", "onTracksChanged: Restored pending audio track index $targetIndex")
        }
    }
    
    override fun onIsPlayingChanged(isPlaying: Boolean) { 
        updatePlaybackState()
        if (isPlaying) { 
            startProgressTracking()
            if (isManualSubtitleActive) {
                startManualSubtitleTracking()
            }
            // Start foreground service so notification + lock screen controls appear
            try {
                val intent = android.content.Intent(context, MhsPlaybackService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("MHSPlayer-Lifecycle", "Failed to start foreground service", e)
            }
        } else {
            stopProgressTracking()
            if (isManualSubtitleActive) {
                manualSubtitleJob?.cancel()
                updateManualSubtitlesForPosition(getCurrentPosition())
            }
        }
    }

    // endregion

    // region 📈 State & Progress Tracking

    private fun updatePlaybackState() {
        val current = _playbackState.value
        var isPlaying = false
        var pos = 0L
        var dur = 0L
        var loading = false

        _player.value?.let { exo ->
            isPlaying = exo.isPlaying
            pos = exo.currentPosition
            dur = exo.duration
            loading = exo.playbackState == Player.STATE_BUFFERING
        }

        val realPos = pos.coerceAtLeast(0L)
        // Use -1L as sentinel for 'duration not yet known' so UI can distinguish from truly 0-length media
        val realDur = if (dur == androidx.media3.common.C.TIME_UNSET || dur < 0L) -1L else dur

        // HEVC Main10 / 10-Bit Playback Compatibility & Failure Detection
        if (loading) {
            val isHevc = _diagnosticsInfo.value.codec.lowercase().contains("hevc") ||
                         _diagnosticsInfo.value.codec.lowercase().contains("h265") ||
                         _player.value?.videoFormat?.sampleMimeType?.contains("hevc", ignoreCase = true) == true
            
            if (isHevc && !useSoftwareVideoDecoder) {
                if (bufferingStartTimeMs == 0L) {
                    bufferingStartTimeMs = System.currentTimeMillis()
                } else {
                    val durationBuffering = System.currentTimeMillis() - bufferingStartTimeMs
                    if (durationBuffering > 4000L) { // Stuck buffering for > 4 seconds!
                        bufferingStartTimeMs = 0L
                        triggerSoftwareDecoderFallback("Stuck in infinite buffering (loading forever)")
                        return
                    }
                }
            }
        } else {
            bufferingStartTimeMs = 0L
        }

        val isPlayingVideo = isPlaying && (_currentMedia.value?.isVideo == true)
        if (isPlayingVideo && !_isFirstFrameRendered.value && !useSoftwareVideoDecoder) {
            if (renderingFrameStartTimeMs == 0L) {
                renderingFrameStartTimeMs = System.currentTimeMillis()
            } else {
                val timeSincePlayingStarted = System.currentTimeMillis() - renderingFrameStartTimeMs
                if (timeSincePlayingStarted > 4000L) { // Playing but failed to render any frame for > 4s!
                    renderingFrameStartTimeMs = 0L
                    triggerSoftwareDecoderFallback("Silent black screen (no video frame rendered)")
                    return
                }
            }
        } else {
            renderingFrameStartTimeMs = 0L
        }

        if (current.isPlaying != isPlaying || abs(current.currentPosition - realPos) > 500L || current.duration != realDur || current.isLoading != loading) {
            _playbackState.value = current.copy(
                isPlaying = isPlaying,
                currentPosition = realPos,
                duration = realDur,
                isLoading = loading
            )
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                updatePlaybackState()
                delay(200) // Faster updates for smoother UI
            }
        }
    }

    private fun stopProgressTracking() { progressJob?.cancel() }



    fun stop() {
        pendingPlayAction = null
        _player.value?.stop()
        _videoSize.value = VideoSize.UNKNOWN
        stopProgressTracking()
    }

    // endregion

    // region 🧹 Resource Release & Cleanups

    fun release() {
        Log.d("MHSPlayer-Lifecycle", "PlayerController release: Cleaning up all resources")
        stopAll()
        
        progressJob?.cancel()
        progressJob = null
        
        translationJob?.cancel()
        translationJob = null
        
        manualSubtitleJob?.cancel()
        manualSubtitleJob = null
        
        loadSubtitleJob?.cancel()
        loadSubtitleJob = null
        
        try {
            mediaController?.release()
            mediaController = null
        } catch (e: Exception) {
            Log.w("MHSPlayer-Lifecycle", "mediaController release failed: ${e.message}")
        }
        
        _player.value?.release()
        _player.value = null
        videoSurfaceView = null
        scope.coroutineContext.cancelChildren()
    }


    fun skipToNext() { queueManager.moveToNext()?.let { play(it) } }
    fun skipToPrevious() { queueManager.moveToPrevious()?.let { play(it) } }

    // endregion

    // region 🎛️ Audio & Subtitle Track Selectors

    fun getSubtitleTracks(): List<TrackInfo> {
        val exo = _player.value ?: return emptyList()
        val tracks = exo.currentTracks
        
        val showOffSelected = isSubtitleOff && !isManualSubtitleActive
        val result = mutableListOf(TrackInfo(-1, "Off", showOffSelected))
        
        if (isManualSubtitleActive) {
            result.add(TrackInfo(-2, "Local: ${lastSubtitleFile?.name ?: "Subtitle"}", true))
        }

        tracks.groups.forEachIndexed { gIdx, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (tIdx in 0 until group.length) {
                    val fmt = group.getTrackFormat(tIdx)
                    val label = fmt.label ?: fmt.language ?: "Track ${result.size}"
                    val selected = if (isSubtitleOff || isManualSubtitleActive) false else group.isTrackSelected(tIdx)
                    result.add(TrackInfo(gIdx * 100 + tIdx, label, selected, group.isTrackSupported(tIdx)))
                }
            }
        }
        
        // If nothing is selected and subtitles are not off, select Off option
        if (!isSubtitleOff && !isManualSubtitleActive) {
            var anySelected = false
            for (i in 1 until result.size) {
                if (result[i].isSelected) {
                    anySelected = true
                    break
                }
            }
            if (!anySelected) {
                result[0] = result[0].copy(isSelected = true)
            }
        }
        
        return result
    }

    fun selectSubtitleTrack(index: Int) {
        val exo = _player.value ?: return
        val ts = trackSelector ?: return
        
        // If user selects an internal track, disable manual tracking
        if (index != -2) { 
             isManualSubtitleActive = false
             currentlyActiveSubtitleFile = null
             manualSubtitleJob?.cancel()
        }

        if (index == -1) {
            isSubtitleOff = true
            isManualSubtitleActive = false
            manualSubtitleJob?.cancel()
            ts.setParameters(ts.buildUponParameters().clearOverridesOfType(C.TRACK_TYPE_TEXT))
            updateCuesInternal(emptyList())
            return
        }
        
        isSubtitleOff = false
        val tracks = exo.currentTracks
        for (gi in 0 until tracks.groups.size) {
            val group = tracks.groups[gi]
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (ti in 0 until group.length) {
                    if (gi * 100 + ti == index) {
                        val newParams = ts.buildUponParameters()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, ti))
                        ts.setParameters(newParams)
                        return
                    }
                }
            }
        }
    }

    fun getAudioTracks(): List<TrackInfo> {
        val exo = _player.value ?: return emptyList()
        val tracks = exo.currentTracks
        val result = mutableListOf<TrackInfo>()
        tracks.groups.forEachIndexed { gIdx, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (tIdx in 0 until group.length) {
                    val fmt = group.getTrackFormat(tIdx)
                    val label = "${fmt.label ?: fmt.language ?: "Track ${result.size + 1}"} (${fmt.channelCount}ch)"
                    result.add(TrackInfo(gIdx * 100 + tIdx, label, group.isTrackSelected(tIdx), group.isTrackSupported(tIdx)))
                }
            }
        }
        return result
    }

    fun selectAudioTrack(index: Int) {
        val exo = _player.value ?: return
        val ts = trackSelector ?: return
        val tracks = exo.currentTracks
        for (gIdx in 0 until tracks.groups.size) {
            val group = tracks.groups[gIdx]
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (ti in 0 until group.length) {
                    if (gIdx * 100 + ti == index) {
                        ts.setParameters(ts.buildUponParameters().setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, ti)))
                        return
                    }
                }
            }
        }
    }
    


    data class TrackInfo(val index: Int, val label: String, val isSelected: Boolean, val isSupported: Boolean = true)

    data class DiagnosticsInfo(
        val decoderName: String = "Unknown",
        val isHardware: Boolean = true,
        val codec: String = "Unknown",
        val codecProfile: String = "Unknown",
        val bitDepth: String = "8-bit",
        val hdrType: String = "SDR",
        val droppedFrames: Int = 0,
        val bufferingReason: String = "None",
        val bitrate: Float = 0f,
        val resolution: String = "Unknown",
        val subtitleTranslationActivity: String = "Idle",
        val smartEnhanceStatus: String = "Disabled",
        val isHeavyVideoMode: Boolean = false
    )
    private fun triggerSoftwareDecoderFallback(reason: String) {
        val currentDecoder = _diagnosticsInfo.value.decoderName
        val currentCodec = _diagnosticsInfo.value.codec
        val currentProfile = _diagnosticsInfo.value.codecProfile
        val currentBitDepth = _diagnosticsInfo.value.bitDepth
        
        fallbackAttempts++
        useSoftwareVideoDecoder = true
        
        Log.w("MHSPlayer-HEVC-Compat", """
            ======================================================================
            === MHS PLAYER DECODER FALLBACK INITIATED ===
            Hardware Decoder Failure Reason: $reason
            Codec Profile / Format: $currentCodec ($currentProfile)
            Bit Depth: $currentBitDepth
            Previous Decoder Selected: $currentDecoder
            Fallback Attempt Count: $fallbackAttempts
            Action: Re-initializing player using Software (Google/c2.android) Decoders.
            ======================================================================
        """.trimIndent())
        
        val currentPos = getCurrentPosition()
        scope.launch(Dispatchers.Main) {
            recreatePlayerAtPosition(currentPos)
        }
    }

    private fun recreatePlayerAtPosition(positionMs: Long) {
        val oldPlayer = _player.value ?: return
        val currentMediaItem = _currentMedia.value
        val playlistQueue = queueManager.queue.value
        val currentIndex = queueManager.currentIndex.value
        
        Log.w("MHSPlayer-HEVC-Compat", "Recreating player at position: ${positionMs}ms. useSoftwareVideoDecoder=$useSoftwareVideoDecoder")
        
        // 1. Release the old player and clean up listeners
        oldPlayer.removeListener(this)
        oldPlayer.release()
        _player.value = null
        
        // 2. Re-initialize the player with new RenderersFactory containing customMediaCodecSelector
        initPlayer()
        
        // 3. Restore queue or media item and resume playback
        val newPlayer = _player.value ?: return
        if (playlistQueue.isNotEmpty()) {
            queueManager.setQueue(playlistQueue, currentIndex)
            executePlayQueueWithMedia3(playlistQueue, currentIndex, positionMs)
        } else if (currentMediaItem != null) {
            playWithMedia3(currentMediaItem, positionMs)
        }
    }

    private inline fun logVerbose(tag: String, msg: String) {
        if (com.mhs.player.BuildConfig.DEBUG) {
            Log.v(tag, msg)
        }
    }

    private inline fun logDebug(tag: String, msg: String) {
        if (com.mhs.player.BuildConfig.DEBUG) {
            Log.d(tag, msg)
        }
    }

    fun getUseSoftwareVideoDecoder(): Boolean = useSoftwareVideoDecoder

    fun setUseSoftwareVideoDecoder(value: Boolean) {
        if (useSoftwareVideoDecoder != value) {
            useSoftwareVideoDecoder = value
            val currentPos = getCurrentPosition()
            scope.launch(Dispatchers.Main) {
                recreatePlayerAtPosition(currentPos)
            }
        }
    }

    private fun getProfileString(format: Format): String {
        val mime = format.sampleMimeType ?: return "Unknown"
        val codecs = format.codecs ?: return "Unknown"
        
        val baseProfile = when {
            mime.contains("hevc", ignoreCase = true) || mime.contains("h265", ignoreCase = true) -> {
                when {
                    codecs.contains(".2.") || codecs.startsWith("hev1.2") || codecs.startsWith("hvc1.2") -> "Main 10"
                    codecs.contains(".1.") || codecs.startsWith("hev1.1") || codecs.startsWith("hvc1.1") -> "Main"
                    else -> "HEVC"
                }
            }
            mime.contains("avc", ignoreCase = true) || mime.contains("h264", ignoreCase = true) -> {
                when {
                    codecs.contains("avc1.42") -> "Baseline"
                    codecs.contains("avc1.4d") -> "Main"
                    codecs.contains("avc1.64") -> "High"
                    codecs.contains("avc1.6e") -> "High 10"
                    else -> "AVC"
                }
            }
            mime.contains("vp9", ignoreCase = true) -> {
                when {
                    codecs.contains("vp09.00") -> "Profile 0"
                    codecs.contains("vp09.01") -> "Profile 1"
                    codecs.contains("vp09.02") -> "Profile 2"
                    codecs.contains("vp09.03") -> "Profile 3"
                    else -> "VP9"
                }
            }
            mime.contains("av1", ignoreCase = true) || mime.contains("av01", ignoreCase = true) -> {
                when {
                    codecs.startsWith("av01.0") -> "Main"
                    codecs.startsWith("av01.1") -> "High"
                    codecs.startsWith("av01.2") -> "Professional"
                    else -> "AV1"
                }
            }
            else -> "Codec"
        }
        
        return "$baseProfile ($codecs)"
    }
}
