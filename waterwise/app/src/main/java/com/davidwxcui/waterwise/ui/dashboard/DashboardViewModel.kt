package com.davidwxcui.waterwise.ui.dashboard

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.davidwxcui.waterwise.BuildConfig
import com.davidwxcui.waterwise.ui.profile.FirebaseAuthRepository
import com.davidwxcui.waterwise.ui.profile.ProfilePrefs
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val generativeModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    data class DrinkRow(
        val timestamp: Long,
        val type: String,
        val volumeMl: Int,
        val effectiveMl: Int,
        val note: String
    )

    data class Stats(
        val totalMl: Int,
        val avgDailyMl: Int,
        val mostConsumed: String,
        val waterPercentage: Int
    )

    // LiveData
    private val _drinkLogs = MutableLiveData<List<DrinkRow>>(emptyList())
    val drinkLogs: LiveData<List<DrinkRow>> = _drinkLogs

    private val _aiRecommendation = MutableLiveData<String>("")
    val aiRecommendation: LiveData<String> = _aiRecommendation

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var lastDrinkLogCount = 0
    private var cachedRecommendation = ""

    // Fetch drink logs from Firestore
    fun fetchDrinkLogs() {
        val uid = FirebaseAuthRepository.currentUid()
        if (uid == null) {
            _drinkLogs.postValue(emptyList())
            _aiRecommendation.postValue("")
            return
        }

        viewModelScope.launch {
            try {
                val snapshot = db.collection("users")
                    .document(uid)
                    .collection("drinkLogs")
                    .get()
                    .await()

                val rows = snapshot.documents.mapNotNull { doc ->
                    try {
                        val type = doc.getString("type") ?: "Unknown"
                        val volumeMl = (doc.getLong("volumeMl") ?: 0L).toInt()
                        val effectiveMl = (doc.getLong("effectiveMl") ?: 0L).toInt()
                        val timeMillis = doc.getLong("timeMillis") ?: 0L
                        val note = doc.getString("note") ?: ""

                        DrinkRow(
                            timestamp = timeMillis,
                            type = type,
                            volumeMl = volumeMl,
                            effectiveMl = effectiveMl,
                            note = note
                        )
                    } catch (e: Exception) {
                        Log.e("DashboardViewModel", "Parse error: ${e.message}")
                        null
                    }
                }

                _drinkLogs.postValue(rows)
                generateAIRecommendations(rows)

            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Failed to fetch drink logs", e)
                _drinkLogs.postValue(emptyList())
            }
        }
    }

    // Generate AI recommendations
    private fun generateAIRecommendations(rows: List<DrinkRow>) {
        // Only generate if there are new drink logs since last time
        if (rows.size == lastDrinkLogCount || rows.isEmpty() || BuildConfig.GEMINI_API_KEY.isEmpty()) {
            // Use cached recommendation
            if (cachedRecommendation.isNotEmpty()) {
                _aiRecommendation.postValue(cachedRecommendation)
            }
            return
        }

        // New drink logs detected, generate new recommendation
        lastDrinkLogCount = rows.size
        _isLoading.postValue(true)

        viewModelScope.launch {
            try {
                val profile = ProfilePrefs.load(getApplication())
                val stats = calculateStats(rows)

                val prompt = """
                    Based on these hydration tracking stats from the past 7 days:
                    - Total intake: ${stats.totalMl}ml
                    - Average daily: ${stats.avgDailyMl}ml
                    - Most consumed: ${stats.mostConsumed}
                    - Water percentage: ${stats.waterPercentage}%
                    - User age: ${profile.age}, weight: ${profile.weightKg}kg, activity: ${profile.activity.name}
                    
                    Provide ONE SHORT actionable hydration improvement tip (max 1-2 sentences).
                    Be specific and encouraging.
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val recommendation = response.text ?: return@launch

                cachedRecommendation = "$recommendation"
                _aiRecommendation.postValue(cachedRecommendation)
                _isLoading.postValue(false)

            } catch (e: Exception) {
                Log.e("DashboardViewModel", "AI recommendation error: ${e.message}", e)
                _aiRecommendation.postValue("")
                _isLoading.postValue(false)
            }
        }
    }

    // Calculate stats from drink logs
    private fun calculateStats(rows: List<DrinkRow>): Stats {
        val totalMl = rows.sumOf { it.volumeMl }
        val avgDaily = if (rows.isNotEmpty()) totalMl / 7 else 0
        val mostConsumed = rows.groupingBy { it.type }.eachCount().maxByOrNull { it.value }?.key ?: "None"
        val waterMl = rows.filter { it.type == "Water" }.sumOf { it.volumeMl }
        val waterPercentage = if (totalMl > 0) (waterMl * 100) / totalMl else 0

        return Stats(totalMl, avgDaily, mostConsumed, waterPercentage)
    }

    // Get cached recommendation
    fun getCachedRecommendation(): String = cachedRecommendation

    // Reset cache when needed
    fun resetRecommendationCache() {
        cachedRecommendation = ""
        lastDrinkLogCount = 0
        _aiRecommendation.postValue("")
    }
}