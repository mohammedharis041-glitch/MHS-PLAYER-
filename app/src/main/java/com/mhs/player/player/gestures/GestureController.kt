package com.mhs.player.player.gestures

import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt
import com.mhs.player.player.controller.PlayerController

@Singleton
class GestureController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerController: PlayerController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    enum class GestureType { NONE, SEEK, VOLUME, BRIGHTNESS }

    data class GestureState(
        val activeGesture: GestureType = GestureType.NONE,
        val seekDelta: Long = 0L,
        val volumePercent: Float = 0f,
        val brightnessPercent: Int = 50,
        val isLocked: Boolean = false
    )

    private val _gestureState = MutableStateFlow(GestureState())
    val gestureState: StateFlow<GestureState> = _gestureState.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    // Track cumulative values during a gesture
    private var startVolume = 0
    private var startBrightness = 0f
    private var gestureStartPosition = 0L
    private var lastCommittedSeekPosition = -1L  // Last position we explicitly seeked to
    private var volumeAccumulator = 0f
    private var currentVolumeDelta = 0f
    private var currentBrightnessDelta = 0f
    private var lastGestureEndTime = 0L

    // Sensitivity: 1.0 = default
    // seek sensitivity: fraction of duration per full-width swipe (0.1 = 10%)
    private var seekSensitivity = 1.0f
    // vertical sensitivity: how much of range per full-height swipe (0.5 = 50%)
    private var verticalSensitivity = 1.0f

    // Activity reference for brightness (must be set when player screen is active)
    private var activityRef: ComponentActivity? = null
    private var autoHideJob: Job? = null

    fun attachActivity(activity: ComponentActivity) {
        activityRef = activity
        // Initialize brightness from current window setting
        val lp = activity.window.attributes
        val currentBrightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
        _gestureState.value = _gestureState.value.copy(
            brightnessPercent = (currentBrightness * 100).roundToInt().coerceIn(1, 100),
            volumePercent = getCurrentVolumePercent().toFloat()
        )
        startBrightness = currentBrightness
    }

    fun detachActivity() {
        resetBrightness()
        activityRef = null
    }

    private fun resetBrightness() {
        val activity = activityRef ?: return
        val lp = activity.window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = lp
    }

    fun setSensitivities(seek: Float, swipe: Float) {
        seekSensitivity = seek
        verticalSensitivity = swipe
    }

    // Keep old API for compatibility
    fun setSensitivity(s: Float) {
        seekSensitivity = s
        verticalSensitivity = s
    }

    fun onGestureStart(x: Float, y: Float, screenWidth: Float, screenHeight: Float) {
        if (_gestureState.value.isLocked) return
        
        val now = System.currentTimeMillis()
        if (now - lastGestureEndTime > 1500L) {
            lastCommittedSeekPosition = -1L
        }
        
        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        volumeAccumulator = startVolume.toFloat()
        gestureStartPosition = playerController.getCurrentPosition()
        currentVolumeDelta = 0f
        currentBrightnessDelta = 0f

        // Capture current brightness
        val activity = activityRef
        if (activity != null) {
            val lp = activity.window.attributes
            startBrightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
        }
    }

    /** Called when a new media item starts to clear stale seek chain state. */
    fun resetForNewMedia() {
        lastCommittedSeekPosition = -1L
        gestureStartPosition = 0L
        Log.d("MHSPlayer-Gesture", "resetForNewMedia: seek chain state cleared")
    }

    fun onHorizontalScroll(totalDeltaX: Float, totalWidth: Float) {
        if (_gestureState.value.isLocked) return
        val duration = playerController.getDuration()
        val effectiveDuration = if (duration > 0L) duration else 1800000L // 30 minutes fallback under stress/unset

        // Highly precise, controllable seeking:
        // A full-screen horizontal swipe seeks a maximum of 90 seconds, scaled by seekSensitivity.
        // For short videos (< 5 minutes), we scale it down proportionally to 30% of duration.
        val baseSeekRange = if (effectiveDuration < 300000L) {
            (effectiveDuration * 0.30f).toLong()
        } else {
            90000L // 90 seconds
        }
        val seekRange = (baseSeekRange * seekSensitivity).toLong()
        val delta = ((totalDeltaX / totalWidth) * seekRange).toLong()

        _gestureState.value = _gestureState.value.copy(
            activeGesture = GestureType.SEEK,
            seekDelta = delta
        )
        resetAutoHideTimer()
    }

    fun onVerticalScroll(totalDeltaY: Float, x: Float, screenWidth: Float, screenHeight: Float) {
        if (_gestureState.value.isLocked) return
        // Scale up swipe sensitivity and responsiveness to feel extremely premium, fast, and precise
        val targetDelta = -(totalDeltaY / screenHeight) * (verticalSensitivity * 0.8f)
        
        if (x < screenWidth * 0.25f) {
            // Smoothly interpolate brightness delta with 0.8f factor to remove delay/lag
            currentBrightnessDelta = currentBrightnessDelta + 0.8f * (targetDelta - currentBrightnessDelta)
            adjustBrightness(currentBrightnessDelta)
        } else if (x > screenWidth * 0.75f) {
            // Smoothly interpolate volume delta with 0.8f factor to remove delay/lag
            currentVolumeDelta = currentVolumeDelta + 0.8f * (targetDelta - currentVolumeDelta)
            adjustVolume(currentVolumeDelta)
        }
        resetAutoHideTimer()
    }

    fun onGestureEnd(capturedStartPosition: Long = -1L) {
        Log.d("MHSPlayer-Gesture", "onGestureEnd: state=${_gestureState.value.activeGesture}")
        autoHideJob?.cancel()
        val state = _gestureState.value
        if (state.activeGesture == GestureType.SEEK) {
            // Use UI-captured start position (from Compose main thread playbackState).
            // Fall back to lastCommittedSeekPosition if capturedStartPosition looks stale (equals
            // an old value from before a prior seek that Compose hasn't recomposed yet).
            val basePos = when {
                capturedStartPosition >= 0L -> capturedStartPosition
                else -> gestureStartPosition
            }
            // If we seeked recently and the captured position hasn't advanced past our last commit,
            // chain from the last committed position to prevent seeks from same stale origin
            val startPos = if (lastCommittedSeekPosition > 0L && basePos < lastCommittedSeekPosition) {
                Log.d("MHSPlayer-Gesture", "Stale gestureStartPosition detected ($basePos < lastCommit $lastCommittedSeekPosition) - chaining from lastCommittedSeekPosition")
                lastCommittedSeekPosition
            } else {
                basePos
            }
            val duration = playerController.getDuration()
            val maxPos = if (duration > 0L) duration else Long.MAX_VALUE
            val newPos = (startPos + state.seekDelta)
                .coerceIn(0L, maxPos)
            Log.d("MHSPlayer-Gesture", "Seeking: startPos=$startPos delta=${state.seekDelta} → newPos=$newPos (duration=$duration)")
            lastCommittedSeekPosition = newPos
            // Revert back to CLOSEST_SYNC. EXACT is violently rejected by the hardware decoder on this device.
            val seekParams = androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC
            playerController.seekTo(newPos, seekParams)
        }
        _gestureState.value = _gestureState.value.copy(
            activeGesture = GestureType.NONE,
            seekDelta = 0L
        )
        // Don't reset lastCommittedSeekPosition here — it's needed for the next gesture's chain check
        lastGestureEndTime = System.currentTimeMillis()
        currentVolumeDelta = 0f
        currentBrightnessDelta = 0f
    }

    private fun resetAutoHideTimer() {
        autoHideJob?.cancel()
        
        // Never auto-seek or auto-hide preview while holding seek gesture
        val currentGesture = _gestureState.value.activeGesture
        if (currentGesture == GestureType.SEEK) return
        
        autoHideJob = scope.launch {
            delay(1000) // Auto hide after 1s of no updates
            if (_gestureState.value.activeGesture != GestureType.NONE) {
                Log.d("MHSPlayer-Gesture", "Auto-hiding gesture overlay (timeout)")
                onGestureEnd()
            }
        }
    }

    fun onDoubleTapLeft(seekMs: Long = 10000L) {
        if (_gestureState.value.isLocked) return
        playerController.seekBackward(seekMs)
    }

    fun onDoubleTapRight(seekMs: Long = 10000L) {
        if (_gestureState.value.isLocked) return
        playerController.seekForward(seekMs)
    }

    fun toggleLock() {
        _gestureState.value = _gestureState.value.copy(
            isLocked = !_gestureState.value.isLocked
        )
    }

    fun isPlayerActive(): Boolean {
        return activityRef != null
    }

    fun triggerVolumeKey(isUp: Boolean) {
        if (_gestureState.value.isLocked) return
        
        // Cancel any pending auto-hide job
        autoHideJob?.cancel()
        
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        // Make volume key increments faster by stepping by 2 on standard max=15 stream volumes, or a proportional scale
        val step = if (maxVolume <= 15) 2 else (maxVolume / 8).coerceAtLeast(2)
        val newVolume = if (isUp) {
            (currentVolume + step).coerceAtMost(maxVolume)
        } else {
            (currentVolume - step).coerceAtLeast(0)
        }
        
        // Adjust the volume with 0 flags to completely suppress standard Android volume UI
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        
        // Synchronize accumulator states so gestures starting next remain seamless
        startVolume = newVolume
        volumeAccumulator = newVolume.toFloat()
        
        val percent = (newVolume.toFloat() / maxVolume.toFloat()) * 100f
        _gestureState.value = _gestureState.value.copy(
            activeGesture = GestureType.VOLUME,
            volumePercent = percent
        )
        
        resetAutoHideTimer()
    }

    private fun adjustVolume(normalizedDelta: Float) {
        // volumeAccumulator is in [0, maxVolume] float range
        // normalizedDelta is fraction of total screen height
        val delta = normalizedDelta * maxVolume
        volumeAccumulator = (startVolume + delta).coerceIn(0f, maxVolume.toFloat())
        
        val newVolumeInt = volumeAccumulator.roundToInt()
        // Only update system volume if it actually changes a step to avoid jitter
        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != newVolumeInt) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolumeInt, 0)
        }
        
        val percent = (volumeAccumulator / maxVolume.toFloat()) * 100f
        _gestureState.value = _gestureState.value.copy(
            activeGesture = GestureType.VOLUME,
            volumePercent = percent
        )
    }

    private fun adjustBrightness(normalizedDelta: Float) {
        // normalizedDelta is in [-1, 1] from startBrightness
        val newBrightness = (startBrightness + normalizedDelta).coerceIn(0.01f, 1f)
        val percent = (newBrightness * 100).roundToInt()

        // Apply to window immediately
        val activity = activityRef
        if (activity != null) {
            val lp = activity.window.attributes
            lp.screenBrightness = newBrightness
            activity.window.attributes = lp
        }

        _gestureState.value = _gestureState.value.copy(
            activeGesture = GestureType.BRIGHTNESS,
            brightnessPercent = percent
        )
    }

    fun applyBrightnessToWindow(activity: ComponentActivity) {
        val brightness = _gestureState.value.brightnessPercent / 100f
        val lp = activity.window.attributes
        lp.screenBrightness = brightness.coerceIn(0.01f, 1f)
        activity.window.attributes = lp
    }

    fun getCurrentVolumePercent(): Int {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((current.toFloat() / maxVolume) * 100).roundToInt()
    }

    fun getCurrentBrightnessPercent(activity: ComponentActivity): Int {
        val lp = activity.window.attributes
        val brightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
        return (brightness * 100).roundToInt()
    }
}
