package com.sj.gpsutil.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TrackingStatus {
    Idle,
    Recording,
    Paused
}

data class TrackingSample(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val speedKmph: Double?,
    val bearingDegrees: Float?,
    val verticalAccuracyMeters: Float?,
    val provider: String?,
    val timestampMillis: Long
)

object TrackingState {
    private val _status = MutableStateFlow(TrackingStatus.Idle)
    private val _latestSample = MutableStateFlow<TrackingSample?>(null)

    val status = _status.asStateFlow()
    val latestSample = _latestSample.asStateFlow()

    fun updateStatus(status: TrackingStatus) {
        _status.value = status
    }

    fun updateSample(sample: TrackingSample) {
        _latestSample.value = sample
    }
}
