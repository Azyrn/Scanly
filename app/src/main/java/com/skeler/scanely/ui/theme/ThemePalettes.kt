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
    val tertiary: Int,
    /** True for a pure grayscale scheme; skips the HCT chroma boost so no hue leaks in. */
    val monochrome: Boolean = false
)

/**
 * Curated, ordered set of premium palettes.
 *
 * Primaries walk the colour wheel (warm → cool → magenta) so the picker reads as
 * one intentional spectrum. Each palette pairs its primary with saturated,
 * clearly-separated secondary/tertiary hues so no two palettes look alike.
 */
object SeedPalettes {
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

    val EmeraldGrove = SeedColor(
        name = "Emerald Grove",
        primary = 0xFF10B981.toInt(),
        secondary = 0xFF34D399.toInt(),
        tertiary = 0xFF2DD4BF.toInt()
    )
    val SunsetCoral = SeedColor(
        name = "Sunset Coral",
        primary = 0xFFF97316.toInt(),
        secondary = 0xFFFB923C.toInt(),
        tertiary = 0xFFF59E0B.toInt()
    )
    val RoseBlush = SeedColor(
        name = "Rose Blush",
        primary = 0xFFF43F5E.toInt(),
        secondary = 0xFFFB7185.toInt(),
        tertiary = 0xFFEC4899.toInt()
    )

    val ALL: List<SeedColor> = listOf(
        RoseBlush, CeruleanWave, LavenderVolt, AmethystHaze, EmeraldGrove, SunsetCoral
    )

    /** Purple-leaning anchor that reads as the classic Material 3 default. */
    val DEFAULT: SeedColor = LavenderVolt

    /** Persisted index that selects [DEFAULT]; the single source of truth for defaults. */
    val DEFAULT_INDEX: Int = ALL.indexOf(DEFAULT)

    fun seedAt(index: Int): SeedColor = ALL.getOrElse(index) { DEFAULT }
}
