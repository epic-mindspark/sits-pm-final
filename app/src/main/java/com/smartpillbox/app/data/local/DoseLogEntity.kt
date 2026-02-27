package com.smartpillbox.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dose_logs")
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val medicineId: Int,
    val alarmId: Int,
    val medicineName: String,
    val scheduledTime: Long,
    val actionTime: Long = 0,
    val status: String = "pending",
    val compartmentAccessed: Boolean = false
)