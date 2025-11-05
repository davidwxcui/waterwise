package com.davidwxcui.waterwise.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.davidwxcui.waterwise.data.DrinkLog
import com.davidwxcui.waterwise.data.DrinkType
import kotlin.math.max
import kotlin.math.min

class HomeViewModel : ViewModel() {

    // 配置：目标与上限
    private val weightKg = 70
    private val dailyCoeff = 35  // ml/kg 目标
    private val limitCoeff = 60  // ml/kg 上限

    // 各饮品折算系数
    private val factor = mapOf(
        DrinkType.Water to 1.0,
        DrinkType.Tea to 0.9,
        DrinkType.Coffee to 0.8,
        DrinkType.Juice to 0.9,
        DrinkType.Soda to 0.85,
        DrinkType.Milk to 0.9,
        DrinkType.Yogurt to 0.9,
        DrinkType.Alcohol to 0.7,
        DrinkType.Sparkling to 1.0
    )

    // 默认份量（全部 50 的倍数）
    private val defaultPortions = mapOf(
        DrinkType.Water to listOf(200, 250, 500),
        DrinkType.Tea to listOf(200, 300),
        DrinkType.Coffee to listOf(150, 250),
        DrinkType.Juice to listOf(200, 300),
        DrinkType.Soda to listOf(200, 350),
        DrinkType.Milk to listOf(200, 300),
        DrinkType.Yogurt to listOf(200, 300),
        DrinkType.Alcohol to listOf(150, 350),
        DrinkType.Sparkling to listOf(200, 350)
    )

    private val _timeline = MutableLiveData<List<DrinkLog>>(emptyList())
    val timeline: LiveData<List<DrinkLog>> = _timeline

    data class UIState(
        val intakeMl: Int,
        val effectiveMl: Int,
        val goalMl: Int,
        val limitMl: Int,
        val overLimit: Boolean,
        val caffeineRatio: Double,
        val importantEvent: ImportantEvent?
    )

    data class ImportantEvent(val name: String, val daysToEvent: Int, val todayTip: String)

    private val _uiState = MutableLiveData(
        UIState(
            intakeMl = 0,
            effectiveMl = 0,
            goalMl = weightKg * dailyCoeff,
            limitMl = weightKg * limitCoeff,
            overLimit = false,
            caffeineRatio = 0.0,
            importantEvent = ImportantEvent("10K Run", 3, "Tip: +300 ml water today")
        )
    )
    val uiState: LiveData<UIState> = _uiState

    data class Summary(val waterRatio: Double, val caffeineRatio: Double, val sugaryRatio: Double)
    private val _summary = MutableLiveData(Summary(0.0, 0.0, 0.0))
    val summary: LiveData<Summary> = _summary

    private var idSeed = 1
    private val lastByType: MutableMap<DrinkType, Int> = mutableMapOf()

    fun defaultPortionsFor(t: DrinkType): List<Int> = defaultPortions[t] ?: listOf(200, 250, 500)

    fun addSameAsLast(type: DrinkType) {
        val v = lastByType[type]
        if (v != null) addDrink(type, v)
    }

    fun addDrink(type: DrinkType, volumeMl: Int) {
        val rounded = max(50, (volumeMl / 50) * 50)
        val eff = (rounded * (factor[type] ?: 1.0)).toInt()
        val new = DrinkLog(
            id = idSeed++,
            type = type,
            volumeMl = rounded,
            effectiveMl = eff,
            note = null,
            timeMillis = System.currentTimeMillis()
        )
        lastByType[type] = rounded
        val list = listOf(new) + (_timeline.value ?: emptyList())
        _timeline.value = list
        recompute(list)
    }

    fun deleteDrink(id: Int) {
        val list = (_timeline.value ?: emptyList()).filterNot { it.id == id }
        _timeline.value = list
        recompute(list)
    }

    fun editDrink(item: DrinkLog) {
        val updated = item.copy(
            volumeMl = item.volumeMl + 50,
            effectiveMl = ((item.volumeMl + 50) * (factor[item.type] ?: 1.0)).toInt()
        )
        val list = (_timeline.value ?: emptyList()).map { if (it.id == item.id) updated else it }
        _timeline.value = list
        lastByType[item.type] = updated.volumeMl
        recompute(list)
    }

    private fun recompute(list: List<DrinkLog>) {
        val intake = list.sumOf { it.volumeMl }
        val effective = list.sumOf { it.effectiveMl }
        val goal = weightKg * dailyCoeff
        val limit = weightKg * limitCoeff
        val over = intake > limit

        val totalEff = max(1, effective)
        val caffeineEff = list.filter { it.type == DrinkType.Coffee || it.type == DrinkType.Tea }
            .sumOf { it.effectiveMl }.toDouble() / totalEff
        val sugaryEff = list.filter { it.type == DrinkType.Juice || it.type == DrinkType.Soda || it.type == DrinkType.Yogurt }
            .sumOf { it.effectiveMl }.toDouble() / totalEff
        val waterEff = min(1.0, 1.0 - caffeineEff - sugaryEff)

        _uiState.value = _uiState.value?.copy(
            intakeMl = intake,
            effectiveMl = effective,
            goalMl = goal,
            limitMl = limit,
            overLimit = over,
            caffeineRatio = caffeineEff
        )

        _summary.value = Summary(
            waterRatio = waterEff.coerceIn(0.0, 1.0),
            caffeineRatio = caffeineEff.coerceIn(0.0, 1.0),
            sugaryRatio = sugaryEff.coerceIn(0.0, 1.0)
        )
    }

    fun recommendNextMl(): Int {
        val st = _uiState.value ?: return 250
        val remaining = (st.goalMl - st.intakeMl).coerceAtLeast(0)
        return when {
            remaining >= 500 -> 500
            remaining >= 300 -> 300
            remaining >= 250 -> 250
            remaining >= 200 -> 200
            remaining == 0 -> 0
            else -> 200
        }
    }
}
