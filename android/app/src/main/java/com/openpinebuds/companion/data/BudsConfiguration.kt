package com.openpinebuds.companion.data

/**
 * Complete configuration for both earbuds
 */
data class BudsConfiguration(
    var leftEarbud: EarbudConfig = EarbudConfig.defaultLeft(),
    var rightEarbud: EarbudConfig = EarbudConfig.defaultRight(),
    var versionMajor: Int = 1,
    var versionMinor: Int = 0,
    var versionPatch: Int = 0
) {
    /**
     * Get version as a string
     */
    fun getVersionString(): String = "$versionMajor.$versionMinor.$versionPatch"

    companion object {
        /**
         * Create default configuration
         */
        fun default() = BudsConfiguration()
    }
}
