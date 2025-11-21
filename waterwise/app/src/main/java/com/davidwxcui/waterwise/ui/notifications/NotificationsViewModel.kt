// NotificationsViewModel.kt
package com.davidwxcui.waterwise.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davidwxcui.waterwise.database.EventDatabase
import com.davidwxcui.waterwise.database.Event
import com.davidwxcui.waterwise.database.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: EventRepository
    val allEvents: Flow<List<Event>>

    init {
        val db = EventDatabase.getDatabase(application)
        repository = EventRepository(db.eventDao())
        allEvents = repository.allEvents
    }

    // Calculate days until event date
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

    // Get default recommendation based on event type
    private fun getDefaultRecommendation(eventType: String): Pair<String, String> {
        return when (eventType.lowercase()) {
            "marathon race" -> Pair(
                "Increase hydration 2 days before",
                "Recommended: 3000ml/day"
            )
            "beach vacation" -> Pair(
                "Stay extra hydrated in hot weather",
                "Recommended: 3500ml/day"
            )
            "hiking trip" -> Pair(
                "Prepare for high altitude activity",
                "Recommended: 3000ml/day"
            )
            "workout" -> Pair(
                "Increase water intake before exercise",
                "Recommended: 2500ml/day"
            )
            "travel" -> Pair(
                "Stay hydrated during journey",
                "Recommended: 3000ml/day"
            )
            else -> Pair(
                "Remember to stay hydrated",
                "Recommended: 2000ml/day"
            )
        }
    }

    // Get color based on event type
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

    // Create and insert event
    fun createEvent(eventTitle: String, eventDate: String) {
        viewModelScope.launch {
            val daysUntil = calculateDaysUntil(eventDate)
            val (description, recommendation) = getDefaultRecommendation(eventTitle)
            val color = getColorForEventType(eventTitle)

            val event = Event(
                title = eventTitle,
                date = eventDate,
                daysUntil = daysUntil,
                description = description,
                recommendation = recommendation,
                recommendedAmount = recommendation.substringAfter(": "),
                color = color
            )
            repository.insertEvent(event)
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