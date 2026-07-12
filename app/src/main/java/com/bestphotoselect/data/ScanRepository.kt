package com.bestphotoselect.data

import com.bestphotoselect.data.model.PhotoGroup
import com.bestphotoselect.data.model.ScanPhase
import com.bestphotoselect.data.model.ScanState
import com.bestphotoselect.di.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Etkileşimli tarama oturumunun durumu. Uygulama süreci yaşadığı sürece sonuçlar
 * bellekte tutulur; ekranlar arasında gezinmek taramayı bozmaz.
 */
@Singleton
class ScanRepository @Inject constructor(
    private val scanEngine: ScanEngine,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun startScan(bucketIds: Set<Long>) {
        scanJob?.cancel()
        _state.value = ScanState.Running(ScanPhase.READING, 0, 0)
        scanJob = scope.launch {
            try {
                val settings = settingsRepository.current()
                val groups = scanEngine.scan(bucketIds, settings) { progress ->
                    _state.value = when (progress) {
                        is ScanEngine.Progress.Reading ->
                            ScanState.Running(ScanPhase.READING, 0, 0)
                        is ScanEngine.Progress.Hashing ->
                            ScanState.Running(ScanPhase.HASHING, progress.done, progress.total)
                        is ScanEngine.Progress.Grouping ->
                            ScanState.Running(ScanPhase.GROUPING, 0, 0)
                        is ScanEngine.Progress.Scoring ->
                            ScanState.Running(ScanPhase.SCORING, progress.done, progress.total)
                    }
                }
                _state.value = ScanState.Done(groups)
            } catch (e: CancellationException) {
                _state.value = ScanState.Idle
                throw e
            } catch (e: Exception) {
                _state.value = ScanState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _state.value = ScanState.Idle
    }

    fun reset() {
        cancelScan()
    }

    /**
     * "En iyi" işaretli fotoğraf dahil, gruptaki HERHANGİ bir fotoğraf silinmeye
     * işaretlenebilir/işareti kaldırılabilir — kullanıcı yapay zekanın seçimine
     * katılmıyorsa veya tüm grubu temizlemek istiyorsa buna izin verilir.
     */
    fun toggleDeletion(groupId: Int, photoId: Long) = mutateGroups { groups ->
        groups.map { group ->
            if (group.id != groupId) group
            else group.copy(photos = group.photos.map { sp ->
                if (sp.photo.id == photoId) sp.copy(markedForDeletion = !sp.markedForDeletion) else sp
            })
        }
    }

    fun setBest(groupId: Int, photoId: Long) = mutateGroups { groups ->
        groups.map { group ->
            if (group.id != groupId) group
            else group.copy(
                bestPhotoId = photoId,
                photos = group.photos.map { sp ->
                    sp.copy(markedForDeletion = sp.photo.id != photoId)
                }
            )
        }
    }

    fun skipGroup(groupId: Int) = mutateGroups { groups ->
        groups.filterNot { it.id == groupId }
    }

    /** Başarıyla silinen fotoğrafları oturumdan düşürür. */
    fun onPhotosDeleted(deletedIds: Set<Long>) = mutateGroups { groups ->
        groups.mapNotNull { group ->
            val remaining = group.photos.filterNot { it.photo.id in deletedIds }
            when {
                remaining.size == group.photos.size -> group
                remaining.size < 2 -> null
                else -> {
                    val best = if (remaining.any { it.photo.id == group.bestPhotoId }) {
                        group.bestPhotoId
                    } else {
                        remaining.maxByOrNull { it.score }!!.photo.id
                    }
                    group.copy(photos = remaining, bestPhotoId = best)
                }
            }
        }
    }

    fun currentGroups(): List<PhotoGroup> =
        (_state.value as? ScanState.Done)?.groups ?: emptyList()

    private fun mutateGroups(transform: (List<PhotoGroup>) -> List<PhotoGroup>) {
        val current = _state.value
        if (current is ScanState.Done) {
            _state.value = ScanState.Done(transform(current.groups))
        }
    }
}
