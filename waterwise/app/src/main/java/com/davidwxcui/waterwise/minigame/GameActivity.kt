package com.davidwxcui.waterwise.minigame

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.davidwxcui.waterwise.MainActivity
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.data.FirestoreRoomStorage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlin.random.Random
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ContextThemeWrapper
private lateinit var btnGameRanking: ImageButton
// ================== DATA MODELS ==================

data class PlayerState(
    val coins: Int = 10000,
    val position: Int = 0,
    val diceLeft: Int = 0,
    val ownedProperties: List<String> = emptyList()
)

data class EventChoice(
    val label: String,
    val successDescription: String,
    val failDescription: String,
    val successCoinDelta: Int,
    val failCoinDelta: Int,
    val successRatePercent: Int
)

data class TileEventTemplate(
    val title: String,
    val description: String,
    val choices: List<EventChoice>
)

data class ResolvedEvent(
    val description: String,
    val coinDelta: Int
)

data class PropertyInfo(
    val id: String,
    val name: String,
    val price: Int,
    val incomePerTurn: Int
)

/** For drawing all players on the board with different colors */
data class PlayerMarker(
    val uid: String,
    val name: String,
    val position: Int,
    val color: Int,
    val colorName: String
)

// ================== ACTIVITY ==================

class GameActivity : AppCompatActivity() {

    companion object {
        private const val TOTAL_TILES = 22

        // Firestore structure: rooms/<roomId>/members/<uid>
        private const val ROOMS_COLLECTION = "rooms"
        private const val MEMBERS_SUBCOLLECTION = "members"

        const val EXTRA_ROOM_ID = "roomId"
    }

    // At most 5 players in one room, each one has a color
    private val PLAYER_COLORS = listOf(
        0xFFFF4444.toInt(), // Red
        0xFF33B5E5.toInt(), // Blue
        0xFF99CC00.toInt(), // Green
        0xFFFFBB33.toInt(), // Yellow/Orange
        0xFFAA66CC.toInt()  // Purple
    )

    private val PLAYER_COLOR_NAMES = listOf(
        "Red",
        "Blue",
        "Green",
        "Yellow",
        "Purple"
    )

    private val PLAYER_COLOR_ICONS = listOf(
        R.drawable.ic_player_red,
        R.drawable.ic_player_blue,
        R.drawable.ic_player_green,
        R.drawable.ic_player_yellow,
        R.drawable.ic_player_purple
    )


    // 8x5 outer ring with 22 tiles
    private val tilePositions = listOf(
        0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4,
        1 to 4, 2 to 4, 3 to 4, 4 to 4, 5 to 4, 6 to 4, 7 to 4,
        7 to 3, 7 to 2, 7 to 1, 7 to 0,
        6 to 0, 5 to 0, 4 to 0, 3 to 0, 2 to 0, 1 to 0
    )

    // Property configuration
    private val allProperties = listOf(
        PropertyInfo("coffee", "Coffee Shop", price = 40000, incomePerTurn = 3000),
        PropertyInfo("book", "Book Store", price = 60000, incomePerTurn = 4000),
        PropertyInfo("apartment", "Apartment", price = 90000, incomePerTurn = 6000),
        PropertyInfo("startup", "Tech Startup", price = 150000, incomePerTurn = 12000),
        PropertyInfo("restaurant", "Restaurant", price = 70000, incomePerTurn = 4500),
        PropertyInfo("mall", "Shopping Mall", price = 130000, incomePerTurn = 9000),
        PropertyInfo("bank", "Bank", price = 200000, incomePerTurn = 15000)
    )

    // propertyId -> tileIndex
    private val propertyIdToTile = mutableMapOf<String, Int>()
    // tileIndex -> propertyId
    private val tileToPropertyId = mutableMapOf<Int, String>()
    // propertyId -> ownerUid
    private val propertyOwners = mutableMapOf<String, String>()
    // ownerUid -> name cache
    private val ownerNameCache = mutableMapOf<String, String>()
    // all players with their position and color
    private val playerMarkers = mutableListOf<PlayerMarker>()

    // Property icons
    private val propertyIdToIconRes = mapOf(
        "coffee" to R.drawable.ic_property_coffee,
        "book" to R.drawable.ic_property_book,
        "apartment" to R.drawable.ic_property_apartment,
        "startup" to R.drawable.ic_property_startup,
        "restaurant" to R.drawable.ic_property_restaurant,
        "mall" to R.drawable.ic_property_mall,
        "bank" to R.drawable.ic_property_bank
    )
    // Random event tile icon
    private val randomTileIconRes = R.drawable.ic_tile_random

    // Dice images
    private val diceDrawables = intArrayOf(
        R.drawable.dice_1,
        R.drawable.dice_2,
        R.drawable.dice_3,
        R.drawable.dice_4,
        R.drawable.dice_5,
        R.drawable.dice_6
    )

    // Views
    private lateinit var tvCoins: TextView
    private lateinit var tvDiceLeft: TextView
    private lateinit var tvEvent: TextView
    private lateinit var btnRollDice: Button
    private lateinit var boardGrid: GridLayout
    private lateinit var boardContainer: FrameLayout
    private lateinit var diceImageView: ImageView

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val roomStorage by lazy { FirestoreRoomStorage() }

    private lateinit var roomId: String
    private lateinit var uid: String
    private var playerState = PlayerState()

    private val tileContainers = mutableListOf<LinearLayout>()
    private val tileIconViews = mutableListOf<ImageView>()
    private val tileEvents = mutableMapOf<Int, TileEventTemplate>()
    private lateinit var btnGameRanking: ImageButton
    private lateinit var btnMenu: ImageButton
    // ---------- Lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_game)

        roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: ""
        if (roomId.isEmpty()) {
            Toast.makeText(this, "Room ID not provided!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        uid = loadUidFromLocal() ?: run {
            Toast.makeText(this, "UID not found, please login first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initViews()

        // Use width only to compute board size, to avoid height=0 problem inside NestedScrollView
        boardContainer.post {
            val w = boardContainer.width
            if (w <= 0) return@post

            val cell = w / 5 // 5 columns

            val boardParams = boardGrid.layoutParams as FrameLayout.LayoutParams
            boardParams.width = cell * 5
            boardParams.height = cell * 8 // 8 rows
            boardParams.gravity = Gravity.CENTER
            boardGrid.layoutParams = boardParams

            val diceSize = (cell * 1.2f).toInt()
            val diceParams = diceImageView.layoutParams
            diceParams.width = diceSize
            diceParams.height = diceSize
            diceImageView.layoutParams = diceParams
        }

        initBoard()
        initTileEvents()

        lifecycleScope.launch {
            loadPlayerAndRoomState()
        }

        btnRollDice.setOnClickListener { onRollDice() }
    }

    private fun loadUidFromLocal(): String? {
        val sp = getSharedPreferences("profile", Context.MODE_PRIVATE)
        return sp.getString("uid", null)
    }

    private fun initViews() {
        tvCoins = findViewById(R.id.tvCoins)
        tvDiceLeft = findViewById(R.id.tvDiceLeft)
        tvEvent = findViewById(R.id.tvEvent)
        btnRollDice = findViewById(R.id.btnRollDice)
        boardGrid = findViewById(R.id.boardGrid)
        boardContainer = findViewById(R.id.boardContainer)
        diceImageView = findViewById(R.id.diceImageView)

        // ⭐ 排行榜按钮
        btnGameRanking = findViewById(R.id.btnGameRanking)
        btnGameRanking.setOnClickListener {
            val intent = Intent(this, GameRankingActivity::class.java)
            intent.putExtra(EXTRA_ROOM_ID, roomId)
            startActivity(intent)
        }
        btnMenu = findViewById(R.id.btnMenu)
        btnMenu.setOnClickListener { view ->
            showCustomMenu(view)
        }
    }

    // ---------- Top-right menu ----------

    private fun showCustomMenu(view: View) {
        val wrapper = ContextThemeWrapper(this, R.style.PopupMenuBlack)
        val popup = PopupMenu(wrapper, view)
        // Ensure you have res/menu/menu_game.xml
        popup.menuInflater.inflate(R.menu.menu_game, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_room_id -> {
                    showRoomIdDialog(); true
                }
                R.id.menu_members -> {
                    showRoomMembersDialog(); true
                }
                R.id.menu_leave_room -> {
                    promptLeaveRoom(); true
                }
                R.id.menu_home -> {
                    goHome(); true
                }
                R.id.menu_player_colors -> {
                    showPlayerColorsDialog(); true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRoomIdDialog() {
        val msg = "Room ID:\n$roomId"
        showGameMessageDialog(
            iconRes = R.drawable.ic_dice,
            title = "Room ID",
            message = msg
        )
    }


    /** Show list of members (names only, no colors) */
    private fun showRoomMembersDialog() {
        lifecycleScope.launch {
            try {
                val membersRef = firestore.collection(ROOMS_COLLECTION)
                    .document(roomId)
                    .collection(MEMBERS_SUBCOLLECTION)

                val snapshot = membersRef.get().await()
                if (snapshot.isEmpty) {
                    showGameMessageDialog(
                        iconRes = R.drawable.ic_dice,
                        title = "Room Members",
                        message = "No players in this room."
                    )
                    return@launch
                }

                val names = snapshot.documents.map { playerDoc ->
                    val memberUid = playerDoc.id
                    try {
                        val userSnap = firestore.collection("users")
                            .document(memberUid)
                            .get()
                            .await()
                        userSnap.getString("name") ?: memberUid
                    } catch (e: Exception) {
                        memberUid
                    }
                }.sorted()

                val msg = names.joinToString("\n")

                showGameMessageDialog(
                    iconRes = R.drawable.ic_dice,
                    title = "Room Members",
                    message = msg
                )
            } catch (e: Exception) {
                showGameMessageDialog(
                    iconRes = R.drawable.ic_dice,
                    title = "Room Members",
                    message = "Failed to load members: ${e.message}"
                )
            }
        }
    }

    /** Show mapping: player -> color name */
    private fun showPlayerColorsDialog() {
        lifecycleScope.launch {
            if (playerMarkers.isEmpty()) {
                refreshPlayerMarkers()
            }

            val dialogView = layoutInflater.inflate(R.layout.dialog_player_colors, null)
            val container = dialogView.findViewById<LinearLayout>(R.id.containerPlayerColors)
            val btnOk = dialogView.findViewById<Button>(R.id.btnPlayerColorOk)

            if (playerMarkers.isEmpty()) {
                val tv = TextView(this@GameActivity).apply {
                    text = "No players in this room."
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 14f
                }
                container.addView(tv)
            } else {
                val density = resources.displayMetrics.density
                val verticalPadding = (8 * density).toInt()
                val iconSize = (20 * density).toInt()
                val textMarginStart = (8 * density).toInt()

                playerMarkers.forEach { marker ->
                    val idx = PLAYER_COLOR_NAMES.indexOf(marker.colorName)
                    val iconRes = if (idx in PLAYER_COLOR_ICONS.indices) {
                        PLAYER_COLOR_ICONS[idx]
                    } else {
                        R.drawable.ic_dice
                    }

                    val row = LinearLayout(this@GameActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, verticalPadding, 0, verticalPadding)
                    }

                    val iv = ImageView(this@GameActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                        setImageResource(iconRes)
                    }

                    val tv = TextView(this@GameActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).also { lp ->
                            lp.marginStart = textMarginStart
                        }
                        text = "${marker.name}: ${marker.colorName}"
                        setTextColor(0xFFFFFFFF.toInt())
                        textSize = 14f
                    }

                    row.addView(iv)
                    row.addView(tv)
                    container.addView(row)
                }
            }

            val dialog = AlertDialog.Builder(this@GameActivity)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            btnOk.setOnClickListener { dialog.dismiss() }

            dialog.show()
            dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
    }



    private fun promptLeaveRoom() {
        showGameConfirmDialog(
            iconRes = R.drawable.ic_dice,
            title = "Leave Room",
            message = "Are you sure you want to leave this room?",
            positiveText = "Leave",
            negativeText = "Cancel"
        ) {
            // 点击 Leave 后执行
            leaveRoomFromMenu()
        }
    }

    /** Menu "Leave Room": only call leaveRoom API, do not delete player data here */
    private fun leaveRoomFromMenu() {
        btnRollDice.isEnabled = false

        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    roomStorage.leaveRoom(uid, roomId)
                }
                if (res.isFailure) {
                    Toast.makeText(
                        this@GameActivity,
                        res.exceptionOrNull()?.message ?: "Leave room failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@GameActivity,
                    "Leave room failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }

            val intent = Intent(this@GameActivity, RoomMatchActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    /** Menu "Home": go back to MainActivity and clear back stack */
    private fun goHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    // ---------- Board & events ----------

    private fun initBoard() {
        tileContainers.clear()
        tileIconViews.clear()
        boardGrid.removeAllViews()
        boardGrid.rowCount = 8
        boardGrid.columnCount = 5

        for (i in 0 until TOTAL_TILES) {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_tile_normal)
            }

            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    2f
                )
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageResource(randomTileIconRes)
            }

            val textView = TextView(this).apply {
                text = i.toString()
                textSize = 12f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            container.addView(iconView)
            container.addView(textView)
            container.setOnClickListener { onTileClicked(i) }

            val (row, col) = tilePositions[i]
            val params = GridLayout.LayoutParams().apply {
                rowSpec = GridLayout.spec(row, 1f)
                columnSpec = GridLayout.spec(col, 1f)
                width = 0
                height = 0
            }
            boardGrid.addView(container, params)

            tileContainers.add(container)
            tileIconViews.add(iconView)
        }

        updateBoardHighlight()
        updateTileIcons()
    }

    /** Initialize all random event templates and assign them randomly to tiles */
    private fun initTileEvents() {
        val treasureEvent = TileEventTemplate(
            title = "Treasure Chest",
            description = "You discover a mysterious treasure chest on the road.",
            choices = listOf(
                EventChoice(
                    label = "Open it boldly",
                    successDescription = "The chest is full of gold!",
                    failDescription = "It was a trap! You lose a lot of coins.",
                    successCoinDelta = +40000,
                    failCoinDelta = -30000,
                    successRatePercent = 40
                ),
                EventChoice(
                    label = "Inspect carefully",
                    successDescription = "You safely find some coins inside.",
                    failDescription = "It's mostly junk, some effort is wasted.",
                    successCoinDelta = +15000,
                    failCoinDelta = -5000,
                    successRatePercent = 75
                )
            )
        )

        val tollEvent = TileEventTemplate(
            title = "Toll Gate",
            description = "You arrive at a toll gate guarded by a strict officer.",
            choices = listOf(
                EventChoice(
                    label = "Pay the full toll",
                    successDescription = "You pay the official fee and pass.",
                    failDescription = "The officer still finds a problem and adds extra fees.",
                    successCoinDelta = 0,
                    failCoinDelta = -15000,
                    successRatePercent = 80
                ),
                EventChoice(
                    label = "Try to bargain",
                    successDescription = "You negotiate successfully and even save some money.",
                    failDescription = "Negotiation fails, a heavy fine is added.",
                    successCoinDelta = +5000,
                    failCoinDelta = -30000,
                    successRatePercent = 45
                )
            )
        )

        val lotteryEvent = TileEventTemplate(
            title = "Street Lottery",
            description = "A street vendor invites you to buy a lottery ticket.",
            choices = listOf(
                EventChoice(
                    label = "Buy an expensive ticket",
                    successDescription = "Jackpot! You win a huge prize.",
                    failDescription = "No luck at all. You burn a pile of money.",
                    successCoinDelta = +80000,
                    failCoinDelta = -20000,
                    successRatePercent = 25
                ),
                EventChoice(
                    label = "Buy a cheap ticket",
                    successDescription = "You win a small prize.",
                    failDescription = "No winning number, you still lose some cash.",
                    successCoinDelta = +15000,
                    failCoinDelta = -5000,
                    successRatePercent = 40
                )
            )
        )

        val jobEvent = TileEventTemplate(
            title = "Part-time Job",
            description = "You see a sign looking for part-time helpers.",
            choices = listOf(
                EventChoice(
                    label = "Work overtime",
                    successDescription = "You work hard and get a big bonus.",
                    failDescription = "You get exhausted and pay is heavily deducted.",
                    successCoinDelta = +25000,
                    failCoinDelta = -15000,
                    successRatePercent = 55
                ),
                EventChoice(
                    label = "Work casually",
                    successDescription = "Easy shift, you still get some money.",
                    failDescription = "Business is slow, no pay at all.",
                    successCoinDelta = +8000,
                    failCoinDelta = 0,
                    successRatePercent = 80
                )
            )
        )

        val shopEvent = TileEventTemplate(
            title = "Shopping Temptation",
            description = "You walk by a shop with a big SALE sign.",
            choices = listOf(
                EventChoice(
                    label = "Buy a fancy item",
                    successDescription = "It turns out to be valuable, you resell it later.",
                    failDescription = "Impulse purchase. It wasn't worth the price.",
                    successCoinDelta = +30000,
                    failCoinDelta = -35000,
                    successRatePercent = 35
                ),
                EventChoice(
                    label = "Just browse",
                    successDescription = "You find a small bargain.",
                    failDescription = "Nothing interesting, but you keep your money.",
                    successCoinDelta = +5000,
                    failCoinDelta = 0,
                    successRatePercent = 70
                )
            )
        )

        val hospitalEvent = TileEventTemplate(
            title = "Hospital Visit",
            description = "You feel a little sick on the way.",
            choices = listOf(
                EventChoice(
                    label = "Go to hospital",
                    successDescription = "The doctor finds the issue early, you avoid big costs.",
                    failDescription = "Tests are expensive, the bill is higher than expected.",
                    successCoinDelta = -5000,
                    failCoinDelta = -20000,
                    successRatePercent = 70
                ),
                EventChoice(
                    label = "Ignore it",
                    successDescription = "It was just fatigue, you recover by resting.",
                    failDescription = "Condition becomes worse, emergency treatment is needed.",
                    successCoinDelta = 0,
                    failCoinDelta = -30000,
                    successRatePercent = 50
                )
            )
        )

        val taxEvent = TileEventTemplate(
            title = "Tax Inspection",
            description = "A tax officer checks your records.",
            choices = listOf(
                EventChoice(
                    label = "Provide all documents honestly",
                    successDescription = "Everything is correct, the officer leaves.",
                    failDescription = "Some small errors are found, you pay a minor fine.",
                    successCoinDelta = 0,
                    failCoinDelta = -10000,
                    successRatePercent = 75
                ),
                EventChoice(
                    label = "Try to hide small mistakes",
                    successDescription = "The officer doesn’t notice anything and you keep the money.",
                    failDescription = "You are caught, a big fine is issued.",
                    successCoinDelta = +7000,
                    failCoinDelta = -35000,
                    successRatePercent = 40
                )
            )
        )

        val festivalEvent = TileEventTemplate(
            title = "Festival Market",
            description = "A festival market is held nearby.",
            choices = listOf(
                EventChoice(
                    label = "Open a small stall",
                    successDescription = "Your stall is very popular, you earn a good profit.",
                    failDescription = "Few customers show up, you barely cover costs.",
                    successCoinDelta = +25000,
                    failCoinDelta = -5000,
                    successRatePercent = 60
                ),
                EventChoice(
                    label = "Just enjoy the festival",
                    successDescription = "You win some mini-games and prizes.",
                    failDescription = "You spend some money on snacks and games.",
                    successCoinDelta = +5000,
                    failCoinDelta = -8000,
                    successRatePercent = 55
                )
            )
        )

        val investEvent = TileEventTemplate(
            title = "Investment Offer",
            description = "A friend recommends a new investment project.",
            choices = listOf(
                EventChoice(
                    label = "Invest a large amount",
                    successDescription = "The project explodes in popularity, huge returns!",
                    failDescription = "The project fails, you lose most of your money.",
                    successCoinDelta = +90000,
                    failCoinDelta = -60000,
                    successRatePercent = 35
                ),
                EventChoice(
                    label = "Invest carefully",
                    successDescription = "Stable returns from a safe investment.",
                    failDescription = "The project is slow, almost no profit.",
                    successCoinDelta = +20000,
                    failCoinDelta = -2000,
                    successRatePercent = 70
                )
            )
        )

        val walletEvent = TileEventTemplate(
            title = "Lost Wallet",
            description = "You find a wallet on the street.",
            choices = listOf(
                EventChoice(
                    label = "Return it to the owner",
                    successDescription = "The owner is very grateful and gives you a reward.",
                    failDescription = "The owner thanks you but is short on money.",
                    successCoinDelta = +15000,
                    failCoinDelta = 0,
                    successRatePercent = 80
                ),
                EventChoice(
                    label = "Keep the cash quietly",
                    successDescription = "Nobody sees you, you get the money.",
                    failDescription = "Nearby cameras catch you, you pay a fine.",
                    successCoinDelta = +20000,
                    failCoinDelta = -30000,
                    successRatePercent = 50
                )
            )
        )

        val templates = listOf(
            treasureEvent,
            tollEvent,
            lotteryEvent,
            jobEvent,
            shopEvent,
            hospitalEvent,
            taxEvent,
            festivalEvent,
            investEvent,
            walletEvent
        )

        tileEvents.clear()
        for (i in 0 until TOTAL_TILES) {
            tileEvents[i] = templates.random()
        }
    }

    private fun randomizePropertyPositions() {
        propertyIdToTile.clear()
        tileToPropertyId.clear()

        val availableTiles = (0 until TOTAL_TILES).toMutableList()
        allProperties.forEach { property ->
            if (availableTiles.isEmpty()) return@forEach
            val index = availableTiles.random()
            availableTiles.remove(index)
            propertyIdToTile[property.id] = index
            tileToPropertyId[index] = property.id
        }

        updateTileIcons()
    }

    private fun getPropertyInfoById(id: String): PropertyInfo? =
        allProperties.find { it.id == id }

    private fun getPropertyAtTile(tileIndex: Int): PropertyInfo? {
        val id = tileToPropertyId[tileIndex] ?: return null
        return getPropertyInfoById(id)
    }

    private fun updateTileIcons() {
        if (tileIconViews.size != TOTAL_TILES) return

        for (i in 0 until TOTAL_TILES) {
            val iconView = tileIconViews[i]
            val propertyId = tileToPropertyId[i]
            if (propertyId != null) {
                val resId = propertyIdToIconRes[propertyId] ?: randomTileIconRes
                iconView.setImageResource(resId)
            } else {
                iconView.setImageResource(randomTileIconRes)
            }
        }
    }

    // ---------- Tile click: show tile info ----------

    private fun onTileClicked(tileIndex: Int) {
        if (!btnRollDice.isEnabled) return

        val propertyId = tileToPropertyId[tileIndex]
        if (propertyId != null) {
            val info = getPropertyInfoById(propertyId) ?: return
            val ownerUid = propertyOwners[propertyId]

            if (ownerUid != null && ownerUid != uid) {
                getOwnerName(ownerUid) { ownerName ->
                    showPropertyInfoDialog(tileIndex, info, ownerName)
                }
            } else {
                val ownerName = when {
                    ownerUid == null -> "None"
                    ownerUid == uid -> "You"
                    else -> "Unknown"
                }
                showPropertyInfoDialog(tileIndex, info, ownerName)
            }
        } else {
            val template = tileEvents[tileIndex]
            val msg = if (template != null && template.choices.size >= 2) {
                val c1 = template.choices[0]
                val c2 = template.choices[1]
                buildString {
                    append("Tile index: $tileIndex\n")
                    append("Type: Random event tile\n\n")
                    append("Event: ${template.title}\n")
                    append("${template.description}\n\n")

                    append("Option 1: ${c1.label}\n")
                    append("  Success rate: ${c1.successRatePercent}%\n")
                    append("  On success: ${c1.successCoinDelta} coins\n")
                    append("  On fail: ${c1.failCoinDelta} coins\n\n")

                    append("Option 2: ${c2.label}\n")
                    append("  Success rate: ${c2.successRatePercent}%\n")
                    append("  On success: ${c2.successCoinDelta} coins\n")
                    append("  On fail: ${c2.failCoinDelta} coins\n")
                }
            } else {
                "Tile index: $tileIndex\nType: Random event tile\n\n(No detailed event template found.)"
            }

            showGameMessageDialog(
                iconRes = R.drawable.ic_dice,
                title = "Tile Info",
                message = msg
            )
        }
    }

    private fun showPropertyInfoDialog(tileIndex: Int, info: PropertyInfo, ownerName: String) {
        val msg = buildString {
            append("Tile index: $tileIndex\n")
            append("Type: Property tile\n\n")
            append("Name: ${info.name}\n")
            append("Price: ${info.price} coins\n")
            append("Income per turn: ${info.incomePerTurn} coins\n")
            append("Owner: $ownerName\n\n")
            append("Your coins: ${playerState.coins}")
        }

        showGameMessageDialog(
            iconRes = R.drawable.ic_coin,
            title = "Tile Info",
            message = msg
        )
    }


    // ---------- Dice roll ----------

    private fun onRollDice() {
        if (playerState.diceLeft <= 0) {
            Toast.makeText(this, "No dice left!", Toast.LENGTH_SHORT).show()
            return
        }

        val dice = Random.nextInt(1, 7)
        btnRollDice.isEnabled = false

        playDiceAnimation(dice) {
            val newPosition = (playerState.position + dice) % TOTAL_TILES
            val property = getPropertyAtTile(newPosition)
            if (property != null) {
                val propertyId = tileToPropertyId[newPosition]!!
                handlePropertyLanding(newPosition, dice, propertyId, property)
            } else {
                val template = tileEvents[newPosition]
                showEventDialog(newPosition, dice, template)
            }
        }
    }

    /** Rent formula when landing on someone else's property */
    private fun calculateRent(info: PropertyInfo): Int {
        // Example: rent is 2x income per turn
        return info.incomePerTurn * 2
    }

    /** Handle landing on a property tile (buy, own, or pay rent) */
    private fun handlePropertyLanding(
        newPosition: Int,
        dice: Int,
        propertyId: String,
        info: PropertyInfo
    ) {
        val ownerUid = propertyOwners[propertyId]

        when {
            ownerUid == null -> {
                // No owner yet, show buy dialog
                showPropertyBuyDialog(newPosition, dice, propertyId, info)
            }
            ownerUid == uid -> {
                // Your own property
                val desc = "You visited your property: ${info.name}."
                endTurn(newPosition, dice, 0, desc, null)
            }
            else -> {
                // Another player's property -> pay rent
                getOwnerName(ownerUid) { ownerName ->
                    val rent = calculateRent(info)
                    val desc =
                        "You landed on $ownerName's property: ${info.name} and paid $rent coins as rent."
                    endTurn(newPosition, dice, -rent, desc, null)
                }
            }
        }
    }

    private fun showPropertyBuyDialog(
        newPosition: Int,
        dice: Int,
        propertyId: String,
        info: PropertyInfo
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_property_buy, null)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitleProp)
        val tvName = dialogView.findViewById<TextView>(R.id.tvPropName)
        val tvPrice = dialogView.findViewById<TextView>(R.id.tvPropPrice)
        val tvIncome = dialogView.findViewById<TextView>(R.id.tvPropIncome)
        val tvCoins = dialogView.findViewById<TextView>(R.id.tvPropCoins)
        val ivIcon = dialogView.findViewById<ImageView>(R.id.ivPropertyIcon)
        val btnBuy = dialogView.findViewById<Button>(R.id.btnBuyProp)
        val btnSkip = dialogView.findViewById<Button>(R.id.btnSkipProp)

        tvTitle.text = "Buy Property?"
        tvName.text = info.name
        tvPrice.text = "Price: ${info.price} coins"
        tvIncome.text = "Income per turn: ${info.incomePerTurn} coins"
        tvCoins.text = "Your coins: ${playerState.coins}"

        val resId = propertyIdToIconRes[propertyId]
        if (resId != null) {
            ivIcon.setImageResource(resId)
        } else {
            ivIcon.setImageResource(R.drawable.ic_coin)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnBuy.setOnClickListener {
            dialog.dismiss()
            if (playerState.coins < info.price) {
                Toast.makeText(
                    this,
                    "Not enough coins to buy this property.",
                    Toast.LENGTH_SHORT
                ).show()
                val desc = "You wanted to buy ${info.name}, but didn't have enough coins."
                endTurn(newPosition, dice, 0, desc, null)
            } else {
                val newList = playerState.ownedProperties.toMutableList()
                if (!newList.contains(propertyId)) newList.add(propertyId)
                propertyOwners[propertyId] = uid
                saveRoomBoard()

                val desc = "You bought property: ${info.name} for ${info.price} coins."
                endTurn(newPosition, dice, -info.price, desc, newList)
            }
        }

        btnSkip.setOnClickListener {
            dialog.dismiss()
            val desc = "You decided not to buy ${info.name}."
            endTurn(newPosition, dice, 0, desc, null)
        }

        dialog.setOnCancelListener {
            val desc = "You hesitated and walked away from ${info.name}."
            endTurn(newPosition, dice, 0, desc, null)
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }


    private fun showEventDialog(newPosition: Int, dice: Int, template: TileEventTemplate?) {
        if (template == null || template.choices.size < 2) {
            val result = ResolvedEvent("Nothing happens on this tile.", 0)
            applyEventChoice(newPosition, dice, result)
            return
        }

        val choice1 = template.choices[0]
        val choice2 = template.choices[1]

        val dialogView = layoutInflater.inflate(R.layout.dialog_event_choice, null)

        val ivIcon = dialogView.findViewById<ImageView>(R.id.ivDialogIcon)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvDesc = dialogView.findViewById<TextView>(R.id.tvEventDesc)
        val tvOpt1Title = dialogView.findViewById<TextView>(R.id.tvOption1Title)
        val tvOpt1Detail = dialogView.findViewById<TextView>(R.id.tvOption1Detail)
        val tvOpt2Title = dialogView.findViewById<TextView>(R.id.tvOption2Title)
        val tvOpt2Detail = dialogView.findViewById<TextView>(R.id.tvOption2Detail)
        val btnChoice1 = dialogView.findViewById<Button>(R.id.btnChoice1)
        val btnChoice2 = dialogView.findViewById<Button>(R.id.btnChoice2)

        ivIcon.setImageResource(R.drawable.ic_dice)
        tvTitle.text = template.title
        tvDesc.text = template.description

        tvOpt1Title.text = choice1.label
        tvOpt1Detail.text = buildString {
            append("Success rate: ${choice1.successRatePercent}%\n")
            append("On success: ${choice1.successCoinDelta} coins\n")
            append("On fail: ${choice1.failCoinDelta} coins")
        }

        tvOpt2Title.text = choice2.label
        tvOpt2Detail.text = buildString {
            append("Success rate: ${choice2.successRatePercent}%\n")
            append("On success: ${choice2.successCoinDelta} coins\n")
            append("On fail: ${choice2.failCoinDelta} coins")
        }

        btnChoice1.text = choice1.label
        btnChoice2.text = choice2.label

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnChoice1.setOnClickListener {
            dialog.dismiss()
            val result = resolveChoice(choice1, template.title)
            applyEventChoice(newPosition, dice, result)
        }

        btnChoice2.setOnClickListener {
            dialog.dismiss()
            val result = resolveChoice(choice2, template.title)
            applyEventChoice(newPosition, dice, result)
        }

        dialog.setOnCancelListener {
            // 和原来逻辑一样，取消等同选择第二个选项
            val result = resolveChoice(choice2, template.title)
            applyEventChoice(newPosition, dice, result)
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }


    private fun resolveChoice(choice: EventChoice, title: String): ResolvedEvent {
        val roll = Random.nextInt(0, 100)
        val success = roll < choice.successRatePercent
        return if (success) {
            ResolvedEvent(
                description = "$title - ${choice.label}: SUCCESS! ${choice.successDescription}",
                coinDelta = choice.successCoinDelta
            )
        } else {
            ResolvedEvent(
                description = "$title - ${choice.label}: FAILED. ${choice.failDescription}",
                coinDelta = choice.failCoinDelta
            )
        }
    }

    private fun applyEventChoice(
        newPosition: Int,
        dice: Int,
        result: ResolvedEvent
    ) {
        endTurn(newPosition, dice, result.coinDelta, result.description, null)
    }

    // ---------- End turn & bankruptcy ----------

    private fun endTurn(
        newPosition: Int,
        dice: Int,
        baseCoinDelta: Int,
        description: String,
        overrideOwnedProperties: List<String>?
    ) {
        val owned = overrideOwnedProperties ?: playerState.ownedProperties
        val propertyIncome = calculatePropertyIncome(owned)
        val totalDelta = baseCoinDelta + propertyIncome
        val newCoinsRaw = playerState.coins + totalDelta
        val newDiceLeft = playerState.diceLeft - 1

        val finalDesc = buildString {
            append(description)
            if (propertyIncome != 0) {
                append(" Property income this turn: $propertyIncome.")
            }
        }

        if (newCoinsRaw < 0) {
            handleGameOver(newPosition, dice, finalDesc, totalDelta, newCoinsRaw)
            return
        }

        playerState = PlayerState(
            coins = newCoinsRaw,
            position = newPosition,
            diceLeft = newDiceLeft,
            ownedProperties = owned
        )

        updateUI()
        savePlayerState()

        tvEvent.text = finalDesc
        tvEvent.alpha = 0f
        tvEvent.animate()
            .alpha(1f)
            .setDuration(220)
            .start()

        lifecycleScope.launch {
            refreshPlayerMarkers()
            updateBoardHighlight()
        }

        showTurnResultDialog(newPosition, dice, ResolvedEvent(finalDesc, totalDelta))
    }

    private fun calculatePropertyIncome(ownedIds: List<String>): Int {
        var total = 0
        for (id in ownedIds) {
            val info = getPropertyInfoById(id)
            if (info != null) total += info.incomePerTurn
        }
        return total
    }

    private fun handleGameOver(
        newPosition: Int,
        dice: Int,
        description: String,
        totalDelta: Int,
        newCoinsRaw: Int
    ) {
        val message = buildString {
            append("Rolled: $dice, moved to tile $newPosition.\n\n")
            append(description)
            append("\n\n")
            append("Coin change this turn: $totalDelta\n")
            append("Your coins would become: $newCoinsRaw (< 0).\n\n")
            append("Game over! You are bankrupt.\n")
            append("You will leave this room.")
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_game_over, null)

        val ivIcon = dialogView.findViewById<ImageView>(R.id.ivGameOverIcon)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvGameOverTitle)
        val tvMsg = dialogView.findViewById<TextView>(R.id.tvGameOverMessage)
        val btnLeave = dialogView.findViewById<Button>(R.id.btnLeaveRoom)

        ivIcon.setImageResource(R.drawable.ic_coin) // 有骷髅图标的话可以换成别的
        tvTitle.text = "Game Over"
        tvMsg.text = message

        val gameOverDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnLeave.setOnClickListener {
            gameOverDialog.dismiss()
            onGameOverExitRoom()
        }

        gameOverDialog.show()
        gameOverDialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )
    }

    /** On bankruptcy: delete this member doc, then call leaveRoom */
    private fun onGameOverExitRoom() {
        btnRollDice.isEnabled = false

        lifecycleScope.launch {
            try {
                firestore.collection(ROOMS_COLLECTION)
                    .document(roomId)
                    .collection(MEMBERS_SUBCOLLECTION)
                    .document(uid)
                    .delete()
                    .await()
            } catch (e: Exception) {
                Toast.makeText(
                    this@GameActivity,
                    "Failed to delete player data: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }

            try {
                val res = withContext(Dispatchers.IO) {
                    roomStorage.leaveRoom(uid, roomId)
                }
                if (res.isFailure) {
                    Toast.makeText(
                        this@GameActivity,
                        res.exceptionOrNull()?.message ?: "leave room failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@GameActivity,
                    "leave room failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }

            val intent = Intent(this@GameActivity, RoomMatchActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun showGameConfirmDialog(
        iconRes: Int,
        title: String,
        message: String,
        positiveText: String,
        negativeText: String = "Cancel",
        onPositive: () -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_game_confirm, null)

        val ivIcon = dialogView.findViewById<ImageView>(R.id.ivConfirmIcon)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvConfirmTitle)
        val tvMsg = dialogView.findViewById<TextView>(R.id.tvConfirmMessage)
        val btnNegative = dialogView.findViewById<Button>(R.id.btnConfirmNegative)
        val btnPositive = dialogView.findViewById<Button>(R.id.btnConfirmPositive)

        ivIcon.setImageResource(iconRes)
        tvTitle.text = title
        tvMsg.text = message
        btnNegative.text = negativeText
        btnPositive.text = positiveText

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnNegative.setOnClickListener { dialog.dismiss() }
        btnPositive.setOnClickListener {
            dialog.dismiss()
            onPositive()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun showGameMessageDialog(
        iconRes: Int,
        title: String,
        message: String,
        onDismiss: (() -> Unit)? = null
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_game_message, null)

        val ivIcon = dialogView.findViewById<ImageView>(R.id.ivDialogIcon)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnOk = dialogView.findViewById<Button>(R.id.btnDialogOk)

        ivIcon.setImageResource(iconRes)
        tvTitle.text = title
        tvMessage.text = message

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnOk.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            onDismiss?.invoke()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun showTurnResultDialog(
        newPosition: Int,
        dice: Int,
        event: ResolvedEvent
    ) {
        val message = buildString {
            append("Rolled: $dice, moved to tile $newPosition.\n\n")
            append(event.description)
            append("\n\n")
            append("Coin change this turn: ${event.coinDelta}\n")
            append("Total coins: ${playerState.coins}")
        }

        showGameMessageDialog(
            iconRes = R.drawable.ic_dice,
            title = "Turn Result",
            message = message
        ) {
            btnRollDice.isEnabled = true
        }
    }


    // ---------- Dice animation ----------

    private fun playDiceAnimation(finalDice: Int, onEnd: () -> Unit) {
        val duration = 700L

        val frameAnimator = ValueAnimator.ofInt(0, 15).apply {
            this.duration = duration
            addUpdateListener {
                val idx = Random.nextInt(0, 6)
                diceImageView.setImageResource(diceDrawables[idx])
            }
        }

        val rotate = ObjectAnimator.ofFloat(diceImageView, "rotation", 0f, 360f)
        val flipY = ObjectAnimator.ofFloat(diceImageView, "rotationY", 0f, 360f)
        val scaleX = ObjectAnimator.ofFloat(diceImageView, "scaleX", 1f, 1.3f, 1f)
        val scaleY = ObjectAnimator.ofFloat(diceImageView, "scaleY", 1f, 1.3f, 1f)

        rotate.duration = duration
        flipY.duration = duration
        scaleX.duration = duration
        scaleY.duration = duration

        AnimatorSet().apply {
            playTogether(rotate, flipY, scaleX, scaleY, frameAnimator)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    diceImageView.rotation = 0f
                    diceImageView.rotationY = 0f
                    diceImageView.scaleX = 1f
                    diceImageView.scaleY = 1f
                    diceImageView.setImageResource(diceDrawables[finalDice - 1])
                    onEnd()
                }
            })
            start()
        }
    }


    // ---------- UI & state ----------

    private fun updateUI() {
        tvCoins.text = "Coins: ${playerState.coins}"
        tvDiceLeft.text = "Dice left: ${playerState.diceLeft}"
        updateBoardHighlight()
        updateTileIcons()
    }


    /** Use all players' markers to color the board tiles */
    private fun updateBoardHighlight() {
        tileContainers.forEachIndexed { index, container ->
            val playersHere = playerMarkers.filter { it.position == index }
            when {
                playersHere.isEmpty() -> {
                    container.setBackgroundResource(R.drawable.bg_tile_normal)
                }
                playersHere.size == 1 -> {
                    container.setBackgroundColor(playersHere[0].color)
                }
                else -> {
                    container.setBackgroundColor(0xFF777777.toInt())
                }
            }
        }

        val myPos = playerState.position
        tileContainers.getOrNull(myPos)?.apply {
            animate().cancel()
            scaleX = 1f
            scaleY = 1f
            animate()
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(120)
                .withEndAction {
                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start()
                }
                .start()
        }
    }


    private fun startNewGame() {
        initTileEvents()
        randomizePropertyPositions()
        propertyOwners.clear()
        saveRoomBoard()

        playerState = PlayerState(
            coins = 10000,
            position = 0,
            diceLeft = 0,
            ownedProperties = emptyList()
        )
        updateUI()
        savePlayerState()

        lifecycleScope.launch {
            refreshPlayerMarkers()
            updateBoardHighlight()
        }

        btnRollDice.isEnabled = true
    }

    // ---------- Firestore I/O (coroutines) ----------

    /** Load room board + current player state + markers for all players */
    private suspend fun loadPlayerAndRoomState() {
        val roomRef = firestore.collection(ROOMS_COLLECTION).document(roomId)

        try {
            val roomSnap = roomRef.get().await()

            if (roomSnap.exists()) {
                val assignmentsAny = roomSnap.get("propertyAssignments") as? Map<*, *>
                if (assignmentsAny != null && assignmentsAny.isNotEmpty()) {
                    propertyIdToTile.clear()
                    tileToPropertyId.clear()
                    assignmentsAny.forEach { (k, v) ->
                        val id = k as? String ?: return@forEach
                        val tileIndex = (v as? Long)?.toInt() ?: return@forEach
                        propertyIdToTile[id] = tileIndex
                        tileToPropertyId[tileIndex] = id
                    }
                } else {
                    randomizePropertyPositions()
                }

                val ownersAny = roomSnap.get("propertyOwners") as? Map<*, *>
                if (ownersAny != null && ownersAny.isNotEmpty()) {
                    propertyOwners.clear()
                    ownersAny.forEach { (k, v) ->
                        val id = k as? String ?: return@forEach
                        val ownerUid = v as? String ?: return@forEach
                        propertyOwners[id] = ownerUid
                    }
                }

                updateTileIcons()
            } else {
                startNewGame()
                return
            }

            val playerSnap = roomRef.collection(MEMBERS_SUBCOLLECTION)
                .document(uid)
                .get()
                .await()

            if (playerSnap.exists()) {
                val coins = playerSnap.getLong("coins")?.toInt() ?: 10000
                val position = playerSnap.getLong("position")?.toInt() ?: 0
                val diceLeft = playerSnap.getLong("diceLeft")?.toInt() ?: 0
                val ownedPropsAny = playerSnap.get("ownedProperties") as? List<*>
                val ownedProps = ownedPropsAny?.mapNotNull { it as? String } ?: emptyList()

                playerState = PlayerState(coins, position, diceLeft, ownedProps)
                updateUI()
            } else {
                // New player in this room: start with default state.
                playerState = PlayerState()
                updateUI()
                savePlayerState()
            }

            // ---- NEW: daily dice bonus based on yesterday's drink volume ----
            try {
                val result = AddDiceDaily.computeDailyDice(
                    context = this@GameActivity,
                    uid = uid,
                    roomId = roomId,
                    firestore = firestore
                )

                // Only show message / change dice on the first calculation of the day.
                if (result.isFirstTimeToday && result.diceToAdd > 0) {
                    val addedDice = result.diceToAdd

                    // Add bonus dice to the existing diceLeft.
                    playerState = playerState.copy(
                        diceLeft = playerState.diceLeft + addedDice
                    )
                    updateUI()
                    savePlayerState()

                    Toast.makeText(
                        this@GameActivity,
                        "Yesterday you drank ${result.yesterdayVolumeMl} ml, so you received $addedDice dice!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
// ---- NEW END ----


            refreshPlayerMarkers()
            updateBoardHighlight()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Failed to load room/player: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            startNewGame()
        }
    }

    /** Save board layout and property owners into rooms/<roomId> */
    private fun saveRoomBoard() {
        val roomData = hashMapOf(
            "propertyAssignments" to propertyIdToTile,
            "propertyOwners" to propertyOwners
        )

        firestore.collection(ROOMS_COLLECTION)
            .document(roomId)
            .set(roomData, SetOptions.merge())
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save room: ${it.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    /** Save current player state into rooms/<roomId>/members/<uid> */
    private fun savePlayerState() {
        val data = hashMapOf(
            "coins" to playerState.coins,
            "position" to playerState.position,
            "diceLeft" to playerState.diceLeft,
            "ownedProperties" to playerState.ownedProperties
        )

        firestore.collection(ROOMS_COLLECTION)
            .document(roomId)
            .collection(MEMBERS_SUBCOLLECTION)
            .document(uid)
            .set(data)
            .addOnSuccessListener {
                updateHighestCoinsIfNeeded(playerState.coins)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save player: ${it.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }


    private fun updateHighestCoinsIfNeeded(currentCoins: Int) {
        val userDocRef = firestore.collection("users")
            .document(uid)

        userDocRef.get()
            .addOnSuccessListener { snap ->
                val highest = snap.getLong("highestcoins") ?: 0L
                if (currentCoins > highest) {
                    val data = hashMapOf(
                        "highestcoins" to currentCoins
                    )
                    userDocRef.set(data, SetOptions.merge())
                }
            }
            .addOnFailureListener {
            }
    }

    /** Load all members -> assign at most 5 colors by sorted UID */
    private suspend fun refreshPlayerMarkers() {
        try {
            val membersRef = firestore.collection(ROOMS_COLLECTION)
                .document(roomId)
                .collection(MEMBERS_SUBCOLLECTION)

            val snapshot = membersRef.get().await()
            if (snapshot.isEmpty) {
                playerMarkers.clear()
                return
            }

            val docs = snapshot.documents
            val uids = docs.map { it.id }
            val positions = docs.associate { doc ->
                val p = doc.getLong("position")?.toInt() ?: 0
                doc.id to p
            }

            val sortedUids = uids.sorted().take(PLAYER_COLORS.size)

            val nameMap = mutableMapOf<String, String>()
            coroutineScope {
                sortedUids.map { memberUid ->
                    async(Dispatchers.IO) {
                        try {
                            val userSnap = firestore.collection("users")
                                .document(memberUid)
                                .get()
                                .await()
                            nameMap[memberUid] =
                                userSnap.getString("name") ?: memberUid
                        } catch (e: Exception) {
                            nameMap[memberUid] = memberUid
                        }
                    }
                }.awaitAll()
            }

            playerMarkers.clear()
            sortedUids.forEachIndexed { index, memberUid ->
                val pos = positions[memberUid] ?: 0
                val color = PLAYER_COLORS[index]
                val colorName = PLAYER_COLOR_NAMES[index]
                val name = nameMap[memberUid] ?: memberUid
                playerMarkers.add(PlayerMarker(memberUid, name, pos, color, colorName))
            }
        } catch (e: Exception) {
            // Keep old markers if refresh failed, do not spam Toast
        }
    }

    // ---------- Owner name lookup (single user) ----------

    private fun getOwnerName(ownerUid: String, onResult: (String) -> Unit) {
        ownerNameCache[ownerUid]?.let {
            onResult(it)
            return
        }

        firestore.collection("users")
            .document(ownerUid)
            .get()
            .addOnSuccessListener { snap ->
                val name = snap.getString("name") ?: ownerUid
                ownerNameCache[ownerUid] = name
                onResult(name)
            }
            .addOnFailureListener {
                onResult(ownerUid)
            }
    }
}
