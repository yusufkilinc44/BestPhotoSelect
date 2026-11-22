package com.bestphotoselect.data

import com.bestphotoselect.data.db.IgnoredGroupDao
import com.bestphotoselect.data.db.IgnoredGroupEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kullanıcının "bir daha gösterme" dediği grupların kalıcı kaydı. Bkz.
 * [IgnoredGroupEntity] — imza, grup üyesi fotoğraf ID'lerinin sıralı birleşimidir.
 */
@Singleton
class IgnoredGroupsRepository @Inject constructor(
    private val dao: IgnoredGroupDao
) {
    val ignoredCount: Flow<Int> = dao.observeCount()

    private fun signature(memberIds: List<Long>): String = memberIds.sorted().joinToString(",")

    suspend fun ignoredSignatures(): Set<String> = dao.allSignatures().toSet()

    suspend fun ignore(memberIds: List<Long>) {
        dao.insert(IgnoredGroupEntity(signature(memberIds)))
    }

    /** Aynı imza biçimini kullanan eski (lite) kayıtları içe aktarır. */
    suspend fun importSignatures(signatures: Collection<String>) {
        if (signatures.isEmpty()) return
        dao.insertAll(signatures.map { IgnoredGroupEntity(it) })
    }

    fun isIgnored(memberIds: List<Long>, signatures: Set<String>): Boolean =
        signature(memberIds) in signatures

    suspend fun clearAll() = dao.clear()
}
