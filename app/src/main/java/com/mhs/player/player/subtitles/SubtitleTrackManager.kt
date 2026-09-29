package com.mhs.player.player.subtitles

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2-beta: Fix for issue #1 - Disappearing Subtitle Tracks
 *
 * Previously tapping "Off" cleared the track. Now we cache the last track
 * and only disable rendering, keeping one-tap re-enable.
 */
data class CachedSubtitleTrack(
    val uri: Uri,
    val language: String,
    val label: String,
    val isOnline: Boolean
)

@Singleton
class SubtitleTrackManager @Inject constructor() {

    private val _cachedTrack = MutableStateFlow<CachedSubtitleTrack?>(null)
    val cachedTrack: StateFlow<CachedSubtitleTrack?> = _cachedTrack

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled

    fun cacheTrack(track: CachedSubtitleTrack) {
        _cachedTrack.value = track
        _isEnabled.value = true
    }

    /**
     * Disable rendering WITHOUT clearing cache.
     * This is the fix: Off != Remove
     */
    fun disableWithoutClearing() {
        _isEnabled.value = false
        // Do NOT null _cachedTrack
    }

    fun enable() {
        _isEnabled.value = true
    }

    fun toggle(): Boolean {
        _isEnabled.value = !_isEnabled.value
        return _isEnabled.value
    }

    fun clear() {
        _cachedTrack.value = null
        _isEnabled.value = true
    }

    fun hasCachedTrack(): Boolean = _cachedTrack.value != null
}
