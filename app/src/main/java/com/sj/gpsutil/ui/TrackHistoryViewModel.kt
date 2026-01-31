package com.sj.gpsutil.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sj.gpsutil.data.TrackingSettings
import com.sj.gpsutil.tracking.TrackHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TrackHistoryUiState(
    val tracks: List<TrackHistoryRepository.TrackFileInfo> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearchCompleted: Boolean = false,
    val errorMessage: String? = null,
    val lastLoadedFolderUri: String? = null
)

class TrackHistoryViewModel(private val repository: TrackHistoryRepository) : ViewModel() {
    private val _state = MutableStateFlow(TrackHistoryUiState())
    val state: StateFlow<TrackHistoryUiState> = _state

    fun shouldRefresh(settings: TrackingSettings): Boolean {
        val targetFolder = settings.folderUri
        if (targetFolder == null) return false
        val current = _state.value
        return current.lastLoadedFolderUri != targetFolder || current.tracks.isEmpty()
    }

    fun refresh(settings: TrackingSettings) {
        val targetFolder = settings.folderUri ?: return
        _state.value = _state.value.copy(
            isSearching = true,
            hasSearchCompleted = false,
            errorMessage = null
        )
        viewModelScope.launch {
            val result = runCatching { repository.listTracks(settings) }
            val tracks = result.getOrNull()
            val error = result.exceptionOrNull()?.localizedMessage
            _state.value = _state.value.copy(
                tracks = tracks ?: emptyList(),
                isSearching = false,
                hasSearchCompleted = true,
                errorMessage = error,
                lastLoadedFolderUri = targetFolder
            )
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = TrackHistoryRepository(context.applicationContext)
                @Suppress("UNCHECKED_CAST")
                return TrackHistoryViewModel(repo) as T
            }
        }
    }
}
