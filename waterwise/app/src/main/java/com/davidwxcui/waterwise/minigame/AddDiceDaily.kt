package com.davidwxcui.waterwise.minigame

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/**
 * Helper for daily dice bonus based on yesterday's drink volume.
 *
 * Firestore structure:
 *   users/<uid>/drinkLogs/<...>
 *   Fields in each document:
 *      timeMillis: Long   // timestamp in millis when this drink was logged
 *      volumeMl  : Long/Double // volume for this single drink
 *
 * Rules:
 *  - Sum all volumeMl whose timeMillis is in [yesterday 00:00, today 00:00).
 *  - Every 250 ml => +1 dice.
 *  - For each (uid, roomId, date) we only calculate once (SharedPreferences).
 */
object AddDiceDaily {

    private const val PREF_NAME = "daily_dice_pref"
    private const val TAG = "AddDiceDaily"

    /**
     * Result of daily dice computation.
     *
     * @param isFirstTimeToday whether this is the first time we process this (uid, roomId) today
     * @param diceToAdd how many dice should be added today (can be 0)
     * @param yesterdayVolumeMl total drink volume for yesterday in ml
     */
    data class DailyDiceResult(
        val isFirstTimeToday: Boolean,
        val diceToAdd: Int,
        val yesterdayVolumeMl: Long
    )

    /** Date string like 2025-11-27 for SharedPreferences key. */
    private fun getTodayKey(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(year, month, day)
    }

    /** Today 00:00 (local) in millis. */
    private fun getStartOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = System.currentTimeMillis()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Yesterday 00:00 (local) in millis. */
    private fun getStartOfYesterdayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = getStartOfTodayMillis()
        cal.add(Calendar.DAY_OF_MONTH, -1)
        return cal.timeInMillis
    }

    /**
     * Compute today's dice bonus based on yesterday's drink logs.
     *
     * - If this (uid, roomId) has already been processed today:
     *      -> return DailyDiceResult(isFirstTimeToday = false, diceToAdd = 0, yesterdayVolumeMl = 0)
     * - If first time today:
     *      -> sum yesterday's volume, dice = volume / 250 (integer division)
     *      -> store today's date in SharedPreferences
     *      -> return DailyDiceResult(isFirstTimeToday = true, diceToAdd = dice, yesterdayVolumeMl = volume)
     *
     * NOTE: This function does NOT update Firestore. You should add `diceToAdd`
     * to `diceLeft` in GameActivity and then save the player state yourself.
     */
    suspend fun computeDailyDice(
        context: Context,
        uid: String,
        roomId: String,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    ): DailyDiceResult {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val today = getTodayKey()
        val key = "last_grant_${uid}_$roomId"
        val last = sp.getString(key, null)

        Log.d(TAG, "computeDailyDice: uid=$uid roomId=$roomId today=$today last=$last")

        if (last == today) {
            // Already processed today, do not change diceLeft.
            Log.d(TAG, "Already processed today, skip calculation.")
            return DailyDiceResult(
                isFirstTimeToday = false,
                diceToAdd = 0,
                yesterdayVolumeMl = 0L
            )
        }

        val volumeMl = getYesterdayVolumeMl(uid, firestore)
        Log.d(TAG, "Yesterday total volumeMl = $volumeMl")

        val diceToAdd = (volumeMl / 250).toInt()
        Log.d(TAG, "diceToAdd (volume/250) = $diceToAdd")

        // Mark today as processed (even if diceToAdd == 0: “no drink” -> bonus 0).
        sp.edit().putString(key, today).apply()

        return DailyDiceResult(
            isFirstTimeToday = true,
            diceToAdd = diceToAdd,
            yesterdayVolumeMl = volumeMl
        )
    }

    /**
     * Sum all volumeMl for yesterday:
     *   timeMillis in [yesterdayStart, todayStart)
     *
     * Each drink creates one document, so we simply sum volumeMl for this range.
     */
    private suspend fun getYesterdayVolumeMl(
        uid: String,
        firestore: FirebaseFirestore
    ): Long {
        val yesterdayStart = getStartOfYesterdayMillis()
        val todayStart = getStartOfTodayMillis()

        Log.d(TAG, "Query logs where timeMillis in [$yesterdayStart, $todayStart)")

        val drinkLogsRef = firestore.collection("users")
            .document(uid)
            .collection("drinkLogs")

        val snapshot = drinkLogsRef
            .whereGreaterThanOrEqualTo("timeMillis", yesterdayStart)
            .whereLessThan("timeMillis", todayStart)
            .get()
            .await()

        if (snapshot.isEmpty) {
            Log.d(TAG, "No drinkLogs for yesterday, total = 0.")
            return 0L
        }

        var total = 0L
        for (doc in snapshot.documents) {
            // volumeMl might be stored as Long, Double, etc. Use Number for safety.
            val raw = doc.get("volumeMl") ?: doc.get("volumeML")
            val v = (raw as? Number)?.toLong() ?: 0L

            total += v
            Log.d(
                TAG,
                "Doc ${doc.id}: rawVolume=$raw, parsedVolume=$v, accumulatedTotal=$total"
            )
        }

        Log.d(TAG, "Final total volume for yesterday = $total")
        return total
    }
}
