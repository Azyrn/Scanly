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

// M3 shared-axis X: delayed fade-in avoids double-exposure blend.
private const val AXIS_DURATION_MS = 350
private const val FADE_IN_DURATION_MS = 250
private const val FADE_IN_DELAY_MS = 60
private const val FADE_OUT_DURATION_MS = 140

// Partial width so motion suggests edge without full viewport travel.
private const val AXIS_DISTANCE_FRACTION = 0.30f

private fun axisDistance(fullWidth: Int): Int =
    (fullWidth * AXIS_DISTANCE_FRACTION).toInt()

fun gallerySlideEnter(): EnterTransition = slideInHorizontally(
    animationSpec = tween(AXIS_DURATION_MS, easing = EaseOutExpo),
    initialOffsetX = { axisDistance(it) }
) + fadeIn(
    animationSpec = tween(FADE_IN_DURATION_MS, delayMillis = FADE_IN_DELAY_MS, easing = LinearOutSlowInEasing)
)

fun gallerySlideExit(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(AXIS_DURATION_MS, easing = EaseOutExpo),
    targetOffsetX = { -axisDistance(it) }
) + fadeOut(
    animationSpec = tween(FADE_OUT_DURATION_MS, easing = FastOutLinearInEasing)
)

fun galleryPopEnter(): EnterTransition = slideInHorizontally(
    animationSpec = tween(AXIS_DURATION_MS, easing = EaseOutExpo),
    initialOffsetX = { -axisDistance(it) }
) + fadeIn(
    animationSpec = tween(FADE_IN_DURATION_MS, delayMillis = FADE_IN_DELAY_MS, easing = LinearOutSlowInEasing)
)

fun galleryPopExit(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(AXIS_DURATION_MS, easing = EaseOutExpo),
    targetOffsetX = { axisDistance(it) }
) + fadeOut(
    animationSpec = tween(FADE_OUT_DURATION_MS, easing = FastOutLinearInEasing)
)

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
