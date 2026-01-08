package com.skeler.scanely.core.animation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * Google Gallery-style EaseOutExpo easing function.
 *
 * This creates an initial burst of speed followed by a gradual, almost imperceptible slowdown.
 * Mimics real-world physics (objects decelerating due to friction) for a "buttery" feel.
 */
val EaseOutExpo = Easing { fraction ->
    if (fraction == 1f) 1f else 1f - 2f.pow(-10f * fraction)
}

// --- Animation Duration Constants ---
private const val ENTER_ANIMATION_DURATION_MS = 500
private const val ENTER_ANIMATION_DELAY_MS = 100
private const val EXIT_ANIMATION_DURATION_MS = 500

/**
 * Creates enter transition tween spec matching Google Gallery.
 * 500ms duration, EaseOutExpo easing, 100ms delay.
 */
fun enterTween(): FiniteAnimationSpec<IntOffset> = tween(
    durationMillis = ENTER_ANIMATION_DURATION_MS,
    easing = EaseOutExpo,
    delayMillis = ENTER_ANIMATION_DELAY_MS,
)

/**
 * Creates enter transition tween spec for POP (back navigation).
 * 500ms duration, EaseOutExpo easing, NO delay (instant).
 */
fun popEnterTween(): FiniteAnimationSpec<IntOffset> = tween(
    durationMillis = ENTER_ANIMATION_DURATION_MS,
    easing = EaseOutExpo,
    // No delay for back navigation to prevent "lag" feeling
)

/**
 * Creates exit transition tween spec matching Google Gallery.
 * 500ms duration, EaseOutExpo easing, no delay.
 */
fun exitTween(): FiniteAnimationSpec<IntOffset> = tween(
    durationMillis = EXIT_ANIMATION_DURATION_MS,
    easing = EaseOutExpo,
)

/**
 * Slide enter transition from RIGHT (forward navigation).
 */
fun AnimatedContentTransitionScope<*>.gallerySlideEnter(): EnterTransition = slideIntoContainer(
    towards = AnimatedContentTransitionScope.SlideDirection.Left,
    animationSpec = enterTween(),
)

/**
 * Slide exit transition to LEFT (forward navigation).
 */
fun AnimatedContentTransitionScope<*>.gallerySlideExit(): ExitTransition = slideOutOfContainer(
    towards = AnimatedContentTransitionScope.SlideDirection.Left,
    animationSpec = exitTween(),
)

/**
 * Pop enter transition from LEFT (back navigation).
 */
fun AnimatedContentTransitionScope<*>.galleryPopEnter(): EnterTransition = slideIntoContainer(
    towards = AnimatedContentTransitionScope.SlideDirection.Right,
    animationSpec = popEnterTween(),
)

/**
 * Pop exit transition to RIGHT (back navigation).
 */
fun AnimatedContentTransitionScope<*>.galleryPopExit(): ExitTransition = slideOutOfContainer(
    towards = AnimatedContentTransitionScope.SlideDirection.Right,
    animationSpec = exitTween(),
)

/**
 * Reusable animated float progress after an initial delay.
 *
 * Ideal for staggered "enter" animations. Uses `animateFloatAsState` to smoothly
 * transition progress from 0f to 1f after `initialDelay`.
 *
 * @param initialDelay Delay before animation starts (milliseconds)
 * @param animationDurationMs Duration of the animation
 * @param animationLabel Label for animation debugging
 * @param easing Easing function (default: FastOutSlowInEasing)
 */
@Composable
fun rememberDelayedAnimationProgress(
    initialDelay: Long = 0,
    animationDurationMs: Int,
    animationLabel: String,
    easing: Easing = FastOutSlowInEasing,
): Float {
    var startAnimation by remember { mutableStateOf(false) }
    val progress: Float by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        label = animationLabel,
        animationSpec = tween(durationMillis = animationDurationMs, easing = easing),
    )
    LaunchedEffect(Unit) {
        delay(initialDelay)
        startAnimation = true
    }
    return progress
}

