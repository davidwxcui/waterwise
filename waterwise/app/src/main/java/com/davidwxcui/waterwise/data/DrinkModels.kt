package com.davidwxcui.waterwise.data

enum class DrinkType(val displayName: String) {
    Water("Water"),
    Tea("Tea"),
    Coffee("Coffee"),
    Juice("Juice"),
    Soda("Soda"),
    Milk("Milk"),
    Yogurt("Yogurt drinks"),
    Alcohol("Alcohol"),
    Sparkling("Sparkling water");

    companion object {
        fun fromFirestore(raw: String?): DrinkType {
            if (raw.isNullOrBlank()) return Water
            val key = raw.trim()

            entries.firstOrNull { it.name.equals(key, ignoreCase = true) }?.let { return it }

            entries.firstOrNull { it.displayName.equals(key, ignoreCase = true) }?.let { return it }

            return Water
        }
    }
}

data class DrinkLog(
    val id: Int = 0,
    val type: DrinkType = DrinkType.Water,
    val volumeMl: Int = 0,
    val effectiveMl: Int = 0,
    val note: String? = null,
    val timeMillis: Long = 0L,
    val uid: String = ""
)
