package com.bestphotoselect.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestphotoselect.data.MediaStoreDataSource
import com.bestphotoselect.data.ScanRepository
import com.bestphotoselect.data.SettingsRepository
import com.bestphotoselect.data.model.Album
import com.bestphotoselect.data.model.ThemeMode
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

    val manageMediaPrompted: StateFlow<Boolean?> = settingsRepository.manageMediaPrompted
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val albumsAsList: StateFlow<Boolean> = settingsRepository.albumsAsList
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    val includeWhatsApp: StateFlow<Boolean> = settingsRepository.includeWhatsApp
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val whatsAppBuckets: Set<Long>
        get() = _albums.value.orEmpty().filter { it.isWhatsApp }.map { it.bucketId }.toSet()

    /** Kapatılınca WhatsApp klasörleri listeden gizlenir ve seçimden çıkarılır. */
    fun setIncludeWhatsApp(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setIncludeWhatsApp(value)
            if (!value) {
                settingsRepository.setSelectedBuckets(selectedBuckets.value - whatsAppBuckets)
            }
        }
    }

    fun setAlbumsAsList(value: Boolean) {
        viewModelScope.launch { settingsRepository.setAlbumsAsList(value) }
    }

    fun markManageMediaPrompted() {
        viewModelScope.launch { settingsRepository.setManageMediaPrompted() }
    }

    fun loadAlbums() {
        viewModelScope.launch {
            _albums.value = mediaStore.queryAlbums()
        }
    }

    fun toggleAlbum(bucketId: Long) {
        viewModelScope.launch {
            if (!includeWhatsApp.value && bucketId in whatsAppBuckets) return@launch
            val current = selectedBuckets.value
            val next = if (bucketId in current) current - bucketId else current + bucketId
            settingsRepository.setSelectedBuckets(next)
        }
    }

    fun selectAll() {
        viewModelScope.launch {
            val includeWa = includeWhatsApp.value
            settingsRepository.setSelectedBuckets(
                _albums.value.orEmpty()
                    .filter { includeWa || !it.isWhatsApp }
                    .map { it.bucketId }
                    .toSet()
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
