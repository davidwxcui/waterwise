package com.davidwxcui.waterwise.ui.profile

import android.content.Context

object ProfilePrefs {
    private const val FILE = "profile"

    fun load(ctx: Context): Profile {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val sex = when (p.getString("sex", "UNSPECIFIED")) {
            "MALE" -> Sex.MALE
            "FEMALE" -> Sex.FEMALE
            else -> Sex.UNSPECIFIED
        }
        val act = when (p.getString("activity", "SEDENTARY")) {
            "LIGHT" -> ActivityLevel.LIGHT
            "MODERATE" -> ActivityLevel.MODERATE
            "ACTIVE" -> ActivityLevel.ACTIVE
            "VERY_ACTIVE" -> ActivityLevel.VERY_ACTIVE
            else -> ActivityLevel.SEDENTARY
        }
        return Profile(
            name = p.getString("name", " ") ?: "",
            email = p.getString("email", " ") ?: "",
            age = p.getInt("age", 28),
            sex = sex,
            heightCm = p.getInt("heightCm", 175),
            weightKg = p.getInt("weightKg", 72),
            activity = act,
            activityFreqLabel = p.getString("activityFreq", "3-5 days/week") ?: "3-5 days/week",
            avatarUri = p.getString("avatarUri", null)
        )
    }

    fun save(ctx: Context, pf: Profile) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("name", pf.name)
            .putString("email", pf.email)
            .putInt("age", pf.age)
            .putString("sex", pf.sex.name)
            .putInt("heightCm", pf.heightCm)
            .putInt("weightKg", pf.weightKg)
            .putString("activity", pf.activity.name)
            .putString("activityFreq", pf.activityFreqLabel)
            .putString("avatarUri", pf.avatarUri)
            .apply()
    }
}
