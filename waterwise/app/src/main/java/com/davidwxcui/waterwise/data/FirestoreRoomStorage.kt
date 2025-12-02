package com.davidwxcui.waterwise.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreRoomStorage {

    private val db = FirebaseFirestore.getInstance()

    private fun roomsCol() = db.collection("rooms")
    private fun roomDoc(roomId: String) = roomsCol().document(roomId)
    private fun membersCol(roomId: String) = roomDoc(roomId).collection("members")
    private fun stateDoc(roomId: String) =
        roomDoc(roomId).collection("state").document("gameState")

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    data class Room(
        val roomId: String = "",
        val password: String = "",
        val status: String = "IDLE",
        val memberCount: Long = 0,
        val createdAt: Long = 0
    )

    data class Member(
        val uid: String = "",
        val joinedAt: Long = 0,
        val coins: Long = 0
    )

    private fun randomRoomId(len: Int = 6): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..len).map { chars.random() }.joinToString("")
    }

    private fun getLongSafe(v: Any?): Long {
        return when (v) {
            is Number -> v.toLong()
            is Timestamp -> v.toDate().time
            is String -> v.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    suspend fun fetchCurrentRoomId(uid: String): String? {
        val snap = userDoc(uid).get().await()
        return snap.getString("activeRoomId")
            ?: snap.getString("roomId")
    }

    suspend fun leaveActiveRoomIfAny(uid: String) {
        val oldRoomId = fetchCurrentRoomId(uid) ?: return
        leaveRoom(uid, oldRoomId)
    }

    suspend fun createRoom(hostUid: String, password: String): Result<String> {
        return try {
            val existingRoomId = fetchCurrentRoomId(hostUid)
            if (!existingRoomId.isNullOrEmpty()) {
                return Result.failure(
                    Exception("You are already in room $existingRoomId. Leave it before creating a new one.")
                )
            }

            while (true) {
                val roomId = randomRoomId()
                val docRef = roomDoc(roomId)

                try {
                    db.runTransaction { tx ->
                        val snap = tx.get(docRef)
                        if (snap.exists()) throw Exception("roomId collision")

                        tx.set(
                            docRef,
                            mapOf(
                                "roomId" to roomId,
                                "password" to password,
                                "status" to "IDLE",
                                "memberCount" to 1L,
                                "createdAt" to System.currentTimeMillis()
                            )
                        )

                        tx.set(
                            membersCol(roomId).document(hostUid),
                            mapOf(
                                "uid" to hostUid,
                                "joinedAt" to System.currentTimeMillis(),
                            )
                        )

                        tx.set(
                            userDoc(hostUid),
                            mapOf(
                                "activeRoomId" to roomId,
                                "roomId" to roomId
                            ),
                            SetOptions.merge()
                        )
                    }.await()

                    return Result.success(roomId)
                } catch (e: Exception) {
                    Log.w("FirestoreRoomStorage", "createRoom retry: ${e.message}")
                }
            }

            Result.failure(Exception("createRoom failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinRoom(uid: String, roomId: String, password: String): Result<Unit> {
        return try {
            val current = fetchCurrentRoomId(uid)

            if (current == roomId) {
                userDoc(uid).set(
                    mapOf(
                        "activeRoomId" to roomId,
                        "roomId" to roomId
                    ),
                    SetOptions.merge()
                ).await()

                return Result.success(Unit)
            }

            if (!current.isNullOrEmpty() && current != roomId) {
                leaveRoom(uid, current)
            }

            db.runTransaction { tx ->
                val roomRef = roomDoc(roomId)
                val roomSnap = tx.get(roomRef)
                if (!roomSnap.exists()) throw Exception("Room not found")

                val realPwd = roomSnap.getString("password") ?: ""
                if (realPwd != password) throw Exception("Wrong password")

                val memRef = membersCol(roomId).document(uid)
                val memSnap = tx.get(memRef)

                if (!memSnap.exists()) {
                    tx.set(
                        memRef,
                        mapOf(
                            "uid" to uid,
                            "joinedAt" to System.currentTimeMillis(),
                        )
                    )
                    tx.update(roomRef, "memberCount", FieldValue.increment(1))
                }

                tx.set(
                    userDoc(uid),
                    mapOf(
                        "activeRoomId" to roomId,
                        "roomId" to roomId
                    ),
                    SetOptions.merge()
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // leave the room
    suspend fun leaveRoom(uid: String, roomId: String): Result<Unit> {
        return try {
            db.runTransaction { tx ->

                val roomRef = roomDoc(roomId)
                val memRef = membersCol(roomId).document(uid)
                val userRef = userDoc(uid)
                val roomSnap = tx.get(roomRef)
                val memSnap = tx.get(memRef)
                val userSnap = tx.get(userRef)

                if (!roomSnap.exists()) {
                    if (userSnap.getString("activeRoomId") == roomId) {
                        tx.update(userRef, "activeRoomId", null)
                    }
                    return@runTransaction
                }

                // Delete member
                if (memSnap.exists()) {
                    tx.delete(memRef)
                }

                // Update memberCount
                val oldCount = roomSnap.getLong("memberCount") ?: 1L
                val newCount = oldCount - 1

                if (newCount <= 0L) {
                    tx.update(roomRef, "memberCount", 0L)
                } else {
                    tx.update(roomRef, "memberCount", newCount)
                }

                // Clear activeRoomId and roomId
                val ar = userSnap.getString("activeRoomId")
                val r = userSnap.getString("roomId")
                if (ar == roomId || r == roomId) {
                    tx.set(
                        userRef,
                        mapOf(
                            "activeRoomId" to null,
                            "roomId" to null
                        ),
                        SetOptions.merge()
                    )
                }
            }.await()
            cleanupRoomIfEmpty(roomId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Clean the room
    suspend fun cleanupRoomIfEmpty(roomId: String) {
        try {
            // Check room exist
            val roomSnap = roomDoc(roomId).get().await()
            if (!roomSnap.exists()) return

            // If room has other user, then return
            val oneMemberSnap = membersCol(roomId).limit(1).get().await()
            if (!oneMemberSnap.isEmpty) return

            // Delete sub set members
            val membersDocs = membersCol(roomId).get().await().documents
            for (doc in membersDocs) {
                doc.reference.delete().await()
            }

            // Delete sub set state
            val stateDocs = roomDoc(roomId).collection("state").get().await().documents
            for (doc in stateDocs) {
                doc.reference.delete().await()
            }

            // Delete room file
            roomDoc(roomId).delete().await()

            Log.i("FirestoreRoomStorage", "Room $roomId fully cleaned up")
        } catch (e: Exception) {
            Log.e("FirestoreRoomStorage", "cleanupRoomIfEmpty failed: ${e.message}", e)
        }
    }


    suspend fun startGame(roomId: String): Result<Unit> {
        return try {
            val membersSnap = membersCol(roomId).get().await()
            val members = membersSnap.documents.mapNotNull { it.getString("uid") }

            db.runTransaction { tx ->
                val roomRef = roomDoc(roomId)
                if (!tx.get(roomRef).exists()) throw Exception("Room not found")

                tx.update(roomRef, "status", "PLAYING")

                tx.set(
                    stateDoc(roomId),
                    mapOf(
                        "startedAt" to System.currentTimeMillis(),
                        "turnIndex" to 0L,
                    )
                )

                for (uid in members) {
                    tx.set(
                        userDoc(uid),
                        mapOf(
                            "activeRoomId" to roomId,
                            "roomId" to roomId
                        ),
                        SetOptions.merge()
                    )
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Listen the room
    fun listenRoom(
        roomId: String,
        onUpdate: (Room?) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        return roomDoc(roomId).addSnapshotListener { snap, err ->
            if (err != null) { onError(err); return@addSnapshotListener }
            if (snap == null || !snap.exists()) { onUpdate(null); return@addSnapshotListener }

            val room = Room(
                roomId = snap.getString("roomId") ?: roomId,
                password = snap.getString("password") ?: "",
                status = snap.getString("status") ?: "IDLE",
                memberCount = getLongSafe(snap.get("memberCount")),
                createdAt = getLongSafe(snap.get("createdAt"))
            )
            onUpdate(room)
        }
    }

    // Listen number in the room
    fun listenMembers(
        roomId: String,
        onUpdate: (List<Member>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        return membersCol(roomId)
            .orderBy("joinedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { onError(err); return@addSnapshotListener }
                if (snap == null) { onUpdate(emptyList()); return@addSnapshotListener }

                val list = snap.documents.mapNotNull { d ->
                    val uid = d.getString("uid") ?: return@mapNotNull null
                    Member(
                        uid = uid,
                        joinedAt = getLongSafe(d.get("joinedAt")),
                    )
                }
                onUpdate(list)
            }
    }
}
