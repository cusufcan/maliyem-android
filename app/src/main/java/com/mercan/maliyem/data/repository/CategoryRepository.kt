package com.mercan.maliyem.data.repository

import com.mercan.maliyem.data.local.CategoryDao
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.TransactionType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface CategoryRepository {
    fun observeAll(): Flow<List<CategoryEntity>>

    /** [type] türündeki kategoriler, kullanıcının sırasıyla; "Diğer" hep sonda. */
    fun observeByType(type: TransactionType): Flow<List<CategoryEntity>>

    /** Yeni kategori türünün sonuna ("Diğer"in önüne) eklenir. */
    suspend fun add(type: TransactionType, name: String, icon: String): Long

    suspend fun update(category: CategoryEntity)

    /** Kayıtları ve kuralları aynı türün "Diğer"ine taşıyıp kategoriyi siler; "Diğer"in kendisi silinmez. */
    suspend fun delete(category: CategoryEntity)
}

/** "Diğer" ve "Diğer gelir": silinemez, kayıtlar silinen kategoriden buraya geçer. */
fun CategoryEntity.isFallback() = key == DefaultCategories.OTHER_EXPENSE || key == DefaultCategories.OTHER_INCOME

internal fun fallbackKey(type: TransactionType) =
    if (type == TransactionType.INCOME) DefaultCategories.OTHER_INCOME else DefaultCategories.OTHER_EXPENSE

internal fun List<CategoryEntity>.forType(type: TransactionType): List<CategoryEntity> =
    filter { it.type == type }.sortedBy { it.isFallback() }

class RoomCategoryRepository @Inject constructor(
    private val dao: CategoryDao,
) : CategoryRepository {
    override fun observeAll() = dao.observeAll()

    // Kategori sayısı az; ayrı sorgu yerine tüm listeyi süzmek yeterli.
    override fun observeByType(type: TransactionType) =
        dao.observeAll().map { it.forType(type) }.distinctUntilChanged()

    override suspend fun add(type: TransactionType, name: String, icon: String) =
        dao.append(CategoryEntity(type = type, name = name, icon = icon, sortOrder = 0))

    override suspend fun update(category: CategoryEntity) = dao.update(category)

    override suspend fun delete(category: CategoryEntity) {
        if (!category.isFallback()) dao.deleteMovingTransactions(category.id, fallbackKey(category.type))
    }
}
