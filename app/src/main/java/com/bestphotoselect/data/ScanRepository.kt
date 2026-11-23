package com.bestphotoselect.data

import com.bestphotoselect.data.model.PhotoGroup
import com.bestphotoselect.data.model.ScanPhase
import com.bestphotoselect.data.model.ScanState
import com.bestphotoselect.data.model.ScoringWeights
import com.bestphotoselect.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Etkileşimli tarama oturumunun durumu. Uygulama süreci yaşadığı sürece sonuçlar
 * bellekte tutulur; ekranlar arasında gezinmek taramayı bozmaz.
 */
@OptIn(FlowPreview::class)
@Singleton
class ScanRepository @Inject constructor(
    private val scanEngine: ScanEngine,
    private val settingsRepository: SettingsRepository,
    private val ignoredGroupsRepository: IgnoredGroupsRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    private companion object {
        const val RESCAN_DEBOUNCE_MS = 800L
    }

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private var scanJob: Job? = null

    /** Son taramanın albümleri; ayar değişince aynı albümler yeniden taranır. */
    private var lastBucketIds: Set<Long>? = null

    init {
        // Gruplamayı ya da "en iyi" seçimini etkileyen bir ayar değişirse (kaydırıcı
        // bırakıldıktan kısa süre sonra) eldeki sonuçlar yeni ayarlarla baştan taranır.
        // Fotoğraf başına ölçümler (parmak izi, netlik, yüz, etiketler) ayarlardan
        // bağımsız olduğu için önbellekten gelir; tekrar tarama hızlıdır.
        scope.launch {
            settingsRepository.settings
                .map { ScanSettingsKey(it.hammingThreshold, it.timeWindowSec, it.scoringWeights) }
                .debounce(RESCAN_DEBOUNCE_MS)
                .distinctUntilChanged()
                .drop(1)
                .collect { rescanIfActive() }
        }
        // Yoksayılan gruplar sıfırlanınca o gruplar yeniden önerilebilsin.
        scope.launch {
            var previous: Int? = null
            ignoredGroupsRepository.ignoredCount.collect { count ->
                if (previous != null && count < previous!!) rescanIfActive()
                previous = count
            }
        }
    }

    private data class ScanSettingsKey(
        val hamming: Int,
        val timeWindowSec: Int,
        val weights: ScoringWeights
    )

    /** Eldeki (ya da süren) tarama varsa aynı albümlerle yeniden başlatır. */
    private fun rescanIfActive() {
        val buckets = lastBucketIds ?: return
        when (_state.value) {
            is ScanState.Done, is ScanState.Running, is ScanState.Failed -> startScan(buckets)
            ScanState.Idle -> Unit
        }
    }

    fun startScan(bucketIds: Set<Long>) {
        lastBucketIds = bucketIds
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

    /**
     * Arka plandaki otomatik taramanın sonuçlarını, kullanıcı bildirime dokunup
     * uygulamayı açınca gözden geçirebilsin diye oturuma koyar. O sırada
     * etkileşimli bir tarama sürüyorsa ona dokunulmaz.
     */
    fun publishBackgroundResults(groups: List<PhotoGroup>, bucketIds: Set<Long>) {
        if (_state.value is ScanState.Running) return
        lastBucketIds = bucketIds
        _state.value = ScanState.Done(groups)
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

    /**
     * Grubu "bir daha gösterme": üyelerin imzası kalıcı olarak kaydedilir
     * (bkz. IgnoredGroupsRepository) — bir sonraki taramada bu tam fotoğraf
     * kümesi ARTIK HİÇ grup olarak önerilmez. Ayrıca oturumdan da düşürülür.
     */
    fun skipGroup(groupId: Int) {
        val memberIds = currentGroups().firstOrNull { it.id == groupId }?.photos?.map { it.photo.id }
        if (memberIds != null) {
            scope.launch { ignoredGroupsRepository.ignore(memberIds) }
        }
        mutateGroups { groups -> groups.filterNot { it.id == groupId } }
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
                    } else if (group.isScreenshots) {
                        remaining.maxBy { it.photo.dateTakenMs }.photo.id
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
