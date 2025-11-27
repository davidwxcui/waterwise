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


        uid = loadUidFromLocal() ?: run {
            Toast.makeText(this, "UID not found, please login first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        tvUid.text = "UID: $uid"

        // 1. 打开页面时自动尝试回房
        autoReenterRoomIfNeeded()

        // 2. 按钮：创建房间
        btnCreateRoom.setOnClickListener {
            val pwd = etPassword.text.toString()
            lifecycleScope.launch {
                val res = withContext(Dispatchers.IO) {
                    roomStorage.createRoom(uid, pwd)
                }
                if (res.isSuccess) {
                    val roomId = res.getOrNull()!!
                    Toast.makeText(this@RoomMatchActivity, "Create room: $roomId", Toast.LENGTH_SHORT).show()
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

        // 3. 按钮：加入房间
        btnJoinRoom.setOnClickListener {
            val roomId = etRoomId.text.toString().trim()
            val pwd = etPassword.text.toString()

            if (roomId.isEmpty()) {
                Toast.makeText(this, "Room ID is empty.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                // 先检查人数
                val canJoin = withContext(Dispatchers.IO) {
                    try {
                        val snap = firestore.collection("rooms")
                            .document(roomId)
                            .collection("members")
                            .get()
                            .await()
                        snap.size() < 5   // 最多 5 人
                    } catch (e: Exception) {
                        true  // 出错时先允许加入，避免卡死
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

                // 再真正调用 roomStorage.joinRoom
                val res = withContext(Dispatchers.IO) {
                    roomStorage.joinRoom(uid, roomId, pwd)
                }
                if (res.isSuccess) {
                    Toast.makeText(this@RoomMatchActivity, "Join success", Toast.LENGTH_SHORT).show()
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

    /** 从 SharedPreferences 里读取 uid */
    private fun loadUidFromLocal(): String? {
        val sp = getSharedPreferences("profile", Context.MODE_PRIVATE)
        return sp.getString("uid", null)
    }

    /** 自动重进：进入页面后自动调用一次 fetchCurrentRoomId */
    private fun autoReenterRoomIfNeeded() {
        lifecycleScope.launch {
            val roomId = withContext(Dispatchers.IO) {
                // FirestoreRoomStorage 提供的 API：fetchCurrentRoomId(uid): String?
                roomStorage.fetchCurrentRoomId(uid)
            }

            if (!roomId.isNullOrEmpty()) {
                // 这里你也可以先 Toast 一下提示玩家
                // Toast.makeText(this@RoomMatchActivity, "Auto re-enter room: $roomId", Toast.LENGTH_SHORT).show()
                openGame(roomId)
                // 直接关闭匹配页面，避免回退时再看到
                finish()
            }
        }
    }

    /** 跳转到游戏页面 */
    private fun openGame(roomId: String) {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra(GameActivity.EXTRA_ROOM_ID, roomId)
        startActivity(intent)
    }
}