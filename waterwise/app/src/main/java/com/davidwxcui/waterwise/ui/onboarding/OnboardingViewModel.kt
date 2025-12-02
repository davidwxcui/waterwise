package com.davidwxcui.waterwise.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.davidwxcui.waterwise.data.OnboardingPreferences
import com.davidwxcui.waterwise.data.models.*
import com.davidwxcui.waterwise.ui.profile.HydrationFormula
import com.davidwxcui.waterwise.ui.profile.ActivityLevel
import com.davidwxcui.waterwise.ui.profile.Sex

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val onboardingPrefs = OnboardingPreferences(application)
    val userProfile = UserProfile()

    private val _currentStep = MutableLiveData<Int>(0)
    val currentStep: LiveData<Int> = _currentStep

    private val _dailyGoalMl = MutableLiveData<Int>()
    val dailyGoalMl: LiveData<Int> = _dailyGoalMl

    // Update methods for each onboarding step
    fun setUserInfo(name: String) {
        userProfile.name = name
        // Email is obtained from Firebase Auth registration, not from onboarding
    }

    fun setGender(gender: Gender) {
        userProfile.gender = gender
    }

    fun setWeight(weight: Float, unit: WeightUnit) {
        userProfile.weight = weight
        userProfile.weightUnit = unit
    }

    fun setHeight(height: Float, unit: HeightUnit) {
        userProfile.height = height
        userProfile.heightUnit = unit
    }

    fun setTrainingFrequency(frequency: TrainingFrequency) {
        userProfile.trainingFrequency = frequency
    }

    fun setCaffeineIntake(intake: IntakeFrequency) {
        userProfile.caffeineIntake = intake
    }

    fun setVegetablesIntake(intake: IntakeFrequency) {
        userProfile.vegetablesIntake = intake
    }

    fun setPersonalGoal(goal: PersonalGoal) {
        userProfile.personalGoal = goal
    }

    fun setGoalTimeline(timeline: GoalTimeline) {
        userProfile.goalTimeline = timeline
    }

    fun calculateDailyGoal() {
        // Convert UserProfile data to match HydrationFormula parameters
        val weightKg = userProfile.weight?.let {
            userProfile.weightUnit.toKg(it)
        } ?: 70f

        val sex = when (userProfile.gender) {
            Gender.MALE -> Sex.MALE
            Gender.FEMALE -> Sex.FEMALE
            else -> Sex.UNSPECIFIED
        }

        val age = 28 // Default age, as UserProfile doesn't have age

        val activityLevel = when (userProfile.trainingFrequency) {
            TrainingFrequency.NONE -> ActivityLevel.SEDENTARY
            TrainingFrequency.LOW -> ActivityLevel.LIGHT
            TrainingFrequency.MEDIUM -> ActivityLevel.MODERATE
            TrainingFrequency.HIGH -> ActivityLevel.ACTIVE
            else -> ActivityLevel.MODERATE
        }

        val calculatedGoal = HydrationFormula.dailyGoalMl(weightKg, sex, age, activityLevel)
        userProfile.dailyWaterGoalMl = calculatedGoal
        _dailyGoalMl.value = calculatedGoal
    }

    fun getDailyGoalSummary(): String {
        return _dailyGoalMl.value?.let {
            val liters = it / 1000.0
            val cups = (it / 240.0).toInt()
            String.format("%.1f L per day (about %d cups)", liters, cups)
        } ?: "Calculating..."
    }

    fun completeOnboarding() {
        // Save user profile to OnboardingPreferences
        onboardingPrefs.saveUserProfile(userProfile)
        // Mark onboarding as completed
        onboardingPrefs.setOnboardingCompleted(true)

        // Also save to ProfilePrefs for consistency
        val context = getApplication<Application>()

        // Load existing profile to get name and email from registration
        val existingProfile = com.davidwxcui.waterwise.ui.profile.ProfilePrefs.load(context)

        val weightKg = userProfile.weight?.let {
            userProfile.weightUnit.toKg(it).toInt()
        } ?: 70

        val heightCm = userProfile.height?.let {
            userProfile.heightUnit.toCm(it).toInt()
        } ?: 170

        val sex = when (userProfile.gender) {
            Gender.MALE -> com.davidwxcui.waterwise.ui.profile.Sex.MALE
            Gender.FEMALE -> com.davidwxcui.waterwise.ui.profile.Sex.FEMALE
            else -> com.davidwxcui.waterwise.ui.profile.Sex.UNSPECIFIED
        }

        val activityLevel = when (userProfile.trainingFrequency) {
            TrainingFrequency.NONE -> com.davidwxcui.waterwise.ui.profile.ActivityLevel.SEDENTARY
            TrainingFrequency.LOW -> com.davidwxcui.waterwise.ui.profile.ActivityLevel.LIGHT
            TrainingFrequency.MEDIUM -> com.davidwxcui.waterwise.ui.profile.ActivityLevel.MODERATE
            TrainingFrequency.HIGH -> com.davidwxcui.waterwise.ui.profile.ActivityLevel.ACTIVE
            else -> com.davidwxcui.waterwise.ui.profile.ActivityLevel.MODERATE
        }

        val activityFreqLabel = userProfile.trainingFrequency?.daysPerWeek ?: "3-5 days/week"

        // Create Profile object and save to ProfilePrefs, preserving name and email from registration
        val profile = com.davidwxcui.waterwise.ui.profile.Profile(
            name = existingProfile.name,
            email = existingProfile.email,
            age = 28,
            sex = sex,
            heightCm = heightCm,
            weightKg = weightKg,
            activity = activityLevel,
            activityFreqLabel = activityFreqLabel
        )
        com.davidwxcui.waterwise.ui.profile.ProfilePrefs.save(context, profile)
    }

    fun nextStep() {
        _currentStep.value = (_currentStep.value ?: 0) + 1
    }

    fun previousStep() {
        val current = _currentStep.value ?: 0
        if (current > 0) {
            _currentStep.value = current - 1
        }
    }
}
