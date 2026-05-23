package com.mhs.player.ui.theme.designsystem

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Central haptic feedback utility for MHS Player.
 *
 * Uses platform HapticFeedbackConstants on API 30+ for richer
 * vibration patterns, falling back to Compose HapticFeedback API
 * on older devices. All haptic calls are battery-safe — they fire
 * a single short impulse, never sustained vibrations.
 *
 * Usage from any @Composable:
 *   val haptics = rememberHaptics()
 *   haptics.tick()       // lightweight tick for seekbar dots, toggle switches
 *   haptics.click()      // medium click for buttons, nav tab selection
 *   haptics.heavyClick() // strong click for play/pause, swipe-to-dismiss threshold
 *   haptics.reject()     // double-buzz for error/reject/boundary
 */
class AppHaptics(
    private val view: View,
    private val composeFeedback: HapticFeedback
) {
    /** Lightest feedback — seekbar ticks, EQ band crossings, switch toggles */
    fun tick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            composeFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /** Medium feedback — button presses, nav tab switch, card tap */
    fun click() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            composeFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /** Heavy feedback — play/pause, swipe dismiss threshold, important actions */
    fun heavyClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            composeFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /** Double-buzz reject — boundary reached, drag cancel, error */
    fun reject() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            composeFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

/** Remember-cached AppHaptics instance for the current composition. */
@Composable
fun rememberHaptics(): AppHaptics {
    val view = LocalView.current
    val feedback = LocalHapticFeedback.current
    return remember(view, feedback) { AppHaptics(view, feedback) }
}
