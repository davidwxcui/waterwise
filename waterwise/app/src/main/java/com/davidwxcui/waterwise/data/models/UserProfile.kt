package com.davidwxcui.waterwise.data.models

data class UserProfile(
    var name: String? = null,
    var email: String? = null,
    var gender: Gender? = null,
    var weight: Float? = null,
    var weightUnit: WeightUnit = WeightUnit.KG,
    var height: Float? = null,
    var heightUnit: HeightUnit = HeightUnit.CM,
    var trainingFrequency: TrainingFrequency? = null,
    var caffeineIntake: IntakeFrequency? = null,
    var vegetablesIntake: IntakeFrequency? = null,
    var personalGoal: PersonalGoal? = null,
    var goalTimeline: GoalTimeline? = null,
    var dailyWaterGoalMl: Int? = null
)

enum class Gender {
    MALE, FEMALE, OTHER
}

enum class WeightUnit {
    KG, LB;

    fun toKg(value: Float): Float {
        return when (this) {
            KG -> value
            LB -> value * 0.453592f
        }
    }
}

enum class HeightUnit {
    CM, FEET_INCHES;

    fun toCm(value: Float): Float {
        return when (this) {
            CM -> value
            FEET_INCHES -> value * 30.48f // value in feet
        }
    }
}

enum class TrainingFrequency(val daysPerWeek: String) {
    NONE("0-1 day"),
    LOW("2-3 days"),
    MEDIUM("4-5 days"),
    HIGH("6-7 days")
}

enum class IntakeFrequency(val description: String) {
    ALMOST_NEVER("Almost never (never/several times a month)"),
    RARELY("Rarely (few times a week)"),
    REGULARLY("Regularly (every day)"),
    OFTEN("Often (more than one per day)")
}

enum class PersonalGoal(val displayName: String) {
    DRINK_MORE_WATER("Drink more water"),
    LOSE_WEIGHT("Lose weight"),
    SHINY_SKIN("Shiny skin"),
    HEALTHY_LIFESTYLE("Lead a healthy lifestyle"),
    IMPROVE_DIGESTION("Improve digestion")
}

enum class GoalTimeline(val days: Int) {
    THREE_DAYS(3),
    SEVEN_DAYS(7),
    FOURTEEN_DAYS(14),
    THIRTY_DAYS(30)
}
