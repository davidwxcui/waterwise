package com.davidwxcui.waterwise.minigame

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.davidwxcui.waterwise.MainActivity
import com.davidwxcui.waterwise.R
import com.davidwxcui.waterwise.data.FirestoreDrinkStorage
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.min
import kotlin.random.Random
import android.widget.ImageButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

// ================== DATA MODELS ==================

data class PlayerState(
    val coins: Int = 0,          // 初始金币：现在从 0 开始
    val position: Int = 0,
    val diceLeft: Int = 0,       // 初始骰子：0，靠喝水解锁
    val ownedProperties: List<String> = emptyList(),
    val diceUsedToday: Int = 0
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

// ================== ACTIVITY ==================

class GameActivity : AppCompatActivity() {

    companion object {
        private const val TOTAL_TILES = 22
        private const val COLLECTION_NAME = "games"
    }

    private lateinit var btnGameRanking: ImageView

    // 8x5 outer ring positions, 22 tiles, clockwise
    private val tilePositions = listOf(
        // Top row (5)
        0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4,
        // Right column (7)
        1 to 4, 2 to 4, 3 to 4, 4 to 4, 5 to 4, 6 to 4, 7 to 4,
        // Bottom row (4)
        7 to 3, 7 to 2, 7 to 1, 7 to 0,
        // Left column (6)
        6 to 0, 5 to 0, 4 to 0, 3 to 0, 2 to 0, 1 to 0
    )

    // 7 properties
    private val allProperties = listOf(
        PropertyInfo("coffee", "Coffee Shop", price = 40000, incomePerTurn = 3000),
        PropertyInfo("book", "Book Store", price = 60000, incomePerTurn = 4000),
        PropertyInfo("apartment", "Apartment", price = 90000, incomePerTurn = 6000),
        PropertyInfo("startup", "Tech Startup", price = 150000, incomePerTurn = 12000),
        PropertyInfo("restaurant", "Restaurant", price = 70000, incomePerTurn = 4500),
        PropertyInfo("mall", "Shopping Mall", price = 130000, incomePerTurn = 9000),
        PropertyInfo("bank", "Bank", price = 200000, incomePerTurn = 15000)
    )

    // propertyId -> tileIndex / tileIndex -> propertyId
    private val propertyIdToTile = mutableMapOf<String, Int>()
    private val tileToPropertyId = mutableMapOf<Int, String>()

    // propertyId -> icon drawable
    private val propertyIdToIconRes = mapOf(
        "coffee" to R.drawable.ic_property_coffee,
        "book" to R.drawable.ic_property_book,
        "apartment" to R.drawable.ic_property_apartment,
        "startup" to R.drawable.ic_property_startup,
        "restaurant" to R.drawable.ic_property_restaurant,
        "mall" to R.drawable.ic_property_mall,
        "bank" to R.drawable.ic_property_bank
    )
    private val randomTileIconRes = R.drawable.ic_tile_random

    // Dice faces
    private val diceDrawables = intArrayOf(
        R.drawable.dice_1, R.drawable.dice_2, R.drawable.dice_3,
        R.drawable.dice_4, R.drawable.dice_5, R.drawable.dice_6
    )

    // Views
    private lateinit var tvCoins: TextView
    private lateinit var tvDiceLeft: TextView
    private lateinit var tvEvent: TextView
    private lateinit var btnRollDice: Button
    private lateinit var btnAddDice: Button   // Back button
    private lateinit var boardGrid: GridLayout
    private lateinit var boardContainer: FrameLayout
    private lateinit var diceImageView: ImageView

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val drinkStorage by lazy { FirestoreDrinkStorage() }

    private var playerState = PlayerState()

    // Board tiles
    private val tileContainers = mutableListOf<LinearLayout>()
    private val tileIconViews = mutableListOf<ImageView>()
    private val tileEvents = mutableMapOf<Int, TileEventTemplate>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        supportActionBar?.hide()

        setContentView(R.layout.activity_game)
        initViews()

        // Layout board & dice after container measured
        boardContainer.post {
            val w = boardContainer.width
            val h = boardContainer.height
            val cell = min(w / 5, h / 8)

            val boardParams = boardGrid.layoutParams as FrameLayout.LayoutParams
            boardParams.width = cell * 5
            boardParams.height = cell * 8
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
        loadPlayerStateFromFirebase()

        btnRollDice.setOnClickListener { onRollDice() }

        // Back to main page
        btnAddDice.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnGameRanking.setOnClickListener {
            val intent = Intent(this, GameRankingActivity::class.java)
            startActivity(intent)
        }
    }

    private fun initViews() {
        tvCoins = findViewById(R.id.tvCoins)
        tvDiceLeft = findViewById(R.id.tvDiceLeft)
        tvEvent = findViewById(R.id.tvEvent)
        btnRollDice = findViewById(R.id.btnRollDice)
        btnAddDice = findViewById(R.id.btnAddDice)
        boardGrid = findViewById(R.id.boardGrid)
        boardContainer = findViewById(R.id.boardContainer)
        diceImageView = findViewById(R.id.diceImageView)
        btnGameRanking = findViewById(R.id.btnGameRanking)
    }

    // ---------------- BOARD ----------------

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
                setBackgroundResource(android.R.drawable.btn_default)
            }

            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 2f
                )
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageResource(randomTileIconRes)
            }

            val textView = TextView(this).apply {
                text = i.toString()
                textSize = 12f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
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

    // ---------------- EVENTS ----------------

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
                    failCoinDelta = -60000,
                    successRatePercent = 40
                ),
                EventChoice(
                    label = "Inspect carefully",
                    successDescription = "You find some coins safely.",
                    failDescription = "Mostly junk, some effort wasted.",
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
                    successDescription = "You pass smoothly.",
                    failDescription = "Extra fees are added.",
                    successCoinDelta = -12000,
                    failCoinDelta = -25000,
                    successRatePercent = 80
                ),
                EventChoice(
                    label = "Try to bargain",
                    successDescription = "You pay less.",
                    failDescription = "Heavy fine for wasting time.",
                    successCoinDelta = -5000,
                    failCoinDelta = -40000,
                    successRatePercent = 45
                )
            )
        )

        val lotteryEvent = TileEventTemplate(
            title = "Street Lottery",
            description = "A vendor invites you to buy a lottery ticket.",
            choices = listOf(
                EventChoice(
                    label = "Buy an expensive ticket",
                    successDescription = "Jackpot! Huge prize.",
                    failDescription = "No luck. Big loss.",
                    successCoinDelta = +60000,
                    failCoinDelta = -30000,
                    successRatePercent = 25
                ),
                EventChoice(
                    label = "Buy a cheap ticket",
                    successDescription = "Small prize.",
                    failDescription = "No win, small loss.",
                    successCoinDelta = +10000,
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
                    successDescription = "Big bonus!",
                    failDescription = "You mess up and get fined.",
                    successCoinDelta = +25000,
                    failCoinDelta = -20000,
                    successRatePercent = 55
                ),
                EventChoice(
                    label = "Work casually",
                    successDescription = "Easy pay.",
                    failDescription = "No pay today.",
                    successCoinDelta = +9000,
                    failCoinDelta = 0,
                    successRatePercent = 80
                )
            )
        )

        val shopEvent = TileEventTemplate(
            title = "Shopping Temptation",
            description = "A shop is having a big SALE.",
            choices = listOf(
                EventChoice(
                    label = "Buy a fancy item",
                    successDescription = "You resell it for profit.",
                    failDescription = "Terrible buy. Big loss.",
                    successCoinDelta = +25000,
                    failCoinDelta = -50000,
                    successRatePercent = 35
                ),
                EventChoice(
                    label = "Just browse",
                    successDescription = "You find a small bargain.",
                    failDescription = "Nothing interesting.",
                    successCoinDelta = +6000,
                    failCoinDelta = 0,
                    successRatePercent = 70
                )
            )
        )

        val templates = listOf(treasureEvent, tollEvent, lotteryEvent, jobEvent, shopEvent)
        tileEvents.clear()
        for (i in 0 until TOTAL_TILES) {
            tileEvents[i] = templates.random()
        }
    }

    // ---------------- PROPERTIES ----------------

    private fun randomizePropertyPositions() {
        propertyIdToTile.clear()
        tileToPropertyId.clear()

        val availableTiles = (0 until TOTAL_TILES).toMutableList()
        allProperties.forEach { property ->
            if (availableTiles.isEmpty()) return@forEach
            val idx = availableTiles.random()
            availableTiles.remove(idx)
            propertyIdToTile[property.id] = idx
            tileToPropertyId[idx] = property.id
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
                iconView.setImageResource(propertyIdToIconRes[propertyId] ?: randomTileIconRes)
            } else {
                iconView.setImageResource(randomTileIconRes)
            }
        }
    }

    // ---------------- TILE CLICK INFO ----------------

    private fun onTileClicked(tileIndex: Int) {
        // only allow when not in rolling/animating
        if (!btnRollDice.isEnabled) return

        val propertyId = tileToPropertyId[tileIndex]
        if (propertyId != null) {
            val info = getPropertyInfoById(propertyId) ?: return
            val owned = playerState.ownedProperties.contains(propertyId)

            val msg = buildString {
                append("Tile index: $tileIndex\n")
                append("Type: Property tile\n\n")
                append("Name: ${info.name}\n")
                append("Price: ${info.price} coins\n")
                append("Income per turn: ${info.incomePerTurn} coins\n")
                append("Owned: ${if (owned) "Yes" else "No"}\n")
                append("\nYour coins: ${playerState.coins}")
            }

            AlertDialog.Builder(this)
                .setTitle("Tile Info")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
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
                "Tile index: $tileIndex\nType: Random event tile\n\n(No template found.)"
            }

            AlertDialog.Builder(this)
                .setTitle("Tile Info")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // ---------------- GAME LOOP ----------------

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
                showPropertyDialog(newPosition, dice, propertyId, property)
            } else {
                val template = tileEvents[newPosition]
                showEventDialog(newPosition, dice, template)
            }
        }
    }

    private fun showPropertyDialog(
        newPosition: Int,
        dice: Int,
        propertyId: String,
        info: PropertyInfo
    ) {
        val alreadyOwned = playerState.ownedProperties.contains(propertyId)

        if (alreadyOwned) {
            endTurn(newPosition, dice, 0, "You visited your property: ${info.name}.", null)
            return
        }

        val message = buildString {
            append("You found a property for sale!\n\n")
            append("Name: ${info.name}\n")
            append("Price: ${info.price} coins\n")
            append("Income per turn: ${info.incomePerTurn} coins\n\n")
            append("Your coins: ${playerState.coins}")
        }

        AlertDialog.Builder(this)
            .setTitle("Buy Property?")
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton("Buy") { _, _ ->
                if (playerState.coins < info.price) {
                    Toast.makeText(this, "Not enough coins.", Toast.LENGTH_SHORT).show()
                    endTurn(newPosition, dice, 0, "You couldn't afford ${info.name}.", null)
                } else {
                    val newList = playerState.ownedProperties.toMutableList()
                    newList.add(propertyId)
                    endTurn(
                        newPosition,
                        dice,
                        -info.price,
                        "You bought ${info.name} for ${info.price} coins.",
                        newList
                    )
                }
            }
            .setNegativeButton("Skip") { _, _ ->
                endTurn(newPosition, dice, 0, "You skipped ${info.name}.", null)
            }
            .setOnCancelListener {
                endTurn(newPosition, dice, 0, "You walked away from ${info.name}.", null)
            }
            .show()
    }

    private fun showEventDialog(newPosition: Int, dice: Int, template: TileEventTemplate?) {
        if (template == null || template.choices.size < 2) {
            applyEventChoice(newPosition, dice, ResolvedEvent("Nothing happens.", 0))
            return
        }

        val c1 = template.choices[0]
        val c2 = template.choices[1]

        val message = buildString {
            append("You landed on tile $newPosition.\n\n")
            append(template.description)
            append("\n\n")

            append("Option 1: ${c1.label}\n")
            append("Success rate: ${c1.successRatePercent}%\n")
            append("On success: ${c1.successCoinDelta}\n")
            append("On fail: ${c1.failCoinDelta}\n\n")

            append("Option 2: ${c2.label}\n")
            append("Success rate: ${c2.successRatePercent}%\n")
            append("On success: ${c2.successCoinDelta}\n")
            append("On fail: ${c2.failCoinDelta}\n")
        }

        AlertDialog.Builder(this)
            .setTitle(template.title)
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton(c1.label) { _, _ ->
                applyEventChoice(newPosition, dice, resolveChoice(c1, template.title))
            }
            .setNegativeButton(c2.label) { _, _ ->
                applyEventChoice(newPosition, dice, resolveChoice(c2, template.title))
            }
            .setOnCancelListener {
                applyEventChoice(newPosition, dice, resolveChoice(c2, template.title))
            }
            .show()
    }

    private fun resolveChoice(choice: EventChoice, title: String): ResolvedEvent {
        val roll = Random.nextInt(0, 100)
        val success = roll < choice.successRatePercent
        return if (success) {
            ResolvedEvent(
                "$title - ${choice.label}: SUCCESS! ${choice.successDescription}",
                choice.successCoinDelta
            )
        } else {
            ResolvedEvent(
                "$title - ${choice.label}: FAILED. ${choice.failDescription}",
                choice.failCoinDelta
            )
        }
    }

    private fun applyEventChoice(newPosition: Int, dice: Int, result: ResolvedEvent) {
        endTurn(newPosition, dice, result.coinDelta, result.description, null)
    }

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
        val newDiceLeft = playerState.diceLeft - 1   // 用掉一个骰子
        val newDiceUsed = playerState.diceUsedToday + 1

        val finalDesc = buildString {
            append(description)
            if (propertyIncome != 0) append(" Property income: $propertyIncome.")
        }

        if (newCoinsRaw < 0) {
            handleGameOver(newPosition, dice, finalDesc, totalDelta, newCoinsRaw)
            return
        }

        playerState = PlayerState(
            coins = newCoinsRaw,
            position = newPosition,
            diceLeft = newDiceLeft,
            ownedProperties = owned,
            diceUsedToday = newDiceUsed
        )

        updateUI()
        savePlayerStateToFirebase()
        showTurnResultDialog(newPosition, dice, ResolvedEvent(finalDesc, totalDelta))
    }

    private fun calculatePropertyIncome(ownedIds: List<String>): Int {
        var total = 0
        ownedIds.forEach { id ->
            getPropertyInfoById(id)?.let { total += it.incomePerTurn }
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
        val message = """
            Rolled: $dice, moved to tile $newPosition.

            $description

            Coin change this turn: $totalDelta
            Your coins would become: $newCoinsRaw (< 0).

            Game over! You are bankrupt.
            A new game will start.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Game Over")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Start New Game") { d, _ ->
                d.dismiss()
                startNewGame()
            }
            .show()
    }

    private fun showTurnResultDialog(newPosition: Int, dice: Int, event: ResolvedEvent) {
        val message = """
            Rolled: $dice, moved to tile $newPosition.

            ${event.description}

            Coin change this turn: ${event.coinDelta}
            Total coins: ${playerState.coins}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Turn Result")
            .setMessage(message)
            .setPositiveButton("OK") { d, _ -> d.dismiss() }
            .setOnDismissListener { btnRollDice.isEnabled = true }
            .show()
    }

    // ---------------- DICE ANIMATION ----------------

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
        val scaleX = ObjectAnimator.ofFloat(diceImageView, "scaleX", 1f, 1.3f, 1f)
        val scaleY = ObjectAnimator.ofFloat(diceImageView, "scaleY", 1f, 1.3f, 1f)

        rotate.duration = duration
        scaleX.duration = duration
        scaleY.duration = duration

        AnimatorSet().apply {
            playTogether(rotate, scaleX, scaleY, frameAnimator)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    diceImageView.rotation = 0f
                    diceImageView.scaleX = 1f
                    diceImageView.scaleY = 1f
                    diceImageView.setImageResource(diceDrawables[finalDice - 1])
                    onEnd()
                }
            })
            start()
        }
    }

    // ---------------- UI & STATE ----------------

    private fun updateUI() {
        tvCoins.text = "Coins: ${playerState.coins}"
        tvDiceLeft.text = "Dice left: ${playerState.diceLeft}"
        tvEvent.text = ""
        updateBoardHighlight()
        updateTileIcons()
    }

    private fun updateBoardHighlight() {
        tileContainers.forEachIndexed { index, container ->
            if (index == playerState.position) {
                container.setBackgroundColor(0xFF00FF00.toInt())
            } else {
                container.setBackgroundResource(android.R.drawable.btn_default)
            }
        }
    }

    private fun startNewGame() {
        initTileEvents()
        randomizePropertyPositions()
        playerState = PlayerState(
            coins = 0,
            position = 0,
            ownedProperties = emptyList()
        )
        updateUI()
        savePlayerStateToFirebase()
        btnRollDice.isEnabled = true
    }

    // ======== 工具函数：月份 key & 每日骰子数量 ========

    private fun currentMonthKey(): Int {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return year * 100 + month      // 例如 202501
    }


    private fun currentDayKey(): Int {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return year * 10000 + month * 100 + day    // 例如 20251201
    }
    /**
     * 根据今天喝水进度计算骰子数量：
     * 每 20% 给 1 个，最多 5 个（0~5）
     */
    private suspend fun calcDiceFromWater(uid: String): Int {
        val todayTotalMl = drinkStorage.fetchTodayTotalIntake(uid)

        val userSnap = firestore.collection("users")
            .document(uid)
            .get()
            .await()

        val goalPerDay = (userSnap.getLong("goalMl")
            ?: userSnap.getLong("dailyGoalMl")
            ?: 2500L).toInt()

        if (goalPerDay <= 0) return 0

        val percent = ((todayTotalMl.toDouble() / goalPerDay) * 100.0)
            .toInt()
            .coerceIn(0, 100)

        // 每 20% 一个骰子，最多 5 个
        return (percent / 20).coerceIn(0, 5)
    }

    // ---------------- FIREBASE ----------------

    private fun loadPlayerStateFromFirebase() {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        if (myUid == null) {
            startNewGame()
            return
        }

        firestore.collection(COLLECTION_NAME)
            .document(myUid)
            .get()
            .addOnSuccessListener { snapshot ->
                lifecycleScope.launch {
                    val monthKeyNow = currentMonthKey()
                    val lastResetMonth = snapshot.getLong("lastResetMonth")?.toInt()
                    val needMonthlyReset =
                        !snapshot.exists() || lastResetMonth == null || lastResetMonth != monthKeyNow

                    // 1) 处理棋盘布局（月度重置）
                    if (needMonthlyReset) {
                        initTileEvents()
                        randomizePropertyPositions()
                    } else {
                        // 恢复 propertyAssignments
                        val assignmentsAny = snapshot.get("propertyAssignments") as? Map<*, *>
                        if (assignmentsAny != null && assignmentsAny.isNotEmpty()) {
                            propertyIdToTile.clear()
                            tileToPropertyId.clear()
                            assignmentsAny.forEach { (k, v) ->
                                val id = k as? String ?: return@forEach
                                val tileIndex = (v as? Long)?.toInt() ?: return@forEach
                                propertyIdToTile[id] = tileIndex
                                tileToPropertyId[tileIndex] = id
                            }
                            updateTileIcons()
                        } else {
                            randomizePropertyPositions()
                        }
                    }

                    // 2) 基本状态（金币 / 位置 / 房产）
                    val coins = if (needMonthlyReset) 0
                    else snapshot.getLong("coins")?.toInt() ?: 0

                    val position = if (needMonthlyReset) 0
                    else snapshot.getLong("position")?.toInt() ?: 0

                    val ownedProps =
                        if (needMonthlyReset) emptyList()
                        else {
                            val ownedPropsAny = snapshot.get("ownedProperties") as? List<*>
                            ownedPropsAny?.mapNotNull { it as? String } ?: emptyList()
                        }

                    // 3) 计算今天“理论上最多可以获得”的骰子数量（0~5）
                    val diceFromWater = calcDiceFromWater(myUid)

                    // Firestore 里上次保存的骰子信息
                    val savedDiceLeft = snapshot.getLong("diceLeft")?.toInt() ?: 0
                    val lastDiceDayKey = snapshot.getLong("lastDiceDayKey")?.toInt()
                    val todayKey = currentDayKey()

                    // 4) 决定这次进入游戏时应该还有多少骰子
                    val diceLeft = when {
                        // 新月份：重新开始，当天按喝水给骰子
                        needMonthlyReset -> diceFromWater

                        // 文档不存在：第一次玩小游戏
                        !snapshot.exists() -> diceFromWater

                        // 新的一天：按喝水重新计算今天可用骰子
                        lastDiceDayKey == null || lastDiceDayKey != todayKey -> diceFromWater

                        // 同一天：保留剩余骰子，但不超过今天理论最大值
                        else -> savedDiceLeft.coerceAtMost(diceFromWater)
                    }

                    playerState = PlayerState(
                        coins = coins,
                        position = position,
                        diceLeft = diceLeft,
                        ownedProperties = ownedProps
                    )

                    updateUI()
                    savePlayerStateToFirebase()  // 会顺便写入 lastDiceDayKey
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load data: ${it.message}", Toast.LENGTH_SHORT)
                    .show()
                startNewGame()
            }
    }


    private fun savePlayerStateToFirebase() {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val monthKey = currentMonthKey()
        val dayKey = currentDayKey()

        val data = hashMapOf(
            "uid" to myUid,
            "coins" to playerState.coins,
            "position" to playerState.position,
            "diceLeft" to playerState.diceLeft,
            "ownedProperties" to playerState.ownedProperties,
            "propertyAssignments" to propertyIdToTile,
            "lastResetMonth" to monthKey,
            "lastDiceDayKey" to dayKey          // ⬅ 新增：记录骰子属于哪一天
        )

        firestore.collection(COLLECTION_NAME)
            .document(myUid)
            .set(data)
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Failed to save data: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }


}
