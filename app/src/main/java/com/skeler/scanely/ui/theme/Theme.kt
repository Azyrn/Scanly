@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.skeler.scanely.core.common.LocalDarkMode
import com.skeler.scanely.core.common.LocalSettings

// =============================================================================
// SHAPES
// =============================================================================

val ScanelyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ScanelyTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = LocalDarkMode.current
    val isOledModeEnabled = LocalSettings.current.isOledModeEnabled

    val settings = LocalSettings.current
    val useDynamicColors = settings.useDynamicColors
    val seedColorIndex = settings.seedColorIndex
    
    // SYNCHRONOUSLY update SeedColorProvider BEFORE color scheme generation
    // Using remember ensures this runs during composition, not after (LaunchedEffect race fix)
    val currentSeedColor = remember(seedColorIndex) {
        val seed = SeedPalettes.ALL.getOrElse(seedColorIndex) { SeedPalettes.DEFAULT }
        SeedColorProvider.setSeedColor(seed)
        seed
    }

    val baseColorScheme = when {
        useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme && isOledModeEnabled) highContrastDynamicDarkColorScheme(context)
            else if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        
        darkTheme -> {
            if (isOledModeEnabled) highContrastDarkColorSchemeFromSeed()
            else darkColorSchemeFromSeed()
        }
        
        else -> lightColorSchemeFromSeed()
    }

    // OLED Override & Output Logic
    val colorScheme = when {
        // Case 1: OLED Mode Enabled -> FORCE Black
        darkTheme && isOledModeEnabled -> {
            baseColorScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainerLowest = Color.Black
            )
        }
        // Case 2: OLED Mode Disabled -> FORCE Non-Black (Fix for Stuck Toggle)
        // If Dynamic Theme or Seed generates Pure Black, we must override it to Dark Gray.
        darkTheme && !isOledModeEnabled && baseColorScheme.background == Color.Black -> {
            baseColorScheme.copy(
                background = Color(0xFF141218), // Standard M3 Dark (Tone 6)
                surface = Color(0xFF141218),
                surfaceContainerLowest = Color(0xFF0F0D13) // Slightly darker than surface (Tone 4)
            )
        }
        // Case 3: Default (Light Theme or standard Dark Theme)
        else -> baseColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ScanelyShapes,
        content = content
    )
}