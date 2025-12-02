package com.davidwxcui.waterwise.ui.reminder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.davidwxcui.waterwise.data.ReminderPreferences
import com.davidwxcui.waterwise.data.models.ReminderMode
import com.davidwxcui.waterwise.data.models.ReminderSettings

/**
 * ViewModel for managing reminder settings state
 */
class ReminderSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val reminderPrefs = ReminderPreferences(application)

    private val _settings = MutableLiveData<ReminderSettings>()
    val settings: LiveData<ReminderSettings> = _settings

    private val _customTimes = MutableLiveData<List<String>>()
    val customTimes: LiveData<List<String>> = _customTimes

    private val _intervalMinutes = MutableLiveData<Int>()
    val intervalMinutes: LiveData<Int> = _intervalMinutes

    init {
        loadSettings()
    }

    /**
     * Load current settings from preferences
     */
    fun loadSettings() {
        val currentSettings = reminderPrefs.getReminderSettings()
        _settings.value = currentSettings
        _customTimes.value = currentSettings.customTimes
        _intervalMinutes.value = currentSettings.intervalMinutes
    }

    /**
     * Update reminder mode
     */
    fun updateMode(mode: ReminderMode) {
        val current = _settings.value ?: return
        _settings.value = current.copy(mode = mode)
    }

    /**
     * Update interval in minutes
     */
    fun updateInterval(minutes: Int) {
        _intervalMinutes.value = minutes
        val current = _settings.value ?: return
        _settings.value = current.copy(intervalMinutes = minutes)
    }

    /**
     * Add a custom time
     */
    fun addCustomTime(time: String) {
        val currentTimes = _customTimes.value?.toMutableList() ?: mutableListOf()

        // Check if time already exists
        if (currentTimes.contains(time)) {
            return // Don't add duplicates
        }

        currentTimes.add(time)
        _customTimes.value = currentTimes.sorted()

        // Update settings
        val current = _settings.value ?: return
        _settings.value = current.copy(customTimes = currentTimes.sorted())
    }

    /**
     * Remove a custom time
     */
    fun removeCustomTime(time: String) {
        val currentTimes = _customTimes.value?.toMutableList() ?: return
        currentTimes.remove(time)
        _customTimes.value = currentTimes

        // Update settings
        val current = _settings.value ?: return
        _settings.value = current.copy(customTimes = currentTimes)
    }

    /**
     * Save all settings to preferences
     */
    fun saveSettings(): Boolean {
        val currentSettings = _settings.value ?: return false

        // Validation
        if (currentSettings.mode == ReminderMode.CUSTOM_TIMES &&
            currentSettings.customTimes.isEmpty()) {
            return false // Need at least one time for custom mode
        }

        // Save to preferences
        reminderPrefs.saveReminderSettings(currentSettings)
        return true
    }

    /**
     * Check if time already exists
     */
    fun timeExists(time: String): Boolean {
        return _customTimes.value?.contains(time) == true
    }
}

