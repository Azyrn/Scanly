package com.skeler.scanely.settings

import androidx.appcompat.app.AppCompatDelegate
import com.skeler.scanely.core.common.resolvedDarkTheme
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.domain.model.SettingsState
import com.skeler.scanely.ui.theme.SeedPalettes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookAndFeelSettingsTest {

    @Test
    fun `SEED_COLOR_INDEX default matches SeedPalettes DEFAULT_INDEX`() {
        val defaultIndex = SettingsKeys.SEED_COLOR_INDEX.default as Int
        assertEquals(SeedPalettes.DEFAULT_INDEX, defaultIndex)
    }

    @Test
    fun `USE_DYNAMIC_COLORS default value is true`() {
        val defaultValue = SettingsKeys.USE_DYNAMIC_COLORS.default as Boolean
        assertTrue(defaultValue)
    }

    @Test
    fun `IS_OLED_MODE_ENABLED default is false`() {
        val defaultValue = SettingsKeys.IS_OLED_MODE_ENABLED.default as Boolean
        assertEquals(false, defaultValue)
    }

    @Test
    fun `SeedPalettes ALL is non-empty and names are unique`() {
        assertTrue(SeedPalettes.ALL.isNotEmpty())
        val names = SeedPalettes.ALL.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `SeedPalettes DEFAULT is LavenderVolt`() {
        assertEquals(SeedPalettes.LavenderVolt, SeedPalettes.DEFAULT)
    }

    @Test
    fun `DEFAULT_INDEX resolves back to DEFAULT`() {
        assertEquals(SeedPalettes.DEFAULT, SeedPalettes.ALL[SeedPalettes.DEFAULT_INDEX])
    }

    @Test
    fun `seedAt returns default for invalid index`() {
        assertEquals(SeedPalettes.DEFAULT, SeedPalettes.seedAt(100))
    }

    @Test
    fun `seedAt returns correct palette for valid index`() {
        assertEquals(SeedPalettes.ALL[0], SeedPalettes.seedAt(0))
    }

    @Test
    fun `every SeedColor triplet has distinct anchors`() {
        SeedPalettes.ALL.forEach { seed ->
            assertTrue(seed.name, seed.primary != seed.secondary)
            assertTrue(seed.name, seed.secondary != seed.tertiary)
            assertTrue(seed.name, seed.primary != seed.tertiary)
        }
    }

    @Test
    fun `THEME_MODE default follows system`() {
        val defaultMode = SettingsKeys.THEME_MODE.default as Int
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, defaultMode)
    }

    @Test
    fun `resolved dark theme follows explicit and system modes`() {
        val baseState = SettingsState(
            themeMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            isOledModeEnabled = false,
            ocrLanguages = emptySet(),
            useDynamicColors = true,
            seedColorIndex = SeedPalettes.DEFAULT_INDEX
        )

        assertFalse(baseState.resolvedDarkTheme(systemDarkTheme = false))
        assertTrue(baseState.resolvedDarkTheme(systemDarkTheme = true))
        assertTrue(baseState.copy(themeMode = AppCompatDelegate.MODE_NIGHT_YES).resolvedDarkTheme(false))
        assertFalse(baseState.copy(themeMode = AppCompatDelegate.MODE_NIGHT_NO).resolvedDarkTheme(true))
    }
}
