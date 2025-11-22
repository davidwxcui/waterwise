package com.davidwxcui.waterwise.database.event
import kotlinx.coroutines.flow.Flow

class EventRepository(private val eventDao: EventDao) {

    val allEvents: Flow<List<Event>> = eventDao.getAllEvents()
    val upcomingEvents: Flow<List<Event>> = eventDao.getUpcomingEvents()

    suspend fun insertEvent(event: Event) {
        eventDao.insertEvent(event)
    }

    suspend fun updateEvent(event: Event) {
        eventDao.updateEvent(event)
    }

    suspend fun deleteEvent(event: Event) {
        eventDao.deleteEvent(event)
    }

    suspend fun deleteEventById(eventId: Int) {
        eventDao.deleteEventById(eventId)
    }

    fun searchEvents(query: String): Flow<List<Event>> {
        return eventDao.searchEvents(query)
    }

    fun getEventsSortedByDays(): Flow<List<Event>> {
        return eventDao.getEventsSortedByDays()
    }
}