package com.sj.gpsutil.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.sj.gpsutil.data.OutputFormat
import com.sj.gpsutil.data.SettingsRepository
import com.sj.gpsutil.data.TrackingSettings
import com.sj.gpsutil.data.CalibrationSettings
import com.sj.gpsutil.data.MIN_INTERVAL_SECONDS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val settings by repository.settingsFlow.collectAsState(initial = TrackingSettings())
    var intervalText by remember(settings.intervalSeconds) {
        mutableStateOf(settings.intervalSeconds.toString())
    }
    var folderLabel by remember(settings.folderUri) {
        mutableStateOf(folderPathFromUri(context, settings.folderUri))
    }
    var showCalibration by remember { mutableStateOf(false) }

    // Calibration state (as strings for TextFields)
    val cal = settings.calibration
    var rmsSmoothMax by remember(cal) { mutableStateOf(cal.rmsSmoothMax.toString()) }
    var rmsAverageMax by remember(cal) { mutableStateOf(cal.rmsAverageMax.toString()) }
    var peakThresholdZ by remember(cal) { mutableStateOf(cal.peakThresholdZ.toString()) }
    var symmetricBumpThreshold by remember(cal) { mutableStateOf(cal.symmetricBumpThreshold.toString()) }
    var potholeDipThreshold by remember(cal) { mutableStateOf(cal.potholeDipThreshold.toString()) }
    var bumpSpikeThreshold by remember(cal) { mutableStateOf(cal.bumpSpikeThreshold.toString()) }
    var peakCountSmoothMax by remember(cal) { mutableStateOf(cal.peakCountSmoothMax.toString()) }
    var peakCountAverageMax by remember(cal) { mutableStateOf(cal.peakCountAverageMax.toString()) }
    var movingAverageWindow by remember(cal) { mutableStateOf(cal.movingAverageWindow.toString()) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            takePersistablePermission(context, uri)
            folderLabel = folderPathFromUri(context, uri.toString())
            scope.launch(Dispatchers.IO) {
                repository.updateFolderUri(uri.toString())
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Interval (sec)")
            TextField(
                modifier = Modifier.width(80.dp),
                value = intervalText,
                onValueChange = { newValue ->
                    val digitsOnly = newValue.filter { it.isDigit() }.take(2)
                    intervalText = digitsOnly
                },
                singleLine = true
            )
            Button(
                modifier = Modifier.width(88.dp),
                onClick = {
                    val parsed = intervalText.toLongOrNull()
                    if (parsed == null) {
                        Toast.makeText(context, "Enter a valid number of seconds", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (parsed < MIN_INTERVAL_SECONDS) {
                        Toast.makeText(context, "Minimum ${MIN_INTERVAL_SECONDS}s", Toast.LENGTH_LONG).show()
                    }
                    val seconds = parsed.coerceAtLeast(MIN_INTERVAL_SECONDS)
                    intervalText = seconds.toString()
                    scope.launch(Dispatchers.IO) {
                        repository.updateIntervalSeconds(seconds)
                    }
                    Toast.makeText(context, "Interval set to ${seconds}s", Toast.LENGTH_LONG).show()
                }
            ) {
                Text("Save")
            }
        }
        val presetOptions = listOf(5L, 10L, 15L, 30L)
        Text("Quick select:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presetOptions.forEach { seconds ->
                OutlinedButton(onClick = {
                    intervalText = seconds.toString()
                    scope.launch(Dispatchers.IO) {
                        repository.updateIntervalSeconds(seconds)
                    }
                    Toast.makeText(context, "Interval set to ${seconds}s", Toast.LENGTH_LONG).show()
                }) {
                    Text("${seconds}s")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Disable point filtering")
            val disableFilteringState = rememberUpdatedState(settings.disablePointFiltering)
            Switch(
                checked = disableFilteringState.value,
                onCheckedChange = { checked ->
                    scope.launch(Dispatchers.IO) {
                        repository.updateDisablePointFiltering(checked)
                    }
                    Toast.makeText(
                        context,
                        if (checked) "Point filtering disabled" else "Point filtering enabled",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Record acceleration")
            val recordAccelState = rememberUpdatedState(settings.enableAccelerometer)
            Switch(
                checked = recordAccelState.value,
                onCheckedChange = { checked ->
                    scope.launch(Dispatchers.IO) {
                        repository.updateEnableAccelerometer(checked)
                    }
                    Toast.makeText(
                        context,
                        if (checked) "Acceleration recording enabled" else "Acceleration recording disabled",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calibration entry point
        Button(onClick = { showCalibration = true }) {
            Text("Calibration")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Output format:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val currentFormat = settings.outputFormat
            OutlinedButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        repository.updateOutputFormat(OutputFormat.KML)
                    }
                    Toast.makeText(context, "Output format: KML", Toast.LENGTH_LONG).show()
                },
                enabled = currentFormat != OutputFormat.KML
            ) {
                Text("KML")
            }
            OutlinedButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        repository.updateOutputFormat(OutputFormat.GPX)
                    }
                    Toast.makeText(context, "Output format: GPX", Toast.LENGTH_LONG).show()
                },
                enabled = currentFormat != OutputFormat.GPX
            ) {
                Text("GPX")
            }
            OutlinedButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        repository.updateOutputFormat(OutputFormat.JSON)
                    }
                    Toast.makeText(context, "Output format: JSON", Toast.LENGTH_LONG).show()
                },
                enabled = currentFormat != OutputFormat.JSON
            ) {
                Text("JSON")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Save folder: ${folderLabel ?: defaultDownloadsPath(context)}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { folderPicker.launch(null) }) {
                Text("Choose folder")
            }
            OutlinedButton(onClick = {
                folderLabel = null
                scope.launch(Dispatchers.IO) {
                    repository.updateFolderUri(null)
                }
            }) {
                Text("Use Downloads")
            }
        }
    }

    if (showCalibration) {
        CalibrationDialog(
            initialValues = settings.calibration,
            rmsSmoothMaxText = rmsSmoothMax,
            rmsAverageMaxText = rmsAverageMax,
            peakThresholdZText = peakThresholdZ,
            symmetricBumpThresholdText = symmetricBumpThreshold,
            potholeDipThresholdText = potholeDipThreshold,
            bumpSpikeThresholdText = bumpSpikeThreshold,
            peakCountSmoothMaxText = peakCountSmoothMax,
            peakCountAverageMaxText = peakCountAverageMax,
            movingAverageWindowText = movingAverageWindow,
            onValuesChange = { newVals ->
                rmsSmoothMax = newVals.rmsSmoothMax
                rmsAverageMax = newVals.rmsAverageMax
                peakThresholdZ = newVals.peakThresholdZ
                symmetricBumpThreshold = newVals.symmetricBumpThreshold
                potholeDipThreshold = newVals.potholeDipThreshold
                bumpSpikeThreshold = newVals.bumpSpikeThreshold
                peakCountSmoothMax = newVals.peakCountSmoothMax
                peakCountAverageMax = newVals.peakCountAverageMax
                movingAverageWindow = newVals.movingAverageWindow
            },
            onResetDefaults = {
                val defaults = CalibrationSettings()
                rmsSmoothMax = defaults.rmsSmoothMax.toString()
                rmsAverageMax = defaults.rmsAverageMax.toString()
                peakThresholdZ = defaults.peakThresholdZ.toString()
                symmetricBumpThreshold = defaults.symmetricBumpThreshold.toString()
                potholeDipThreshold = defaults.potholeDipThreshold.toString()
                bumpSpikeThreshold = defaults.bumpSpikeThreshold.toString()
                peakCountSmoothMax = defaults.peakCountSmoothMax.toString()
                peakCountAverageMax = defaults.peakCountAverageMax.toString()
                movingAverageWindow = defaults.movingAverageWindow.toString()
            },
            onSave = {
                val parsed = parseCalibration(
                    rmsSmoothMax,
                    rmsAverageMax,
                    peakThresholdZ,
                    symmetricBumpThreshold,
                    potholeDipThreshold,
                    bumpSpikeThreshold,
                    peakCountSmoothMax,
                    peakCountAverageMax,
                    movingAverageWindow
                )
                if (parsed == null) {
                    Toast.makeText(context, "Enter valid calibration numbers", Toast.LENGTH_LONG).show()
                    return@CalibrationDialog
                }
                scope.launch(Dispatchers.IO) {
                    repository.updateCalibration(parsed)
                }
                Toast.makeText(context, "Calibration saved", Toast.LENGTH_LONG).show()
                showCalibration = false
            },
            onDismiss = { showCalibration = false }
        )
    }
}

