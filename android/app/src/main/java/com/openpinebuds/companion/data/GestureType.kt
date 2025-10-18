package com.openpinebuds.companion.data

/**
 * Touch gesture types supported by the earbuds
 */
enum class GestureType(val displayName: String) {
    SINGLE_TAP("Single Tap"),
    DOUBLE_TAP("Double Tap"),
    TRIPLE_TAP("Triple Tap"),
    LONG_PRESS("Long Press");
}
