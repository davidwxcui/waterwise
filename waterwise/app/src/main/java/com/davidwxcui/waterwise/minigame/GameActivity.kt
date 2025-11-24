package com.davidwxcui.waterwise.minigame

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.davidwxcui.waterwise.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.min
import kotlin.random.Random

// ================== DATA MODELS ==================

data class PlayerState(
    val coins: Int = 100000,              // 初始金币调高到 100000
    val position: Int = 0,
    val diceLeft: Int = 20,
    val ownedProperties: List<String> = emptyList() // list of property IDs
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
        private const val DOCUMENT_ID = "player1"
    }

    // 8x5 outer ring, 22 tiles, starting from top-left going clockwise
    private val tilePositions = listOf(
        // Top row (5)
        0 to 0,  // 0
        0 to 1,  // 1
        0 to 2,  // 2
        0 to 3,  // 3
        0 to 4,  // 4

        // Right column (7)
        1 to 4,  // 5
        2 to 4,  // 6
        3 to 4,  // 7
        4 to 4,  // 8
        5 to 4,  // 9
        6 to 4,  // 10
        7 to 4,  // 11

        // Bottom row (4)
        7 to 3,  // 12
        7 to 2,  // 13
        7 to 1,  // 14
        7 to 0,  // 15

        // Left column (6)
        6 to 0,  // 16
        5 to 0,  // 17
        4 to 0,  // 18
        3 to 0,  // 19
        2 to 0,  // 20
        1 to 0   // 21
    )

    // 所有产业类型（金额还是×100，但整体更贵，收益更低，回本更慢）
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
    private lateinit var btnAddDice: Button
    private lateinit var boardGrid: GridLayout
    private lateinit var boardContainer: FrameLayout
    private lateinit var diceImageView: ImageView

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private var playerState = PlayerState()

    // 棋盘格子视图：容器+图标
    private val tileContainers = mutableListOf<LinearLayout>()
    private val tileIconViews = mutableListOf<ImageView>()

    private val tileEvents = mutableMapOf<Int, TileEventTemplate>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        initViews()

        // Layout board & dice based on container size
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

        // 临时：一键加 1000 次骰子
        btnAddDice.setOnClickListener {
            playerState = playerState.copy(
                diceLeft = playerState.diceLeft + 1000
            )
            updateUI()
            savePlayerStateToFirebase()
            Toast.makeText(this, "Added 1000 dice!", Toast.LENGTH_SHORT).show()
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
    }

    // ---------------- BOARD & EVENTS ----------------

    private fun initBoard() {
        tileContainers.clear()
        tileIconViews.clear()
        boardGrid.removeAllViews()
        boardGrid.rowCount = 8
        boardGrid.columnCount = 5

        for (i in 0 until TOTAL_TILES) {
            // 外层容器
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundResource(android.R.drawable.btn_default)
            }

            // 图标
            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    2f
                )
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setImageResource(randomTileIconRes) // 默认问号
            }

            // 底部数字
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

            // 点击格子查看详情（只有在没有事件进行时）
            container.setOnClickListener {
                onTileClicked(i)
            }

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

    /** 事件模板：这里重做了数值 & 概率，整体负面更狠，正面更难 / 更小 */
    private fun initTileEvents() {
        val treasureEvent = TileEventTemplate(
            title = "Treasure Chest",
            description = "You discover a mysterious treasure chest on the road.",
            choices = listOf(
                // 高风险高收益：期望值略负
                EventChoice(
                    label = "Open it boldly",
                    successDescription = "The chest is full of gold!",
                    failDescription = "It was a trap! A blast scares you and you drop a lot of coins.",
                    successCoinDelta = +40000,
                    failCoinDelta = -60000,
                    successRatePercent = 40
                ),
                // 稍微保守一点：期望值略正
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
                // 乖乖付：必亏，但损失稳定
                EventChoice(
                    label = "Pay the full toll",
                    successDescription = "You pass smoothly and feel relieved.",
                    failDescription = "The officer still finds a problem and adds extra fees.",
                    successCoinDelta = -12000,  // 常规损失
                    failCoinDelta = -25000,     // 偶尔更狠
                    successRatePercent = 80
                ),
                // 讨价还价：期望值更负
                EventChoice(
                    label = "Try to bargain",
                    successDescription = "You negotiate successfully and pay less.",
                    failDescription = "Negotiation fails, heavy fine is added.",
                    successCoinDelta = -5000,
                    failCoinDelta = -40000,
                    successRatePercent = 45
                )
            )
        )

        val lotteryEvent = TileEventTemplate(
            title = "Street Lottery",
            description = "A street vendor invites you to buy a lottery ticket.",
            choices = listOf(
                // 豪赌：大正、大负、成功率低
                EventChoice(
                    label = "Buy an expensive ticket",
                    successDescription = "Jackpot! You win a huge prize.",
                    failDescription = "No luck at all. You burn a pile of money.",
                    successCoinDelta = +60000,
                    failCoinDelta = -30000,
                    successRatePercent = 25
                ),
                // 小赌：轻盈一些
                EventChoice(
                    label = "Buy a cheap ticket",
                    successDescription = "You win a small prize.",
                    failDescription = "No winning number, you still lose some cash.",
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
                // 加班：期望略正，但有可能掉很多
                EventChoice(
                    label = "Work overtime",
                    successDescription = "You work hard and get a big bonus.",
                    failDescription = "You get exhausted and make mistakes. Pay is heavily deducted.",
                    successCoinDelta = +25000,
                    failCoinDelta = -20000,
                    successRatePercent = 55
                ),
                // 摸鱼班：小正面，几乎无风险
                EventChoice(
                    label = "Work casually",
                    successDescription = "Easy shift, you still get some money.",
                    failDescription = "Business is slow, no pay at all.",
                    successCoinDelta = +9000,
                    failCoinDelta = 0,
                    successRatePercent = 80
                )
            )
        )

        val shopEvent = TileEventTemplate(
            title = "Shopping Temptation",
            description = "You walk by a shop with a big SALE sign.",
            choices = listOf(
                // 冲动消费：大亏风险 + 中等奖励
                EventChoice(
                    label = "Buy a fancy item",
                    successDescription = "It turns out to be valuable, you resell it later.",
                    failDescription = "Impulse purchase. It wasn't worth the price at all.",
                    successCoinDelta = +25000,
                    failCoinDelta = -50000,
                    successRatePercent = 35
                ),
                // 围观：小赚 or 持平
                EventChoice(
                    label = "Just browse",
                    successDescription = "You find a small bargain.",
                    failDescription = "Nothing interesting, but you keep all your money.",
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

    /** 随机分配产业格位置（新局时调用） */
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

    /** 根据当前的产业位置，为每个格子设置对应图标（产业/随机） */
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

    // ---------------- TILE CLICK INFO ----------------

    /** 点击格子，弹出该格子的详细信息（只有在没有事件/动画时才响应） */
    private fun onTileClicked(tileIndex: Int) {
        // 如果当前在动画/事件流程中（Roll Dice 按钮禁用），就不处理
        if (!btnRollDice.isEnabled) {
            return
        }

        val propertyId = tileToPropertyId[tileIndex]
        if (propertyId != null) {
            // 产业格信息
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
            // 随机事件格信息
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

    // ----- Property event -----

    private fun showPropertyDialog(
        newPosition: Int,
        dice: Int,
        propertyId: String,
        info: PropertyInfo
    ) {
        val alreadyOwned = playerState.ownedProperties.contains(propertyId)

        if (alreadyOwned) {
            val desc = "You visited your property: ${info.name}."
            endTurn(
                newPosition = newPosition,
                dice = dice,
                baseCoinDelta = 0,
                description = desc,
                overrideOwnedProperties = null
            )
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
                    val desc = "You bought property: ${info.name} for ${info.price} coins."
                    endTurn(newPosition, dice, -info.price, desc, newList)
                }
            }
            .setNegativeButton("Skip") { _, _ ->
                val desc = "You decided not to buy ${info.name}."
                endTurn(newPosition, dice, 0, desc, null)
            }
            .setOnCancelListener {
                val desc = "You hesitated and walked away from ${info.name}."
                endTurn(newPosition, dice, 0, desc, null)
            }
            .show()
    }

    // ----- Normal event -----

    private fun showEventDialog(newPosition: Int, dice: Int, template: TileEventTemplate?) {
        if (template == null || template.choices.size < 2) {
            val result = ResolvedEvent("Nothing happens on this tile.", 0)
            applyEventChoice(newPosition, dice, result)
            return
        }

        val choice1 = template.choices[0]
        val choice2 = template.choices[1]

        val message = buildString {
            append("You landed on tile $newPosition.\n\n")
            append(template.description)
            append("\n\n")

            append("Option 1: ${choice1.label}\n")
            append("Success rate: ${choice1.successRatePercent}%\n")
            append("On success: ${choice1.successCoinDelta} coins\n")
            append("On fail: ${choice1.failCoinDelta} coins\n\n")

            append("Option 2: ${choice2.label}\n")
            append("Success rate: ${choice2.successRatePercent}%\n")
            append("On success: ${choice2.successCoinDelta} coins\n")
            append("On fail: ${choice2.failCoinDelta} coins")
        }

        AlertDialog.Builder(this)
            .setTitle(template.title)
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton(choice1.label) { _, _ ->
                val result = resolveChoice(choice1, template.title)
                applyEventChoice(newPosition, dice, result)
            }
            .setNegativeButton(choice2.label) { _, _ ->
                val result = resolveChoice(choice2, template.title)
                applyEventChoice(newPosition, dice, result)
            }
            .setOnCancelListener {
                val result = resolveChoice(choice2, template.title)
                applyEventChoice(newPosition, dice, result)
            }
            .show()
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
        endTurn(
            newPosition = newPosition,
            dice = dice,
            baseCoinDelta = result.coinDelta,
            description = result.description,
            overrideOwnedProperties = null
        )
    }

    // ----- Turn end + bankruptcy check -----

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

        // Bankruptcy check BEFORE we clamp coins
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
        savePlayerStateToFirebase()
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

    /** When coins would go < 0, end current game and start a new one */
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
            append("A new game will start.")
        }

        AlertDialog.Builder(this)
            .setTitle("Game Over")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Start New Game") { dialog, _ ->
                dialog.dismiss()
                startNewGame()
            }
            .show()
    }

    /** Normal turn result dialog (no bankruptcy) */
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

        AlertDialog.Builder(this)
            .setTitle("Turn Result")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener {
                btnRollDice.isEnabled = true
            }
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
        tvEvent.text = "" // no log at bottom
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

    /** Start a completely NEW GAME: reset player & randomize properties */
    private fun startNewGame() {
        initTileEvents()
        randomizePropertyPositions()
        playerState = PlayerState(
            coins = 100000,       // 和 PlayerState 默认保持一致
            position = 0,
            diceLeft = 20,
            ownedProperties = emptyList()
        )
        updateUI()
        savePlayerStateToFirebase()
        btnRollDice.isEnabled = true
    }

    // ---------------- FIREBASE ----------------

    private fun loadPlayerStateFromFirebase() {
        firestore.collection(COLLECTION_NAME)
            .document(DOCUMENT_ID)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val coins = snapshot.getLong("coins")?.toInt() ?: 100000
                    val position = snapshot.getLong("position")?.toInt() ?: 0
                    val diceLeft = snapshot.getLong("diceLeft")?.toInt() ?: 20

                    val ownedPropsAny = snapshot.get("ownedProperties") as? List<*>
                    val ownedProps = ownedPropsAny
                        ?.mapNotNull { it as? String }
                        ?: emptyList()

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
                        // No property info stored -> treat as new game
                        startNewGame()
                        return@addOnSuccessListener
                    }

                    playerState = PlayerState(coins, position, diceLeft, ownedProps)
                    updateUI()
                } else {
                    // No save yet -> new game
                    startNewGame()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load data: ${it.message}", Toast.LENGTH_SHORT).show()
                startNewGame()
            }
    }

    private fun savePlayerStateToFirebase() {
        val data = hashMapOf(
            "coins" to playerState.coins,
            "position" to playerState.position,
            "diceLeft" to playerState.diceLeft,
            "ownedProperties" to playerState.ownedProperties,
            "propertyAssignments" to propertyIdToTile
        )

        firestore.collection(COLLECTION_NAME)
            .document(DOCUMENT_ID)
            .set(data)
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save data: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
