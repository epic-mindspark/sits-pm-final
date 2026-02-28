package com.smartpillbox.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val pillName = intent?.getStringExtra("PILL_NAME") ?: "Pill"

        // For now, just show a Toast. Later, you can add Notification logic here.
        Toast.makeText(context, "Time to take your medicine: $pillName", Toast.LENGTH_LONG).show()
    }
}