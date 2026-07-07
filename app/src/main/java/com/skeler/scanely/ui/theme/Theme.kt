@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.skeler.scanely.core.common.LocalDarkMode
import com.skeler.scanely.core.common.LocalSettings

/**
 * Root theme. Resolves a single [androidx.compose.material3.ColorScheme] through one
 * pipeline (dynamic wallpaper OR curated seed) → applies the OLED / pure-black
 * surface guard → crossfades it → hands it to Material 3 Expressive.
 *
 * Colour generation lives in [ColorSchemes], palette data in [ThemePalettes],
 * shapes in [ThemeShapes], typography in [Type], and the transition in [ThemeMotion].
 */
@Composable
fun ScanelyTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val darkTheme = LocalDarkMode.current

    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamic = settings.useDynamicColors && supportsDynamic
    val seed = remember(settings.seedColorIndex) { SeedPalettes.seedAt(settings.seedColorIndex) }

    val baseScheme = remember(seed, darkTheme, useDynamic, context) {
        when {
            useDynamic && darkTheme -> dynamicDarkColorScheme(context)
            useDynamic -> dynamicLightColorScheme(context)
            else -> seedColorScheme(seed, darkTheme)
        }
    }

    val colorScheme = when {
        darkTheme && settings.isOledModeEnabled -> baseScheme.toOledBlack()
        darkTheme -> baseScheme.enlivenDark()
        else -> baseScheme
    }
    val animatedColorScheme = colorScheme.animated()

    // Transparent, contrast-free system bars keyed on brightness. Letting the
    // (animated) Compose surface paint edge-to-edge behind the bars is what stops
    // the OS-drawn status/nav strip from lagging a frame behind a theme switch.
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        val useDarkSystemBarIcons = animatedColorScheme.background.luminance() > 0.5f
        SideEffect {
            window.decorView.setBackgroundColor(animatedColorScheme.background.toArgb())
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = useDarkSystemBarIcons
                isAppearanceLightNavigationBars = useDarkSystemBarIcons
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = animatedColorScheme,
        typography = Typography,
        shapes = ScanelyShapes,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(animatedColorScheme.background)
        ) {
            content()
        }
    }
}
