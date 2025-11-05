package com.davidwxcui.waterwise.data

import android.content.Context
import android.content.SharedPreferences
import com.davidwxcui.waterwise.data.models.*

class OnboardingPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "onboarding_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_GENDER = "gender"
        private const val KEY_WEIGHT = "weight"
        private const val KEY_WEIGHT_UNIT = "weight_unit"
        private const val KEY_HEIGHT = "height"
        private const val KEY_HEIGHT_UNIT = "height_unit"
        private const val KEY_TRAINING_FREQ = "training_frequency"
        private const val KEY_CAFFEINE_INTAKE = "caffeine_intake"
        private const val KEY_VEGETABLES_INTAKE = "vegetables_intake"
        private const val KEY_PERSONAL_GOAL = "personal_goal"
        private const val KEY_GOAL_TIMELINE = "goal_timeline"
        private const val KEY_DAILY_GOAL_ML = "daily_goal_ml"
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun saveUserProfile(profile: UserProfile) {
        prefs.edit().apply {
            profile.gender?.let { putString(KEY_GENDER, it.name) }
            profile.weight?.let { putFloat(KEY_WEIGHT, it) }
            profile.name?.let { putString(KEY_NAME, it) }
            profile.email?.let { putString(KEY_EMAIL, it) }
            putString(KEY_WEIGHT_UNIT, profile.weightUnit.name)
            profile.height?.let { putFloat(KEY_HEIGHT, it) }
            putString(KEY_HEIGHT_UNIT, profile.heightUnit.name)
            profile.trainingFrequency?.let { putString(KEY_TRAINING_FREQ, it.name) }
            profile.caffeineIntake?.let { putString(KEY_CAFFEINE_INTAKE, it.name) }
            profile.vegetablesIntake?.let { putString(KEY_VEGETABLES_INTAKE, it.name) }
            profile.personalGoal?.let { putString(KEY_PERSONAL_GOAL, it.name) }
            profile.goalTimeline?.let { putString(KEY_GOAL_TIMELINE, it.name) }
            profile.dailyWaterGoalMl?.let { putInt(KEY_DAILY_GOAL_ML, it) }
            apply()
        }
    }

    fun getUserProfile(): UserProfile {
        return UserProfile(
            gender = prefs.getString(KEY_GENDER, null)?.let { Gender.valueOf(it) },
            weight = if (prefs.contains(KEY_WEIGHT)) prefs.getFloat(KEY_WEIGHT, 0f) else null,
            name = prefs.getString(KEY_NAME, null),
            email = prefs.getString(KEY_EMAIL, null),
            weightUnit = prefs.getString(KEY_WEIGHT_UNIT, null)?.let { WeightUnit.valueOf(it) } ?: WeightUnit.KG,
            height = if (prefs.contains(KEY_HEIGHT)) prefs.getFloat(KEY_HEIGHT, 0f) else null,
            heightUnit = prefs.getString(KEY_HEIGHT_UNIT, null)?.let { HeightUnit.valueOf(it) } ?: HeightUnit.CM,
            trainingFrequency = prefs.getString(KEY_TRAINING_FREQ, null)?.let { TrainingFrequency.valueOf(it) },
            caffeineIntake = prefs.getString(KEY_CAFFEINE_INTAKE, null)?.let { IntakeFrequency.valueOf(it) },
            vegetablesIntake = prefs.getString(KEY_VEGETABLES_INTAKE, null)?.let { IntakeFrequency.valueOf(it) },
            personalGoal = prefs.getString(KEY_PERSONAL_GOAL, null)?.let { PersonalGoal.valueOf(it) },
            goalTimeline = prefs.getString(KEY_GOAL_TIMELINE, null)?.let { GoalTimeline.valueOf(it) },
            dailyWaterGoalMl = if (prefs.contains(KEY_DAILY_GOAL_ML)) prefs.getInt(KEY_DAILY_GOAL_ML, 2000) else null
        )
    }

    fun getDailyWaterGoalMl(): Int {
        return prefs.getInt(KEY_DAILY_GOAL_ML, 2000) // Default 2L
    }
}
