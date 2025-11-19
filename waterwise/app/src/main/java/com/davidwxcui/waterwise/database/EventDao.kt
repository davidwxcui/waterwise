package com.davidwxcui.waterwise.database
import com.davidwxcui.waterwise.database.Event
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    // Insert a new event
    @Insert
    suspend fun insertEvent(event: Event)

    // Update an existing event
    @Update
    suspend fun updateEvent(event: Event)

    // Delete an event
    @Delete
    suspend fun deleteEvent(event: Event)

    // Delete event by ID
    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEventById(eventId: Int)

    // Get all events (returns Flow for real-time updates)
    @Query("SELECT * FROM events ORDER BY date ASC")
    fun getAllEvents(): Flow<List<Event>>

    // Get event by ID
    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun getEventById(eventId: Int): Event?

    // Get events sorted by days until
    @Query("SELECT * FROM events ORDER BY daysUntil ASC")
    fun getEventsSortedByDays(): Flow<List<Event>>

    // Search events by title
    @Query("SELECT * FROM events WHERE title LIKE '%' || :query || '%'")
    fun searchEvents(query: String): Flow<List<Event>>

    // Get upcoming events (daysUntil > 0)
    @Query("SELECT * FROM events WHERE daysUntil > 0 ORDER BY daysUntil ASC")
    fun getUpcomingEvents(): Flow<List<Event>>

    // Delete all events
    @Query("DELETE FROM events")
    suspend fun deleteAllEvents()

    // Get event count
    @Query("SELECT COUNT(*) FROM events")
    suspend fun getEventCount(): Int
}