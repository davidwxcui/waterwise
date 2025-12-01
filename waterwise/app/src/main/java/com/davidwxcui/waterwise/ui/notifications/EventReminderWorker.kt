package com.davidwxcui.waterwise.notifications

import android.content.Context
import android.util.Log
import androidx.work.*
import com.davidwxcui.waterwise.database.event.EventDatabase
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class EventReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("EventReminderWorker", "🔴 WORKER EXECUTED at ${LocalDateTime.now()}")
            val db = EventDatabase.getDatabase(applicationContext)
            val eventDao = db.eventDao()

            val today = LocalDate.now()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

            eventDao.getAllEvents().collect { events ->
                for (event in events) {
                    val eventDate = LocalDate.parse(event.date, formatter)
                    val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, eventDate).toInt()

                    // Send notification 3 days before, 1 day before, and on the day
                    if (daysUntil in listOf(3, 1, 0)) {
                        NotificationHelper.sendEventReminder(
                            applicationContext,
                            event.title,
                            event.description,
                            event.recommendedAmount,
                            daysUntil,
                            event.id
                        )
                        Log.d("EventReminderWorker", "Notification sent for: ${event.title}")
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("EventReminderWorker", "Error in EventReminderWorker", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "event_reminder_work"

        fun scheduleEventReminders(context: Context) {
            // Schedule daily work that runs at 9 AM
            val eventReminderWork = PeriodicWorkRequestBuilder<EventReminderWorker>(
                1, // Repeat interval
                TimeUnit.DAYS // Repeat unit
            )
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                eventReminderWork
            )

            Log.d("EventReminderWorker", "Event reminders scheduled")
        }

        private fun calculateInitialDelay(): Long {
            val now = LocalDateTime.now()
            val target = now.withHour(9).withMinute(0).withSecond(0)

            val delay = if (now.isBefore(target)) {
                Duration.between(now, target).toMinutes()
            } else {
                Duration.between(now, target.plusDays(1)).toMinutes()
            }

            return delay
        }

        fun cancelEventReminders(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_NAME)
        }
    }
}