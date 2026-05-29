package com.mhs.player.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    val settings = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SettingsRepository.AppSettings(showOnboarding = false)
    )

    fun setSeekDuration(seconds: Int) = viewModelScope.launch {
        repository.setSeekDuration(seconds)
    }

    fun setGestureSensitivity(value: Float) = viewModelScope.launch {
        repository.setGestureSensitivity(value)
    }

    fun setSeekSensitivity(value: Float) = viewModelScope.launch {
        repository.setSeekSensitivity(value)
    }

    fun setSwipeSensitivity(value: Float) = viewModelScope.launch {
        repository.setSwipeSensitivity(value)
    }

    fun setSubtitleSize(size: Float) = viewModelScope.launch {
        repository.setSubtitleSize(size)
    }

    fun setPlaybackSpeed(speed: Float) = viewModelScope.launch {
        repository.setPlaybackSpeed(speed)
    }

    fun setRememberPosition(remember: Boolean) = viewModelScope.launch {
        repository.setRememberPosition(remember)
    }

    fun setHardwareDecoding(enabled: Boolean) = viewModelScope.launch {
        repository.setHardwareDecoding(enabled)
    }

    fun setEnhancedPlaybackMode(enabled: Boolean) = viewModelScope.launch {
        repository.setEnhancedPlaybackMode(enabled)
    }

    fun setBrightnessGesture(enabled: Boolean) = viewModelScope.launch {
        repository.setBrightnessGesture(enabled)
    }

    fun setVolumeGesture(enabled: Boolean) = viewModelScope.launch {
        repository.setVolumeGesture(enabled)
    }

    fun setSeekGesture(enabled: Boolean) = viewModelScope.launch {
        repository.setSeekGesture(enabled)
    }

    fun setDoubleTapSeek(enabled: Boolean) = viewModelScope.launch {
        repository.setDoubleTapSeek(enabled)
    }

    fun setPipOnHome(enabled: Boolean) = viewModelScope.launch {
        repository.setPipOnHome(enabled)
    }

    fun setDefaultSort(sort: String) = viewModelScope.launch {
        repository.setDefaultSort(sort)
    }

    fun setShowHidden(show: Boolean) = viewModelScope.launch {
        repository.setShowHidden(show)
    }

    fun setSubtitleBackground(enabled: Boolean) = viewModelScope.launch {
        repository.setSubtitleBackground(enabled)
    }

    fun setDarkMode(enabled: Boolean) = viewModelScope.launch {
        repository.setDarkMode(enabled)
    }

    fun setEqualizerEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setEqualizerEnabled(enabled)
    }

    fun setResumePreference(pref: SettingsRepository.ResumePreference) = viewModelScope.launch {
        repository.setResumePreference(pref)
    }

    fun resetResumePreference() = viewModelScope.launch {
        repository.setResumePreference(SettingsRepository.ResumePreference.ASK)
    }

    fun setSubtitleApiKey(key: String) = viewModelScope.launch {
        repository.setSubtitleApiKey(key)
    }

    fun setSubtitleLanguage(lang: String) = viewModelScope.launch {
        repository.setSubtitleLanguage(lang)
    }

    fun setSeekDurationPreset(seconds: Int) = viewModelScope.launch {
        repository.setSeekDurationPreset(seconds)
    }

    fun setThumbnailQuality(quality: Int) = viewModelScope.launch {
        repository.setThumbnailQuality(quality)
    }


    fun setCodecInfoDismissed(dismissed: Boolean) = viewModelScope.launch {
        repository.setCodecInfoDismissed(dismissed)
    }

    fun setOrientationMode(mode: SettingsRepository.OrientationMode) = viewModelScope.launch {
        repository.setOrientationMode(mode)
    }

    fun setSmartEnhanceEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setSmartEnhanceEnabled(enabled)
    }

    fun setHardwareScaling(enabled: Boolean) = viewModelScope.launch {
        repository.setHardwareScaling(enabled)
    }

    fun setFasterFullscreen(enabled: Boolean) = viewModelScope.launch {
        repository.setFasterFullscreen(enabled)
    }

    fun setSurfaceStabilization(enabled: Boolean) = viewModelScope.launch {
        repository.setSurfaceStabilization(enabled)
    }

    fun setAudioBoost(enabled: Boolean) = viewModelScope.launch {
        repository.setAudioBoost(enabled)
    }

    fun setSubtitleOpacity(opacity: Float) = viewModelScope.launch {
        repository.setSubtitleOpacity(opacity)
    }

    fun setSubtitlePosition(position: Float) = viewModelScope.launch {
        repository.setSubtitlePosition(position)
    }

    fun setSubtitleShadowEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setSubtitleShadowEnabled(enabled)
    }

    fun setSubtitleDelay(delayMs: Long) = viewModelScope.launch {
        repository.setSubtitleDelay(delayMs)
    }

    fun setShowOnboarding(enabled: Boolean) = viewModelScope.launch {
        repository.setShowOnboarding(enabled)
    }
}
