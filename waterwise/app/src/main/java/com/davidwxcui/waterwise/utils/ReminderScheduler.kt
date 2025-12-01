package com.davidwxcui.waterwise.utils

import android.content.Context
import androidx.work.*
import com.davidwxcui.waterwise.data.ReminderPreferences
import com.davidwxcui.waterwise.data.models.ReminderMode
import com.davidwxcui.waterwise.workers.ReminderWorker
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Manages scheduling of drink reminder notifications using WorkManager
 */
object ReminderScheduler {

    private const val REMINDER_WORK_TAG = "drink_reminder_work"
    private const val INTERVAL_WORK_NAME = "drink_reminder_interval"
    private const val CUSTOM_TIME_WORK_PREFIX = "drink_reminder_custom_"

    /**
     * Schedule reminders based on current settings
     */
    fun scheduleReminders(context: Context) {
        val reminderPrefs = ReminderPreferences(context)
        val settings = reminderPrefs.getReminderSettings()

        // Cancel existing reminders first
        cancelReminders(context)

        // Only schedule if reminders are enabled
        if (!settings.isEnabled) {
            return
        }

        when (settings.mode) {
            ReminderMode.INTERVAL -> scheduleIntervalReminders(context, settings.intervalMinutes)
            ReminderMode.CUSTOM_TIMES -> scheduleCustomTimeReminders(context, settings.customTimes)
        }
    }

    /**
     * Schedule periodic reminders at fixed intervals
     */
    private fun scheduleIntervalReminders(context: Context, intervalMinutes: Int) {
        // WorkManager requires minimum 15 minutes for periodic work
        val safeInterval = intervalMinutes.coerceAtLeast(15)

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false) // Allow even on low battery
            .build()

        val reminderRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
            safeInterval.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(REMINDER_WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            INTERVAL_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            reminderRequest
        )
    }

    /**
     * Schedule reminders for specific times of day
     */
    private fun scheduleCustomTimeReminders(context: Context, times: List<String>) {
        if (times.isEmpty()) return

        val now = ZonedDateTime.now()

        times.forEach { timeString ->
            try {
                val localTime = LocalTime.parse(timeString) // Format: "HH:mm"
                var scheduledTime = now.with(localTime)

                // If the time has already passed today, schedule for tomorrow
                if (scheduledTime.isBefore(now)) {
                    scheduledTime = scheduledTime.plusDays(1)
                }

                val delayInMinutes = Duration.between(now, scheduledTime).toMinutes()

                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()

                val reminderRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delayInMinutes, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .addTag(REMINDER_WORK_TAG)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "$CUSTOM_TIME_WORK_PREFIX$timeString",
                    ExistingWorkPolicy.REPLACE,
                    reminderRequest
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Cancel all scheduled reminders
     */
    fun cancelReminders(context: Context) {
        // Cancel all work with the reminder tag
        WorkManager.getInstance(context).cancelAllWorkByTag(REMINDER_WORK_TAG)

        // Also cancel by unique work names
        WorkManager.getInstance(context).cancelUniqueWork(INTERVAL_WORK_NAME)
    }
}

