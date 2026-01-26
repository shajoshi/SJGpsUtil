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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.sj.gpsutil.data.OutputFormat
import com.sj.gpsutil.data.SettingsRepository
import com.sj.gpsutil.data.TrackingSettings
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                modifier = Modifier.weight(1f),
                value = intervalText,
                onValueChange = { intervalText = it },
                label = { Text("Interval (seconds)") },
                supportingText = {
                    Text("Minimum ${MIN_INTERVAL_SECONDS}s; shorter values are clamped")
                },
                singleLine = true
            )
            Button(
                modifier = Modifier.width(88.dp),
                onClick = {
                    val seconds = intervalText.toLongOrNull()?.coerceAtLeast(MIN_INTERVAL_SECONDS) ?: MIN_INTERVAL_SECONDS
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
        val intervalTooLow = intervalText.toLongOrNull()?.let { it < MIN_INTERVAL_SECONDS } ?: false
        if (intervalTooLow) {
            Text(
                text = "Value below minimum – it will be saved as ${MIN_INTERVAL_SECONDS}s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
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
