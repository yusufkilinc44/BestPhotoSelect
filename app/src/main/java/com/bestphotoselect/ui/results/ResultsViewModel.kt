package com.bestphotoselect.ui.results

import android.app.PendingIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestphotoselect.data.DeletionRepository
import com.bestphotoselect.data.ScanRepository
import com.bestphotoselect.data.SettingsRepository
import com.bestphotoselect.data.model.PhotoItem
import com.bestphotoselect.data.model.ScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val deletionRepository: DeletionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    sealed interface Event {
        data class LaunchSystemDelete(val intent: PendingIntent) : Event
        data class Deleted(val count: Int) : Event
        data object DeleteFailed : Event
    }

    val state: StateFlow<ScanState> = scanRepository.state

    val trashMode: StateFlow<Boolean> = settingsRepository.settings
        .map { it.trashMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val _confirmDialogVisible = MutableStateFlow(false)
    val confirmDialogVisible: StateFlow<Boolean> = _confirmDialogVisible.asStateFlow()

    private var pendingDeletion: List<PhotoItem> = emptyList()

    fun deletionCandidates(): List<PhotoItem> =
        scanRepository.currentGroups().flatMap { it.deletionCandidates }.map { it.photo }

    fun requestDeleteConfirmation() {
        if (deletionCandidates().isNotEmpty()) _confirmDialogVisible.value = true
    }

    fun dismissConfirmDialog() {
        _confirmDialogVisible.value = false
    }

    /** Kullanıcı uygulama içi onayı verdi: sistem silme isteğini başlat. */
    fun confirmDelete() {
        _confirmDialogVisible.value = false
        viewModelScope.launch {
            val photos = deletionCandidates()
            if (photos.isEmpty()) return@launch
            pendingDeletion = photos
            val trash = settingsRepository.settings.first().trashMode
            val intent = deletionRepository.buildDeleteRequest(photos, toTrash = trash)
            _events.emit(Event.LaunchSystemDelete(intent))
        }
    }

    /** Sistem diyaloğu sonucu. */
    fun onSystemDeleteResult(approved: Boolean) {
        viewModelScope.launch {
            val photos = pendingDeletion
            pendingDeletion = emptyList()
            if (approved && photos.isNotEmpty()) {
                val trash = settingsRepository.settings.first().trashMode
                deletionRepository.recordDeletion(photos, wasAuto = false, trashed = trash)
                scanRepository.onPhotosDeleted(photos.map { it.id }.toSet())
                _events.emit(Event.Deleted(photos.count()))
            } else if (!approved) {
                _events.emit(Event.DeleteFailed)
            }
        }
    }

    fun skipGroup(groupId: Int) = scanRepository.skipGroup(groupId)
}
