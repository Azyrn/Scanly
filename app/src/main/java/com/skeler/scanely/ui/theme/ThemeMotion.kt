package com.skeler.scanely.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

const val ThemeSwitchDurationMillis = 220

@Composable
fun ColorScheme.animated(
    spec: AnimationSpec<Color> = tween(ThemeSwitchDurationMillis, easing = FastOutSlowInEasing)
): ColorScheme {
    @Composable
    fun Color.role(label: String): Color = animateColorAsState(this, spec, label = label).value
    return copy(
        primary = primary.role("primary"),
        onPrimary = onPrimary.role("onPrimary"),
        primaryContainer = primaryContainer.role("primaryContainer"),
        onPrimaryContainer = onPrimaryContainer.role("onPrimaryContainer"),
        inversePrimary = inversePrimary.role("inversePrimary"),
        secondary = secondary.role("secondary"),
        onSecondary = onSecondary.role("onSecondary"),
        secondaryContainer = secondaryContainer.role("secondaryContainer"),
        onSecondaryContainer = onSecondaryContainer.role("onSecondaryContainer"),
        tertiary = tertiary.role("tertiary"),
        onTertiary = onTertiary.role("onTertiary"),
        tertiaryContainer = tertiaryContainer.role("tertiaryContainer"),
        onTertiaryContainer = onTertiaryContainer.role("onTertiaryContainer"),
        background = background.role("background"),
        onBackground = onBackground.role("onBackground"),
        surface = surface.role("surface"),
        onSurface = onSurface.role("onSurface"),
        surfaceVariant = surfaceVariant.role("surfaceVariant"),
        onSurfaceVariant = onSurfaceVariant.role("onSurfaceVariant"),
        surfaceTint = surfaceTint.role("surfaceTint"),
        surfaceDim = surfaceDim.role("surfaceDim"),
        surfaceBright = surfaceBright.role("surfaceBright"),
        surfaceContainerLowest = surfaceContainerLowest.role("surfaceContainerLowest"),
        surfaceContainerLow = surfaceContainerLow.role("surfaceContainerLow"),
        surfaceContainer = surfaceContainer.role("surfaceContainer"),
        surfaceContainerHigh = surfaceContainerHigh.role("surfaceContainerHigh"),
        surfaceContainerHighest = surfaceContainerHighest.role("surfaceContainerHighest"),
        inverseSurface = inverseSurface.role("inverseSurface"),
        inverseOnSurface = inverseOnSurface.role("inverseOnSurface"),
        outline = outline.role("outline"),
        outlineVariant = outlineVariant.role("outlineVariant"),
        error = error.role("error"),
        onError = onError.role("onError"),
        errorContainer = errorContainer.role("errorContainer"),
        onErrorContainer = onErrorContainer.role("onErrorContainer"),
    )
}
