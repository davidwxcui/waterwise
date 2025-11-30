package com.davidwxcui.waterwise.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirestoreFriendStorage {

    private val db = FirebaseFirestore.getInstance()

    private fun usersCol() = db.collection("users")
    private fun userDoc(uid: String) = usersCol().document(uid)
    private fun friendsCol(uid: String) = userDoc(uid).collection("friends")

    private fun friendRequestsCol(uid: String) = userDoc(uid).collection("friendRequests")

    data class Friend(
        val uid: String = "",
        val name: String = "",
        val avatarUri: String? = null,
        val since: Long = 0L
    )

    data class FriendRequest(
        val uid: String = "",
        val name: String = "",
        val avatarUri: String? = null,
        val createdAt: Long = 0L
    )

    private fun looksLikeEmail(s: String): Boolean {
        return s.contains("@") && s.contains(".")
    }


    suspend fun addFriendByQuery(myUid: String, query: String): Result<Unit> {
        return try {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                throw Exception("Please input Friend's UID or E-mail")
            }

            val friendSnap = if (looksLikeEmail(trimmed)) {
                val q = usersCol()
                    .whereEqualTo("email", trimmed)
                    .limit(1)
                    .get()
                    .await()
                if (q.isEmpty) {
                    throw Exception("Doesn't find any user with this email")
                }
                q.documents.first()
            } else {
                val doc = userDoc(trimmed).get().await()
                if (!doc.exists()) {
                    throw Exception("Doesn't find any user with this UID")
                }
                doc
            }

            val friendUid = friendSnap.id
            if (friendUid == myUid) {
                throw Exception("You can't add yourself as a friend")
            }

            val existingFriend = friendsCol(myUid).document(friendUid).get().await()
            if (existingFriend.exists()) {
                return Result.success(Unit)
            }

            val existingReq = friendRequestsCol(friendUid).document(myUid).get().await()
            if (existingReq.exists()) {
                return Result.success(Unit)
            }

            val mySnap = userDoc(myUid).get().await()
            val myName = mySnap.getString("name") ?: ""
            val myAvatar = mySnap.getString("avatarUri")
            val now = System.currentTimeMillis()

            friendRequestsCol(friendUid).document(myUid)
                .set(
                    mapOf(
                        "uid" to myUid,
                        "name" to myName,
                        "avatarUri" to myAvatar,
                        "createdAt" to now
                    )
                ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreFriendStorage", "addFriendByQuery failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun listenFriends(
        uid: String,
        onUpdate: (List<Friend>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        return friendsCol(uid)
            .orderBy("since", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                if (snap == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val list = snap.documents.mapNotNull { d ->
                    val fid = d.getString("uid") ?: return@mapNotNull null
                    Friend(
                        uid = fid,
                        name = d.getString("name") ?: "",
                        avatarUri = d.getString("avatarUri"),
                        since = d.getLong("since") ?: 0L
                    )
                }
                onUpdate(list)
            }
    }


    fun listenFriendRequests(
        uid: String,
        onUpdate: (List<FriendRequest>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        return friendRequestsCol(uid)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                if (snap == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val list = snap.documents.mapNotNull { d ->
                    val fromUid = d.getString("uid") ?: d.id
                    FriendRequest(
                        uid = fromUid,
                        name = d.getString("name") ?: "",
                        avatarUri = d.getString("avatarUri"),
                        createdAt = d.getLong("createdAt") ?: 0L
                    )
                }
                onUpdate(list)
            }
    }

    suspend fun removeFriend(myUid: String, friendUid: String): Result<Unit> {
        return try {
            db.runBatch { batch ->
                batch.delete(friendsCol(myUid).document(friendUid))
                batch.delete(friendsCol(friendUid).document(myUid))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreFriendStorage", "removeFriend failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequest(myUid: String, fromUid: String): Result<Unit> {
        return try {
            val mySnap = userDoc(myUid).get().await()
            val fromSnap = userDoc(fromUid).get().await()
            if (!fromSnap.exists()) {
                throw Exception("User not found")
            }

            val myName = mySnap.getString("name") ?: ""
            val myAvatar = mySnap.getString("avatarUri")
            val fromName = fromSnap.getString("name") ?: ""
            val fromAvatar = fromSnap.getString("avatarUri")
            val now = System.currentTimeMillis()

            db.runBatch { batch ->
                batch.set(
                    friendsCol(myUid).document(fromUid),
                    mapOf(
                        "uid" to fromUid,
                        "name" to fromName,
                        "avatarUri" to fromAvatar,
                        "since" to now
                    )
                )
                batch.set(
                    friendsCol(fromUid).document(myUid),
                    mapOf(
                        "uid" to myUid,
                        "name" to myName,
                        "avatarUri" to myAvatar,
                        "since" to now
                    )
                )

                batch.delete(friendRequestsCol(myUid).document(fromUid))
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreFriendStorage", "acceptFriendRequest failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 一次性获取当前用户的好友列表（不是实时监听）
     */
    suspend fun fetchFriendsOnce(uid: String): List<Friend> {
        return try {
            val snap = friendsCol(uid)
                .orderBy("since", Query.Direction.ASCENDING)
                .get()
                .await()

            snap.documents.mapNotNull { d ->
                val fid = d.getString("uid") ?: return@mapNotNull null
                Friend(
                    uid = fid,
                    name = d.getString("name") ?: "",
                    avatarUri = d.getString("avatarUri"),
                    since = d.getLong("since") ?: 0L
                )
            }
        } catch (e: Exception) {
            Log.e("FirestoreFriendStorage", "fetchFriendsOnce failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun declineFriendRequest(myUid: String, fromUid: String): Result<Unit> {
        return try {
            friendRequestsCol(myUid).document(fromUid).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreFriendStorage", "declineFriendRequest failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
