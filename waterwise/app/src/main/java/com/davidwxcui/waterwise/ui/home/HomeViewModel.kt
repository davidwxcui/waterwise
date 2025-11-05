package com.davidwxcui.waterwise.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.davidwxcui.waterwise.data.CsvDrinkStorage
import com.davidwxcui.waterwise.data.DrinkLog
import com.davidwxcui.waterwise.data.DrinkType
import kotlin.math.max
import kotlin.math.min

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    // 本地 CSV 存储：/data/data/com.davidwxcui.waterwise/files/drinkLog.csv
    private val storage = CsvDrinkStorage(getApplication())

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

    data class ImportantEvent(
        val name: String,
        val daysToEvent: Int,
        val todayTip: String
    )

    private val _uiState = MutableLiveData(
        UIState(
            intakeMl = 0,
            effectiveMl = 0,
            goalMl = weightKg * dailyCoeff,
            limitMl = weightKg * limitCoeff,
            overLimit = false,
            caffeineRatio = 0.0,
            importantEvent = ImportantEvent(
                "10K Run",
                3,
                "Tip: +300 ml water today"
            )
        )
    )
    val uiState: LiveData<UIState> = _uiState

    data class Summary(
        val waterRatio: Double,
        val caffeineRatio: Double,
        val sugaryRatio: Double
    )

    private val _summary = MutableLiveData(Summary(0.0, 0.0, 0.0))
    val summary: LiveData<Summary> = _summary

    private var idSeed = 1
    private val lastByType: MutableMap<DrinkType, Int> = mutableMapOf()

    init {
        // 启动时从 CSV 读所有记录
        val loaded = storage.loadAll()
            .sortedByDescending { it.timeMillis }
            .mapIndexed { index, log ->
                log.copy(id = index + 1)
            }

        _timeline.value = loaded
        idSeed = (loaded.maxOfOrNull { it.id } ?: 0) + 1

        // 恢复“和上次一样”的缓存
        loaded.forEach { log ->
            lastByType[log.type] = log.volumeMl
        }

        recompute(loaded)
    }

    fun defaultPortionsFor(t: DrinkType): List<Int> =
        defaultPortions[t] ?: listOf(200, 250, 500)

    fun addSameAsLast(type: DrinkType) {
        val v = lastByType[type]
        if (v != null) {
            addDrink(type, v)
        } else {
            val mid = defaultPortionsFor(type).getOrNull(1) ?: 200
            addDrink(type, mid)
        }
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
        val finalList = list.sortedByDescending { it.timeMillis }

        _timeline.value = finalList
        recompute(finalList)
        storage.saveAll(finalList)
    }

    fun deleteDrink(id: Int) {
        val list = _timeline.value ?: emptyList()
        val finalList = list.filterNot { it.id == id }
        _timeline.value = finalList
        recompute(finalList)
        storage.saveAll(finalList)
    }

    /**
     * 原来你是直接 +50ml 的，这里先保留，给时间线以外的地方用。
     * 真正的弹窗编辑用下面的 updateDrinkVolume。
     */
    fun editDrink(item: DrinkLog) {
        updateDrinkVolume(item.id, item.volumeMl + 50)
    }

    /**
     * ✅ 按指定 ml 更新一条记录，弹窗要用这个
     */
    fun updateDrinkVolume(id: Int, newVolumeMl: Int) {
        val current = _timeline.value.orEmpty().toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx == -1) return

        val old = current[idx]
        val rounded = max(50, (newVolumeMl / 50) * 50)
        val eff = (rounded * (factor[old.type] ?: 1.0)).toInt()
        val updated = old.copy(
            volumeMl = rounded,
            effectiveMl = eff
        )
        current[idx] = updated

        val finalList = current.sortedByDescending { it.timeMillis }
        _timeline.value = finalList

        // 更新“和上次一样”
        lastByType[old.type] = rounded

        recompute(finalList)
        storage.saveAll(finalList)
    }

    private fun recompute(list: List<DrinkLog>) {
        val intake = list.sumOf { it.volumeMl }
        val effective = list.sumOf { it.effectiveMl }
        val goal = weightKg * dailyCoeff
        val limit = weightKg * limitCoeff
        val over = intake > limit

        val totalEff = max(1, effective)
        val caffeineEff = list
            .filter { it.type == DrinkType.Coffee || it.type == DrinkType.Tea }
            .sumOf { it.effectiveMl }.toDouble() / totalEff
        val sugaryEff = list
            .filter {
                it.type == DrinkType.Juice ||
                        it.type == DrinkType.Soda ||
                        it.type == DrinkType.Yogurt
            }
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
