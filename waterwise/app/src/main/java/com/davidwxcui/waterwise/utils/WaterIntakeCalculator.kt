package com.davidwxcui.waterwise.utils

import com.davidwxcui.waterwise.data.models.*
import kotlin.math.roundToInt

object WaterIntakeCalculator {

    /**
     * Calculate daily water intake goal in milliliters based on user profile
     *
     * Base calculation:
     * - Weight-based: 30-35ml per kg of body weight
     * - Adjusted for gender, training, caffeine, vegetables, and goals
     */
    fun calculateDailyGoal(profile: UserProfile): Int {
        // Ensure we have minimum required data
        val weightKg = profile.weight?.let {
            profile.weightUnit.toKg(it)
        } ?: return 2000 // Default 2L if no weight

        // Base calculation: 33ml per kg (middle ground)
        var dailyIntakeMl = (weightKg * 33).toDouble()

        // Gender adjustment
        when (profile.gender) {
            Gender.MALE -> dailyIntakeMl *= 1.05 // Males need slightly more
            Gender.FEMALE -> dailyIntakeMl *= 0.95 // Females need slightly less
            Gender.OTHER -> dailyIntakeMl *= 1.0 // No adjustment
            null -> dailyIntakeMl *= 1.0
        }

        // Training frequency adjustment (exercise increases water needs)
        when (profile.trainingFrequency) {
            TrainingFrequency.NONE -> dailyIntakeMl += 0
            TrainingFrequency.LOW -> dailyIntakeMl += 300
            TrainingFrequency.MEDIUM -> dailyIntakeMl += 600
            TrainingFrequency.HIGH -> dailyIntakeMl += 900
            null -> dailyIntakeMl += 0
        }

        // Caffeine adjustment (caffeine is diuretic, increases water needs)
        when (profile.caffeineIntake) {
            IntakeFrequency.ALMOST_NEVER -> dailyIntakeMl += 0
            IntakeFrequency.RARELY -> dailyIntakeMl += 150
            IntakeFrequency.REGULARLY -> dailyIntakeMl += 300
            IntakeFrequency.OFTEN -> dailyIntakeMl += 500
            null -> dailyIntakeMl += 0
        }

        // Vegetables/fruits adjustment (high water content foods reduce extra water needs)
        when (profile.vegetablesIntake) {
            IntakeFrequency.ALMOST_NEVER -> dailyIntakeMl += 400 // Need more water
            IntakeFrequency.RARELY -> dailyIntakeMl += 200
            IntakeFrequency.REGULARLY -> dailyIntakeMl += 0
            IntakeFrequency.OFTEN -> dailyIntakeMl -= 200 // Get water from food
            null -> dailyIntakeMl += 0
        }

        // Personal goal adjustment
        when (profile.personalGoal) {
            PersonalGoal.DRINK_MORE_WATER -> dailyIntakeMl += 500 // Ambitious goal
            PersonalGoal.LOSE_WEIGHT -> dailyIntakeMl += 400 // Water helps with weight loss
            PersonalGoal.SHINY_SKIN -> dailyIntakeMl += 300 // Hydration for skin
            PersonalGoal.HEALTHY_LIFESTYLE -> dailyIntakeMl += 200 // General health
            PersonalGoal.IMPROVE_DIGESTION -> dailyIntakeMl += 350 // Water aids digestion
            null -> dailyIntakeMl += 0
        }

        // Timeline adjustment (shorter timeline = more aggressive goal)
        when (profile.goalTimeline) {
            GoalTimeline.THREE_DAYS -> dailyIntakeMl *= 1.10 // 10% increase for quick start
            GoalTimeline.SEVEN_DAYS -> dailyIntakeMl *= 1.05 // 5% increase
            GoalTimeline.FOURTEEN_DAYS -> dailyIntakeMl *= 1.0 // Standard
            GoalTimeline.THIRTY_DAYS -> dailyIntakeMl *= 0.95 // Gradual approach
            null -> dailyIntakeMl *= 1.0
        }

        // Ensure reasonable bounds (1.5L to 5L per day)
        val result = dailyIntakeMl.roundToInt().coerceIn(1500, 5000)

        return result
    }

    /**
     * Get a formatted string of the daily goal with explanation
     */
    fun getDailyGoalSummary(goalMl: Int): String {
        val liters = goalMl / 1000.0
        val cups = (goalMl / 240.0).roundToInt() // Approximate cups (240ml each)
        return String.format("%.1fL per day (about %d cups)", liters, cups)
    }
}

