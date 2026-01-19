package com.sj.gpsutil.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SETTINGS_STORE_NAME = "tracking_settings"

val Context.settingsDataStore by preferencesDataStore(SETTINGS_STORE_NAME)

data class TrackingSettings(
    val intervalSeconds: Long = 5L,
    val folderUri: String? = null
)

class SettingsRepository(private val context: Context) {
    private val intervalKey = longPreferencesKey("interval_seconds")
    private val folderUriKey = stringPreferencesKey("folder_uri")

    val settingsFlow: Flow<TrackingSettings> = context.settingsDataStore.data.map { prefs ->
        TrackingSettings(
            intervalSeconds = prefs[intervalKey] ?: 5L,
            folderUri = prefs[folderUriKey]
        )
    }

    suspend fun updateIntervalSeconds(seconds: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[intervalKey] = seconds
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
}
