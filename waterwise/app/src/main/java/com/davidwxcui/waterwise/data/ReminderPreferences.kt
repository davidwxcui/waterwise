package com.davidwxcui.waterwise.data

import android.content.Context
import android.content.SharedPreferences
import com.davidwxcui.waterwise.data.models.ReminderMode
import com.davidwxcui.waterwise.data.models.ReminderSettings

/**
 * Manages reminder settings using SharedPreferences
 */
class ReminderPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "reminder_prefs"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_MODE = "reminder_mode"
        private const val KEY_INTERVAL_MINUTES = "interval_minutes"
        private const val KEY_CUSTOM_TIMES = "custom_times"
    }

    /**
     * Check if reminders are enabled
     */
    fun isReminderEnabled(): Boolean {
        return prefs.getBoolean(KEY_REMINDER_ENABLED, false)
    }

    /**
     * Enable or disable reminders
     */
    fun setReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
    }

    /**
     * Get the current reminder mode
     */
    fun getReminderMode(): ReminderMode {
        val modeName = prefs.getString(KEY_REMINDER_MODE, ReminderMode.INTERVAL.name)
        return try {
            ReminderMode.valueOf(modeName ?: ReminderMode.INTERVAL.name)
        } catch (e: IllegalArgumentException) {
            ReminderMode.INTERVAL
        }
    }

    /**
     * Set the reminder mode
     */
    fun setReminderMode(mode: ReminderMode) {
        prefs.edit().putString(KEY_REMINDER_MODE, mode.name).apply()
    }

    /**
     * Get the interval in minutes
     */
    fun getIntervalMinutes(): Int {
        return prefs.getInt(KEY_INTERVAL_MINUTES, 60) // Default 1 hour
    }

    /**
     * Set the interval in minutes
     */
    fun setIntervalMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_INTERVAL_MINUTES, minutes).apply()
    }

    /**
     * Get custom reminder times
     */
    fun getCustomTimes(): Set<String> {
        return prefs.getStringSet(KEY_CUSTOM_TIMES, emptySet()) ?: emptySet()
    }

    /**
     * Set custom reminder times
     */
    fun setCustomTimes(times: Set<String>) {
        prefs.edit().putStringSet(KEY_CUSTOM_TIMES, times).apply()
    }

    /**
     * Get all reminder settings as a single object
     */
    fun getReminderSettings(): ReminderSettings {
        return ReminderSettings(
            isEnabled = isReminderEnabled(),
            mode = getReminderMode(),
            intervalMinutes = getIntervalMinutes(),
            customTimes = getCustomTimes().toList().sorted()
        )
    }

    /**
     * Save all reminder settings at once
     */
    fun saveReminderSettings(settings: ReminderSettings) {
        prefs.edit().apply {
            putBoolean(KEY_REMINDER_ENABLED, settings.isEnabled)
            putString(KEY_REMINDER_MODE, settings.mode.name)
            putInt(KEY_INTERVAL_MINUTES, settings.intervalMinutes)
            putStringSet(KEY_CUSTOM_TIMES, settings.customTimes.toSet())
            apply()
        }
    }
}

