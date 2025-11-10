package com.erikspinebuds.companion.data

/**
 * Predefined EQ presets matching firmware definitions
 * These can be used as starting points for customization
 */
object EqPresets {

    /**
     * Get preset configuration by enum value
     */
    fun getPresetConfig(preset: com.erikspinebuds.companion.ble.EqPreset): EqConfiguration {
        return when (preset) {
            com.erikspinebuds.companion.ble.EqPreset.FLAT -> FLAT
            com.erikspinebuds.companion.ble.EqPreset.BASS_BOOST -> BASS_BOOST
            com.erikspinebuds.companion.ble.EqPreset.TREBLE_BOOST -> TREBLE_BOOST
            com.erikspinebuds.companion.ble.EqPreset.V_SHAPE -> V_SHAPE
            com.erikspinebuds.companion.ble.EqPreset.VOCAL -> VOCAL
            com.erikspinebuds.companion.ble.EqPreset.CLASSICAL -> CLASSICAL
            com.erikspinebuds.companion.ble.EqPreset.ROCK -> ROCK
            com.erikspinebuds.companion.ble.EqPreset.JAZZ -> JAZZ
            com.erikspinebuds.companion.ble.EqPreset.ELECTRONIC -> ELECTRONIC
            com.erikspinebuds.companion.ble.EqPreset.PODCAST -> PODCAST
            com.erikspinebuds.companion.ble.EqPreset.CUSTOM -> EqConfiguration.flat() // Default for custom
        }
    }

    /**
     * Get preset name for display
     */
    fun getPresetName(preset: com.erikspinebuds.companion.ble.EqPreset): String {
        return when (preset) {
            com.erikspinebuds.companion.ble.EqPreset.FLAT -> "Flat"
            com.erikspinebuds.companion.ble.EqPreset.BASS_BOOST -> "Bass Boost"
            com.erikspinebuds.companion.ble.EqPreset.TREBLE_BOOST -> "Treble Boost"
            com.erikspinebuds.companion.ble.EqPreset.V_SHAPE -> "V-Shape"
            com.erikspinebuds.companion.ble.EqPreset.VOCAL -> "Vocal"
            com.erikspinebuds.companion.ble.EqPreset.CLASSICAL -> "Classical"
            com.erikspinebuds.companion.ble.EqPreset.ROCK -> "Rock"
            com.erikspinebuds.companion.ble.EqPreset.JAZZ -> "Jazz"
            com.erikspinebuds.companion.ble.EqPreset.ELECTRONIC -> "Electronic"
            com.erikspinebuds.companion.ble.EqPreset.PODCAST -> "Podcast"
            com.erikspinebuds.companion.ble.EqPreset.CUSTOM -> "Custom"
        }
    }

    /**
     * Get preset description
     */
    fun getPresetDescription(preset: com.erikspinebuds.companion.ble.EqPreset): String {
        return when (preset) {
            com.erikspinebuds.companion.ble.EqPreset.FLAT -> "Bypass (no EQ)"
            com.erikspinebuds.companion.ble.EqPreset.BASS_BOOST -> "Warm, punchy bass"
            com.erikspinebuds.companion.ble.EqPreset.TREBLE_BOOST -> "Bright, airy"
            com.erikspinebuds.companion.ble.EqPreset.V_SHAPE -> "Exciting, smiley curve"
            com.erikspinebuds.companion.ble.EqPreset.VOCAL -> "Clear voice, reduced bass/treble"
            com.erikspinebuds.companion.ble.EqPreset.CLASSICAL -> "Natural, balanced"
            com.erikspinebuds.companion.ble.EqPreset.ROCK -> "Punchy, energetic"
            com.erikspinebuds.companion.ble.EqPreset.JAZZ -> "Smooth, detailed"
            com.erikspinebuds.companion.ble.EqPreset.ELECTRONIC -> "Deep bass, sparkle"
            com.erikspinebuds.companion.ble.EqPreset.PODCAST -> "Speech optimized"
            com.erikspinebuds.companion.ble.EqPreset.CUSTOM -> "User-defined configuration"
        }
    }

