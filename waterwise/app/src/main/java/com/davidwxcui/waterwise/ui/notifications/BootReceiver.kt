package com.davidwxcui.waterwise.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Log.d("BootReceiver", "📱 Device boot/time change detected. Rescheduling worker...")

                // Reschedule the event reminders
                EventReminderWorker.cancelEventReminders(context)
                EventReminderWorker.scheduleEventReminders(context)

                Log.d("BootReceiver", "✅ Worker rescheduled")
            }
        }
    }
}