package com.skeler.scanely.ui.theme

/**
 * A palette seed: three HCT anchors that drive the full Material 3 tonal scheme.
 *
 * Only the hue of each anchor really matters — [ColorSchemes] boosts chroma when
 * it expands these into tonal palettes, so even a muted hex resolves to a vibrant
 * accent. Pick hues that are distinct from one another for an elegant, modern feel.
 */
data class SeedColor(
    val name: String,
    val primary: Int,
    val secondary: Int,
    val tertiary: Int
)

/**
 * Curated, ordered set of premium palettes.
 *
 * Primaries walk the colour wheel (warm → cool → magenta) so the picker reads as
 * one intentional spectrum. Each palette pairs its primary with saturated,
 * clearly-separated secondary/tertiary hues so no two palettes look alike.
 */
object SeedPalettes {
    val RubyEmber = SeedColor(
        name = "Ruby Ember",
        primary = 0xFFE11D48.toInt(),
        secondary = 0xFFFB7185.toInt(),
        tertiary = 0xFFF59E0B.toInt()
    )
    val MoltenCoral = SeedColor(
        name = "Molten Coral",
        primary = 0xFFF54A0C.toInt(),
        secondary = 0xFFFB923C.toInt(),
        tertiary = 0xFFFACC15.toInt()
    )
    val GoldenHour = SeedColor(
        name = "Golden Hour",
        primary = 0xFFF0A020.toInt(),
        secondary = 0xFFFFD24C.toInt(),
        tertiary = 0xFFE8643C.toInt()
    )
    val OrchardGreen = SeedColor(
        name = "Orchard Green",
        primary = 0xFF16A34A.toInt(),
        secondary = 0xFF84CC16.toInt(),
        tertiary = 0xFF10B981.toInt()
    )
    val JadeTide = SeedColor(
        name = "Jade Tide",
        primary = 0xFF10B981.toInt(),
        secondary = 0xFF34D399.toInt(),
        tertiary = 0xFF22D3EE.toInt()
    )
    val LagoonTeal = SeedColor(
        name = "Lagoon Teal",
        primary = 0xFF06B6D4.toInt(),
        secondary = 0xFF22D3EE.toInt(),
        tertiary = 0xFF3B82F6.toInt()
    )
    val CeruleanWave = SeedColor(
        name = "Cerulean Wave",
        primary = 0xFF3B82F6.toInt(),
        secondary = 0xFF60A5FA.toInt(),
        tertiary = 0xFF06B6D4.toInt()
    )
    val LavenderVolt = SeedColor(
        name = "Lavender Volt",
        primary = 0xFF7C6FF0.toInt(),
        secondary = 0xFFA78BFA.toInt(),
        tertiary = 0xFFF472B6.toInt()
    )
    val AmethystHaze = SeedColor(
        name = "Amethyst Haze",
        primary = 0xFFA855F7.toInt(),
        secondary = 0xFFC084FC.toInt(),
        tertiary = 0xFFF0ABFC.toInt()
    )
    val RaspberryCrush = SeedColor(
        name = "Raspberry Crush",
        primary = 0xFFE11D74.toInt(),
        secondary = 0xFFFB7185.toInt(),
        tertiary = 0xFFFB923C.toInt()
    )

    val ALL: List<SeedColor> = listOf(
        RubyEmber, MoltenCoral, GoldenHour, OrchardGreen, JadeTide,
        LagoonTeal, CeruleanWave, LavenderVolt, AmethystHaze, RaspberryCrush
    )

    /** Purple-leaning anchor that reads as the classic Material 3 default. */
    val DEFAULT: SeedColor = LavenderVolt

    /** Persisted index that selects [DEFAULT]; the single source of truth for defaults. */
    val DEFAULT_INDEX: Int = ALL.indexOf(DEFAULT)

    fun seedAt(index: Int): SeedColor = ALL.getOrElse(index) { DEFAULT }
}
