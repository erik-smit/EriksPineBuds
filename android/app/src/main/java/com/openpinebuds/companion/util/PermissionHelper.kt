package com.openpinebuds.companion.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

/**
 * Helper for requesting and checking Bluetooth permissions
 * Handles differences between Android versions
 */
object PermissionHelper {

    /**
     * Get required permissions based on Android version
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: New Bluetooth permissions
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            // Android 11 and below: Location + old Bluetooth permissions
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    /**
     * Check if all required permissions are granted
     */
    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request permissions using the provided launcher
     */
    fun requestPermissions(launcher: ActivityResultLauncher<Array<String>>) {
        launcher.launch(getRequiredPermissions())
    }

    /**
     * Get human-readable permission explanations
     */
    fun getPermissionExplanations(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            """
            This app needs the following permissions:

            • Bluetooth Scan: To discover your OpenPineBuds
            • Bluetooth Connect: To configure your earbuds
            """.trimIndent()
        } else {
            """
            This app needs the following permissions:

            • Bluetooth: To connect to your OpenPineBuds
            • Location: Required for Bluetooth scanning (Android requirement)
            """.trimIndent()
        }
    }
}
