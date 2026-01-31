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
