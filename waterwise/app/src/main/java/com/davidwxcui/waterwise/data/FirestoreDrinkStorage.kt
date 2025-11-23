package com.davidwxcui.waterwise.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class FirestoreDrinkStorage {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private fun userDoc(uid: String) =
        db.collection("users").document(uid)

    private fun drinkLogsCol(uid: String) =
        userDoc(uid).collection("drinkLogs")

    /**
     * List DrinkLogs（by timeMillis decreasing）
     */
    fun listenDrinkLogs(
        uid: String,
        onUpdate: (List<DrinkLog>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        return drinkLogsCol(uid)
            .orderBy("timeMillis", com.google.firebase.firestore.Query.Direction.DESCENDING)
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
                        val typeStr = doc.getString("type") ?: return@mapNotNull null
                        val type = DrinkType.valueOf(typeStr)
                        val id = (doc.getLong("id") ?: 0L).toInt()
                        val volumeMl = (doc.getLong("volumeMl") ?: 0L).toInt()
                        val effectiveMl = (doc.getLong("effectiveMl") ?: 0L).toInt()
                        val note = doc.getString("note")
                        val timeMillis = doc.getLong("timeMillis") ?: 0L

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
     * update  volume
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
}
