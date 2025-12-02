package com.davidwxcui.waterwise.ui.notifications

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davidwxcui.waterwise.BuildConfig
import com.davidwxcui.waterwise.database.event.Event
import com.davidwxcui.waterwise.database.event.EventDatabase
import com.davidwxcui.waterwise.database.event.EventRepository
import com.davidwxcui.waterwise.ui.profile.ProfilePrefs
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: EventRepository
    val allEvents: Flow<List<Event>>
    private val app = application

    private val generativeModel: GenerativeModel

    init {
        val db = EventDatabase.getDatabase(application)
        repository = EventRepository(db.eventDao())
        allEvents = repository.allEvents

        // Initialize Gemini SDK
        generativeModel = GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    private fun calculateDaysUntil(eventDate: String): Int {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val date = LocalDate.parse(eventDate, formatter)
            val today = LocalDate.now()
            ChronoUnit.DAYS.between(today, date).toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun getColorForEventType(eventType: String): String {
        return when (eventType.lowercase()) {
            "marathon race" -> "purple"
            "beach vacation" -> "orange"
            "hiking trip" -> "teal"
            "workout" -> "blue"
            "travel" -> "green"
            else -> "purple"
        }
    }

    fun createEventWithAI(eventTitle: String, eventDate: String) {
        viewModelScope.launch {
            // Check for Empty Key
            if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
                Log.e("NotificationsViewModel", "GEMINI_API_KEY not found")
                createEventWithDefaults(eventTitle, eventDate)
                return@launch
            }

            try {
                // Get user profile data
                val profile = ProfilePrefs.load(app)

                val userProfileInfo = """
                    Age: ${profile.age}
                    Weight: ${profile.weightKg} kg
                    Height: ${profile.heightCm} cm
                    Gender: ${profile.sex.name}
                    Activity Level: ${profile.activity.name}
                """.trimIndent()

                val prompt = """
                    I need personalized hydration recommendations for a user with these details:
                    $userProfileInfo
                    
                    The user is participating in: "$eventTitle" on $eventDate
                    
                    Please provide EXACTLY this format with no additional text:
                    DESCRIPTION: [One sentence description of the event and its impact on hydration considering their profile]
                    RECOMMENDATION: [Specific daily hydration amount in ml considering their weight/age/activity, like 3000]
                    TIPS: [2-3 specific hydration tips tailored to their profile and event type]
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val responseText = response.text

                if (responseText.isNullOrEmpty()) {
                    Log.w("NotificationsViewModel", "Empty response from AI, using defaults")
                    createEventWithDefaults(eventTitle, eventDate)
                    return@launch
                }

                Log.d("NotificationsViewModel", "AI Response: $responseText")

                // Parse response
                val description = extractField(responseText, "DESCRIPTION:")?.trim()
                    ?: "Prepare for your upcoming event"

                val recommendationStr = extractField(responseText, "RECOMMENDATION:")?.trim()?.filter { it.isDigit() }
                    ?: "2500"

                val tips = extractField(responseText, "TIPS:")?.trim()
                    ?: "Stay hydrated throughout the event"

                val daysUntil = calculateDaysUntil(eventDate)
                val color = getColorForEventType(eventTitle)

                val event = Event(
                    title = eventTitle,
                    date = eventDate,
                    daysUntil = daysUntil,
                    description = description,
                    recommendation = tips,
                    recommendedAmount = "${recommendationStr}ml/day",
                    color = color
                )
                repository.insertEvent(event)

            } catch (e: Exception) {
                Log.e("NotificationsViewModel", "Error creating event with AI: ${e.message}", e)
                createEventWithDefaults(eventTitle, eventDate)
            }
        }
    }

    private suspend fun createEventWithDefaults(eventTitle: String, eventDate: String) {
        val daysUntil = calculateDaysUntil(eventDate)
        val color = getColorForEventType(eventTitle)

        val (description, recommendation) = when (eventTitle.lowercase()) {
            "marathon race" -> Pair(
                "Prepare for high-intensity endurance activity",
                "Increase hydration 2 days before. Drink 400-600ml every hour during race"
            )
            "beach vacation" -> Pair(
                "Sun exposure and heat increase water loss significantly",
                "Drink 400-600ml every hour. Avoid caffeine and alcohol"
            )
            "hiking trip" -> Pair(
                "Altitude and physical activity increase hydration needs",
                "Start with extra hydration. Drink before feeling thirsty"
            )
            "workout" -> Pair(
                "Exercise causes significant fluid loss through sweat",
                "Pre-hydrate before workout. Drink 200-300ml every 15-20 minutes"
            )
            else -> Pair(
                "Stay well hydrated for your upcoming event",
                "Maintain regular hydration patterns. Drink when thirsty"
            )
        }

        val event = Event(
            title = eventTitle,
            date = eventDate,
            daysUntil = daysUntil,
            description = description,
            recommendation = recommendation,
            recommendedAmount = "2500ml/day",
            color = color
        )
        repository.insertEvent(event)
    }

    private fun extractField(text: String, fieldName: String): String? {
        return try {
            val startIndex = text.indexOf(fieldName)
            if (startIndex == -1) return null

            val contentStart = startIndex + fieldName.length
            val nextField = text.indexOf("\n", contentStart)

            return if (nextField == -1) {
                text.substring(contentStart).trim()
            } else {
                text.substring(contentStart, nextField).trim()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun updateEvent(event: Event) {
        viewModelScope.launch {
            repository.updateEvent(event)
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            repository.deleteEvent(event)
        }
    }

    fun deleteEventById(eventId: Int) {
        viewModelScope.launch {
            repository.deleteEventById(eventId)
        }
    }

    fun searchEvents(query: String): Flow<List<Event>> {
        return repository.searchEvents(query)
    }
}