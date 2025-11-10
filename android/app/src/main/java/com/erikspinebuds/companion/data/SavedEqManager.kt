package com.erikspinebuds.companion.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages saved custom EQ configurations
 */
class SavedEqManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "saved_eq_configs",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    companion object {
        private const val KEY_SAVED_EQS = "saved_eqs"
        private const val KEY_ACTIVE_CUSTOM_EQ = "active_custom_eq"
    }

    /**
     * Saved EQ entry with name and configuration
     */
    data class SavedEq(
        val name: String,
        val config: EqConfiguration,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Get all saved custom EQs
     */
    fun getSavedEqs(): List<SavedEq> {
        val json = prefs.getString(KEY_SAVED_EQS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SavedEq>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            android.util.Log.e("SavedEqManager", "Failed to load saved EQs", e)
            emptyList()
        }
    }

    /**
     * Save a custom EQ with a name
     */
    fun saveEq(name: String, config: EqConfiguration): Boolean {
        val saved = getSavedEqs().toMutableList()

        // Remove existing EQ with same name
        saved.removeAll { it.name == name }

        // Add new EQ
        saved.add(SavedEq(name, config.copy()))

        // Save to preferences
        val json = gson.toJson(saved)
        return prefs.edit().putString(KEY_SAVED_EQS, json).commit()
    }

    /**
     * Delete a saved EQ by name
     */
    fun deleteEq(name: String): Boolean {
        val saved = getSavedEqs().toMutableList()
        saved.removeAll { it.name == name }

        val json = gson.toJson(saved)
        return prefs.edit().putString(KEY_SAVED_EQS, json).commit()
    }

    /**
     * Get a saved EQ by name
     */
    fun getEq(name: String): SavedEq? {
        return getSavedEqs().find { it.name == name }
    }

    /**
     * Check if a name already exists
     */
    fun nameExists(name: String): Boolean {
        return getSavedEqs().any { it.name == name }
    }

    /**
     * Save the currently active custom EQ configuration
     * (the one that's applied to the device)
     */
    fun setActiveCustomEq(config: EqConfiguration) {
        val json = gson.toJson(config)
        prefs.edit().putString(KEY_ACTIVE_CUSTOM_EQ, json).commit()
    }

    /**
     * Get the currently active custom EQ configuration
     * (the one that's applied to the device)
     */
    fun getActiveCustomEq(): EqConfiguration? {
        val json = prefs.getString(KEY_ACTIVE_CUSTOM_EQ, null) ?: return null
        return try {
            gson.fromJson(json, EqConfiguration::class.java)
        } catch (e: Exception) {
            android.util.Log.e("SavedEqManager", "Failed to load active custom EQ", e)
            null
        }
    }

    /**
     * Clear the active custom EQ
     */
    fun clearActiveCustomEq() {
        prefs.edit().remove(KEY_ACTIVE_CUSTOM_EQ).commit()
    }
}
