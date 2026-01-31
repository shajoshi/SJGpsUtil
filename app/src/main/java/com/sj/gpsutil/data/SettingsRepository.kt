package com.sj.gpsutil.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

const val MIN_INTERVAL_SECONDS = 1L

enum class OutputFormat {
    KML,
    GPX,
    JSON
}

private const val SETTINGS_STORE_NAME = "tracking_settings"

val Context.settingsDataStore by preferencesDataStore(SETTINGS_STORE_NAME)

data class TrackingSettings(
    val intervalSeconds: Long = MIN_INTERVAL_SECONDS,
    val folderUri: String? = null,
    val outputFormat: OutputFormat = OutputFormat.KML,
    val disablePointFiltering: Boolean = false,
    val enableAccelerometer: Boolean = true
)

class SettingsRepository(private val context: Context) {
    private val intervalKey = longPreferencesKey("interval_seconds")
    private val folderUriKey = stringPreferencesKey("folder_uri")
    private val outputFormatKey = stringPreferencesKey("output_format")
    private val disableFilteringKey = booleanPreferencesKey("disable_point_filtering")
    private val enableAccelerometerKey = booleanPreferencesKey("enable_accelerometer")

    val settingsFlow: Flow<TrackingSettings> = context.settingsDataStore.data.map { prefs ->
        TrackingSettings(
            intervalSeconds = (prefs[intervalKey] ?: MIN_INTERVAL_SECONDS).coerceAtLeast(MIN_INTERVAL_SECONDS),
            folderUri = prefs[folderUriKey],
            outputFormat = runCatching {
                prefs[outputFormatKey]?.let { OutputFormat.valueOf(it) }
            }.getOrNull() ?: OutputFormat.KML,
            disablePointFiltering = prefs[disableFilteringKey] ?: false,
            enableAccelerometer = prefs[enableAccelerometerKey] ?: true
        )
    }

    suspend fun updateIntervalSeconds(seconds: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[intervalKey] = seconds.coerceAtLeast(MIN_INTERVAL_SECONDS)
        }
    }

    suspend fun updateFolderUri(uri: String?) {
        context.settingsDataStore.edit { prefs ->
            if (uri.isNullOrBlank()) {
                prefs.remove(folderUriKey)
            } else {
                prefs[folderUriKey] = uri
            }
        }
    }

    suspend fun updateOutputFormat(format: OutputFormat) {
        context.settingsDataStore.edit { prefs ->
            prefs[outputFormatKey] = format.name
        }
    }

    suspend fun updateDisablePointFiltering(disable: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[disableFilteringKey] = disable
        }
    }

    suspend fun updateEnableAccelerometer(enable: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[enableAccelerometerKey] = enable
        }
    }
}
