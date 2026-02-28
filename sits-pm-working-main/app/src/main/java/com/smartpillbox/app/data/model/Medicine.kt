package com.smartpillbox.app.data.model

data class Medicine(
    val name: String,
    val dosage: String,
    val frequency: String,
    val timing: String = "",
    val userTimes: MutableList<String> = mutableListOf(),
    var isConfirmed: Boolean = false
)