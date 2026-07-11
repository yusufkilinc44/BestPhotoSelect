package com.bestphotoselect.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Fotoğraf başına analiz önbelleği. dateModified değişmediği sürece hash ve
 * kalite metrikleri yeniden hesaplanmaz; tekrar taramalar bu sayede hızlıdır.
 */
@Entity(tableName = "photo_cache")
data class PhotoCacheEntity(
    @PrimaryKey val mediaId: Long,
    val dateModified: Long,
    val hash: Long,
    val sharpness: Double?,
    val exposure: Float?,
    val faceCount: Int?,
    val eyesOpen: Float?,
    val frontal: Float?,
    val smile: Float?,
    val faceAreaRatio: Float?
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val sizeBytes: Long,
    val deletedAtMs: Long,
    val wasAuto: Boolean,
    val trashed: Boolean
)

@Dao
interface PhotoCacheDao {
    @Query("SELECT * FROM photo_cache WHERE mediaId IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<PhotoCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entities: List<PhotoCacheEntity>)

    @Query("DELETE FROM photo_cache WHERE mediaId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY deletedAtMs DESC LIMIT 500")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insertAll(entries: List<HistoryEntity>)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Database(
    entities = [PhotoCacheEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoCacheDao(): PhotoCacheDao
    abstract fun historyDao(): HistoryDao
}
