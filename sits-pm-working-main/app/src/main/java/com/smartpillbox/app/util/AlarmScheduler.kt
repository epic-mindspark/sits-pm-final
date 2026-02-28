package com.smartpillbox.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.smartpillbox.app.data.local.AlarmEntity
import com.smartpillbox.app.receiver.AlarmReceiver
import java.util.Calendar

object AlarmScheduler {

    fun scheduleAllAlarms(context: Context, alarms: List<AlarmEntity>) {
        Log.d("AlarmScheduler", "Restoring ${alarms.size} alarms...")
        alarms.forEach { alarm ->
            if (alarm.isEnabled) {
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, alarm.hour)
                    set(Calendar.MINUTE, alarm.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    
                    if (timeInMillis <= System.currentTimeMillis()) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                scheduleAlarm(context, calendar.timeInMillis, alarm.medicineName, alarm.id)
            }
        }
    }

    fun scheduleAlarm(context: Context, timeInMillis: Long, pillName: String, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("PILL_NAME", pillName)
            putExtra("ALARM_ID", alarmId)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
            Log.d("AlarmScheduler", "Alarm scheduled for $pillName at $timeInMillis")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Failed to schedule alarm: ${e.message}")
        }
    }

    fun cancelAlarm(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}