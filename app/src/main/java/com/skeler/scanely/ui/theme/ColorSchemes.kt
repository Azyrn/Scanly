@file:SuppressLint("RestrictedApi")
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.theme

import android.annotation.SuppressLint
import androidx.annotation.FloatRange
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.utilities.CorePalette
import com.google.android.material.color.utilities.Hct

// =============================================================================
// TONAL PALETTES
//
// A SeedColor is expanded into Material 3 HCT tonal palettes via CorePalette
// (the same colour science behind Material You). Each of the three seed anchors
// contributes its own accent ramp; neutrals/errors derive from the primary.
// =============================================================================

private class TonalPalettes(seed: SeedColor) {
    private val mono = seed.monochrome
    private val primaryCore = CorePalette.of(seed.primary)
    private val secondaryCore = CorePalette.of(seed.secondary)
    private val tertiaryCore = CorePalette.of(seed.tertiary)

    /** Chroma-0 gray at [tone]; CorePalette would otherwise force accents to chroma≥48. */
    private fun gray(tone: Int): Color = Color(Hct.from(0.0, 0.0, tone.toDouble()).toInt())

    /** Primary accent ramp. */
    fun a1(tone: Int): Color = if (mono) gray(tone) else Color(primaryCore.a1.tone(tone))

    /** Secondary accent ramp (from the secondary seed's own vivid a1). */
    fun a2(tone: Int): Color = if (mono) gray(tone) else Color(secondaryCore.a1.tone(tone))

    /** Tertiary accent ramp (from the tertiary seed's own vivid a1). */
    fun a3(tone: Int): Color = if (mono) gray(tone) else Color(tertiaryCore.a1.tone(tone))

    /** Neutral ramp for surfaces/backgrounds. */
    fun n1(tone: Int): Color = if (mono) gray(tone) else Color(primaryCore.n1.tone(tone))

    /** Neutral-variant ramp for outlines/surface variants. */
    fun n2(tone: Int): Color = if (mono) gray(tone) else Color(primaryCore.n2.tone(tone))

    /** Standard error ramp. */
    fun error(tone: Int): Color = Color(primaryCore.error.tone(tone))
}

/** Fraction of primary blended into secondary/tertiary for a cohesive, harmonized set. */
private const val HarmonizeFraction = 0.1f

/** Non-black dark surfaces used when a dynamic scheme resolves to pure black but OLED is off. */
private val DarkSurface = Color(0xFF141218)

// =============================================================================
// SEED-BASED SCHEMES — the single pipeline for all non-dynamic themes
// =============================================================================

/** Build a full Material 3 [ColorScheme] from [seed] for the requested brightness. */
fun seedColorScheme(seed: SeedColor, dark: Boolean): ColorScheme {
    val palettes = TonalPalettes(seed)
    return if (dark) darkSeedScheme(palettes) else lightSeedScheme(palettes)
}

private fun lightSeedScheme(p: TonalPalettes): ColorScheme {
    val primary = p.a1(40)
    return expressiveLightColorScheme().copy(
        primary = primary,
        onPrimary = p.a1(100),
        primaryContainer = p.a1(90),
        onPrimaryContainer = p.a1(10),
        inversePrimary = p.a1(80),

        secondary = p.a2(40).harmonize(primary),
        onSecondary = p.a2(100).harmonize(primary),
        secondaryContainer = p.a2(90).harmonize(primary),
        onSecondaryContainer = p.a2(10).harmonize(primary),

        tertiary = p.a3(40).harmonize(primary),
        onTertiary = p.a3(100).harmonize(primary),
        tertiaryContainer = p.a3(90).harmonize(primary),
        onTertiaryContainer = p.a3(10).harmonize(primary),

        background = p.n1(98),
        onBackground = p.n1(10),
        surface = p.n1(98),
        onSurface = p.n1(10),
        surfaceVariant = p.n2(90),
        onSurfaceVariant = p.n2(30),
        surfaceDim = p.n1(87),
        surfaceBright = p.n1(98),
        surfaceContainerLowest = p.n1(100),
        surfaceContainerLow = p.n1(96),
        surfaceContainer = p.n1(94),
        surfaceContainerHigh = p.n1(92),
        surfaceContainerHighest = p.n1(90),
        inverseSurface = p.n1(20),
        inverseOnSurface = p.n1(95),

        outline = p.n2(50),
        outlineVariant = p.n2(80),

        error = p.error(40),
        onError = p.error(100),
        errorContainer = p.error(90),
        onErrorContainer = p.error(10),

        scrim = Color.Black,
    )
}

