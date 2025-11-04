package com.davidwxcui.waterwise.ui.profile

enum class Sex { MALE, FEMALE, UNSPECIFIED }
enum class ActivityLevel { SEDENTARY, LIGHT, MODERATE, ACTIVE, VERY_ACTIVE }

data class Profile(
    val name: String = "",
    val email: String = "",
    val age: Int = 28,
    val sex: Sex = Sex.UNSPECIFIED,
    val heightCm: Int = 170,
    val weightKg: Int = 60,
    val activity: ActivityLevel = ActivityLevel.SEDENTARY,
    val activityFreqLabel: String = "3-5 days/week",
    val avatarUri: String? = null
)

object HydrationFormula {
    fun dailyGoalMl(weightKg: Float, sex: Sex, age: Int, level: ActivityLevel): Int {
        val base = weightKg * 35f
        val activity = when (level) {
            ActivityLevel.SEDENTARY -> 0
            ActivityLevel.LIGHT -> 250
            ActivityLevel.MODERATE -> 500
            ActivityLevel.ACTIVE -> 750
            ActivityLevel.VERY_ACTIVE -> 1000
        }
        val sexAdj = if (sex == Sex.MALE) 250 else 0
        val ageAdj = if (age >= 55) -250 else 0
        return (base + activity + sexAdj + ageAdj).coerceIn(1500f, 4500f).toInt()
    }
}
