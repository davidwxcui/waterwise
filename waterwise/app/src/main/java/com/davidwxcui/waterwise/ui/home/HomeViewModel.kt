package com.davidwxcui.waterwise.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.davidwxcui.waterwise.data.DrinkLog
import com.davidwxcui.waterwise.data.DrinkType
import com.davidwxcui.waterwise.data.FirestoreDrinkStorage
import com.davidwxcui.waterwise.ui.profile.FirebaseAuthRepository
import com.davidwxcui.waterwise.ui.profile.HydrationFormula
import com.davidwxcui.waterwise.ui.profile.Profile
import com.davidwxcui.waterwise.ui.profile.ProfilePrefs
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = FirestoreDrinkStorage()
    private val db = FirebaseFirestore.getInstance()

    private val uid: String?
        get() = FirebaseAuthRepository.currentUid()

    // Load profile
    private var currentProfile: Profile = ProfilePrefs.load(application)

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

    private val _activeRoomId = MutableLiveData<String?>(null)
    val activeRoomId: LiveData<String?> = _activeRoomId

    private fun computeDailyGoalMl(): Int {
        return HydrationFormula.dailyGoalMl(
            currentProfile.weightKg.toFloat(),
            currentProfile.sex,
            currentProfile.age,
            currentProfile.activity
        )
    }

    private fun computeLimitMl(): Int {
        return currentProfile.weightKg * limitCoeff
    }

    private val _uiState = MutableLiveData(
        UIState(
            intakeMl = 0,
            effectiveMl = 0,
            goalMl = computeDailyGoalMl(),
            limitMl = computeLimitMl(),
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

    private var drinkReg: ListenerRegistration? = null
    private var profileReg: ListenerRegistration? = null

    init {
        startListenDrinkLogs()
        startListenProfile()
        refreshDrinkLogsOnce()
    }

    fun refreshListeners() {
        startListenDrinkLogs()
        startListenProfile()
        refreshDrinkLogsOnce()
    }

    private fun startListenDrinkLogs() {
        val realUid = uid ?: run {
            Log.w("HomeViewModel", "startListenDrinkLogs skipped: uid=null")
            return
        }

        drinkReg?.remove()
        drinkReg = storage.listenDrinkLogs(
            uid = realUid,
            onUpdate = { list ->
                val ordered = list.sortedByDescending { it.timeMillis }
                _timeline.postValue(ordered)

                idSeed = (ordered.maxOfOrNull { it.id } ?: 0) + 1
                ordered.forEach { lastByType[it.type] = it.volumeMl }

                recompute(ordered)
            },
            onError = { e ->
                Log.e("HomeViewModel", "listenDrinkLogs error", e)
            }
        )
    }

    private fun refreshDrinkLogsOnce() {
        val realUid = uid ?: return
        viewModelScope.launch {
            try {
                val list = storage.fetchDrinkLogsOnce(realUid)
                val ordered = list.sortedByDescending { it.timeMillis }
                _timeline.postValue(ordered)

                idSeed = (ordered.maxOfOrNull { it.id } ?: 0) + 1
                ordered.forEach { lastByType[it.type] = it.volumeMl }

                recompute(ordered)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "refreshDrinkLogsOnce failed", e)
            }
        }
    }

    private fun startListenProfile() {
        val realUid = uid ?: run {
            Log.w("HomeViewModel", "startListenProfile skipped: uid=null")
            return
        }

        profileReg?.remove()
        profileReg = db.collection("users").document(realUid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
                val ar = snap.getString("activeRoomId")
                val r = snap.getString("roomId")
                _activeRoomId.postValue(ar ?: r)

                // refresh Profile
                val sexStr = snap.getString("sex")
                val actStr = snap.getString("activityLevel")

                val newProfile = currentProfile.copy(
                    name = snap.getString("name") ?: currentProfile.name,
                    email = snap.getString("email") ?: currentProfile.email,
                    age = (snap.getLong("age") ?: currentProfile.age.toLong()).toInt(),
                    heightCm = (snap.getLong("heightCm") ?: currentProfile.heightCm.toLong()).toInt(),
                    weightKg = (snap.getLong("weightKg") ?: currentProfile.weightKg.toLong()).toInt(),
                    activityFreqLabel = snap.getString("activityFreqLabel") ?: currentProfile.activityFreqLabel,
                    avatarUri = snap.getString("avatarUri") ?: currentProfile.avatarUri,
                    sex = try {
                        if (sexStr != null) currentProfile.sex::class.java.enumConstants
                            ?.firstOrNull { it.name == sexStr } ?: currentProfile.sex
                        else currentProfile.sex
                    } catch (_: Exception) { currentProfile.sex },
                    activity = try {
                        if (actStr != null) currentProfile.activity::class.java.enumConstants
                            ?.firstOrNull { it.name == actStr } ?: currentProfile.activity
                        else currentProfile.activity
                    } catch (_: Exception) { currentProfile.activity }
                )

                // Update local profile cache
                currentProfile = newProfile
                ProfilePrefs.save(getApplication(), newProfile)
                recompute(_timeline.value ?: emptyList())
            }
    }

    override fun onCleared() {
        super.onCleared()
        drinkReg?.remove()
        profileReg?.remove()
    }

    fun defaultPortionsFor(t: DrinkType): List<Int> =
        defaultPortions[t] ?: listOf(200, 250, 500)

    fun addSameAsLast(type: DrinkType) {
        val v = lastByType[type] ?: defaultPortionsFor(type).getOrNull(1) ?: 200
        addDrink(type, v)
    }

    fun addDrink(type: DrinkType, volumeMl: Int) {
        val realUid = uid
        if (realUid == null) {
            Log.e("HomeViewModel", "User is not logged in! Cannot save drink.")
            //Show a toast on screen so you know immediately
            android.widget.Toast.makeText(getApplication(), "Please log in to start tracking", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
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
        storage.addDrinkLog(realUid, log) {
            refreshDrinkLogsOnce()
        }
    }

    fun deleteDrink(id: Int) {
        val realUid = uid ?: return
        val list = _timeline.value ?: return
        val target = list.find { it.id == id } ?: return

        storage.deleteDrinkLog(realUid, target) {
            refreshDrinkLogsOnce()
        }
    }

    fun editDrink(item: DrinkLog) {
        updateDrinkVolume(item.id, item.volumeMl + 50)
    }

    fun updateDrinkVolume(id: Int, newVolumeMl: Int) {
        val realUid = uid ?: return
        val list = _timeline.value ?: return
        val target = list.find { it.id == id } ?: return

        val rounded = max(50, (newVolumeMl / 50) * 50)
        val eff = (rounded * (factor[target.type] ?: 1.0)).toInt()

        val updated = target.copy(
            volumeMl = rounded,
            effectiveMl = eff
        )

        lastByType[target.type] = rounded
        storage.updateDrinkLog(realUid, updated) {
            refreshDrinkLogsOnce()
        }
    }

    private fun recompute(list: List<DrinkLog>) {
        val intake = list.sumOf { it.volumeMl }
        val effective = list.sumOf { it.effectiveMl }
        val goal = computeDailyGoalMl()
        val limit = computeLimitMl()

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
                waterRatio = waterEff.coerceIn(0.0, 1.0),
                caffeineRatio = caffeineEff.coerceIn(0.0, 1.0),
                sugaryRatio = sugaryEff.coerceIn(0.0, 1.0)
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
