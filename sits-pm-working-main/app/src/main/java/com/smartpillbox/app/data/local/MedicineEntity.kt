package com.smartpillbox.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicines")
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val dosage: String,
    val frequency: String,
    val timing: String = "As directed",
    val timesPerDay: Int = 1,
    val compartmentNumber: Int = -1,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val notes: String = "",
    val ownerPatientCode: String = "",
    val addedBy: String = "",
    val firebaseKey: String = ""
)