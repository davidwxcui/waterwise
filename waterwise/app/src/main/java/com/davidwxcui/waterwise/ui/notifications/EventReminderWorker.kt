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
            Log.d("EventReminderWorker", "🔴🔴🔴 WORKER STARTED at ${java.time.LocalDateTime.now()} 🔴🔴🔴")

            val db = EventDatabase.getDatabase(applicationContext)
            val eventDao = db.eventDao()

            val today = LocalDate.now()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

            Log.d("EventReminderWorker", "📅 Today's date: $today")

            eventDao.getAllEvents().collect { events ->
                Log.d("EventReminderWorker", "📦 Total events found: ${events.size}")

                for (event in events) {
                    val eventDate = LocalDate.parse(event.date, formatter)
                    val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, eventDate).toInt()

                    Log.d("EventReminderWorker", "🎯 Event: '${event.title}', Date: ${event.date}, DaysUntil: $daysUntil")

                    // Send notification 3 days before, 1 day before, and on the day
                    if (daysUntil in listOf(3, 1, 0)) {
                        Log.d("EventReminderWorker", "✅ SENDING NOTIFICATION for '${event.title}' (DaysUntil: $daysUntil)")
                        NotificationHelper.sendEventReminder(
                            applicationContext,
                            event.title,
                            event.description,
                            event.recommendedAmount,
                            daysUntil,
                            event.id
                        )
                    } else {
                        Log.d("EventReminderWorker", "⏭️ SKIPPING '${event.title}' - not in reminder window (DaysUntil: $daysUntil)")
                    }
                }
            }

            Log.d("EventReminderWorker", "✔️ WORKER COMPLETED SUCCESSFULLY")
            Result.success()
        } catch (e: Exception) {
            Log.e("EventReminderWorker", "❌ ERROR in EventReminderWorker: ${e.message}", e)
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "event_reminder_work"

        fun scheduleEventReminders(context: Context) {
            val initialDelay = calculateInitialDelay()

            // Schedule daily work that runs at 9 AM
            val eventReminderWork = PeriodicWorkRequestBuilder<EventReminderWorker>(
                1, TimeUnit.DAYS
            )
                .setInitialDelay(initialDelay, TimeUnit.MINUTES)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, // min backoff
                    TimeUnit.MINUTES
                )
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                eventReminderWork
            )

            Log.d("EventReminderWorker", "Event reminders scheduled. Next run in $initialDelay minutes (at 9 AM)")
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