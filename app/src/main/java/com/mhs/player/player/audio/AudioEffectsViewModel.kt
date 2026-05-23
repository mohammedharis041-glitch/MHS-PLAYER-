package com.mhs.player.player.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AudioEffectsViewModel @Inject constructor(
    private val audioEffectsManager: AudioEffectsManager
) : ViewModel() {

    val effectsState: StateFlow<AudioEffectsState> = audioEffectsManager.effectsState

    fun setEqualizerEnabled(enabled: Boolean) {
        audioEffectsManager.setEqualizerEnabled(enabled)
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        audioEffectsManager.setBassBoostEnabled(enabled)
    }

    fun setBandLevel(bandIndex: Short, level: Short) {
        audioEffectsManager.setBandLevel(bandIndex, level)
    }

    fun setBassBoostStrength(strength: Short) {
        audioEffectsManager.setBassBoostStrength(strength)
    }

    fun resetToFlat() {
        audioEffectsManager.setEqualizerEnabled(true)
        val numBands = effectsState.value.bands.size
        for (i in 0 until numBands) {
            audioEffectsManager.setBandLevel(i.toShort(), 0)
        }
        audioEffectsManager.setBassBoostStrength(0)
    }
}
