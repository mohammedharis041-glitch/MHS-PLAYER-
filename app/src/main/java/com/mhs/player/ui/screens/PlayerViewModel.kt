package com.mhs.player.ui.screens

import android.content.Context
import android.widget.Toast
import android.net.Uri
import android.util.Log
import com.mhs.player.core.utils.ScreenshotHelper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mhs.player.database.FavoritesDao
import com.mhs.player.database.HistoryDao
import com.mhs.player.media.model.MediaItemModel
import com.mhs.player.media.model.PlaybackState
import com.mhs.player.media.model.ResizeMode
import com.mhs.player.player.gestures.GestureController
import com.mhs.player.player.controller.PlaybackManager
import com.mhs.player.player.controller.PlayerController
import com.mhs.player.player.controller.PreviewFrameManager
import com.mhs.player.player.controller.QueueManager
import com.mhs.player.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class PlayerUiState(
    val isControlsVisible: Boolean = true,
    val isFullscreen: Boolean = true,
    val isLocked: Boolean = false,
    val currentResizeMode: ResizeMode = ResizeMode.FIT,
    val showSpeedMenu: Boolean = false,
    val showResizeMenu: Boolean = false,
    val showSubtitleMenu: Boolean = false,
    val showAudioTrackMenu: Boolean = false,
    val isFavorite: Boolean = false,
    val showExitConfirm: Boolean = false,
    val resizeModeOverlay: String? = null,
    /** Non-null while the resume-prompt dialog is showing; holds the saved position in ms. */
    val resumePromptPosition: Long? = null,
    val isRotationLocked: Boolean = false,
    val isSheetOpen: Boolean = false,
    val showSubtitleSettings: Boolean = false,
    val showSubtitleSearch: Boolean = false,
    val showAudioSettings: Boolean = false,
    val showEnhancedSettings: Boolean = false,
    val audioDelayMs: Long = 0L,
    val decoderMessage: String? = null,
    // Seek Preview State
    val isSeeking: Boolean = false,
    val previewFrame: android.graphics.Bitmap? = null,
    val seekPreviewTime: String = "00:00",
    val isDiagnosticsEnabled: Boolean = false,
    val isDimActive: Boolean = false
)

