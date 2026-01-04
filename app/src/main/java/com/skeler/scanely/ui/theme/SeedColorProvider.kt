package com.skeler.scanely.ui.theme

/**
 * Global singleton that holds the current seed colors for theme generation.
 * Updated when user selects a palette in Settings.
 * Read by PaletteExtensions to generate tonal palettes via CorePalette.
 */
object SeedColorProvider {
    var primary: Int = SeedPalettes.DEFAULT.primary
        private set
    var secondary: Int = SeedPalettes.DEFAULT.secondary
        private set
    var tertiary: Int = SeedPalettes.DEFAULT.tertiary
        private set

    fun setSeedColor(seedColor: SeedColor) {
        primary = seedColor.primary
        secondary = seedColor.secondary
        tertiary = seedColor.tertiary
    }

    fun setSeedColorByIndex(index: Int) {
        val seed = SeedPalettes.ALL.getOrElse(index) { SeedPalettes.DEFAULT }
        setSeedColor(seed)
    }
}