private fun darkSeedScheme(p: TonalPalettes): ColorScheme {
    val primary = p.a1(80)
    return darkColorScheme(
        primary = primary,
        onPrimary = p.a1(20),
        primaryContainer = p.a1(30),
        onPrimaryContainer = p.a1(90),
        inversePrimary = p.a1(40),

        secondary = p.a2(80).harmonize(primary),
        onSecondary = p.a2(20).harmonize(primary),
        secondaryContainer = p.a2(30).harmonize(primary),
        onSecondaryContainer = p.a2(90).harmonize(primary),

        tertiary = p.a3(80).harmonize(primary),
        onTertiary = p.a3(20).harmonize(primary),
        tertiaryContainer = p.a3(30).harmonize(primary),
        onTertiaryContainer = p.a3(90).harmonize(primary),

        background = p.n1(6),
        onBackground = p.n1(90),
        surface = p.n1(6),
        onSurface = p.n1(90),
        surfaceVariant = p.n2(30),
        onSurfaceVariant = p.n2(80),
        surfaceDim = p.n1(6),
        surfaceBright = p.n1(24),
        surfaceContainerLowest = p.n1(4),
        surfaceContainerLow = p.n1(10),
        surfaceContainer = p.n1(12),
        surfaceContainerHigh = p.n1(17),
        surfaceContainerHighest = p.n1(22),
        inverseSurface = p.n1(90),
        inverseOnSurface = p.n1(20),

        outline = p.n2(60),
        outlineVariant = p.n2(30),

        error = p.error(80),
        onError = p.error(20),
        errorContainer = p.error(30),
        onErrorContainer = p.error(90),

        scrim = Color.Black,
    )
}

// =============================================================================
// SURFACE TRANSFORMS — applied uniformly to any dark scheme (seed OR dynamic)
// =============================================================================

/**
 * Force a true-black OLED scheme: blacken the base surfaces and push the elevation
 * ladder down one step so cards keep a subtle, layered separation from pure black.
 */
fun ColorScheme.toOledBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = surfaceContainerLowest,
    surfaceContainer = surfaceContainerLow,
    surfaceContainerHigh = surfaceContainer,
    surfaceContainerHighest = surfaceContainerHigh,
)

/**
 * Bring any non-OLED dark scheme to life. Wallpaper-derived (dynamic) neutrals
 * often resolve to a flat, near-black gray-brown with almost no separation between
 * background and cards — the classic "sad" dark theme. This rebuilds the surface
 * elevation ladder from a single anchor, widening the steps so cards read clearly,
 * and folds a hint of the scheme's own accent into every surface so the dark tone
 * feels tinted and intentional instead of dead. The accent tint keeps dynamic
 * schemes on-hue while making seed schemes richer.
 */
fun ColorScheme.enlivenDark(): ColorScheme {
    // Anchor the darkest surface; lift a dynamic scheme off (near-)black since OLED
    // mode owns the true-black look via toOledBlack().
    val base = if (surface.luminance() < 0.02f) DarkSurface else surface
    val tint = primary

    fun rung(lift: Float, tintAmount: Float): Color =
        base.blend(Color.White, lift).blend(tint, tintAmount)

    val bg = rung(0.00f, 0.03f)
    return copy(
        background = bg,
        surface = bg,
        surfaceDim = bg,
        surfaceContainerLowest = rung(0.00f, 0.02f),
        surfaceContainerLow = rung(0.045f, 0.05f),
        surfaceContainer = rung(0.075f, 0.06f),
        surfaceContainerHigh = rung(0.11f, 0.07f),
        surfaceContainerHighest = rung(0.15f, 0.08f),
        surfaceBright = rung(0.18f, 0.06f),
    )
}

// =============================================================================
// COLOR UTILITIES
// =============================================================================

/** Blend [HarmonizeFraction] of [primary] into this accent for a cohesive scheme. */
private fun Color.harmonize(primary: Color): Color = blend(primary, HarmonizeFraction)

/** Blend this colour toward [other] by [fraction] (0 = unchanged, 1 = fully [other]). */
fun Color.blend(
    other: Color,
    @FloatRange(from = 0.0, to = 1.0) fraction: Float = 0.1f
): Color = Color(ColorUtils.blendARGB(this.toArgb(), other.toArgb(), fraction))
