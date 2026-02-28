package com.smartpillbox.app.util

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {

    private const val PREFS_NAME = "smartpillbox_prefs"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_ROLE = "role"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_AGE = "user_age"
    private const val KEY_EMERGENCY_CONTACT = "emergency_contact"
    private const val KEY_PATIENT_CODE = "patient_code"
    
    private const val KEY_MORNING_TIME = "morning_time"
    private const val KEY_NIGHT_TIME = "night_time"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isOnboarded(context: Context): Boolean = prefs(context).getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONBOARDED, value).apply()
    }

    fun getRole(context: Context): String? = prefs(context).getString(KEY_ROLE, null)

    fun setRole(context: Context, role: String) {
        prefs(context).edit().putString(KEY_ROLE, role).apply()
    }

    fun getUserName(context: Context): String? = prefs(context).getString(KEY_USER_NAME, null)

    fun setUserName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserAge(context: Context): String? = prefs(context).getString(KEY_USER_AGE, null)

    fun setUserAge(context: Context, age: String) {
        prefs(context).edit().putString(KEY_USER_AGE, age).apply()
    }

    fun getEmergencyContact(context: Context): String? =
        prefs(context).getString(KEY_EMERGENCY_CONTACT, null)

    fun setEmergencyContact(context: Context, contact: String) {
        prefs(context).edit().putString(KEY_EMERGENCY_CONTACT, contact).apply()
    }

    fun getPatientCode(context: Context): String? =
        prefs(context).getString(KEY_PATIENT_CODE, null)

    fun setPatientCode(context: Context, code: String) {
        prefs(context).edit().putString(KEY_PATIENT_CODE, code).apply()
    }

    fun setMorningTime(context: Context, time: String) {
        prefs(context).edit().putString(KEY_MORNING_TIME, time).apply()
    }

    fun getMorningTime(context: Context): String = prefs(context).getString(KEY_MORNING_TIME, "08:00") ?: "08:00"

    fun setNightTime(context: Context, time: String) {
        prefs(context).edit().putString(KEY_NIGHT_TIME, time).apply()
    }

    fun getNightTime(context: Context): String = prefs(context).getString(KEY_NIGHT_TIME, "20:00") ?: "20:00"

    fun generatePatientCode(): String {
        return (100000..999999).random().toString()
    }

    fun logout(context: Context) {
        prefs(context).edit().clear().apply()
    }
}