sealed class ImportState {
    object Idle : ImportState()
    data class Importing(val progress: Float) : ImportState()
    data class Success(val localFile: java.io.File) : ImportState()
    data class Error(val message: String) : ImportState()
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val playerController: PlayerController,
    val gestureController: GestureController,
    val previewFrameManager: PreviewFrameManager,
    val queueManager: QueueManager,
    private val playbackManager: PlaybackManager,
    private val favoritesDao: FavoritesDao,
    private val historyDao: HistoryDao,
    private val settingsRepository: SettingsRepository,
    val contentUriImportManager: com.mhs.player.media.imports.ContentUriImportManager
) : ViewModel() {

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _originalImportUri = MutableStateFlow<String?>(null)
    val originalImportUri: StateFlow<String?> = _originalImportUri.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = playerController.playbackState
    val currentMedia: StateFlow<MediaItemModel?> = playerController.currentMedia
    val currentCues = playerController.currentCues
    val queue = queueManager.queue
    val currentQueueIndex = queueManager.currentIndex

    val settings = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsRepository.AppSettings()
    )

    private var hideControlsJob: Job? = null
    private var favoriteJob: Job? = null
    private var resizeOverlayJob: Job? = null

    // Periodic auto-save state — class-level so it persists across emissions
    @Volatile private var lastAutoSaveTime = 0L
    private val AUTO_SAVE_INTERVAL_MS = 5_000L  // Save at most once every 5 seconds

    init {
        playerController.initPlayer()
        observePlaybackForSaving()
        observeMediaChanges()
        observePreviewFrames()
        observeSettingsForController()
    }


    private fun observeSettingsForController() {
        viewModelScope.launch {
            settings.collect { s ->
                val changed = playerController.translationEnabled != s.subtitleTranslationEnabled ||
                             playerController.targetLang != s.subtitleTargetLang ||
                             playerController.currentSubtitleDelay != s.subtitleDelay
                
                playerController.translationEnabled = s.subtitleTranslationEnabled
                playerController.targetLang = s.subtitleTargetLang
                playerController.currentSubtitleDelay = s.subtitleDelay
                
                if (changed) {
                    playerController.refreshCues()
                }

                // Dynamic GPU visual pipeline synchronization
                playerController.updateSmartEnhanceParams(
                    enabled = s.smartEnhanceEnabled,
                    sharpness = s.smartEnhanceSharpness,
                    contrast = s.smartEnhanceContrast,
                    colorBoost = s.smartEnhanceColorBoost,
                    noiseReduction = s.smartEnhanceNoiseReduction,
                    isAdaptive = s.smartEnhanceAdaptive
                )
            }
        }
    }

    private fun observePreviewFrames() {
        viewModelScope.launch {
            previewFrameManager.isVisible.collect { visible ->
                _uiState.value = _uiState.value.copy(isSeeking = visible)
            }
        }
        viewModelScope.launch {
            previewFrameManager.previewBitmap.collect { bitmap ->
                _uiState.value = _uiState.value.copy(previewFrame = bitmap)
            }
        }
    }

    fun updateSubtitleSize(size: Float) = viewModelScope.launch {
        settingsRepository.setSubtitleSize(size)
    }

    fun updateSubtitleOpacity(opacity: Float) = viewModelScope.launch {
        settingsRepository.setSubtitleOpacity(opacity)
    }

    fun updateSubtitleTranslationEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setSubtitleTranslationEnabled(enabled)
    }

    fun updateSubtitleTargetLang(lang: String) = viewModelScope.launch {
        settingsRepository.setSubtitleTargetLang(lang)
    }

    fun showSubtitleSettings(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSubtitleSettings = show, isSheetOpen = show)
        if (show) hideControlsJob?.cancel() else scheduleHideControls()
    }

    fun showSubtitleSearch(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSubtitleSearch = show, isSheetOpen = show)
        if (show) hideControlsJob?.cancel() else scheduleHideControls()
    }

    fun showAudioSettings(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAudioSettings = show, isSheetOpen = show)
        if (show) hideControlsJob?.cancel() else scheduleHideControls()
    }

    fun showEnhancedSettings(show: Boolean) {
        _uiState.value = _uiState.value.copy(showEnhancedSettings = show, isSheetOpen = show)
        if (show) hideControlsJob?.cancel() else scheduleHideControls()
    }

    fun setLowLatencyMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setLowLatencyMode(enabled)
    }

    fun setHardwareScaling(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setHardwareScaling(enabled)
    }

    fun setFasterFullscreen(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setFasterFullscreen(enabled)
    }

    fun setSurfaceStabilization(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setSurfaceStabilization(enabled)
    }

    fun updateAudioDelay(delayMs: Long) {
        _uiState.value = _uiState.value.copy(audioDelayMs = delayMs)
        playerController.setAudioDelay(delayMs)
        currentMedia.value?.let { media ->
            viewModelScope.launch {
                try {
                    val existing = historyDao.getHistoryForMedia(media.id)
                    val subDelay = existing?.subtitleDelay ?: 0L
                    historyDao.updateSyncDelays(media.id, subDelay, delayMs)
                    Log.d("MHSPlayer-Sync", "Saved audio delay: $delayMs")
                } catch (e: Exception) {
                    Log.e("MHSPlayer-Sync", "Failed to save audio delay", e)
                }
            }
        }
    }

    fun updateSubtitleDelay(delayMs: Long) {
        playerController.setSubtitleDelay(delayMs)
        currentMedia.value?.let { 
            playbackManager.saveSubtitleSync(it, delayMs, playbackState.value.subtitleSpeed) 
        }
    }

    fun updateSubtitleSpeed(speed: Float) {
        playerController.setSubtitleSpeed(speed)
        currentMedia.value?.let { 
            playbackManager.saveSubtitleSync(it, playbackState.value.subtitleDelay, speed) 
        }
    }


    fun openMedia(item: MediaItemModel, allItems: List<MediaItemModel>, startIndex: Int) {
        _uiState.value = _uiState.value.copy(currentResizeMode = ResizeMode.FIT)
        gestureController.resetForNewMedia()
        playbackManager.openMedia(item, allItems, startIndex)
        previewFrameManager.prepareRetriever(item.uri)
        checkFavorite(item.id)
    }


    fun resetResumePreference() = viewModelScope.launch {
        settingsRepository.setResumePreference(SettingsRepository.ResumePreference.ASK)
    }

    fun setResumePreference(pref: SettingsRepository.ResumePreference) = viewModelScope.launch {
        settingsRepository.setResumePreference(pref)
    }

    fun setEqualizerEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setEqualizerEnabled(enabled)
    }

    fun setEqualizerBands(bands: String) = viewModelScope.launch {
        settingsRepository.setEqualizerBands(bands)
    }

    fun setEqualizerBassBoost(strength: Int) = viewModelScope.launch {
        settingsRepository.setEqualizerBassBoost(strength)
    }

    fun setEqualizerPreset(preset: Int) = viewModelScope.launch {
        settingsRepository.setEqualizerPreset(preset)
    }

    fun setOrientationMode(mode: SettingsRepository.OrientationMode) = viewModelScope.launch {
        settingsRepository.setOrientationMode(mode)
    }

    fun setEnhancedPlaybackMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setEnhancedPlaybackMode(enabled)
    }

    fun openMediaByUri(uri: Uri) {
        val uriStr = uri.toString()
        if (uriStr == _originalImportUri.value) {
            Log.d("MHSPlayer-Resolution", "URI matches current playing or importing URI. Skipping.")
            return
        }
        _originalImportUri.value = uriStr

        queueManager.clearQueue()
        _uiState.value = _uiState.value.copy(currentResizeMode = ResizeMode.FIT)
        
        val resolvedName = resolveMediaName(uri)
        val cleanedTitle = cleanTitle(resolvedName)
        
        Log.d("MHSPlayer-Resolution", "Final Resolved: displayName='$resolvedName', title='$cleanedTitle'")

        if (uri.scheme == "content") {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w("MHSPlayer-Resolution", "Could not take persistable URI permission (non-fatal): ${e.message}")
            }
        }

        _importState.value = ImportState.Idle
        val item = MediaItemModel(
            id = 0L,
            uri = uri,
            title = cleanedTitle,
            displayName = resolvedName,
            path = uri.toString(),
            folderPath = "",
            folderName = "",
            duration = 0L,
            size = 0L,
            dateAdded = 0L,
            dateModified = 0L,
            mimeType = "video/*",
            mediaType = com.mhs.player.media.model.MediaType.VIDEO
        )
        gestureController.resetForNewMedia()
        playerController.initPlayer()
        playerController.play(item)
        try {
            previewFrameManager.prepareRetriever(uri)
        } catch (e: Exception) {
            Log.w("MHSPlayer-Preview", "prepareRetriever failed for $uri: ${e.message}")
        }
    }

    private fun resolveMediaName(uri: Uri): String {
        Log.d("MHSPlayer-Resolution", "Incoming URI: $uri")
        Log.d("MHSPlayer-Resolution", "Scheme: ${uri.scheme}, Authority: ${uri.authority}")

        if (uri.scheme == "file") {
            val lastSegment = uri.lastPathSegment
            if (!lastSegment.isNullOrBlank()) {
                val decoded = decodeUriPathSegment(lastSegment)
                if (!decoded.isNullOrBlank()) {
                    Log.d("MHSPlayer-Resolution", "Resolved via file scheme path segment: $decoded")
                    return decoded
                }
            }
            val path = uri.path
            if (!path.isNullOrBlank()) {
                val filename = path.substringAfterLast('/')
                Log.d("MHSPlayer-Resolution", "Resolved via file scheme path: $filename")
                return filename
            }
        }

        if (uri.scheme == "content") {
            // 1. Query DISPLAY_NAME
            var displayName: String? = null
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            displayName = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MHSPlayer-Resolution", "Failed to query DISPLAY_NAME: ${e.message}")
            }

            if (!displayName.isNullOrBlank() && !isPurelyNumeric(displayName)) {
                Log.d("MHSPlayer-Resolution", "Resolved via DISPLAY_NAME: $displayName")
                return displayName
            }

            // 2. Query MediaStore TITLE
            var mediaStoreTitle: String? = null
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.MediaStore.MediaColumns.TITLE),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.TITLE)
                        if (index != -1) {
                            mediaStoreTitle = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MHSPlayer-Resolution", "Failed to query MediaColumns.TITLE: ${e.message}")
            }

            if (!mediaStoreTitle.isNullOrBlank() && !isPurelyNumeric(mediaStoreTitle)) {
                Log.d("MHSPlayer-Resolution", "Resolved via MediaStore TITLE: $mediaStoreTitle")
                return mediaStoreTitle
            }

            // 3. Document Provider specific parsing
            val lastSegment = uri.lastPathSegment
            if (!lastSegment.isNullOrBlank()) {
                val decodedSegment = decodeUriPathSegment(lastSegment)
                if (!decodedSegment.isNullOrBlank() && !isPurelyNumeric(decodedSegment)) {
                    // Extract name if last segment contains a path
                    val parsed = decodedSegment.substringAfterLast('/')
                    if (!isPurelyNumeric(parsed)) {
                        Log.d("MHSPlayer-Resolution", "Resolved via decoded path segment: $parsed")
                        return parsed
                    }
                }
            }

            // 4. Try parsing raw path in the content authority itself
            val path = uri.path
            if (!path.isNullOrBlank()) {
                val decodedPath = decodeUriPathSegment(path)
                if (!decodedPath.isNullOrBlank()) {
                    val parsed = decodedPath.substringAfterLast('/')
                    if (!parsed.isNullOrBlank() && !isPurelyNumeric(parsed) && !parsed.contains("content:")) {
                        Log.d("MHSPlayer-Resolution", "Resolved via decoded path: $parsed")
                        return parsed
                    }
                }
            }
            
            // If we got a purely numeric displayName but nothing else, fall back to it only if no other choice
            if (!displayName.isNullOrBlank()) {
                Log.d("MHSPlayer-Resolution", "Fallback to numeric DISPLAY_NAME: $displayName")
                return displayName
            }
        }

        // Generic fallback with timestamp
        val finalFallback = "Video_" + (System.currentTimeMillis() / 1000)
        Log.d("MHSPlayer-Resolution", "Generic fallback: $finalFallback")
        return finalFallback
    }

    private fun decodeUriPathSegment(segment: String?): String? {
        if (segment == null) return null
        return android.net.Uri.decode(segment)
    }

    private fun isPurelyNumeric(str: String): Boolean {
        val namePart = str.substringBeforeLast(".")
        return namePart.isNotEmpty() && namePart.all { it.isDigit() }
    }

    private fun cleanTitle(filename: String): String {
        // Remove extension first
        var clean = filename.substringBeforeLast(".")
        
        // Replace dots, underscores, dashes with spaces
        clean = clean.replace(Regex("[._-]"), " ")
        
        // Extract year (e.g., 2024 or 1999) and capture it
        val yearRegex = Regex("\\b(19\\d\\d|20\\d\\d)\\b")
        val yearMatch = yearRegex.find(clean)
        val yearStr = yearMatch?.value
        
        if (yearStr != null) {
            // Find the position of the year to truncate everything after the year (like 1080p, WEB-DL, DDP5.1, etc.)
            val yearIndex = clean.indexOf(yearStr)
            val namePart = clean.substring(0, yearIndex).trim()
            clean = "$namePart ($yearStr)"
        } else {
            // If no year is found, clean up common release tags
            val junkRegex = Regex("(?i)\\b(1080p|720p|480p|2160p|web[- ]?dl|bluray|hdtv|h[.]?264|x264|h[.]?265|x265|hevc|ddp5[.]1|dd5[.]1|ac3|aac|dts|dual[- ]?audio|multi[- ]?sub|esub|nf|amzn|dovi|hdr|hdr10|10bit|remux)\\b.*")
            clean = clean.replace(junkRegex, "").trim()
        }
        
        // Clean double spaces
        clean = clean.replace(Regex("\\s+"), " ").trim()
        
        return clean.ifBlank { filename }
    }

    fun togglePlayPause() = playerController.playPause()

    fun seekTo(position: Long) = playerController.seekTo(position)

    fun seekForward() {
        val seekMs = (settings.value.seekDuration * 1000).toLong()
        playerController.seekForward(seekMs)
    }

    fun seekBackward() {
        val seekMs = (settings.value.seekDuration * 1000).toLong()
        playerController.seekBackward(seekMs)
    }

    fun skipToNext() {
        saveCurrentProgress()
        playerController.skipToNext()
    }

    fun skipToPrevious() {
        saveCurrentProgress()
        playerController.skipToPrevious()
    }

    fun setPlaybackSpeed(speed: Float) = playerController.setPlaybackSpeed(speed)

    fun saveBrightness(brightness: Float) {
        viewModelScope.launch {
            settingsRepository.setLastBrightness(brightness)
        }
    }

    fun setResizeMode(mode: ResizeMode) {
        _uiState.value = _uiState.value.copy(currentResizeMode = mode)
    }

    fun showControls() {
        _uiState.value = _uiState.value.copy(isControlsVisible = true)
        scheduleHideControls()
    }

    fun hideControls() {
        _uiState.value = _uiState.value.copy(isControlsVisible = false)
    }

    fun toggleControls() {
        if (_uiState.value.isControlsVisible) hideControls()
        else showControls()
    }

    fun toggleLock() {
        val newLock = !_uiState.value.isLocked
        _uiState.value = _uiState.value.copy(isLocked = newLock)
        gestureController.toggleLock()
    }

    fun toggleRotationLock() {
        _uiState.value = _uiState.value.copy(isRotationLocked = !_uiState.value.isRotationLocked)
    }

    fun showSpeedMenu(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSpeedMenu = show)
    }

    fun showResizeMenu(show: Boolean) {
        _uiState.value = _uiState.value.copy(showResizeMenu = show)
    }

    private fun applyVideoScalingForMode(mode: ResizeMode) {
        val scalingMode = when (mode) {
            ResizeMode.ZOOM, ResizeMode.FILL -> androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            else -> androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
        playerController.setVideoScalingMode(scalingMode)
    }

    /** Cycles through resize modes and briefly shows the mode name on screen. */
    fun cycleResizeMode() {
        val modes = ResizeMode.entries.filter { it != ResizeMode.FIXED_WIDTH && it != ResizeMode.FIXED_HEIGHT }
        val next = modes[(modes.indexOf(_uiState.value.currentResizeMode) + 1) % modes.size]
        _uiState.value = _uiState.value.copy(
            currentResizeMode = next,
            resizeModeOverlay = next.label
        )
        applyVideoScalingForMode(next)
        
        // Auto-hide overlay after 1.5 s
        resizeOverlayJob?.cancel()
        resizeOverlayJob = viewModelScope.launch {
            delay(1500)
            _uiState.value = _uiState.value.copy(resizeModeOverlay = null)
        }
    }

    /** Pinch-in resets to FIT mode. */
    fun fitResizeMode() {
        _uiState.value = _uiState.value.copy(
            currentResizeMode = ResizeMode.FIT,
            resizeModeOverlay = ResizeMode.FIT.label
        )
        applyVideoScalingForMode(ResizeMode.FIT)
        
        resizeOverlayJob?.cancel()
        resizeOverlayJob = viewModelScope.launch {
            delay(1500)
            _uiState.value = _uiState.value.copy(resizeModeOverlay = null)
        }
    }

    fun toggleNightMode() {
        _uiState.value = _uiState.value.copy(isDimActive = !_uiState.value.isDimActive)
    }

    fun toggleDiagnostics() {
        _uiState.value = _uiState.value.copy(isDiagnosticsEnabled = !_uiState.value.isDiagnosticsEnabled)
    }

    fun captureScreenshot(context: Context) {
        val surfaceView = playerController.getVideoSurfaceView()
        if (surfaceView == null) {
            Toast.makeText(context, "Playback surface not ready", Toast.LENGTH_SHORT).show()
            return
        }
        ScreenshotHelper.captureScreenshot(surfaceView, context) { success, message ->
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(context, message ?: "Screenshot captured", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun showSubtitleMenu(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSubtitleMenu = show, isSheetOpen = show)
        if (show) hideControlsJob?.cancel() else scheduleHideControls()
    }

    fun showAudioTrackMenu(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAudioTrackMenu = show, isSheetOpen = show)
        if (show) hideControlsJob?.cancel() else scheduleHideControls()
    }

    fun setSheetOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isSheetOpen = open)
        if (open) hideControlsJob?.cancel() else scheduleHideControls()
    }

    fun getSubtitleTracks() = playerController.getSubtitleTracks()
    fun selectSubtitleTrack(index: Int) = playerController.selectSubtitleTrack(index)
    fun getAudioTracks() = playerController.getAudioTracks()
    fun selectAudioTrack(index: Int) {
        playerController.selectAudioTrack(index)
        val media = currentMedia.value ?: return
        viewModelScope.launch {
            try {
                historyDao.updateAudioTrack(media.id, index)
                Log.d("MHSPlayer-Audio", "Saved audio track index $index for ${media.title}")
            } catch (e: Exception) {
                Log.e("MHSPlayer-Audio", "Failed to save audio track index", e)
            }
        }
    }

    /** Load a locally downloaded .srt/.ass subtitle file into ExoPlayer with language context */
    fun loadExternalSubtitle(file: java.io.File, lang: String = "en") {
        viewModelScope.launch {
            Log.d("MHSPlayer-Subtitles", "Pipeline: Applying subtitle file - Filename: '${file.name}', Path: '${file.absolutePath}', Language Context: '$lang'")
            // Update the target language so if translation IS enabled, it uses the correct language.
            // But don't force-enable it; respect the user's current toggle.
            if (lang != "en") {
                settingsRepository.setSubtitleTargetLang(lang)
            }
            playerController.loadExternalSubtitle(file)
            currentMedia.value?.let { 
                playbackManager.saveSubtitlePath(it, file.absolutePath)
            }
        }
    }

    /** Update seek duration preset from settings */
    fun setSeekDurationPreset(seconds: Int) = viewModelScope.launch {
        settingsRepository.setSeekDurationPreset(seconds)
    }

    fun toggleFavorite() {
        val media = currentMedia.value ?: return
        // Let the DB flow update isFavorite — don't do optimistic toggle to avoid race
        playbackManager.toggleFavorite(media)
    }

    private var previewInactivityJob: Job? = null

    fun onSeekPreviewStart(positionMs: Long) {
        previewFrameManager.requestPreview(positionMs)
        
        // Schedule high-accuracy preview if user stays at this position
        previewInactivityJob?.cancel()
        previewInactivityJob = viewModelScope.launch {
            delay(400)
            previewFrameManager.requestPreview(positionMs, highAccuracy = true)
        }
    }

    fun onSeekPreviewEnd() {
        previewInactivityJob?.cancel()
        previewFrameManager.hidePreview()
        
        // Ensure playback continues if it was playing or supposed to play
        if (playerController.playbackState.value.playWhenReady) {
            playerController.player.value?.play()
        }
        
        // Force schedule hide since isSeeking might still be true in state flow
        hideControlsJob?.cancel()
        hideControlsJob = viewModelScope.launch {
            delay(3500)
            if (playerController.playbackState.value.isPlaying && !_uiState.value.isSheetOpen) {
                hideControls()
            }
        }
    }

    fun stopPlayer() {
        val media = currentMedia.value
        val position = playerController.getCurrentPosition()
        if (media != null && position > 0) {
            // Use blocking save so DB write is guaranteed to complete before
            // the coroutine scope or process is killed (critical for large files)
            playbackManager.saveProgressBlocking(media, position)
        }
        playerController.stop()
    }


    fun setSubtitleApiKey(key: String) {
        viewModelScope.launch {
            settingsRepository.setSubtitleApiKey(key)
        }
    }

    fun dismissAllSheets() {
        _uiState.value = _uiState.value.copy(
            showSubtitleSettings = false,
            showSubtitleSearch = false,
            showAudioSettings = false,
            showSpeedMenu = false,
            showResizeMenu = false,
            showAudioTrackMenu = false,
            showSubtitleMenu = false,
            isSheetOpen = false
        )
        scheduleHideControls()
    }

    fun closeMiniPlayer() {
        dismissAllSheets()
        stopPlayer()
        // Reset current media to hide mini player
        playerController.clearCurrentMedia()
    }

    fun scheduleHideControls() {
        hideControlsJob?.cancel()
        if (_uiState.value.isSheetOpen || _uiState.value.isLocked || _uiState.value.isSeeking) return
        hideControlsJob = viewModelScope.launch {
            delay(3500) // Reduced to 3.5s for snappier feel
            if (playerController.playbackState.value.isPlaying && !_uiState.value.isSheetOpen) {
                hideControls()
            }
        }
    }

    private fun saveCurrentProgress() {
        val media = currentMedia.value ?: return
        val position = playerController.getCurrentPosition()
        // Save if more than 2 seconds, but don't save if it was just reset to 0 by skipToPrevious
        if (position > 2000) {
            playbackManager.saveProgress(media, position, force = true)
        }
    }

    fun saveProgressOnMinimize() {
        val media = currentMedia.value ?: return
        val position = playerController.getCurrentPosition()
        if (position > 2000) {
            playbackManager.saveProgressBlocking(media, position)
        }
    }

    private var lastMediaId: Long = -1
    private fun observeMediaChanges() {
        viewModelScope.launch {
            currentMedia.collectLatest { media ->
                if (media == null) {
                    lastMediaId = -1
                    return@collectLatest
                }
                if (media.id == lastMediaId) return@collectLatest
                lastMediaId = media.id

                // Reset auto-save timer for the new media — prevents stale timer from
                // firing immediately and overwriting the position we're about to seek to.
                lastAutoSaveTime = 0L

                // Track and sync favorite status for the current active media
                checkFavorite(media.id)

                // Initialize preview frame retriever for the current active media
                try {
                    gestureController.resetForNewMedia()
                    previewFrameManager.prepareRetriever(media.uri)
                } catch (e: Exception) {
                    Log.e("PlayerViewModel-Preview", "Failed to prepare retriever for ${media.uri}", e)
                }

                // Restore persistent properties (subtitlePath, subtitleDelay, audioDelay)
                try {
                    val history = historyDao.getHistoryForMedia(media.id)
                    if (history != null) {
                        Log.d("MHSPlayer-Restore", "Restoring state for ${media.title}: subtitlePath = ${history.subtitlePath}, subtitleDelay = ${history.subtitleDelay}, audioDelay = ${history.audioDelay}")
                        if (history.subtitleDelay != 0L) {
                            playerController.setSubtitleDelay(history.subtitleDelay)
                        }
                        if (history.audioDelay != 0L) {
                            _uiState.value = _uiState.value.copy(audioDelayMs = history.audioDelay)
                            playerController.setAudioDelay(history.audioDelay)
                        }
                        if (history.audioTrackIndex != -1) {
                            playerController.pendingAudioTrackIndex = history.audioTrackIndex
                        }
                        val path = history.subtitlePath
                        if (!path.isNullOrBlank()) {
                            val file = java.io.File(path)
                            if (file.exists() && file.isFile) {
                                Log.d("MHSPlayer-Subtitles", "Restoring external subtitle from history: ${file.absolutePath}")
                                playerController.loadExternalSubtitle(file, showToast = false)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MHSPlayer-Restore", "Error restoring history state", e)
                }

                // Resume prompt only for VIDEO — audio always starts from beginning
                if (media.isVideo) {
                    delay(500) // Wait for player to stabilize
                    checkAndPromptResume(media)
                }
            }
        }
    }

    private fun checkAndPromptResume(media: MediaItemModel) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val savedPosition = historyDao.getLastPosition(media.id) ?: 0L
            val dur = media.duration

            // Only prompt if progress is significant (>5s) and not at the very end (>95%)
            val nearEnd = dur > 0 && savedPosition > (dur * 0.95)

            android.util.Log.d("MHSPlayer-Resume",
                "checkAndPromptResume: title='${media.title}' savedPos=${savedPosition}ms " +
                "duration=${dur}ms nearEnd=$nearEnd pref=${settings.resumePreference}")

            if (savedPosition > 5000 && !nearEnd) {
                when (settings.resumePreference) {
                    SettingsRepository.ResumePreference.ALWAYS_RESUME -> {
                        android.util.Log.d("MHSPlayer-Resume", "Auto-resuming to ${savedPosition}ms")
                        resumePlayback(savedPosition)
                    }
                    SettingsRepository.ResumePreference.ALWAYS_START_OVER -> {
                        android.util.Log.d("MHSPlayer-Resume", "Starting over (user preference)")
                        // Stay at 0
                    }
                    SettingsRepository.ResumePreference.ASK -> {
                        android.util.Log.d("MHSPlayer-Resume", "Showing resume dialog at ${savedPosition}ms")
                        _uiState.value = _uiState.value.copy(resumePromptPosition = savedPosition)
                    }
                }
            } else {
                android.util.Log.d("MHSPlayer-Resume",
                    "No resume prompt: savedPos=$savedPosition > 5000? ${savedPosition > 5000}, nearEnd=$nearEnd")
            }
        }
    }

    fun toggleDecoder() {
        val current = playerController.getUseSoftwareVideoDecoder()
        val target = !current
        playerController.setUseSoftwareVideoDecoder(target)
        if (target) {
            showDecoderMessage("SW Decoder (Safe Fallback)")
        } else {
            showDecoderMessage("HW Decoder (Optimized)")
        }
    }

    private fun showDecoderMessage(msg: String) {
        _uiState.value = _uiState.value.copy(decoderMessage = msg)
        viewModelScope.launch {
            delay(2500)
            if (_uiState.value.decoderMessage == msg) {
                _uiState.value = _uiState.value.copy(decoderMessage = null)
            }
        }
    }

    fun resumePlayback(position: Long, remember: Boolean = false) {
        if (remember) {
            viewModelScope.launch {
                settingsRepository.setResumePreference(SettingsRepository.ResumePreference.ALWAYS_RESUME)
            }
        }
        playerController.seekTo(position)
        _uiState.value = _uiState.value.copy(resumePromptPosition = null)
    }

    fun startFromBeginning(remember: Boolean = false) {
        if (remember) {
            viewModelScope.launch {
                settingsRepository.setResumePreference(SettingsRepository.ResumePreference.ALWAYS_START_OVER)
            }
        }
        playerController.seekTo(0)
        _uiState.value = _uiState.value.copy(resumePromptPosition = null)
    }

    private fun checkFavorite(mediaId: Long) {
        // Cancel any existing favorite observer before starting a new one
        favoriteJob?.cancel()
        favoriteJob = viewModelScope.launch {
            favoritesDao.isFavorite(mediaId).collect { isFav ->
                _uiState.value = _uiState.value.copy(isFavorite = isFav)
            }
        }
    }

    private fun observePlaybackForSaving() {
        viewModelScope.launch {
            // Use `collect` (not `collectLatest`) so we never skip an emission.
            // The inner debounce (lastAutoSaveTime) prevents DB hammering.
            playerController.playbackState.collect { state ->
                // Never auto-save while the resume dialog is showing — the player is still
                // at position 0 (or near it), and saving here would overwrite the real
                // saved position that we're about to resume to.
                val resumeDialogActive = _uiState.value.resumePromptPosition != null
                if (state.isPlaying && state.currentPosition > 5000 && !resumeDialogActive) {
                    val now = System.currentTimeMillis()
                    if (now - lastAutoSaveTime > AUTO_SAVE_INTERVAL_MS) {
                        lastAutoSaveTime = now
                        val media = currentMedia.value ?: return@collect
                        // Force=true so PlaybackManager's internal debounce doesn't block us;
                        // our own lastAutoSaveTime guard is sufficient.
                        playbackManager.saveProgress(media, state.currentPosition, force = true)
                    }
                }
            }
        }
    }

    fun updateSubtitlePosition(pos: Float) {
        viewModelScope.launch { settingsRepository.setSubtitlePosition(pos) }
    }

    fun setSmartEnhanceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSmartEnhanceEnabled(enabled)
            updateEngineParams()
            playerController.updateSmartEnhanceEffects(enabled)
        }
    }

    fun setSmartEnhanceSharpness(value: Float) {
        viewModelScope.launch {
            settingsRepository.setSmartEnhanceSharpness(value)
            updateEngineParams()
        }
    }

    fun setSmartEnhanceContrast(value: Float) {
        viewModelScope.launch {
            settingsRepository.setSmartEnhanceContrast(value)
            updateEngineParams()
        }
    }

    fun setSmartEnhanceColorBoost(value: Float) {
        viewModelScope.launch {
            settingsRepository.setSmartEnhanceColorBoost(value)
            updateEngineParams()
        }
    }

    fun setSmartEnhanceNoiseReduction(value: Float) {
        viewModelScope.launch {
            settingsRepository.setSmartEnhanceNoiseReduction(value)
            updateEngineParams()
        }
    }

    fun setSmartEnhanceAdaptive(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSmartEnhanceAdaptive(enabled)
            updateEngineParams()
        }
    }

    private fun updateEngineParams() {
        viewModelScope.launch {
            val currentSettings = settings.value
            playerController.updateSmartEnhanceParams(
                enabled = currentSettings.smartEnhanceEnabled,
                sharpness = currentSettings.smartEnhanceSharpness,
                contrast = currentSettings.smartEnhanceContrast,
                colorBoost = currentSettings.smartEnhanceColorBoost,
                noiseReduction = currentSettings.smartEnhanceNoiseReduction,
                isAdaptive = currentSettings.smartEnhanceAdaptive
            )
        }
    }


    override fun onCleared() {
        super.onCleared()
        // stopPlayer() // Removed to allow background/miniplayer playback
        previewFrameManager.release()
    }
}