    // FLAT (bypass)
    val FLAT = EqConfiguration(
        gain0 = 0.0f,
        gain1 = 0.0f,
        numBands = 0,
        bands = mutableListOf()
    )

    // BASS_BOOST - Warm, punchy bass
    val BASS_BOOST = EqConfiguration(
        gain0 = 0.0f,
        gain1 = 0.0f,
        numBands = 3,
        bands = mutableListOf(
            EqBand(EqFilterType.LOW_SHELF, 6.0f, 100.0f, 0.7f),   // Warm sub-bass
            EqBand(EqFilterType.PEAK, 4.0f, 250.0f, 1.0f),        // Punch in mid-bass
            EqBand(EqFilterType.PEAK, -1.0f, 2000.0f, 1.0f),      // Slight mid reduction for clarity
        )
    )

    // TREBLE_BOOST - Bright, airy
    val TREBLE_BOOST = EqConfiguration(
        gain0 = 0.0f,
        gain1 = 0.0f,
        numBands = 4,
        bands = mutableListOf(
            EqBand(EqFilterType.PEAK, -2.0f, 400.0f, 1.0f),       // Reduce lower mids
            EqBand(EqFilterType.PEAK, 3.0f, 3000.0f, 1.5f),       // Presence boost
            EqBand(EqFilterType.PEAK, 4.0f, 8000.0f, 1.0f),       // Sparkle
            EqBand(EqFilterType.HIGH_SHELF, 3.0f, 12000.0f, 0.7f), // Air
        )
    )

    // V_SHAPE - Exciting, smiley curve
    val V_SHAPE = EqConfiguration(
        gain0 = 0.0f,
        gain1 = 0.0f,
        numBands = 5,
        bands = mutableListOf(
            EqBand(EqFilterType.LOW_SHELF, 5.0f, 80.0f, 0.7f),    // Solid bass
            EqBand(EqFilterType.PEAK, 3.0f, 200.0f, 1.0f),        // Bass punch
            EqBand(EqFilterType.PEAK, -3.0f, 1000.0f, 1.5f),      // Scoop mids
            EqBand(EqFilterType.PEAK, 4.0f, 6000.0f, 1.0f),       // Treble clarity
            EqBand(EqFilterType.HIGH_SHELF, 4.0f, 12000.0f, 0.7f), // Air
        )
    )

    // VOCAL - Clear voice, reduced bass/treble
    val VOCAL = EqConfiguration(
        gain0 = 0.0f,
        gain1 = 0.0f,
        numBands = 5,
        bands = mutableListOf(
            EqBand(EqFilterType.HIGH_PASS, 0.0f, 80.0f, 0.7f),    // Remove rumble
            EqBand(EqFilterType.PEAK, -2.0f, 250.0f, 1.0f),       // Reduce body boom
            EqBand(EqFilterType.PEAK, 3.0f, 1200.0f, 1.0f),       // Vocal presence
            EqBand(EqFilterType.PEAK, 4.0f, 3000.0f, 1.5f),       // Vocal clarity
            EqBand(EqFilterType.HIGH_SHELF, -2.0f, 10000.0f, 0.7f), // Reduce harshness
        )
    )

    // CLASSICAL - Natural, balanced
    val CLASSICAL = EqConfiguration(
        gain0 = 0.0f,
        gain1 = 0.0f,
        numBands = 5,
        bands = mutableListOf(
            EqBand(EqFilterType.LOW_SHELF, 2.0f, 60.0f, 0.7f),    // Warm lows
            EqBand(EqFilterType.PEAK, -1.0f, 300.0f, 1.0f),       // Reduce boxiness
            EqBand(EqFilterType.PEAK, 1.5f, 1500.0f, 1.0f),       // String clarity
            EqBand(EqFilterType.PEAK, 2.0f, 4000.0f, 1.0f),       // Detail
            EqBand(EqFilterType.HIGH_SHELF, 2.0f, 10000.0f, 0.7f), // Gentle air
        )
    )

