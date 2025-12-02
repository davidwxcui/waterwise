package com.davidwxcui.waterwise.data.models

/**
 * Represents the reminder mode for drink notifications
 */
enum class ReminderMode {
    INTERVAL,      // Remind every X hours/minutes
    CUSTOM_TIMES   // Remind at specific times
}

/**
 * Data class representing all reminder settings
 */
data class ReminderSettings(
    val isEnabled: Boolean = false,
    val mode: ReminderMode = ReminderMode.INTERVAL,
    val intervalMinutes: Int = 60, // Default 1 hour
    val customTimes: List<String> = emptyList() // Format: "HH:mm" (24-hour format)
)

