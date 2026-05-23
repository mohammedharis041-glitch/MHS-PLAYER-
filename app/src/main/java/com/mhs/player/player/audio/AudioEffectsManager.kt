package com.mhs.player.player.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.mhs.player.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class EqualizerBand(
    val index: Short,
    val centerFrequency: Int,
    val level: Short
)

data class AudioEffectsState(
    val isEqualizerEnabled: Boolean = false,
    val isBassBoostEnabled: Boolean = false,
    val isVirtualizerEnabled: Boolean = false,
    val bassBoostStrength: Short = 0,
    val virtualizerStrength: Short = 0,
    val bands: List<EqualizerBand> = emptyList()
)

@Singleton
class AudioEffectsManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    private val _effectsState = MutableStateFlow(AudioEffectsState())
    val effectsState: StateFlow<AudioEffectsState> = _effectsState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var audioSessionId: Int = 0

    init {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                val savedBands = settings.equalizerBands.split(",").mapNotNull { it.toIntOrNull() }
                
                // Safe hardware update on Main/Playback context
                try {
                    equalizer?.let { eq ->
                        if (eq.enabled != settings.equalizerEnabled) {
                            eq.enabled = settings.equalizerEnabled
                        }
                        if (settings.equalizerEnabled) {
                            savedBands.forEachIndexed { b, level ->
                                if (b < eq.numberOfBands) {
                                    if (eq.getBandLevel(b.toShort()) != level.toShort()) {
                                        eq.setBandLevel(b.toShort(), level.toShort())
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore hardware errors/unsupported effects safely
                }

                try {
                    bassBoost?.let { bb ->
                        val bbEnabled = settings.equalizerEnabled && settings.equalizerBassBoost > 0
                        if (bb.enabled != bbEnabled) {
                            bb.enabled = bbEnabled
                        }
                        if (bbEnabled) {
                            val targetStrength = (settings.equalizerBassBoost * 10).toShort()
                            if (bb.roundedStrength != targetStrength) {
                                bb.setStrength(targetStrength)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore hardware errors safely
                }

                val currentBands = _effectsState.value.bands
                val updatedBands = currentBands.mapIndexed { index, band ->
                    if (index < savedBands.size) {
                        band.copy(level = savedBands[index].toShort())
                    } else {
                        band
                    }
                }.ifEmpty {
                    savedBands.mapIndexed { index, level ->
                        EqualizerBand(
                            index = index.toShort(),
                            centerFrequency = getCenterFreqForBand(index.toShort()),
                            level = level.toShort()
                        )
                    }
                }

                _effectsState.value = _effectsState.value.copy(
                    isEqualizerEnabled = settings.equalizerEnabled,
                    isBassBoostEnabled = settings.equalizerEnabled && settings.equalizerBassBoost > 0,
                    bassBoostStrength = (settings.equalizerBassBoost * 10).toShort(),
                    bands = updatedBands
                )
            }
        }
    }

    private fun getCenterFreqForBand(bandIndex: Short): Int {
        return when (bandIndex.toInt()) {
            0 -> 60
            1 -> 230
            2 -> 910
            3 -> 3600
            4 -> 14000
            else -> 0
        }
    }

    fun initialize(sessionId: Int) {
        if (sessionId <= 0) return
        if (audioSessionId == sessionId && equalizer != null) return
        audioSessionId = sessionId
        release()
        try {
            val state = _effectsState.value
            equalizer = Equalizer(0, sessionId).apply {
                enabled = state.isEqualizerEnabled
                state.bands.forEach { band ->
                    if (band.index < numberOfBands) {
                        setBandLevel(band.index, band.level)
                    }
                }
            }
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = state.isBassBoostEnabled
                if (state.bassBoostStrength > 0) setStrength(state.bassBoostStrength)
            }
            virtualizer = Virtualizer(0, sessionId).apply {
                enabled = state.isVirtualizerEnabled
                if (state.virtualizerStrength > 0) setStrength(state.virtualizerStrength)
            }
            loadBands()
        } catch (e: Exception) {
            release()
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _effectsState.value = _effectsState.value.copy(isEqualizerEnabled = enabled)
        equalizer?.enabled = enabled
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        bassBoost?.enabled = enabled
        _effectsState.value = _effectsState.value.copy(isBassBoostEnabled = enabled)
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        virtualizer?.enabled = enabled
        _effectsState.value = _effectsState.value.copy(isVirtualizerEnabled = enabled)
    }

    fun setBandLevel(bandIndex: Short, level: Short) {
        equalizer?.setBandLevel(bandIndex, level)
        val bands = _effectsState.value.bands.toMutableList()
        val idx = bands.indexOfFirst { it.index == bandIndex }
        if (idx >= 0) {
            bands[idx] = bands[idx].copy(level = level)
            _effectsState.value = _effectsState.value.copy(bands = bands)
        }
    }

    fun setBassBoostStrength(strength: Short) {
        bassBoost?.setStrength(strength)
        _effectsState.value = _effectsState.value.copy(bassBoostStrength = strength)
    }

    fun setVirtualizerStrength(strength: Short) {
        virtualizer?.setStrength(strength)
        _effectsState.value = _effectsState.value.copy(virtualizerStrength = strength)
    }

    fun release() {
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    private fun loadBands() {
        val eq = equalizer ?: return
        val numBands = eq.numberOfBands
        val bands = mutableListOf<EqualizerBand>()
        for (i in 0 until numBands) {
            val idx = i.toShort()
            bands.add(
                EqualizerBand(
                    index = idx,
                    centerFrequency = eq.getCenterFreq(idx) / 1000,
                    level = eq.getBandLevel(idx)
                )
            )
        }
        _effectsState.value = _effectsState.value.copy(bands = bands)
    }
}

