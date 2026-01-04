@file:SuppressLint("RestrictedApi")

package com.skeler.scanely.ui.theme

import android.annotation.SuppressLint
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.CorePalette

/**
 * Extension properties to query tonal palettes at specific tone levels (0-100).
 * 
 * Uses Google's CorePalette which implements HCT (Hue-Chroma-Tone) color science
 * for perceptually uniform tonal scales - the same algorithm behind Material 3.
 * 
 * Usage:
 *   40.a1  → Primary accent at tone 40 (for light theme primary)
 *   80.a1  → Primary accent at tone 80 (for dark theme primary)
 *   6.n1   → Neutral at tone 6 (for dark theme background)
 *   98.n1  → Neutral at tone 98 (for light theme background)
 */

// CorePalette generates full tonal palettes from a single seed color
private val primaryPalette get() = CorePalette.of(SeedColorProvider.primary)
private val secondaryPalette get() = CorePalette.of(SeedColorProvider.secondary)
private val tertiaryPalette get() = CorePalette.of(SeedColorProvider.tertiary)

/**
 * Primary accent palette (a1) - Main brand color tones
 */
val Int.a1: Color get() = Color(primaryPalette.a1.tone(this))

/**
 * Secondary accent palette (a2) - Derived from secondary seed
 * Note: Using a1 from secondary palette (not a2) to get proper tones from that seed
 */
val Int.a2: Color get() = Color(secondaryPalette.a1.tone(this))

/**
 * Tertiary accent palette (a3) - Derived from tertiary seed
 */
val Int.a3: Color get() = Color(tertiaryPalette.a1.tone(this))

/**
 * Neutral palette (n1) - For surfaces, backgrounds
 * Derived from primary seed but with very low chroma
 */
val Int.n1: Color get() = Color(primaryPalette.n1.tone(this))

/**
 * Neutral variant palette (n2) - For outlines, surface variants
 * Slightly more chromatic than n1
 */
val Int.n2: Color get() = Color(primaryPalette.n2.tone(this))

/**
 * Error palette - Standard error tones (red-based)
 */
val Int.error: Color get() = Color(primaryPalette.error.tone(this))
