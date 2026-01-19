package com.sj.gpsutil.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sj.gpsutil.tracking.TrackingService
import com.sj.gpsutil.tracking.TrackingState
import com.sj.gpsutil.tracking.TrackingStatus

@Composable
fun TrackingScreen(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val status by TrackingState.status.collectAsState()
    val latestSample by TrackingState.latestSample.collectAsState()
    var pendingStart by remember { mutableStateOf(false) }
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = requiredPermissions.all { permission ->
            result[permission] == true
        }
        if (pendingStart && granted) {
            sendTrackingAction(context, TrackingService.ACTION_START)
        }
        pendingStart = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Tracking", style = MaterialTheme.typography.headlineSmall)
        val providerDisplay = latestSample?.provider?.uppercase() ?: "--"
        Text(
            "Status: ${status.name}  |  Provider: $providerDisplay",
            style = MaterialTheme.typography.bodyLarge
        )

        val locationText = latestSample?.let {
            "${"%.5f".format(it.latitude)}, ${"%.5f".format(it.longitude)}"
        } ?: "--"
        val altitudeText = latestSample?.altitudeMeters?.let { "%.1f m".format(it) } ?: "--"
        val speedText = latestSample?.speedKmph?.let { "%.1f km/h".format(it) } ?: "--"
        val bearingText = latestSample?.bearingDegrees?.let { "%.1f°".format(it) } ?: "--"
        val verticalAccuracyText = latestSample?.verticalAccuracyMeters?.let { "±%.1f m".format(it) } ?: "--"

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Last location: $locationText")
            Text("Altitude: $altitudeText")
            Text("Vertical accuracy: $verticalAccuracyText")
            Text("Speed: $speedText")
            Text("Bearing: $bearingText")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val missing = requiredPermissions.filterNot { permission ->
                        ContextCompat.checkSelfPermission(context, permission) ==
                            PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        pendingStart = true
                        permissionsLauncher.launch(missing.toTypedArray())
                    } else {
                        sendTrackingAction(context, TrackingService.ACTION_START)
                    }
                },
                enabled = status != TrackingStatus.Recording
            ) {
                Text("Start")
            }
            OutlinedButton(
                onClick = { sendTrackingAction(context, TrackingService.ACTION_PAUSE) },
                enabled = status == TrackingStatus.Recording
            ) {
                Text("Pause")
            }
            OutlinedButton(
                onClick = { sendTrackingAction(context, TrackingService.ACTION_STOP) },
                enabled = status != TrackingStatus.Idle
            ) {
                Text("Stop")
            }
        }
    }
}

private fun sendTrackingAction(context: Context, action: String) {
    val intent = Intent(context, TrackingService::class.java).apply {
        this.action = action
    }
    if (action == TrackingService.ACTION_START && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.startForegroundService(context, intent)
    } else {
        context.startService(intent)
    }
}
