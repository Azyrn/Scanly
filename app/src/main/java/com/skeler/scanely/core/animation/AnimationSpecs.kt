package com.skeler.scanely.core.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.math.pow


val EaseOutExpo = Easing { fraction ->
    if (fraction == 1f) 1f else 1f - 2f.pow(-10f * fraction)
}

// Shared-axis X (Material 3 motion): screens travel along the horizontal
// axis with a fade-through, so navigation reads as moving through space
// instead of dissolving in place. The incoming fade is delayed so the two
// screens never blend into an illegible double exposure.
private const val AXIS_DURATION_MS = 350
private const val FADE_IN_DURATION_MS = 250
private const val FADE_IN_DELAY_MS = 60
private const val FADE_OUT_DURATION_MS = 140

// A fraction of the screen width, not the full width: the motion suggests
// the edge without dragging content all the way across the viewport.
private const val AXIS_DISTANCE_FRACTION = 0.30f

private fun axisDistance(fullWidth: Int): Int =
    (fullWidth * AXIS_DISTANCE_FRACTION).toInt()

/**
 * Forward enter: new screen slides in from the trailing (right) edge and
 * fades through, easing out to a gentle stop.
 */
fun gallerySlideEnter(): EnterTransition = slideInHorizontally(
    animationSpec = tween(AXIS_DURATION_MS, easing = EaseOutExpo),
    initialOffsetX = { axisDistance(it) }
) + fadeIn(
    animationSpec = tween(FADE_IN_DURATION_MS, delayMillis = FADE_IN_DELAY_MS, easing = LinearOutSlowInEasing)
)

/**
 * Forward exit: outgoing screen recedes toward the leading (left) edge with
 * a quick fade so the incoming screen owns the frame almost immediately.
 */
fun gallerySlideExit(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(AXIS_DURATION_MS, easing = EaseOutExpo),
    targetOffsetX = { -axisDistance(it) }
) + fadeOut(
    animationSpec = tween(FADE_OUT_DURATION_MS, easing = FastOutLinearInEasing)
)

/**
 * Back enter: previous screen returns from the leading (left) edge,
 * mirroring the forward exit for spatial continuity.
 */
fun galleryPopEnter(): EnterTransition = slideInHorizontally(
    animationSpec = tween(AXIS_DURATION_MS, easing = EaseOutExpo),
    initialOffsetX = { -axisDistance(it) }
) + fadeIn(
    animationSpec = tween(FADE_IN_DURATION_MS, delayMillis = FADE_IN_DELAY_MS, easing = LinearOutSlowInEasing)
)

/**
 * Back exit: dismissed screen slides off toward the trailing (right) edge,
 * exactly reversing its entrance.
 */
fun galleryPopExit(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(AXIS_DURATION_MS, easing = EaseOutExpo),
    targetOffsetX = { axisDistance(it) }
) + fadeOut(
    animationSpec = tween(FADE_OUT_DURATION_MS, easing = FastOutLinearInEasing)
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


