package com.bestphotoselect.ui.albums

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestphotoselect.data.MediaStoreDataSource
import com.bestphotoselect.data.ScanRepository
import com.bestphotoselect.data.SettingsRepository
import com.bestphotoselect.data.model.Album
import com.bestphotoselect.data.model.ThemeMode
import com.bestphotoselect.ui.tips.Tip
import com.bestphotoselect.ui.tips.Tips
import com.bestphotoselect.util.GalleryAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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

    private val _tips = MutableStateFlow<List<Tip>>(emptyList())

    /** Bu açılışta gösterilecek ipuçları (boşsa ipucu yok). */
    val tips: StateFlow<List<Tip>> = _tips.asStateFlow()

    init {
        viewModelScope.launch {
            val ids = Tips.selectIds(
                orderedIds = Tips.ALL.map { it.id },
                seen = settingsRepository.seenTips.first(),
                lastShownAt = settingsRepository.tipsLastShownAt.first(),
                now = System.currentTimeMillis()
            )
            _tips.value = Tips.ALL.filter { it.id in ids }
        }
    }

    /** İpucu ekranda göründüğü an "görüldü" sayılır; bir daha gösterilmez. */
    fun onTipShown(tip: Tip) {
        viewModelScope.launch { settingsRepository.markTipShown(tip.id, System.currentTimeMillis()) }
    }

    fun dismissTips() {
        _tips.value = emptyList()
    }

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

    private var loadJob: Job? = null

    /**
     * Galeri değişince (fotoğraf/albüm silindi, eklendi) liste kendiliğinden
     * yenilenir. Silme sırasında çok sayıda bildirim gelebildiği için kısa bir
     * bekleme ile tek yenilemeye indirgenir.
     */
    private val galleryObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = loadAlbums(debounceMs = 500)
    }

    init {
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, galleryObserver
        )
    }

    override fun onCleared() {
        context.contentResolver.unregisterContentObserver(galleryObserver)
    }

    /**
     * Albümleri galeriden yeniden okur (ekran her göründüğünde ve galeri
     * değiştiğinde çağrılır). Galeride artık olmayan albümler seçimden de çıkarılır;
     * kısmi erişimde (Android 14+) yalnızca seçilen fotoğraflar görünür, o yüzden
     * seçime yalnızca tam erişimde dokunulur.
     */
    fun loadAlbums(debounceMs: Long = 0) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val albums = mediaStore.queryAlbums()
            _albums.value = albums
            if (GalleryAccess.current(context) == GalleryAccess.Access.FULL) {
                val existing = albums.map { it.bucketId }.toSet()
                val selected = settingsRepository.current().selectedBucketIds
                if (!existing.containsAll(selected)) {
                    settingsRepository.setSelectedBuckets(selected intersect existing)
                }
            }
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
