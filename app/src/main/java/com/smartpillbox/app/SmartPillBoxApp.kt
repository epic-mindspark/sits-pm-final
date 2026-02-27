package com.smartpillbox.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.smartpillbox.app.data.local.AppDatabase

class SmartPillBoxApp : Application() {

    lateinit var database: AppDatabase
        private set

    companion object {
        const val CHANNEL_MEDICATION = "medication_channel"
        const val CHANNEL_MISSED_DOSE = "missed_dose_channel"

        lateinit var instance: SmartPillBoxApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getDatabase(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val medicationChannel = NotificationChannel(
                CHANNEL_MEDICATION,
                "Medication Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when it's time to take medication"
                enableVibration(true)
            }

            val missedDoseChannel = NotificationChannel(
                CHANNEL_MISSED_DOSE,
                "Missed Dose Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a dose is missed"
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(medicationChannel)
            manager.createNotificationChannel(missedDoseChannel)
        }
    }
}
