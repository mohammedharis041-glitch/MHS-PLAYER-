package com.mhs.player.ui.theme.designsystem

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs

/**
 * A buttery-smooth fling behavior for LazyColumn/LazyRow that produces
 * a fluid, iOS-like scroll experience instead of the default Android
 * "stepped" deceleration.
 *
 * Uses the platform's spline-based decay (natural feel) but amplifies
 * the initial velocity by 1.4x, giving scrolls ~40% more travel distance
 * for a smoother, more flowing scroll.
 */
@Composable
fun rememberSmoothFlingBehavior(): FlingBehavior {
    val splineDecay = rememberSplineBasedDecay<Float>()
    return remember(splineDecay) {
        SmoothFlingBehavior(splineDecay, velocityMultiplier = 1.4f)
    }
}

/**
 * Custom fling that amplifies the initial velocity before passing it
 * to the standard spline decay. This makes scroll feel more fluid —
 * a flick travels further and decelerates more gradually.
 */
private class SmoothFlingBehavior(
    private val decaySpec: DecayAnimationSpec<Float>,
    private val velocityMultiplier: Float = 1.4f
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        // Amplify velocity for smoother glide
        val boostedVelocity = initialVelocity * velocityMultiplier

        // If the fling is too short, skip animation
        if (abs(boostedVelocity) < 50f) return initialVelocity

        var remainingVelocity = boostedVelocity
        var lastValue = 0f

        AnimationState(
            initialValue = 0f,
            initialVelocity = boostedVelocity,
        ).animateDecay(decaySpec) {
            val delta = value - lastValue
            lastValue = value
            val consumed = scrollBy(delta)

            // If we couldn't scroll the full delta (hit bounds), stop
            if (abs(delta - consumed) > 0.5f) {
                cancelAnimation()
            }
            remainingVelocity = velocity
        }

        return remainingVelocity
    }
}
