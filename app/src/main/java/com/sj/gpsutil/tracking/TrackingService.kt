package com.sj.gpsutil.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sj.gpsutil.MainActivity
import com.sj.gpsutil.R
import com.sj.gpsutil.data.OutputFormat
import com.sj.gpsutil.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max

class TrackingService : Service() {
    companion object {
        const val ACTION_START = "com.sj.gpsutil.tracking.action.START"
        const val ACTION_PAUSE = "com.sj.gpsutil.tracking.action.PAUSE"
        const val ACTION_STOP = "com.sj.gpsutil.tracking.action.STOP"

        private const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "GPS Tracking"
        private const val NOTIFICATION_ID = 1001

        private const val REJECTION_WINDOW_SAMPLES = 20
        private const val REJECTION_RATIO_THRESHOLD = 0.5
        private const val REJECTION_WARNING_COOLDOWN_MS = 60_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager
    private lateinit var locationCallback: LocationCallback
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private var gnssStatusCallback: GnssStatus.Callback? = null
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val fileStore by lazy { TrackingFileStore(applicationContext) }

    private var trackWriter: TrackWriter? = null
    private var currentStatus: TrackingStatus = TrackingStatus.Idle
    private var isForeground = false
    private var currentIntervalSeconds: Long = 5L
    private var lastRecordedSample: TrackingSample? = null
    private var totalSamplesSinceWindowReset = 0
    private var rejectedSamplesSinceWindowReset = 0
    private var lastRejectionWarningAtMillis = 0L
    private var disablePointFiltering: Boolean = false

    override fun onCreate() {
        super.onCreate()
        applyDistanceAccuracyConfigFromManifest()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val sample = TrackingSample(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                    speedKmph = if (location.hasSpeed()) location.speed * 3.6 else null,
                    bearingDegrees = if (location.hasBearing()) location.bearing else null,
                    verticalAccuracyMeters = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters else null,
                    accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                    satelliteCount = TrackingState.satelliteCount.value,
                    timestampMillis = location.time
                )
                sample.verticalAccuracyMeters?.let {
                    Log.d("TrackingService", "Vertical accuracy: ${String.format("%.1f", it)} m")
                }
                TrackingState.updateSample(sample)
                if (currentStatus == TrackingStatus.Recording) {
                    scope.launch {
                        totalSamplesSinceWindowReset++
                        if (shouldRecordSample(sample)) {
                            trackWriter?.appendSample(sample)
                            TrackingState.incrementPointCount()
                            TrackingState.onSampleRecorded(sample)
                            TrackingState.markMovement(sample.timestampMillis)
                            lastRecordedSample = sample
                        } else {
                            rejectedSamplesSinceWindowReset++
                            TrackingState.incrementSkippedPoints()
                            updateNotMoving(sample)
                            maybeWarnOnHighRejectionRate()
                        }
                    }
                }
            }
        }
        registerGnssCallback()
    }

    private fun applyDistanceAccuracyConfigFromManifest() {
        val threshold = runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val value = appInfo.metaData?.getFloat("minAccuracyForDistanceCalc")
            value ?: 5f
        }.getOrDefault(5f)
        TrackingState.updateMinAccuracyForDistanceCalcMeters(threshold)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        trackWriter?.close(TrackingState.distanceMeters.value)
        trackWriter = null
        TrackingState.updateStatus(TrackingStatus.Idle)
        isForeground = false
        unregisterGnssCallback()
    }

    private fun startRecording() {
        if (currentStatus == TrackingStatus.Recording) return
        ensureForeground("Starting tracking")
        scope.launch {
            if (trackWriter == null) {
                val settings = settingsRepository.settingsFlow.first()
                disablePointFiltering = settings.disablePointFiltering
                currentIntervalSeconds = settings.intervalSeconds
                val handle = runCatching { fileStore.createTrackOutputStream(settings) }.getOrNull()
                    ?: run {
                        updateNotification("Failed to create file")
                        stopSelf()
                        return@launch
                    }
                trackWriter = when (settings.outputFormat) {
                    OutputFormat.GPX -> GpxWriter(handle.outputStream)
                    OutputFormat.KML -> KmlWriter(handle.outputStream)
                    OutputFormat.JSON -> JsonWriter(handle.outputStream)
                }.apply { writeHeader() }
                TrackingState.resetPointCount()
                TrackingState.updateCurrentFileName(handle.filename)
            }
            currentStatus = TrackingStatus.Recording
            TrackingState.updateStatus(currentStatus)
            TrackingState.onRecordingStarted()
            lastRecordedSample = null
            resetRejectionCounters()
            TrackingState.resetNotMovingTimer()
            TrackingState.resetSkippedPoints()
            startLocationUpdates(currentIntervalSeconds)
            updateNotification("Recording")
        }
    }

    private fun pauseRecording() {
        if (currentStatus != TrackingStatus.Recording) return
        stopLocationUpdates()
        currentStatus = TrackingStatus.Paused
        TrackingState.updateStatus(currentStatus)
        TrackingState.onRecordingPaused()
        updateNotification("Paused")
    }

    private fun stopRecording() {
        if (currentStatus == TrackingStatus.Idle) return
        stopLocationUpdates()
        trackWriter?.close(TrackingState.distanceMeters.value)
        trackWriter = null
        currentStatus = TrackingStatus.Idle
        TrackingState.updateStatus(currentStatus)
        TrackingState.onRecordingStopped()
        lastRecordedSample = null
        resetRejectionCounters()
        TrackingState.resetNotMovingTimer()
        TrackingState.resetSkippedPoints()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
        stopSelf()
    }

    private fun shouldRecordSample(sample: TrackingSample): Boolean {
        if (disablePointFiltering) return true
        if (lastRecordedSample == null) return true // Always accept the first sample
        val accuracy = sample.accuracyMeters
        val minAccuracy = TrackingState.minAccuracyForDistanceCalcMeters.value
        if (accuracy != null && accuracy > minAccuracy) return false

        val previous = lastRecordedSample ?: return true
        val result = FloatArray(1)
        Location.distanceBetween(
            previous.latitude,
            previous.longitude,
            sample.latitude,
            sample.longitude,
            result
        )
        val distance = result[0].toDouble()
        if (distance >= minAccuracy.toDouble()) {
            TrackingState.markMovement(sample.timestampMillis)
            return true
        }
        updateNotMoving(sample)
        return false
    }

    private fun updateNotMoving(sample: TrackingSample) {
        if (!disablePointFiltering) {
            TrackingState.updateNotMoving(sample.timestampMillis)
        }
    }

    private fun maybeWarnOnHighRejectionRate() {
        if (totalSamplesSinceWindowReset < REJECTION_WINDOW_SAMPLES) return
        val ratio = rejectedSamplesSinceWindowReset.toDouble() / totalSamplesSinceWindowReset.toDouble()
        if (ratio < REJECTION_RATIO_THRESHOLD) return

        val now = System.currentTimeMillis()
        if (now - lastRejectionWarningAtMillis < REJECTION_WARNING_COOLDOWN_MS) return

        updateNotification("Low accuracy: many points skipped")
        lastRejectionWarningAtMillis = now
        resetRejectionCounters()
    }

    private fun resetRejectionCounters() {
        totalSamplesSinceWindowReset = 0
        rejectedSamplesSinceWindowReset = 0
    }

    private fun startLocationUpdates(intervalSeconds: Long) {
        if (!hasLocationPermission()) {
            updateNotification("Location permission missing")
            stopSelf()
            return
        }
        val intervalMillis = intervalSeconds.coerceAtLeast(1L) * 1000L
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis)
            .setMaxUpdateDelayMillis(intervalMillis)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun registerGnssCallback() {
        if (!hasLocationPermission() || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (gnssStatusCallback != null) return
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                TrackingState.updateSatelliteCount(status.satelliteCount)
            }
        }
        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                locationManager.registerGnssStatusCallback(mainExecutor, callback)
            }.isSuccess
        } else {
            val handler = Handler(Looper.getMainLooper())
            runCatching {
                locationManager.registerGnssStatusCallback(callback, handler)
            }.isSuccess
        }
        if (!success) {
            gnssStatusCallback = null
        }
    }

    private fun unregisterGnssCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        gnssStatusCallback?.let { callback ->
            locationManager.unregisterGnssStatusCallback(callback)
            gnssStatusCallback = null
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private fun ensureForeground(status: String) {
        val notification = buildNotification(status)
        if (!isForeground) {
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(status: String) {
        if (!isForeground) {
            ensureForeground(status)
            return
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: String) =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("GPS Tracking")
            .setContentText(status)
            .setContentIntent(mainActivityPendingIntent())
            .setOngoing(true)
            .build()

    private fun mainActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }
}
