package com.smartpillbox.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartpillbox.app.data.local.AppDatabase
import com.smartpillbox.app.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val db = AppDatabase.getDatabase(context)
            CoroutineScope(Dispatchers.IO).launch {
                val alarms = db.alarmDao().getEnabledAlarms()
                CoroutineScope(Dispatchers.Main).launch {
                    AlarmScheduler.scheduleAllAlarms(context, alarms)
                }
            }
        }
    }
}