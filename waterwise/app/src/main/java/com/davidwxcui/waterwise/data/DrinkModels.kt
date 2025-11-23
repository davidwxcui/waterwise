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

/**
 * Firestore 需要：
 * 1. 所有字段有默认值
 * 2. data class 必须能无参构造
 */
data class DrinkLog(
    val id: Int = 0,
    val type: DrinkType = DrinkType.Water,
    val volumeMl: Int = 0,
    val effectiveMl: Int = 0,
    val note: String? = null,
    val timeMillis: Long = 0L,
    val uid: String = ""     // Firestore 下必须知道属于谁
)
