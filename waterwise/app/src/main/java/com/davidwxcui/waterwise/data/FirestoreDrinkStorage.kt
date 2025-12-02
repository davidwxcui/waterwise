package com.davidwxcui.waterwise.data

import android.os.Build
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

class FirestoreDrinkStorage {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private fun userDoc(uid: String) =
        db.collection("users").document(uid)

    private fun drinkLogsCol(uid: String) =
        userDoc(uid).collection("drinkLogs")


    private fun getLongSafe(v: Any?): Long {
        return when (v) {
            is Number -> v.toLong()
            is Timestamp -> v.toDate().time
            is String -> v.toLongOrNull() ?: 0L
            else -> 0L
        }
    }


    private fun parseDrinkTypeSafe(raw: String): DrinkType? {
        val key = raw.trim().uppercase()

        return when (key) {
            "WATER" -> DrinkType.Water
            "TEA" -> DrinkType.Tea
            "COFFEE" -> DrinkType.Coffee
            "JUICE" -> DrinkType.Juice
            "SODA" -> DrinkType.Soda
            "MILK" -> DrinkType.Milk
            "YOGURT" -> DrinkType.Yogurt
            "ALCOHOL" -> DrinkType.Alcohol
            "SPARKLING" -> DrinkType.Sparkling
            "SPARKLING_WATER", "SPARKLINGWATER", "CARBONATED" -> DrinkType.Sparkling
            "SOFTDRINK", "POP" -> DrinkType.Soda

            else -> {
                Log.e("FirestoreDrinkStorage", "Unknown drink type in Firestore: '$raw'")
                null
            }
        }
    }


    private fun todayRangeMillis(): Pair<Long, Long> {
        return if (Build.VERSION.SDK_INT >= 26) {
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now()
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
            val end = LocalDate.now()
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
            start to end
        } else {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val end = cal.timeInMillis
            start to end
        }
    }


    suspend fun fetchDrinkLogsOnce(uid: String): List<DrinkLog> {
        val (start, end) = todayRangeMillis()

        val snap = drinkLogsCol(uid)
            .whereGreaterThanOrEqualTo("timeMillis", start)
            .whereLessThan("timeMillis", end)
            .orderBy("timeMillis", Query.Direction.DESCENDING)
            .get()
            .await()

        val list = snap.documents.mapNotNull { doc ->
            try {
                val typeStrRaw = doc.getString("type") ?: return@mapNotNull null
                val type = parseDrinkTypeSafe(typeStrRaw) ?: return@mapNotNull null

                val id = getLongSafe(doc.get("id")).toInt()
                val volumeMl = getLongSafe(doc.get("volumeMl")).toInt()
                val effectiveMl = getLongSafe(doc.get("effectiveMl")).toInt()
                val note = doc.getString("note")
                val timeMillis = getLongSafe(doc.get("timeMillis"))

                DrinkLog(
                    id = id,
                    type = type,
                    volumeMl = volumeMl,
                    effectiveMl = effectiveMl,
                    note = note,
                    timeMillis = timeMillis
                )
            } catch (e: Exception) {
                Log.e("FirestoreDrinkStorage", "fetch parse error: ${e.message}")
                null
            }
        }

        return list
    }


    fun listenDrinkLogs(
        uid: String,
        onUpdate: (List<DrinkLog>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        val (start, end) = todayRangeMillis()

        return drinkLogsCol(uid)
            .whereGreaterThanOrEqualTo("timeMillis", start)
            .whereLessThan("timeMillis", end)
            .orderBy("timeMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                if (snap == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val list = snap.documents.mapNotNull { doc ->
                    try {
                        val typeStrRaw = doc.getString("type") ?: return@mapNotNull null
                        val type = parseDrinkTypeSafe(typeStrRaw) ?: return@mapNotNull null

                        val id = getLongSafe(doc.get("id")).toInt()
                        val volumeMl = getLongSafe(doc.get("volumeMl")).toInt()
                        val effectiveMl = getLongSafe(doc.get("effectiveMl")).toInt()
                        val note = doc.getString("note")
                        val timeMillis = getLongSafe(doc.get("timeMillis"))

                        DrinkLog(
                            id = id,
                            type = type,
                            volumeMl = volumeMl,
                            effectiveMl = effectiveMl,
                            note = note,
                            timeMillis = timeMillis
                        )
                    } catch (e: Exception) {
                        Log.e("FirestoreDrinkStorage", "parse error: ${e.message}")
                        null
                    }
                }

                onUpdate(list)
            }
    }

    /**
     * adding DrinkLog
     */
    fun addDrinkLog(uid: String, log: DrinkLog, onDone: (() -> Unit)? = null) {
        val docId = "${uid}_${log.timeMillis}_${log.id}"

        val (caffeineMl, alcoholMl, sugarMl) = presetNutrients(log.type, log.volumeMl)

        val data = hashMapOf(
            "id" to log.id,
            "type" to log.type.name,
            "volumeMl" to log.volumeMl,
            "effectiveMl" to log.effectiveMl,
            "caffeineMl" to caffeineMl,
            "alcoholMl" to alcoholMl,
            "sugarMl" to sugarMl,
            "timeMillis" to log.timeMillis,
            "note" to log.note
        )

        drinkLogsCol(uid).document(docId)
            .set(data)
            .addOnSuccessListener { onDone?.invoke() }
            .addOnFailureListener { e ->
                Log.e("FirestoreDrinkStorage", "addDrinkLog failed", e)
            }
    }

