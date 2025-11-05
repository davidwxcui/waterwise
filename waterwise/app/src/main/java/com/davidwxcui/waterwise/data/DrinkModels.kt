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
    Sparkling("Sparkling water")
}

data class DrinkLog(
    val id: Int,
    val type: DrinkType,
    val volumeMl: Int,
    val effectiveMl: Int,
    val note: String?,
    val timeMillis: Long
)
