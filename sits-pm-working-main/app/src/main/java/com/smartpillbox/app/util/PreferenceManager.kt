package com.smartpillbox.app.util

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("SmartPillBoxPrefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_USER_ROLE = "user_role"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_PATIENT_NAME = "patient_name"
        const val KEY_PATIENT_CODE = "patient_code"
        const val ROLE_PATIENT = "PATIENT"
        const val ROLE_CAREGIVER = "CAREGIVER"
    }

    fun setUserRole(role: String) {
        prefs.edit().putString(KEY_USER_ROLE, role).apply()
    }

    fun getUserRole(): String? = prefs.getString(KEY_USER_ROLE, null)

    fun setOnboardingDone(isDone: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, isDone).apply()
    }

    fun isOnboardingDone(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setPatientProfile(name: String) {
        prefs.edit().putString(KEY_PATIENT_NAME, name).apply()
    }

    fun setLinkedPatientCode(code: String) {
        prefs.edit().putString(KEY_PATIENT_CODE, code).apply()
    }

    fun generatePatientCode(): String {
        val code = (100000..999999).random().toString()
        setLinkedPatientCode(code)
        return code
    }
}