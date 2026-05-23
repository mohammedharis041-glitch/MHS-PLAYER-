package com.mhs.player.ui.theme.designsystem

import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

object AppAnimations {
    // Premium tactile spring for thumb scaling and responsive controls
    val TactileSpringSpec = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow
    )

    // Dp equivalent for bounce/layout springs
    val LayoutSpringSpec = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow
    )

    // Cinematic fast fade/transitions
    val CinematicFadeSpec = tween<Float>(
        durationMillis = 250,
        easing = LinearOutSlowInEasing
    )

    // Standard panel enter/exit transitions
    val SlideUpEnter: EnterTransition = slideInVertically(
        initialOffsetY = { it },
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        )
    ) + fadeIn(animationSpec = CinematicFadeSpec)

    val SlideUpExit: ExitTransition = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        )
    ) + fadeOut(animationSpec = CinematicFadeSpec)

    val FadeInTransition = fadeIn(animationSpec = CinematicFadeSpec)
    val FadeOutTransition = fadeOut(animationSpec = CinematicFadeSpec)
}
