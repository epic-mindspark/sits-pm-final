package com.smartpillbox.app.data.model

data class DoseItem(
    val medicineName: String,
    val dosage: String,
    val scheduledTime: String,
    val status: String  // "pending", "taken", "missed"
)