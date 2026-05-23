package com.mhs.player.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mhs_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SEEK_DURATION = intPreferencesKey("seek_duration")
        val GESTURE_SENSITIVITY = floatPreferencesKey("gesture_sensitivity")
        val SEEK_SENSITIVITY = floatPreferencesKey("seek_sensitivity")
        val SWIPE_SENSITIVITY = floatPreferencesKey("swipe_sensitivity")
        val SUBTITLE_SIZE = floatPreferencesKey("subtitle_size")
        val SUBTITLE_COLOR = stringPreferencesKey("subtitle_color")
        val SUBTITLE_BACKGROUND = booleanPreferencesKey("subtitle_background")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val AUTO_ROTATE = booleanPreferencesKey("auto_rotate")
        val PIP_ON_HOME = booleanPreferencesKey("pip_on_home")
        val REMEMBER_POSITION = booleanPreferencesKey("remember_position")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val HARDWARE_DECODING = booleanPreferencesKey("hardware_decoding")
        val BRIGHTNESS_GESTURE = booleanPreferencesKey("brightness_gesture")
        val VOLUME_GESTURE = booleanPreferencesKey("volume_gesture")
        val SEEK_GESTURE = booleanPreferencesKey("seek_gesture")
        val DOUBLE_TAP_SEEK = booleanPreferencesKey("double_tap_seek")
        val SWIPE_LOCK = booleanPreferencesKey("swipe_lock")
        val AUDIO_BOOST = booleanPreferencesKey("audio_boost")
        val DEFAULT_SORT = stringPreferencesKey("default_sort")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden")
        val THUMBNAIL_QUALITY = intPreferencesKey("thumbnail_quality")
        // ASK | ALWAYS_RESUME | ALWAYS_START_OVER
        val RESUME_PREFERENCE = stringPreferencesKey("resume_preference")
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQUALIZER_BANDS = stringPreferencesKey("equalizer_bands")
        val EQUALIZER_BASS_BOOST = intPreferencesKey("equalizer_bass_boost")
        val EQUALIZER_PRESET = intPreferencesKey("equalizer_preset")
        // New keys
        val SUBTITLE_API_KEY = stringPreferencesKey("subtitle_api_key")
        val SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle_language")
        val SEEK_DURATION_PRESET = intPreferencesKey("seek_duration_preset") // 5,10,15,30
        val CODEC_INFO_DISMISSED = booleanPreferencesKey("codec_info_dismissed")
        val ORIENTATION_MODE = stringPreferencesKey("orientation_mode")
        // Advanced Subtitle Keys
        val SUBTITLE_FONT_STYLE = stringPreferencesKey("subtitle_font_style")
        val SUBTITLE_SHADOW_ENABLED = booleanPreferencesKey("subtitle_shadow_enabled")
        val SUBTITLE_OPACITY = floatPreferencesKey("subtitle_opacity")
        val SUBTITLE_TRANSLATION_ENABLED = booleanPreferencesKey("subtitle_translation_enabled")
        val SUBTITLE_TARGET_LANG = stringPreferencesKey("subtitle_target_lang")
        val SUBTITLE_POSITION = floatPreferencesKey("subtitle_position") // 0.0 (top) to 1.0 (bottom)
        val SUBTITLE_DELAY = longPreferencesKey("subtitle_delay") // In milliseconds
        val DECODER_MODE = stringPreferencesKey("decoder_mode") // AUTO, HW, SW
        val SHOW_ONBOARDING = booleanPreferencesKey("show_onboarding")
        val LAST_BRIGHTNESS = floatPreferencesKey("last_brightness")
        val ENHANCED_PLAYBACK_MODE = booleanPreferencesKey("enhanced_playback_mode")
        val LOW_LATENCY_MODE = booleanPreferencesKey("low_latency_mode")
        val HARDWARE_SCALING = booleanPreferencesKey("hardware_scaling")
        val FASTER_FULLSCREEN = booleanPreferencesKey("faster_fullscreen")
        val SURFACE_STABILIZATION = booleanPreferencesKey("surface_stabilization")
        val SMART_ENHANCE_ENABLED = booleanPreferencesKey("smart_enhance_enabled")
        val SMART_ENHANCE_SHARPNESS = floatPreferencesKey("smart_enhance_sharpness")
        val SMART_ENHANCE_CONTRAST = floatPreferencesKey("smart_enhance_contrast")
        val SMART_ENHANCE_COLOR_BOOST = floatPreferencesKey("smart_enhance_color_boost")
        val SMART_ENHANCE_NOISE_REDUCTION = floatPreferencesKey("smart_enhance_noise_reduction")
        val SMART_ENHANCE_ADAPTIVE = booleanPreferencesKey("smart_enhance_adaptive")
    }

    /** Controls what happens when re-opening a partially watched video. */
    enum class ResumePreference { ASK, ALWAYS_RESUME, ALWAYS_START_OVER }
    enum class OrientationMode { AUTO, LANDSCAPE, PORTRAIT, SYSTEM }
    enum class DecoderMode { AUTO, HW, SW }

    data class AppSettings(
        val seekDuration: Int = 10,
        val gestureSensitivity: Float = 1.0f,
        val seekSensitivity: Float = 1.0f,
        val swipeSensitivity: Float = 1.0f,
        val subtitleSize: Float = 16f,
        val subtitleColor: String = "#FFFFFF",
        val subtitleBackground: Boolean = true,
        val playbackSpeed: Float = 1.0f,
        val autoRotate: Boolean = true,
        val pipOnHome: Boolean = true,
        val rememberPosition: Boolean = true,
        val darkMode: Boolean = true,
        val hardwareDecoding: Boolean = true,
        val brightnessGesture: Boolean = true,
        val volumeGesture: Boolean = true,
        val seekGesture: Boolean = true,
        val doubleTapSeek: Boolean = true,
        val swipeLock: Boolean = false,
        val audioBoost: Boolean = false,
        val defaultSort: String = "DATE_DESC",
        val showHidden: Boolean = false,
        val thumbnailQuality: Int = 2,
        val resumePreference: ResumePreference = ResumePreference.ASK,
        val equalizerEnabled: Boolean = false,
        val equalizerBands: String = "0,0,0,0,0",
        val equalizerBassBoost: Int = 0,
        val equalizerPreset: Int = 0,
        // New settings
        val subtitleApiKey: String = "",
        val subtitleLanguage: String = "en",
        val seekDurationPreset: Int = 10, // 5, 10, 15, or 30 seconds
        val codecInfoDismissed: Boolean = false,
        val orientationMode: OrientationMode = OrientationMode.AUTO,
        // Advanced Subtitle Settings
        val subtitleFontStyle: String = "Normal",
        val subtitleShadowEnabled: Boolean = true,
        val subtitleOpacity: Float = 1.0f,
        val subtitleTranslationEnabled: Boolean = false,
        val subtitleTargetLang: String = "ml",
        val subtitlePosition: Float = 0.08f,
        val subtitleDelay: Long = 0L,
        val decoderMode: DecoderMode = DecoderMode.AUTO,
        val showOnboarding: Boolean = false,
        val lastBrightness: Float = -1f,
        val enhancedPlaybackMode: Boolean = true,
        val lowLatencyMode: Boolean = true,
        val hardwareScaling: Boolean = true,
        val fasterFullscreen: Boolean = true,
        val surfaceStabilization: Boolean = true,
        val smartEnhanceEnabled: Boolean = true,
        val smartEnhanceSharpness: Float = 0.4f,
        val smartEnhanceContrast: Float = 0.3f,
        val smartEnhanceColorBoost: Float = 0.3f,
        val smartEnhanceNoiseReduction: Float = 0.4f,
        val smartEnhanceAdaptive: Boolean = true
    )

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            AppSettings(
                seekDuration = prefs[Keys.SEEK_DURATION] ?: 10,
                gestureSensitivity = prefs[Keys.GESTURE_SENSITIVITY] ?: 1.0f,
                seekSensitivity = prefs[Keys.SEEK_SENSITIVITY] ?: 1.0f,
                swipeSensitivity = prefs[Keys.SWIPE_SENSITIVITY] ?: 1.0f,
                subtitleSize = prefs[Keys.SUBTITLE_SIZE] ?: 16f,
                subtitleColor = prefs[Keys.SUBTITLE_COLOR] ?: "#FFFFFF",
                subtitleBackground = prefs[Keys.SUBTITLE_BACKGROUND] ?: true,
                playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
                autoRotate = prefs[Keys.AUTO_ROTATE] ?: true,
                pipOnHome = prefs[Keys.PIP_ON_HOME] ?: true,
                rememberPosition = prefs[Keys.REMEMBER_POSITION] ?: true,
                darkMode = prefs[Keys.DARK_MODE] ?: true,
                hardwareDecoding = prefs[Keys.HARDWARE_DECODING] ?: true,
                brightnessGesture = prefs[Keys.BRIGHTNESS_GESTURE] ?: true,
                volumeGesture = prefs[Keys.VOLUME_GESTURE] ?: true,
                seekGesture = prefs[Keys.SEEK_GESTURE] ?: true,
                doubleTapSeek = prefs[Keys.DOUBLE_TAP_SEEK] ?: true,
                swipeLock = prefs[Keys.SWIPE_LOCK] ?: false,
                audioBoost = prefs[Keys.AUDIO_BOOST] ?: false,
                defaultSort = prefs[Keys.DEFAULT_SORT] ?: "DATE_DESC",
                showHidden = prefs[Keys.SHOW_HIDDEN] ?: false,
                thumbnailQuality = prefs[Keys.THUMBNAIL_QUALITY] ?: 2,
                resumePreference = when (prefs[Keys.RESUME_PREFERENCE]) {
                    "ALWAYS_RESUME" -> ResumePreference.ALWAYS_RESUME
                    "ALWAYS_START_OVER" -> ResumePreference.ALWAYS_START_OVER
                    else -> ResumePreference.ASK
                },
                equalizerEnabled = prefs[Keys.EQUALIZER_ENABLED] ?: false,
                equalizerBands = prefs[Keys.EQUALIZER_BANDS] ?: "0,0,0,0,0",
                equalizerBassBoost = prefs[Keys.EQUALIZER_BASS_BOOST] ?: 0,
                equalizerPreset = prefs[Keys.EQUALIZER_PRESET] ?: 0,
                subtitleApiKey = prefs[Keys.SUBTITLE_API_KEY] ?: "",
                subtitleLanguage = prefs[Keys.SUBTITLE_LANGUAGE] ?: "en",
                seekDurationPreset = prefs[Keys.SEEK_DURATION_PRESET] ?: 10,
                codecInfoDismissed = prefs[Keys.CODEC_INFO_DISMISSED] ?: false,
                orientationMode = when (prefs[Keys.ORIENTATION_MODE]) {
                    "LANDSCAPE" -> OrientationMode.LANDSCAPE
                    "PORTRAIT" -> OrientationMode.PORTRAIT
                    "SYSTEM" -> OrientationMode.SYSTEM
                    else -> OrientationMode.AUTO
                },
                subtitleFontStyle = prefs[Keys.SUBTITLE_FONT_STYLE] ?: "Normal",
                subtitleShadowEnabled = prefs[Keys.SUBTITLE_SHADOW_ENABLED] ?: true,
                subtitleOpacity = prefs[Keys.SUBTITLE_OPACITY] ?: 1.0f,
                subtitleTranslationEnabled = prefs[Keys.SUBTITLE_TRANSLATION_ENABLED] ?: false,
                subtitleTargetLang = prefs[Keys.SUBTITLE_TARGET_LANG] ?: "ml",
                subtitlePosition = prefs[Keys.SUBTITLE_POSITION] ?: 0.08f,
                subtitleDelay = prefs[Keys.SUBTITLE_DELAY] ?: 0L,
                decoderMode = when (prefs[Keys.DECODER_MODE]) {
                    "HW" -> DecoderMode.HW
                    "SW" -> DecoderMode.SW
                    else -> DecoderMode.AUTO
                },
                showOnboarding = if (!prefs.contains(Keys.SHOW_ONBOARDING)) {
                    true // first install only
                } else {
                    prefs[Keys.SHOW_ONBOARDING] == true
                },
                lastBrightness = prefs[Keys.LAST_BRIGHTNESS] ?: -1f,
                enhancedPlaybackMode = prefs[Keys.ENHANCED_PLAYBACK_MODE] ?: true,
                lowLatencyMode = prefs[Keys.LOW_LATENCY_MODE] ?: true,
                hardwareScaling = prefs[Keys.HARDWARE_SCALING] ?: true,
                fasterFullscreen = prefs[Keys.FASTER_FULLSCREEN] ?: true,
                surfaceStabilization = prefs[Keys.SURFACE_STABILIZATION] ?: true,
                smartEnhanceEnabled = prefs[Keys.SMART_ENHANCE_ENABLED] ?: true,
                smartEnhanceSharpness = prefs[Keys.SMART_ENHANCE_SHARPNESS] ?: 0.4f,
                smartEnhanceContrast = prefs[Keys.SMART_ENHANCE_CONTRAST] ?: 0.3f,
                smartEnhanceColorBoost = prefs[Keys.SMART_ENHANCE_COLOR_BOOST] ?: 0.3f,
                smartEnhanceNoiseReduction = prefs[Keys.SMART_ENHANCE_NOISE_REDUCTION] ?: 0.4f,
                smartEnhanceAdaptive = prefs[Keys.SMART_ENHANCE_ADAPTIVE] ?: true
            )
        }

    suspend fun setShowOnboarding(enabled: Boolean) = context.dataStore.edit {
        it[Keys.SHOW_ONBOARDING] = enabled
    }

    suspend fun setLastBrightness(brightness: Float) = context.dataStore.edit {
        it[Keys.LAST_BRIGHTNESS] = brightness
    }

    suspend fun setSeekDuration(seconds: Int) = context.dataStore.edit {
        it[Keys.SEEK_DURATION] = seconds
    }

    suspend fun setGestureSensitivity(value: Float) = context.dataStore.edit {
        it[Keys.GESTURE_SENSITIVITY] = value
    }

    suspend fun setSeekSensitivity(value: Float) = context.dataStore.edit {
        it[Keys.SEEK_SENSITIVITY] = value
    }

    suspend fun setSwipeSensitivity(value: Float) = context.dataStore.edit {
        it[Keys.SWIPE_SENSITIVITY] = value
    }

    suspend fun setSubtitleSize(size: Float) = context.dataStore.edit {
        it[Keys.SUBTITLE_SIZE] = size
    }

    suspend fun setPlaybackSpeed(speed: Float) = context.dataStore.edit {
        it[Keys.PLAYBACK_SPEED] = speed
    }

    suspend fun setRememberPosition(remember: Boolean) = context.dataStore.edit {
        it[Keys.REMEMBER_POSITION] = remember
    }

    suspend fun setHardwareDecoding(enabled: Boolean) = context.dataStore.edit {
        it[Keys.HARDWARE_DECODING] = enabled
    }

    suspend fun setBrightnessGesture(enabled: Boolean) = context.dataStore.edit {
        it[Keys.BRIGHTNESS_GESTURE] = enabled
    }

    suspend fun setVolumeGesture(enabled: Boolean) = context.dataStore.edit {
        it[Keys.VOLUME_GESTURE] = enabled
    }

    suspend fun setDoubleTapSeek(enabled: Boolean) = context.dataStore.edit {
        it[Keys.DOUBLE_TAP_SEEK] = enabled
    }

    suspend fun setPipOnHome(enabled: Boolean) = context.dataStore.edit {
        it[Keys.PIP_ON_HOME] = enabled
    }

    suspend fun setDefaultSort(sort: String) = context.dataStore.edit {
        it[Keys.DEFAULT_SORT] = sort
    }

    suspend fun setShowHidden(show: Boolean) = context.dataStore.edit {
        it[Keys.SHOW_HIDDEN] = show
    }

    suspend fun setThumbnailQuality(quality: Int) = context.dataStore.edit {
        it[Keys.THUMBNAIL_QUALITY] = quality
    }

    suspend fun setSubtitleBackground(enabled: Boolean) = context.dataStore.edit {
        it[Keys.SUBTITLE_BACKGROUND] = enabled
    }

    suspend fun setDarkMode(enabled: Boolean) = context.dataStore.edit {
        it[Keys.DARK_MODE] = enabled
    }

    suspend fun setResumePreference(pref: ResumePreference) = context.dataStore.edit {
        it[Keys.RESUME_PREFERENCE] = pref.name
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) = context.dataStore.edit {
        it[Keys.EQUALIZER_ENABLED] = enabled
    }

    suspend fun setEqualizerBands(bands: String) = context.dataStore.edit {
        it[Keys.EQUALIZER_BANDS] = bands
    }

    suspend fun setEqualizerBassBoost(strength: Int) = context.dataStore.edit {
        it[Keys.EQUALIZER_BASS_BOOST] = strength
    }

    suspend fun setEqualizerPreset(preset: Int) = context.dataStore.edit {
        it[Keys.EQUALIZER_PRESET] = preset
    }


    suspend fun setSubtitleApiKey(key: String) = context.dataStore.edit {
        it[Keys.SUBTITLE_API_KEY] = key
    }

    suspend fun setSubtitleLanguage(lang: String) = context.dataStore.edit {
        it[Keys.SUBTITLE_LANGUAGE] = lang
    }

    suspend fun setSeekDurationPreset(seconds: Int) = context.dataStore.edit {
        it[Keys.SEEK_DURATION_PRESET] = seconds
        it[Keys.SEEK_DURATION] = seconds // keep old key in sync
    }

    suspend fun setCodecInfoDismissed(dismissed: Boolean) = context.dataStore.edit {
        it[Keys.CODEC_INFO_DISMISSED] = dismissed
    }

    suspend fun setOrientationMode(mode: OrientationMode) = context.dataStore.edit {
        it[Keys.ORIENTATION_MODE] = mode.name
    }

    suspend fun setSubtitleFontStyle(style: String) = context.dataStore.edit {
        it[Keys.SUBTITLE_FONT_STYLE] = style
    }

    suspend fun setSubtitleShadowEnabled(enabled: Boolean) = context.dataStore.edit {
        it[Keys.SUBTITLE_SHADOW_ENABLED] = enabled
    }

    suspend fun setSubtitleOpacity(opacity: Float) = context.dataStore.edit {
        it[Keys.SUBTITLE_OPACITY] = opacity
    }

    suspend fun setSubtitleTranslationEnabled(enabled: Boolean) = context.dataStore.edit {
        it[Keys.SUBTITLE_TRANSLATION_ENABLED] = enabled
    }

    suspend fun setSubtitleTargetLang(lang: String) = context.dataStore.edit {
        it[Keys.SUBTITLE_TARGET_LANG] = lang
    }

    suspend fun setSubtitlePosition(position: Float) = context.dataStore.edit {
        it[Keys.SUBTITLE_POSITION] = position
    }

    suspend fun setSubtitleDelay(delayMs: Long) = context.dataStore.edit {
        it[Keys.SUBTITLE_DELAY] = delayMs
    }

    suspend fun setDecoderMode(mode: DecoderMode) = context.dataStore.edit {
        it[Keys.DECODER_MODE] = mode.name
    }

    suspend fun setEnhancedPlaybackMode(enabled: Boolean) = context.dataStore.edit {
        it[Keys.ENHANCED_PLAYBACK_MODE] = enabled
    }

    suspend fun setLowLatencyMode(enabled: Boolean) = context.dataStore.edit {
        it[Keys.LOW_LATENCY_MODE] = enabled
    }

    suspend fun setHardwareScaling(enabled: Boolean) = context.dataStore.edit {
        it[Keys.HARDWARE_SCALING] = enabled
    }

    suspend fun setFasterFullscreen(enabled: Boolean) = context.dataStore.edit {
        it[Keys.FASTER_FULLSCREEN] = enabled
    }

    suspend fun setSurfaceStabilization(enabled: Boolean) = context.dataStore.edit {
        it[Keys.SURFACE_STABILIZATION] = enabled
    }

    suspend fun setSmartEnhanceEnabled(enabled: Boolean) = context.dataStore.edit {
        it[Keys.SMART_ENHANCE_ENABLED] = enabled
    }

    suspend fun setSmartEnhanceSharpness(value: Float) = context.dataStore.edit {
        it[Keys.SMART_ENHANCE_SHARPNESS] = value
    }

    suspend fun setSmartEnhanceContrast(value: Float) = context.dataStore.edit {
        it[Keys.SMART_ENHANCE_CONTRAST] = value
    }

    suspend fun setSmartEnhanceColorBoost(value: Float) = context.dataStore.edit {
        it[Keys.SMART_ENHANCE_COLOR_BOOST] = value
    }

    suspend fun setSmartEnhanceNoiseReduction(value: Float) = context.dataStore.edit {
        it[Keys.SMART_ENHANCE_NOISE_REDUCTION] = value
    }

    suspend fun setSmartEnhanceAdaptive(enabled: Boolean) = context.dataStore.edit {
        it[Keys.SMART_ENHANCE_ADAPTIVE] = enabled
    }
}
