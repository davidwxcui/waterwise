package com.davidwxcui.waterwise.database
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val date: String, // Format: "yyyy-MM-dd"
    val daysUntil: Int,
    val description: String,
    val recommendation: String,
    val recommendedAmount: String, // e.g., "3000ml/day"
    val color: String // e.g., "purple", "orange", "teal"
)