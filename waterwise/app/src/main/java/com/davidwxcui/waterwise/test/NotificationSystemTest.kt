package com.davidwxcui.waterwise.test

import android.content.Context
import com.davidwxcui.waterwise.data.ReminderPreferences
import com.davidwxcui.waterwise.data.models.ReminderMode
import com.davidwxcui.waterwise.data.models.ReminderSettings
import com.davidwxcui.waterwise.utils.NotificationHelper
import com.davidwxcui.waterwise.utils.ReminderScheduler

/**
 * Test file to verify notification system compilation
 */
class NotificationSystemTest {

    fun testReminderPreferences(context: Context) {
        val prefs = ReminderPreferences(context)
        prefs.setReminderEnabled(true)
        prefs.setReminderMode(ReminderMode.INTERVAL)
        prefs.setIntervalMinutes(60)

        val settings = prefs.getReminderSettings()
        println("Settings: $settings")
    }

    fun testNotificationHelper(context: Context) {
        NotificationHelper.createNotificationChannel(context)
        val hasPermission = NotificationHelper.checkNotificationPermission(context)
        println("Has permission: $hasPermission")
    }

    fun testScheduler(context: Context) {
        ReminderScheduler.scheduleReminders(context)
        println("Scheduler executed successfully")
    }
}

