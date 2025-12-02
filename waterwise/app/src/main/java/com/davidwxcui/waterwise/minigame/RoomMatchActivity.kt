package com.davidwxcui.waterwise.minigame

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.data.FirestoreRoomStorage
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.davidwxcui.waterwise.MainActivity

class RoomMatchActivity : AppCompatActivity() {

    companion object {
        // SharedPreferences file and keys for authentication state
        private const val AUTH_FILE = "profile"
        private const val KEY_UID = "uid"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_LOGGED_IN = "loggedIn"
        private const val KEY_FAIL_COUNT = "fail_count"
        private const val KEY_LOCK_UNTIL = "lock_until"
    }

    private lateinit var tvUid: TextView
    private lateinit var etRoomId: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnCreateRoom: Button
    private lateinit var btnJoinRoom: Button
    private lateinit var btnReenterRoom: Button

    private lateinit var btnBack: Button

    private val roomStorage by lazy { FirestoreRoomStorage() }
    private lateinit var uid: String

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_match)

        tvUid = findViewById(R.id.tvUid)
        etRoomId = findViewById(R.id.etRoomId)
        etPassword = findViewById(R.id.etPassword)
        btnCreateRoom = findViewById(R.id.btnCreateRoom)
        btnJoinRoom = findViewById(R.id.btnJoinRoom)
        btnBack = findViewById(R.id.btnBack)

        // Read login state + uid. If not logged in, finish this Activity.
        uid = loadUidFromLocal() ?: run {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        tvUid.text = "UID: $uid"

        // 1. When entering this page, automatically try to re-enter the last joined room
        autoReenterRoomIfNeeded()

        // 2. Button: create room
        btnCreateRoom.setOnClickListener {
            val pwd = etPassword.text.toString()
            lifecycleScope.launch {
                val res = withContext(Dispatchers.IO) {
                    roomStorage.createRoom(uid, pwd)
                }
                if (res.isSuccess) {
                    val roomId = res.getOrNull()!!
                    Toast.makeText(
                        this@RoomMatchActivity,
                        "Create room: $roomId",
                        Toast.LENGTH_SHORT
                    ).show()
                    openGame(roomId)
                } else {
                    Toast.makeText(
                        this@RoomMatchActivity,
                        res.exceptionOrNull()?.message ?: "create failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // 3. Button: join room
        btnJoinRoom.setOnClickListener {
            val roomId = etRoomId.text.toString().trim()
            val pwd = etPassword.text.toString()

            if (roomId.isEmpty()) {
                Toast.makeText(this, "Room ID is empty.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                // First, check current member count
                val canJoin = withContext(Dispatchers.IO) {
                    try {
                        val snap = firestore.collection("rooms")
                            .document(roomId)
                            .collection("members")
                            .get()
                            .await()

                        snap.size() < 5   // Max 5 players in a room
                    } catch (e: Exception) {
                        true  // If checking fails, allow join to avoid blocking the user
                    }
                }

                if (!canJoin) {
                    Toast.makeText(
                        this@RoomMatchActivity,
                        "Room is full (max 5 players).",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Then actually call roomStorage.joinRoom
                val res = withContext(Dispatchers.IO) {
                    roomStorage.joinRoom(uid, roomId, pwd)
                }
                if (res.isSuccess) {
                    Toast.makeText(
                        this@RoomMatchActivity,
                        "Join success",
                        Toast.LENGTH_SHORT
                    ).show()
                    openGame(roomId)
                } else {
                    Toast.makeText(
                        this@RoomMatchActivity,
                        res.exceptionOrNull()?.message ?: "join failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        btnBack.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    /**
     * Read login state and uid from SharedPreferences.
     * Only when KEY_LOGGED_IN is true do we treat the user as logged in.
     */
    private fun loadUidFromLocal(): String? {
        val sp = getSharedPreferences(AUTH_FILE, Context.MODE_PRIVATE)

        // First, check explicit login state.
        val loggedIn = sp.getBoolean(KEY_LOGGED_IN, false)
        if (!loggedIn) {
            // User is not logged in according to the auth flag;
            // return null so caller can redirect the user to login.
            return null
        }

        // Logged in: read uid from the same SharedPreferences file.
        return sp.getString(KEY_UID, null)
    }

    /**
     * Auto re-enter the last room: called once after entering this page.
     * It uses fetchCurrentRoomId(uid) to find the room the user is currently in.
     */
    private fun autoReenterRoomIfNeeded() {
        lifecycleScope.launch {
            val roomId = withContext(Dispatchers.IO) {
                // FirestoreRoomStorage API: fetchCurrentRoomId(uid): String?
                roomStorage.fetchCurrentRoomId(uid)
            }

            if (!roomId.isNullOrEmpty()) {
                // Optionally show a Toast here to notify the player
                // Toast.makeText(this@RoomMatchActivity, "Auto re-enter room: $roomId", Toast.LENGTH_SHORT).show()
                openGame(roomId)
                // Close this page so that pressing back from the game will not show it again
                finish()
            }
        }
    }

    /** Navigate to the game page */
    private fun openGame(roomId: String) {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra(GameActivity.EXTRA_ROOM_ID, roomId)
        startActivity(intent)
    }
}
