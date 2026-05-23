package com.mhs.player.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec

object Motion {
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val StandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    val DurationShort1 = 50
    val DurationShort2 = 100
    val DurationShort3 = 150
    val DurationShort4 = 200
    val DurationMedium1 = 250
    val DurationMedium2 = 300
    val DurationMedium3 = 350
    val DurationMedium4 = 400
    val DurationLong1 = 450
    val DurationLong2 = 500

    fun <T> emphasizedTween(durationMillis: Int = DurationMedium2): TweenSpec<T> =
        tween(durationMillis = durationMillis, easing = EmphasizedEasing)

    fun <T> standardTween(durationMillis: Int = DurationMedium1): TweenSpec<T> =
        tween(durationMillis = durationMillis, easing = StandardEasing)

    fun <T> springSpec(dampingRatio: Float = 0.8f, stiffness: Float = 380f): SpringSpec<T> =
        spring(dampingRatio = dampingRatio, stiffness = stiffness)
}
