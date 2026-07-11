package com.bestphotoselect.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestphotoselect.data.MediaStoreDataSource
import com.bestphotoselect.data.ScanRepository
import com.bestphotoselect.data.SettingsRepository
import com.bestphotoselect.data.model.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val mediaStore: MediaStoreDataSource,
    private val settingsRepository: SettingsRepository,
    private val scanRepository: ScanRepository
) : ViewModel() {

    private val _albums = MutableStateFlow<List<Album>?>(null)
    val albums: StateFlow<List<Album>?> = _albums.asStateFlow()

    val selectedBuckets: StateFlow<Set<Long>> = settingsRepository.settings
        .map { it.selectedBucketIds }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun loadAlbums() {
        viewModelScope.launch {
            _albums.value = mediaStore.queryAlbums()
        }
    }

    fun toggleAlbum(bucketId: Long) {
        viewModelScope.launch {
            val current = selectedBuckets.value
            val next = if (bucketId in current) current - bucketId else current + bucketId
            settingsRepository.setSelectedBuckets(next)
        }
    }

    fun selectAll() {
        viewModelScope.launch {
            settingsRepository.setSelectedBuckets(
                _albums.value?.map { it.bucketId }?.toSet() ?: emptySet()
            )
        }
    }

    fun clearSelection() {
        viewModelScope.launch { settingsRepository.setSelectedBuckets(emptySet()) }
    }

    fun startScan() {
        scanRepository.startScan(selectedBuckets.value)
    }
}