    /**
     * update volume
     */
    fun updateDrinkLog(uid: String, log: DrinkLog, onDone: (() -> Unit)? = null) {
        val docId = "${uid}_${log.timeMillis}_${log.id}"

        val (caffeineMl, alcoholMl, sugarMl) = presetNutrients(log.type, log.volumeMl)

        val data = mapOf(
            "volumeMl" to log.volumeMl,
            "effectiveMl" to log.effectiveMl,
            "caffeineMl" to caffeineMl,
            "alcoholMl" to alcoholMl,
            "sugarMl" to sugarMl,
            "note" to log.note
        )

        drinkLogsCol(uid).document(docId)
            .update(data)
            .addOnSuccessListener { onDone?.invoke() }
            .addOnFailureListener { e ->
                Log.e("FirestoreDrinkStorage", "updateDrinkLog failed", e)
            }
    }

    /**
     * delete log
     */
    fun deleteDrinkLog(uid: String, log: DrinkLog, onDone: (() -> Unit)? = null) {
        val docId = "${uid}_${log.timeMillis}_${log.id}"

        drinkLogsCol(uid).document(docId)
            .delete()
            .addOnSuccessListener { onDone?.invoke() }
            .addOnFailureListener { e ->
                Log.e("FirestoreDrinkStorage", "deleteDrinkLog failed", e)
            }
    }

    private fun presetNutrients(type: DrinkType, volumeMl: Int): Triple<Int, Int, Int> {
        val caffeine = when (type) {
            DrinkType.Coffee, DrinkType.Tea -> volumeMl
            else -> 0
        }
        val alcohol = if (type == DrinkType.Alcohol) volumeMl else 0
        val sugar = when (type) {
            DrinkType.Juice, DrinkType.Soda, DrinkType.Yogurt -> volumeMl
            else -> 0
        }
        return Triple(caffeine, alcohol, sugar)
    }




    // 计算最近 N 天的时间范围（含今天），单位：毫秒
    // 例如 days = 7 -> 从 6 天前 00:00 到 明天 00:00
    private fun recentRangeMillis(days: Int): Pair<Long, Long> {
        if (days <= 1) return todayRangeMillis()

        return if (Build.VERSION.SDK_INT >= 26) {
            val zone = ZoneId.systemDefault()
            val startDate = LocalDate.now().minusDays((days - 1).toLong())
            val start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = LocalDate.now()
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
            start to end
        } else {
            val calEnd = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, 1) // 明天 00:00
            }
            val end = calEnd.timeInMillis
            val calStart = calEnd.clone() as Calendar
            calStart.add(Calendar.DAY_OF_YEAR, -(days - 1))
            val start = calStart.timeInMillis
            start to end
        }
    }


    suspend fun fetchTotalIntake(uid: String, days: Int): Int {
        // 1 天就直接用你现成的 today 查询
        if (days <= 1) {
            val logs = fetchDrinkLogsOnce(uid)
            return logs.sumOf { it.volumeMl }
        }

        val (start, end) = recentRangeMillis(days)

        val snap = drinkLogsCol(uid)
            .whereGreaterThanOrEqualTo("timeMillis", start)
            .whereLessThan("timeMillis", end)
            .orderBy("timeMillis", Query.Direction.DESCENDING)
            .get()
            .await()

        val list = snap.documents.mapNotNull { doc ->
            try {
                val typeStrRaw = doc.getString("type") ?: return@mapNotNull null
                val type = parseDrinkTypeSafe(typeStrRaw) ?: return@mapNotNull null

                val id = getLongSafe(doc.get("id")).toInt()
                val volumeMl = getLongSafe(doc.get("volumeMl")).toInt()
                val effectiveMl = getLongSafe(doc.get("effectiveMl")).toInt()
                val note = doc.getString("note")
                val timeMillis = getLongSafe(doc.get("timeMillis"))

                DrinkLog(
                    id = id,
                    type = type,
                    volumeMl = volumeMl,
                    effectiveMl = effectiveMl,
                    note = note,
                    timeMillis = timeMillis
                )
            } catch (e: Exception) {
                Log.e("FirestoreDrinkStorage", "fetchTotalIntake parse error: ${e.message}")
                null
            }
        }

        return list.sumOf { it.volumeMl }
    }

    // 计算最近 N 天的时间范围（含今天，共 days 天）
    private fun rangeLastDaysMillis(days: Int): Pair<Long, Long> {
        val d = if (days <= 0) 1 else days
        return if (Build.VERSION.SDK_INT >= 26) {
            val zone = ZoneId.systemDefault()
            val end = LocalDate.now()
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
            val start = LocalDate.now()
                .minusDays(d.toLong() - 1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
            start to end
        } else {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val endCal = cal.clone() as Calendar
            endCal.add(Calendar.DAY_OF_YEAR, d)
            val start = cal.timeInMillis
            val end = endCal.timeInMillis
            start to end
        }
    }

    /**
     * 给一组 uid，统计最近 days 天内每个人喝了多少 ml（volumeMl 求和）。
     */
    suspend fun fetchTotalIntakeForUids(
        uids: List<String>,
        days: Int
    ): Map<String, Int> {
        if (uids.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, Int>()
        val (start, end) = rangeLastDaysMillis(days)

        for (uid in uids) {
            try {
                val snap = drinkLogsCol(uid)
                    .whereGreaterThanOrEqualTo("timeMillis", start)
                    .whereLessThan("timeMillis", end)
                    .get()
                    .await()

                var total = 0
                for (doc in snap.documents) {
                    val v = getLongSafe(doc.get("volumeMl")).toInt()
                    total += v
                }
                result[uid] = total
            } catch (e: Exception) {
                // 出错就当作 0
                result[uid] = result[uid] ?: 0
            }
        }
        return result
    }




    suspend fun fetchTodayTotalIntake(uid: String): Int =
        fetchTotalIntake(uid, 1)

    suspend fun fetchTodayTotalIntakeForUids(uids: List<String>): Map<String, Int> =
        fetchTotalIntakeForUids(uids, 1)

}
