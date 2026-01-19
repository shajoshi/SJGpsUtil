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
    val accuracyMeters: Float?,
    val timestampMillis: Long
)

object TrackingState {
    private val _status = MutableStateFlow(TrackingStatus.Idle)
    private val _latestSample = MutableStateFlow<TrackingSample?>(null)
    private val _elapsedMillis = MutableStateFlow(0L)
    private val _recordingStartMillis = MutableStateFlow<Long?>(null)
    private val _pointCount = MutableStateFlow(0L)
    private val _satelliteCount = MutableStateFlow(0)
    private val _currentFileName = MutableStateFlow<String?>(null)

    val status = _status.asStateFlow()
    val latestSample = _latestSample.asStateFlow()
    val elapsedMillis = _elapsedMillis.asStateFlow()
    val recordingStartMillis = _recordingStartMillis.asStateFlow()
    val pointCount = _pointCount.asStateFlow()
    val satelliteCount = _satelliteCount.asStateFlow()
    val currentFileName = _currentFileName.asStateFlow()

    fun updateStatus(status: TrackingStatus) {
        _status.value = status
    }

    fun updateSample(sample: TrackingSample) {
        _latestSample.value = sample
    }

    fun onRecordingStarted() {
        if (_recordingStartMillis.value == null) {
            _recordingStartMillis.value = System.currentTimeMillis()
        }
    }

    fun onRecordingPaused() {
        val start = _recordingStartMillis.value ?: return
        _elapsedMillis.value += System.currentTimeMillis() - start
        _recordingStartMillis.value = null
    }

    fun onRecordingStopped() {
        _recordingStartMillis.value = null
        _elapsedMillis.value = 0L
        _pointCount.value = 0L
        _satelliteCount.value = 0
        _currentFileName.value = null
    }

    fun incrementPointCount() {
        _pointCount.value = _pointCount.value + 1
    }

    fun resetPointCount() {
        _pointCount.value = 0L
    }

    fun updateSatelliteCount(count: Int) {
        _satelliteCount.value = count
    }

    fun updateCurrentFileName(name: String?) {
        _currentFileName.value = name
    }
}
