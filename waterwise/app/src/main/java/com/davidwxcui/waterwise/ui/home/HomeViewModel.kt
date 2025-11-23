package com.davidwxcui.waterwise.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.davidwxcui.waterwise.data.DrinkLog
import com.davidwxcui.waterwise.data.DrinkType
import com.davidwxcui.waterwise.data.FirestoreDrinkStorage
import com.davidwxcui.waterwise.ui.profile.HydrationFormula
import com.davidwxcui.waterwise.ui.profile.ProfilePrefs
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.max
import kotlin.math.min

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = FirestoreDrinkStorage()

    // For log in，暂时使用固定 uid；登录做完后换成 FirebaseAuth.getInstance().currentUser!!.uid
    private val uid = "local-test-user"

    private val profile = ProfilePrefs.load(application)
    private val dailyGoalMl = HydrationFormula.dailyGoalMl(
        profile.weightKg.toFloat(),
        profile.sex,
        profile.age,
        profile.activity
    )
    private val limitCoeff = 60

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
            goalMl = dailyGoalMl,
            limitMl = profile.weightKg * limitCoeff,
            overLimit = false,
            caffeineRatio = 0.0,
            importantEvent = ImportantEvent("10K Run", 3, "Tip: +300 ml water today")
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
    private val lastByType = mutableMapOf<DrinkType, Int>()


    private val registration = storage.listenDrinkLogs(
        uid = uid,
        onUpdate = { list ->
            val ordered = list.sortedByDescending { it.timeMillis }
            _timeline.postValue(ordered)

            idSeed = (ordered.maxOfOrNull { it.id } ?: 0) + 1
            ordered.forEach { lastByType[it.type] = it.volumeMl }

            recompute(ordered)
        }
    )


    override fun onCleared() {
        super.onCleared()
        registration.remove()
    }


    fun defaultPortionsFor(t: DrinkType): List<Int> =
        defaultPortions[t] ?: listOf(200, 250, 500)


    fun addSameAsLast(type: DrinkType) {
        val v = lastByType[type] ?: defaultPortionsFor(type).getOrNull(1) ?: 200
        addDrink(type, v)
    }


    fun addDrink(type: DrinkType, volumeMl: Int) {
        val rounded = max(50, (volumeMl / 50) * 50)
        val eff = (rounded * (factor[type] ?: 1.0)).toInt()

        val log = DrinkLog(
            id = idSeed++,
            type = type,
            volumeMl = rounded,
            effectiveMl = eff,
            note = null,
            timeMillis = System.currentTimeMillis()
        )

        lastByType[type] = rounded

        storage.addDrinkLog(uid, log)
    }


    fun deleteDrink(id: Int) {
        val list = _timeline.value ?: return
        val target = list.find { it.id == id } ?: return
        storage.deleteDrinkLog(uid, target)
    }


    fun editDrink(item: DrinkLog) {
        updateDrinkVolume(item.id, item.volumeMl + 50)
    }


    fun updateDrinkVolume(id: Int, newVolumeMl: Int) {
        val list = _timeline.value ?: return
        val target = list.find { it.id == id } ?: return

        val rounded = max(50, (newVolumeMl / 50) * 50)
        val eff = (rounded * (factor[target.type] ?: 1.0)).toInt()

        val updated = target.copy(
            volumeMl = rounded,
            effectiveMl = eff
        )

        lastByType[target.type] = rounded

        storage.updateDrinkLog(uid, updated)
    }


    private fun recompute(list: List<DrinkLog>) {
        val intake = list.sumOf { it.volumeMl }
        val effective = list.sumOf { it.effectiveMl }
        val goal = dailyGoalMl
        val limit = profile.weightKg * limitCoeff

        val totalEff = max(1, effective)
        val caffeineEff = list
            .filter { it.type == DrinkType.Coffee || it.type == DrinkType.Tea }
            .sumOf { it.effectiveMl }.toDouble() / totalEff
        val sugaryEff = list
            .filter { it.type == DrinkType.Juice || it.type == DrinkType.Soda || it.type == DrinkType.Yogurt }
            .sumOf { it.effectiveMl }.toDouble() / totalEff

        val waterEff = min(1.0, 1.0 - caffeineEff - sugaryEff)

        _uiState.postValue(
            _uiState.value?.copy(
                intakeMl = intake,
                effectiveMl = effective,
                goalMl = goal,
                limitMl = limit,
                overLimit = intake > limit,
                caffeineRatio = caffeineEff
            )
        )

        _summary.postValue(
            Summary(
                waterRatio = waterEff,
                caffeineRatio = caffeineEff,
                sugaryRatio = sugaryEff
            )
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
