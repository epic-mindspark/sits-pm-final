package com.smartpillbox.app.data.model

data class Medicine(
    val name: String,
    val dosage: String,
    val frequency: String,
    val timing: String = "",
    var isConfirmed: Boolean = false
)