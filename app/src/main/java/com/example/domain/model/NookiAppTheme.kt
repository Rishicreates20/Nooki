package com.example.domain.model

/**
 * Curated color palettes for Nooki's Theme Selector.
 * Selecting a theme updates both the app's Material 3 surface/accent palette and
 * the 3D particle effect colors (orbital happy particles, touch bursts, and drag trails).
 */
enum class NookiAppTheme(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val previewEmoji: String,
    val backgroundDeepHex: Long,
    val surfaceContainerHex: Long,
    val surfaceVariantHex: Long,
    val primaryAccentHex: Long,
    val secondaryAccentHex: Long,
    val tertiaryAlertHex: Long,
    val textPrimaryHex: Long,
    val threadSpineHex: Long,
    val particleColorsHex: List<Long>
) {
    MIDNIGHT_COZY(
        id = "MIDNIGHT_COZY",
        displayName = "Midnight Cozy",
        subtitle = "Night Indigo, Marigold & Leaf Green fireflies",
        previewEmoji = "🌙",
        backgroundDeepHex = 0xFF12153AL,
        surfaceContainerHex = 0xFF1C2254L,
        surfaceVariantHex = 0xFF252D6BL,
        primaryAccentHex = 0xFFFFAE1AL,
        secondaryAccentHex = 0xFF1F9D6BL,
        tertiaryAlertHex = 0xFFE23B3BL,
        textPrimaryHex = 0xFFEEF0FAL,
        threadSpineHex = 0xFF7B8CDEL,
        particleColorsHex = listOf(
            0xFFFFAE1AL, // Marigold Star
            0xFFE23B3BL, // Sindoor Heart
            0xFF1F9D6BL, // Leaf Green Firefly
            0xFFFF8FA3L, // Cozy Rose
            0xFFFDE68AL  // Starlight Gold
        )
    ),

    FOREST_MEADOW(
        id = "FOREST_MEADOW",
        displayName = "Forest Meadow",
        subtitle = "Deep Pine, Sunlit Fern & Emerald meadow sprites",
        previewEmoji = "🌿",
        backgroundDeepHex = 0xFF0C2218L,
        surfaceContainerHex = 0xFF153526L,
        surfaceVariantHex = 0xFF1E4935L,
        primaryAccentHex = 0xFFA3E635L,
        secondaryAccentHex = 0xFF34D399L,
        tertiaryAlertHex = 0xFFFB7185L,
        textPrimaryHex = 0xFFECFDF5L,
        threadSpineHex = 0xFF6EE7B7L,
        particleColorsHex = listOf(
            0xFFA3E635L, // Sunlit Fern
            0xFF34D399L, // Emerald Firefly
            0xFFFDE047L, // Golden Meadow Pollen
            0xFFFB7185L, // Wildflower Rose
            0xFF6EE7B7L  // Mint Dew
        )
    ),

    WARM_HEARTH(
        id = "WARM_HEARTH",
        displayName = "Warm Hearth",
        subtitle = "Ember Mahogany, Honey Gold & dancing hearth sparks",
        previewEmoji = "🔥",
        backgroundDeepHex = 0xFF24120EL,
        surfaceContainerHex = 0xFF381D17L,
        surfaceVariantHex = 0xFF4D2920L,
        primaryAccentHex = 0xFFFB923CL,
        secondaryAccentHex = 0xFFFBBF24L,
        tertiaryAlertHex = 0xFFF87171L,
        textPrimaryHex = 0xFFFFF7EDL,
        threadSpineHex = 0xFFFDBA74L,
        particleColorsHex = listOf(
            0xFFFB923CL, // Hearth Ember Orange
            0xFFFBBF24L, // Honey Amber Spark
            0xFFF87171L, // Warm Crimson Heart
            0xFFFEF08AL, // Candlelight Glow
            0xFFFDBA74L  // Soft Peach Flame
        )
    ),

    MONSOON_LOTUS(
        id = "MONSOON_LOTUS",
        displayName = "Monsoon Lotus",
        subtitle = "Raincloud Teal, Lotus Pink & luminescent water drops",
        previewEmoji = "🪷",
        backgroundDeepHex = 0xFF0F1B2BL,
        surfaceContainerHex = 0xFF192B42L,
        surfaceVariantHex = 0xFF243B59L,
        primaryAccentHex = 0xFFF472B6L,
        secondaryAccentHex = 0xFF38BDF8L,
        tertiaryAlertHex = 0xFFFB7185L,
        textPrimaryHex = 0xFFF0F9FFL,
        threadSpineHex = 0xFF7DD3FCL,
        particleColorsHex = listOf(
            0xFFF472B6L, // Lotus Petal Pink
            0xFF38BDF8L, // Monsoon Aqua Orb
            0xFFFACC15L, // Golden Lotus Stamen
            0xFFC084FCL, // Twilight Orchid
            0xFFBAE6FDL  // Rain Mist Shimmer
        )
    );

    val badgeEmoji: String
        get() = previewEmoji

    val surfaceCardHex: Long
        get() = surfaceContainerHex

    val surfaceElevatedHex: Long
        get() = surfaceVariantHex

    val textMutedHex: Long
        get() = threadSpineHex

    companion object {
        fun fromId(id: String?): NookiAppTheme {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MIDNIGHT_COZY
        }
    }
}
