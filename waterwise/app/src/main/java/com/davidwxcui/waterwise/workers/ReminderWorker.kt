package com.davidwxcui.waterwise.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.davidwxcui.waterwise.data.ReminderPreferences
import com.davidwxcui.waterwise.utils.NotificationHelper

/**
 * Worker that triggers drink reminder notifications
 * Called by WorkManager at scheduled intervals
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val reminderPrefs = ReminderPreferences(applicationContext)

            // Only show notification if reminders are enabled
            if (reminderPrefs.isReminderEnabled()) {
                NotificationHelper.showDrinkReminderNotification(applicationContext)
            }

            Result.success()
        } catch (e: Exception) {
            // Log error and retry
            e.printStackTrace()
            Result.retry()
        }
    }
}

