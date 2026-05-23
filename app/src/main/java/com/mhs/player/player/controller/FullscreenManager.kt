package com.mhs.player.player.controller

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FullscreenManager @Inject constructor() {

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _isLandscape = MutableStateFlow(false)
    val isLandscape: StateFlow<Boolean> = _isLandscape.asStateFlow()

    fun enterFullscreen(activity: Activity) {
        _isFullscreen.value = true
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideSystemUI(activity)
    }

    fun exitFullscreen(activity: Activity) {
        _isFullscreen.value = false
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        showSystemUI(activity)
    }

    fun toggleFullscreen(activity: Activity) {
        if (_isFullscreen.value) exitFullscreen(activity)
        else enterFullscreen(activity)
    }

    fun setLandscape(landscape: Boolean) {
        _isLandscape.value = landscape
    }

    private fun hideSystemUI(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemUI(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
