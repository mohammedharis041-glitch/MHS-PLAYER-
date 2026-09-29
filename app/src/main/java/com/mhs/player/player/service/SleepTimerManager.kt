package com.mhs.player.player.service

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2-beta: Sleep timer with fade-out.
 * End actions: PAUSE or STOP_AND_EXIT
 */
@Singleton
class SleepTimerManager @Inject constructor() {
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _remainingMinutes = MutableStateFlow(0)
    val remainingMinutes: StateFlow<Int> = _remainingMinutes

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    /**
     * Start timer. onTick called each minute, onFadeStart called 30s before end,
     * onFinish called at 0.
     */
    fun start(
        minutes: Int,
        onTick: (remaining: Int) -> Unit = {},
        onFadeStart: () -> Unit = {},
        onFinish: (endAction: String) -> Unit = {}
    ) {
        cancel()
        if (minutes <= 0) return
        _isActive.value = true
        _remainingMinutes.value = minutes

        job = scope.launch {
            var remaining = minutes
            while (remaining > 0 && isActive) {
                delay(60_000L)
                remaining--
                _remainingMinutes.value = remaining
                onTick(remaining)

                // Fade-out in last minute: trigger 30s before end
                if (remaining == 0) {
                    // Final 30s fade handled by caller via onFadeStart
                    // We do a short 30s wait with fade callback
                    // Simplified: call onFadeStart then wait 30s is handled externally
                }
            }
            if (_isActive.value) {
                _isActive.value = false
                _remainingMinutes.value = 0
                onFinish("PAUSE")
            }
        }

        // Schedule fade 30s before end
        scope.launch {
            delay((minutes * 60_000L - 30_000L).coerceAtLeast(0L))
            if (_isActive.value) onFadeStart()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _isActive.value = false
        _remainingMinutes.value = 0
    }

    fun getRemainingMinutes(): Int = _remainingMinutes.value
}
