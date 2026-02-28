package com.smartpillbox.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dose_logs")
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val medicineId: Int,
    val medicineName: String = "",
    val dosage: String = "",
    val compartmentNumber: Int = 0,   // ← NEW
    val scheduledTime: Long,          // epoch millis when dose was scheduled
    val status: String = "pending",   // "pending", "taken", "missed", "skipped"
    val actionTime: Long = 0,         // when user pressed taken/dismissed
    val mood: String = "",            // ← NEW: emoji mood after dose (optional)
    val sentToFirebase: Boolean = false  // ← NEW: sync flag
)