@Composable
private fun CalibrationDialog(
    initialValues: CalibrationSettings,
    rmsSmoothMaxText: String,
    rmsAverageMaxText: String,
    peakThresholdZText: String,
    symmetricBumpThresholdText: String,
    potholeDipThresholdText: String,
    bumpSpikeThresholdText: String,
    peakCountSmoothMaxText: String,
    peakCountAverageMaxText: String,
    movingAverageWindowText: String,
    onValuesChange: (CalibrationTextValues) -> Unit,
    onResetDefaults: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calibration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CalibrationField("RMS smooth max", rmsSmoothMaxText) { onValuesChange(CalibrationTextValues(it, rmsAverageMaxText, peakThresholdZText, symmetricBumpThresholdText, potholeDipThresholdText, bumpSpikeThresholdText, peakCountSmoothMaxText, peakCountAverageMaxText, movingAverageWindowText)) }
                CalibrationField("RMS average max", rmsAverageMaxText) { onValuesChange(CalibrationTextValues(rmsSmoothMaxText, it, peakThresholdZText, symmetricBumpThresholdText, potholeDipThresholdText, bumpSpikeThresholdText, peakCountSmoothMaxText, peakCountAverageMaxText, movingAverageWindowText)) }
                CalibrationField("Peak threshold Z", peakThresholdZText) { onValuesChange(CalibrationTextValues(rmsSmoothMaxText, rmsAverageMaxText, it, symmetricBumpThresholdText, potholeDipThresholdText, bumpSpikeThresholdText, peakCountSmoothMaxText, peakCountAverageMaxText, movingAverageWindowText)) }
                CalibrationField("Speed bump threshold", symmetricBumpThresholdText) { onValuesChange(CalibrationTextValues(rmsSmoothMaxText, rmsAverageMaxText, peakThresholdZText, it, potholeDipThresholdText, bumpSpikeThresholdText, peakCountSmoothMaxText, peakCountAverageMaxText, movingAverageWindowText)) }
                CalibrationField("Pothole dip threshold", potholeDipThresholdText) { onValuesChange(CalibrationTextValues(rmsSmoothMaxText, rmsAverageMaxText, peakThresholdZText, symmetricBumpThresholdText, it, bumpSpikeThresholdText, peakCountSmoothMaxText, peakCountAverageMaxText, movingAverageWindowText)) }
                CalibrationField("Bump spike threshold", bumpSpikeThresholdText) { onValuesChange(CalibrationTextValues(rmsSmoothMaxText, rmsAverageMaxText, peakThresholdZText, symmetricBumpThresholdText, potholeDipThresholdText, it, peakCountSmoothMaxText, peakCountAverageMaxText, movingAverageWindowText)) }
                CalibrationField("Peak count smooth max", peakCountSmoothMaxText) { onValuesChange(CalibrationTextValues(rmsSmoothMaxText, rmsAverageMaxText, peakThresholdZText, symmetricBumpThresholdText, potholeDipThresholdText, bumpSpikeThresholdText, it, peakCountAverageMaxText, movingAverageWindowText)) }
                CalibrationField("Peak count average max", peakCountAverageMaxText) { onValuesChange(CalibrationTextValues(rmsSmoothMaxText, rmsAverageMaxText, peakThresholdZText, symmetricBumpThresholdText, potholeDipThresholdText, bumpSpikeThresholdText, peakCountSmoothMaxText, it, movingAverageWindowText)) }
                CalibrationField("Moving average window", movingAverageWindowText) { onValuesChange(CalibrationTextValues(rmsSmoothMaxText, rmsAverageMaxText, peakThresholdZText, symmetricBumpThresholdText, potholeDipThresholdText, bumpSpikeThresholdText, peakCountSmoothMaxText, peakCountAverageMaxText, it)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onResetDefaults) { Text("Reset to Defaults") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun CalibrationField(label: String, value: String, onChange: (String) -> Unit) {
    TextField(
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        value = value,
        onValueChange = { input ->
            onChange(input.filter { it.isDigit() || it == '.' || it == '-' })
        },
        singleLine = true
    )
}

private data class CalibrationTextValues(
    val rmsSmoothMax: String,
    val rmsAverageMax: String,
    val peakThresholdZ: String,
    val symmetricBumpThreshold: String,
    val potholeDipThreshold: String,
    val bumpSpikeThreshold: String,
    val peakCountSmoothMax: String,
    val peakCountAverageMax: String,
    val movingAverageWindow: String
)

private fun parseCalibration(
    rmsSmoothMax: String,
    rmsAverageMax: String,
    peakThresholdZ: String,
    symmetricBumpThreshold: String,
    potholeDipThreshold: String,
    bumpSpikeThreshold: String,
    peakCountSmoothMax: String,
    peakCountAverageMax: String,
    movingAverageWindow: String
): CalibrationSettings? {
    val rmsSmooth = rmsSmoothMax.toFloatOrNull() ?: return null
    val rmsAvg = rmsAverageMax.toFloatOrNull() ?: return null
    val peakThresh = peakThresholdZ.toFloatOrNull() ?: return null
    val sym = symmetricBumpThreshold.toFloatOrNull() ?: return null
    val pothole = potholeDipThreshold.toFloatOrNull() ?: return null
    val bump = bumpSpikeThreshold.toFloatOrNull() ?: return null
    val peakSmooth = peakCountSmoothMax.toIntOrNull() ?: return null
    val peakAvg = peakCountAverageMax.toIntOrNull() ?: return null
    val maWindow = movingAverageWindow.toIntOrNull() ?: return null
    if (maWindow <= 0) return null
    return CalibrationSettings(
        rmsSmoothMax = rmsSmooth,
        rmsAverageMax = rmsAvg,
        peakThresholdZ = peakThresh,
        symmetricBumpThreshold = sym,
        potholeDipThreshold = pothole,
        bumpSpikeThreshold = bump,
        peakCountSmoothMax = peakSmooth,
        peakCountAverageMax = peakAvg,
        movingAverageWindow = maWindow
    )
}

private fun takePersistablePermission(context: Context, uri: Uri) {
    val flags = IntentFlags.FLAG_GRANT_READ or IntentFlags.FLAG_GRANT_WRITE
    context.contentResolver.takePersistableUriPermission(uri, flags)
}

private fun folderPathFromUri(context: Context, uriString: String?): String? {
    if (uriString.isNullOrBlank()) return null
    val uri = Uri.parse(uriString)
    return runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val parts = documentId.split(":", limit = 2)
        val root = parts.getOrNull(0) ?: "primary"
        val relativePath = parts.getOrNull(1)
        val basePath = if (root.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$root"
        }
        if (relativePath.isNullOrBlank()) basePath else "$basePath/${relativePath.trimStart('/')}"
    }.getOrElse {
        androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name
    }
}

private fun defaultDownloadsPath(context: Context): String {
    return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
        ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
        ?: "Downloads"
}

private object IntentFlags {
    const val FLAG_GRANT_READ = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
    const val FLAG_GRANT_WRITE = android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
}