    // ROCK - Punchy, energetic
    val ROCK = EqConfiguration(
        gain0 = 0.0f,
        gain1 = 0.0f,
        numBands = 6,
        bands = mutableListOf(
            EqBand(EqFilterType.LOW_SHELF, 4.0f, 80.0f, 0.7f),    // Bass kick
            EqBand(EqFilterType.PEAK, 3.0f, 200.0f, 1.0f),        // Bass guitar
            EqBand(EqFilterType.PEAK, 2.0f, 800.0f, 1.0f),        // Guitar body
            EqBand(EqFilterType.PEAK, -2.0f, 2000.0f, 1.0f),      // Reduce harshness
            EqBand(EqFilterType.PEAK, 3.0f, 4000.0f, 1.0f),       // Presence
            EqBand(EqFilterType.HIGH_SHELF, 3.0f, 10000.0f, 0.7f), // Cymbals
        )
    )

    // JAZZ - Smooth, detailed
    val JAZZ = EqConfiguration(
        gain0 = 0.0f,
        gain1 = 0.0f,
        numBands = 5,
        bands = mutableListOf(
            EqBand(EqFilterType.LOW_SHELF, 2.0f, 80.0f, 0.7f),    // Upright bass warmth
            EqBand(EqFilterType.PEAK, 1.5f, 500.0f, 1.0f),        // Piano body
            EqBand(EqFilterType.PEAK, 2.0f, 2000.0f, 1.0f),       // Saxophone presence
            EqBand(EqFilterType.PEAK, 2.5f, 5000.0f, 1.0f),       // Cymbal detail
            EqBand(EqFilterType.HIGH_SHELF, 1.5f, 12000.0f, 0.7f), // Air
        )
    )

    // ELECTRONIC - Deep bass, sparkle
    val ELECTRONIC = EqConfiguration(
        gain0 = 0.0f,
        gain1 = 0.0f,
        numBands = 6,
        bands = mutableListOf(
            EqBand(EqFilterType.LOW_SHELF, 6.0f, 60.0f, 0.7f),    // Deep sub-bass
            EqBand(EqFilterType.PEAK, 5.0f, 150.0f, 1.0f),        // Bass punch
            EqBand(EqFilterType.PEAK, -2.0f, 600.0f, 1.0f),       // Reduce muddiness
            EqBand(EqFilterType.PEAK, -1.0f, 1500.0f, 1.0f),      // Clear mids
            EqBand(EqFilterType.PEAK, 4.0f, 5000.0f, 1.0f),       // Synth clarity
            EqBand(EqFilterType.HIGH_SHELF, 5.0f, 12000.0f, 0.7f), // Hi-hat/cymbal sparkle
        )
    )

    // PODCAST - Speech optimized
    val PODCAST = EqConfiguration(
        gain0 = 0.0f,
        gain1 = 0.0f,
        numBands = 4,
        bands = mutableListOf(
            EqBand(EqFilterType.HIGH_PASS, 0.0f, 100.0f, 0.7f),   // Remove rumble/wind
            EqBand(EqFilterType.PEAK, 4.0f, 1200.0f, 1.0f),       // Vocal presence
            EqBand(EqFilterType.PEAK, 4.0f, 3000.0f, 1.0f),       // Intelligibility
            EqBand(EqFilterType.HIGH_SHELF, -3.0f, 8000.0f, 0.7f), // Reduce sibilance
        )
    )

    /**
     * Get all available presets (excluding CUSTOM which is user-defined)
     */
    fun getAllPresets(): List<com.erikspinebuds.companion.ble.EqPreset> {
        return listOf(
            com.erikspinebuds.companion.ble.EqPreset.FLAT,
            com.erikspinebuds.companion.ble.EqPreset.BASS_BOOST,
            com.erikspinebuds.companion.ble.EqPreset.TREBLE_BOOST,
            com.erikspinebuds.companion.ble.EqPreset.V_SHAPE,
            com.erikspinebuds.companion.ble.EqPreset.VOCAL,
            com.erikspinebuds.companion.ble.EqPreset.CLASSICAL,
            com.erikspinebuds.companion.ble.EqPreset.ROCK,
            com.erikspinebuds.companion.ble.EqPreset.JAZZ,
            com.erikspinebuds.companion.ble.EqPreset.ELECTRONIC,
            com.erikspinebuds.companion.ble.EqPreset.PODCAST
        )
    }
}
