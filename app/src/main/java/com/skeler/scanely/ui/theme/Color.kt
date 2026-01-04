@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.FloatRange
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

// ============================================================================
// SEED COLOR TRIPLETS (matching aShellYou PaletteWheel pattern)
// ============================================================================
data class SeedColor(
    val primary: Int,
    val secondary: Int,
    val tertiary: Int
)

// Predefined color palettes (matching aShellYou's 20 colors)
object SeedPalettes {
    val Color01 = SeedColor(
        primary = 0xFFB5353F.toInt(),
        secondary = 0xFFB78483.toInt(),
        tertiary = 0xFFB38A45.toInt()
    )
    val Color02 = SeedColor(
        primary = 0xFFF06435.toInt(),
        secondary = 0xFFB98474.toInt(),
        tertiary = 0xFFA48F42.toInt()
    )
    val Color03 = SeedColor(
        primary = 0xFFE07200.toInt(),
        secondary = 0xFFB2886C.toInt(),
        tertiary = 0xFF929553.toInt()
    )
    val Color04 = SeedColor(
        primary = 0xFFC78100.toInt(),
        secondary = 0xFFA78C6C.toInt(),
        tertiary = 0xFF83976A.toInt()
    )
    val Color05 = SeedColor(
        primary = 0xFFB28B00.toInt(),
        secondary = 0xFF9E8F6D.toInt(),
        tertiary = 0xFF789978.toInt()
    )
    val Color06 = SeedColor(
        primary = 0xFF999419.toInt(),
        secondary = 0xFF959270.toInt(),
        tertiary = 0xFF6E9A86.toInt()
    )
    val Color07 = SeedColor(
        primary = 0xFF7D9B36.toInt(),
        secondary = 0xFF8C9476.toInt(),
        tertiary = 0xFF6A9A92.toInt()
    )
    val Color08 = SeedColor(
        primary = 0xFF5BA053.toInt(),
        secondary = 0xFF84967E.toInt(),
        tertiary = 0xFF69999D.toInt()
    )
    val Color09 = SeedColor(
        primary = 0xFF30A370.toInt(),
        secondary = 0xFF7E9686.toInt(),
        tertiary = 0xFF6D97A6.toInt()
    )
    val Color10 = SeedColor(
        primary = 0xFF00A38C.toInt(),
        secondary = 0xFF7D968F.toInt(),
        tertiary = 0xFF7694AC.toInt()
    )
    val Color11 = SeedColor(
        primary = 0xFF00A1A3.toInt(),
        secondary = 0xFF7D9595.toInt(),
        tertiary = 0xFF8092AE.toInt()
    )
    val Color12 = SeedColor(
        primary = 0xFF169EB7.toInt(),
        secondary = 0xFF7F949B.toInt(),
        tertiary = 0xFF898FB0.toInt()
    )
    val Color13 = SeedColor(
        primary = 0xFF389AC7.toInt(),
        secondary = 0xFF81939F.toInt(),
        tertiary = 0xFF938CAF.toInt()
    )
    val Color14 = SeedColor(
        primary = 0xFF5695D2.toInt(),
        secondary = 0xFF8692A2.toInt(),
        tertiary = 0xFF9D8AAB.toInt()
    )
    val Color15 = SeedColor(
        primary = 0xFF728FD8.toInt(),
        secondary = 0xFF8B90A3.toInt(),
        tertiary = 0xFFA687A4.toInt()
    )
    val Color16 = SeedColor(
        primary = 0xFF8C88D8.toInt(),
        secondary = 0xFF918EA4.toInt(),
        tertiary = 0xFFAF8599.toInt()
    )
    val Color17 = SeedColor(
        primary = 0xFFA282D1.toInt(),
        secondary = 0xFF978DA2.toInt(),
        tertiary = 0xFFB4848D.toInt()
    )
    val Color18 = SeedColor(
        primary = 0xFFB67CC2.toInt(),
        secondary = 0xFF9D8B9E.toInt(),
        tertiary = 0xFFB7847F.toInt()
    )
    val Color19 = SeedColor(
        primary = 0xFFC677AD.toInt(),
        secondary = 0xFFA38998.toInt(),
        tertiary = 0xFFB78671.toInt()
    )
    val Color20 = SeedColor(
        primary = 0xFFB23268.toInt(),
        secondary = 0xFFB38491.toInt(),
        tertiary = 0xFFBF844F.toInt()
    )
    
    val ALL = listOf(
        Color01, Color02, Color03, Color04, Color05,
        Color06, Color07, Color08, Color09, Color10,
        Color11, Color12, Color13, Color14, Color15,
        Color16, Color17, Color18, Color19, Color20
    )
    val DEFAULT = Color16 // Purple-ish, M3 default feel
}

// ============================================================================
// DYNAMIC COLOR SCHEME VARIANTS
// ============================================================================

@RequiresApi(Build.VERSION_CODES.S)
fun highContrastDynamicDarkColorScheme(context: Context): ColorScheme {
    return dynamicDarkColorScheme(context = context).copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = dynamicDarkColorScheme(context).surfaceContainerLowest,
        surfaceContainer = dynamicDarkColorScheme(context).surfaceContainerLow,
        surfaceContainerHigh = dynamicDarkColorScheme(context).surfaceContainer,
        surfaceContainerHighest = dynamicDarkColorScheme(context).surfaceContainerHigh,
    )
}

// ============================================================================
// SEED-BASED COLOR SCHEMES (using CorePalette HCT tonal palettes)
// ============================================================================

