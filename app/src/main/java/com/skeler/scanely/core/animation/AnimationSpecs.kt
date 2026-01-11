package com.skeler.scanely.core.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
// ULTRATHINK Reasoning:
// - Enter: 300ms allows eye to track the scale expansion
// - Exit: 150ms feels "responsive" since user initiated the action
// - Scale: 0.92→1.0 is subliminal yet creates depth perception
private const val ENTER_DURATION_MS = 400
private const val ENTER_DELAY_MS = 100
private const val EXIT_DURATION_MS = 150
private const val INITIAL_SCALE = 0.92f
private const val EXIT_SCALE = 1.08f

/**
 * Fade+Scale enter transition (forward navigation).
 *
 * ULTRATHINK Deep Reasoning:
 * - Full-width slides CLIP corners on rounded screens (20-40dp radii)
 * - FadeIn + ScaleIn keeps ALL 4 corners visible throughout animation
 * - Content emerges from CENTER, requiring no eye tracking across viewport
 * - 0.92f scale creates subtle "emerging from depth" effect
 */
fun gallerySlideEnter(): EnterTransition = fadeIn(
    animationSpec = tween(ENTER_DURATION_MS, easing = EaseOutExpo, delayMillis = ENTER_DELAY_MS)
) + scaleIn(
    initialScale = INITIAL_SCALE,
    animationSpec = tween(ENTER_DURATION_MS, easing = EaseOutExpo, delayMillis = ENTER_DELAY_MS)
)

/**
 * Fade+Scale exit transition (forward navigation).
 *
 * Exit scales UP slightly (1.08) to create "pushed back" parallax effect
 * while new content emerges in front.
 */
fun gallerySlideExit(): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis = EXIT_DURATION_MS, easing = FastOutSlowInEasing)
) + scaleOut(
    targetScale = EXIT_SCALE,
    animationSpec = tween(durationMillis = EXIT_DURATION_MS, easing = FastOutSlowInEasing)
)

/**
 * Pop enter transition (back navigation).
 *
 * When going BACK, content scales DOWN from 1.08 to 1.0 (reversing the exit).
 * This creates visual continuity: what "pushed back" now "pulls forward".
 */
fun galleryPopEnter(): EnterTransition = fadeIn(
    animationSpec = tween(ENTER_DURATION_MS, easing = EaseOutExpo, delayMillis = ENTER_DELAY_MS)
) + scaleIn(
    initialScale = EXIT_SCALE,
    animationSpec = tween(ENTER_DURATION_MS, easing = EaseOutExpo, delayMillis = ENTER_DELAY_MS)
)

/**
 * Pop exit transition (back navigation).
 *
 * Exiting screen scales DOWN and fades, as if "falling away" to reveal
 * the previous screen behind it.
 */
fun galleryPopExit(): ExitTransition = fadeOut(
    animationSpec = tween(durationMillis = EXIT_DURATION_MS, easing = FastOutSlowInEasing)
) + scaleOut(
    targetScale = INITIAL_SCALE,
    animationSpec = tween(durationMillis = EXIT_DURATION_MS, easing = FastOutSlowInEasing)
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


