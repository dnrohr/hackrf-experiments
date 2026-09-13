package dev.rfnotebook.domain

data class Observation(
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyM: Float,
    val relativePowerDbfs: Float,
)
