package com.mercan.maliyem.data.repository

import com.mercan.maliyem.data.local.CategoryDao
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.TransactionType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CategoryRepository {
    /** [type] türündeki kategoriler, kullanıcının sırasıyla. */
    fun observeByType(type: TransactionType): Flow<List<CategoryEntity>>
}

class RoomCategoryRepository @Inject constructor(
    private val dao: CategoryDao,
) : CategoryRepository {
    // Kategori sayısı az; ayrı sorgu yerine tüm listeyi süzmek yeterli.
    override fun observeByType(type: TransactionType) = dao.observeAll().map { all -> all.filter { it.type == type } }
}
