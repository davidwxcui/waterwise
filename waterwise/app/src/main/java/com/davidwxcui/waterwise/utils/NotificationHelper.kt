package com.davidwxcui.waterwise.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.davidwxcui.waterwise.MainActivity
import com.davidwxcui.waterwise.R

/**
 * Helper object for managing drink reminder notifications
 */
object NotificationHelper {

    const val CHANNEL_ID = "drink_reminder_channel"
    const val CHANNEL_NAME = "Drink Reminders"
    const val NOTIFICATION_ID = 1001

    /**
     * Create notification channel (required for Android O+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications to remind you to drink water"
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Show a drink reminder notification
     */
    @Suppress("MissingPermission") // Permission is checked before calling notify
    fun showDrinkReminderNotification(context: Context) {
        // Create notification channel if needed
        createNotificationChannel(context)

        // Create intent to open MainActivity when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        builder.setContentTitle("Time to Hydrate!")
        builder.setContentText("Don't forget to drink water")

        val notification = builder.build()

        // Show the notification if permission is granted
        if (checkNotificationPermission(context)) {
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_ID, notification)
            }
        }
    }

    /**
     * Check if notification permission is granted
     * @return true if permission is granted or not required (Android < 13)
     */
    fun checkNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Permission not required for Android < 13
            true
        }
    }

    /**
     * Check if notification permission should show rationale
     */
    fun shouldShowRequestPermissionRationale(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context is android.app.Activity) {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                false
            }
        } else {
            false
        }
    }
}