/**
 * Generate a full Material 3 Light ColorScheme from the current seed colors.
 * Uses CorePalette for proper HCT (Hue-Chroma-Tone) tonal scales.
 */
@Composable
fun lightColorSchemeFromSeed(): ColorScheme {
    return expressiveLightColorScheme().copy(
        // Primary palette
        primary = 40.a1,
        primaryContainer = 90.a1,
        onPrimary = 100.a1,
        onPrimaryContainer = 10.a1,
        inversePrimary = 80.a1,

        // Secondary palette - harmonized with primary
        secondary = 40.a2.harmonizeWithPrimary(0.1f),
        secondaryContainer = 90.a2.harmonizeWithPrimary(0.1f),
        onSecondary = 100.a2.harmonizeWithPrimary(0.1f),
        onSecondaryContainer = 10.a2.harmonizeWithPrimary(0.1f),

        // Tertiary palette - harmonized with primary
        tertiary = 40.a3.harmonizeWithPrimary(0.1f),
        tertiaryContainer = 90.a3.harmonizeWithPrimary(0.1f),
        onTertiary = 100.a3.harmonizeWithPrimary(0.1f),
        onTertiaryContainer = 10.a3.harmonizeWithPrimary(0.1f),

        // Neutral surfaces - derived from primary seed
        background = 98.n1,
        onBackground = 10.n1,

        surface = 98.n1,
        onSurface = 10.n1,
        surfaceVariant = 90.n2,
        onSurfaceVariant = 30.n2,
        surfaceDim = 87.n1,
        surfaceBright = 98.n1,
        surfaceContainerLowest = 100.n1,
        surfaceContainerLow = 96.n1,
        surfaceContainer = 94.n1,
        surfaceContainerHigh = 92.n1,
        surfaceContainerHighest = 90.n1,
        inverseSurface = 20.n1,
        inverseOnSurface = 95.n1,

        // Outline
        outline = 50.n2,
        outlineVariant = 80.n2,
        
        // Error (standard red-based)
        error = 40.error,
        errorContainer = 90.error,
        onError = 100.error,
        onErrorContainer = 10.error,
        
        // Scrim
        scrim = Color.Black,
    )
}

/**
 * Generate a full Material 3 Dark ColorScheme from the current seed colors.
 * Uses CorePalette for proper HCT (Hue-Chroma-Tone) tonal scales.
 */
@Composable
fun darkColorSchemeFromSeed(): ColorScheme {
    return darkColorScheme(
        // Primary palette
        primary = 80.a1,
        primaryContainer = 30.a1,
        onPrimary = 20.a1,
        onPrimaryContainer = 90.a1,
        inversePrimary = 40.a1,

        // Secondary palette - harmonized with primary
        secondary = 80.a2.harmonizeWithPrimary(0.1f),
        secondaryContainer = 30.a2.harmonizeWithPrimary(0.1f),
        onSecondary = 20.a2.harmonizeWithPrimary(0.1f),
        onSecondaryContainer = 90.a2.harmonizeWithPrimary(0.1f),

        // Tertiary palette - harmonized with primary
        tertiary = 80.a3.harmonizeWithPrimary(0.1f),
        tertiaryContainer = 30.a3.harmonizeWithPrimary(0.1f),
        onTertiary = 20.a3.harmonizeWithPrimary(0.1f),
        onTertiaryContainer = 90.a3.harmonizeWithPrimary(0.1f),

        // Neutral surfaces - derived from primary seed
        background = 6.n1,
        onBackground = 90.n1,

        surface = 6.n1,
        onSurface = 90.n1,
        surfaceVariant = 30.n2,
        onSurfaceVariant = 80.n2,
        surfaceDim = 6.n1,
        surfaceBright = 24.n1,
        surfaceContainerLowest = 4.n1,
        surfaceContainerLow = 10.n1,
        surfaceContainer = 12.n1,
        surfaceContainerHigh = 17.n1,
        surfaceContainerHighest = 22.n1,
        inverseSurface = 90.n1,
        inverseOnSurface = 20.n1,

        // Outline
        outline = 60.n2,
        outlineVariant = 30.n2,
        
        // Error (standard red-based)
        error = 80.error,
        errorContainer = 30.error,
        onError = 20.error,
        onErrorContainer = 90.error,
        
        // Scrim
        scrim = Color.Black,
    )
}

/**
 * High contrast dark scheme (OLED black backgrounds).
 */
@Composable
fun highContrastDarkColorSchemeFromSeed(): ColorScheme {
    return darkColorSchemeFromSeed().copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = 6.n1,
        surfaceContainer = 10.n1,
        surfaceContainerHigh = 12.n1,
        surfaceContainerHighest = 17.n1,
    )
}

// ============================================================================
// COLOR UTILITIES
// ============================================================================

/**
 * Harmonize a color with the current primary color.
 * Blends a fraction of the primary color into this color for cohesive theming.
 */
@Composable
fun Color.harmonizeWithPrimary(
    @FloatRange(from = 0.0, to = 1.0) fraction: Float = 0.1f
): Color = blend(MaterialTheme.colorScheme.primary, fraction)

/**
 * Blend this color with another color by a given fraction.
 */
fun Color.blend(
    color: Color,
    @FloatRange(from = 0.0, to = 1.0) fraction: Float = 0.1f
): Color = Color(ColorUtils.blendARGB(this.toArgb(), color.toArgb(), fraction))