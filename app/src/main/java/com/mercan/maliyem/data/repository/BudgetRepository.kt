package com.mercan.maliyem.data.repository

import androidx.room.withTransaction
import com.mercan.maliyem.data.local.BudgetDao
import com.mercan.maliyem.data.local.BudgetEntity
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.local.OverallBudgetEntity
import com.mercan.maliyem.data.local.TransactionType
import javax.inject.Inject

class BudgetRepository @Inject constructor(
    private val db: MaliyemDatabase,
    private val dao: BudgetDao,
) {
    fun observeAll() = dao.observeAll()
    fun observeOverall() = dao.observeOverall()

    /** Boş limit bütçeyi kaldırır. Yeni bütçe eklenirse true döner. */
    suspend fun setLimit(categoryId: Long?, limitMinor: Long?): Boolean = db.withTransaction {
        if (limitMinor == null) {
            if (categoryId == null) dao.deleteOverall() else dao.delete(categoryId)
            return@withTransaction false
        }
        if (categoryId == null) {
            val created = dao.getOverall() == null
            dao.saveOverall(OverallBudgetEntity(limitMinor = limitMinor))
            return@withTransaction created
        }
        check(dao.categoryType(categoryId) == TransactionType.EXPENSE)
        val existing = dao.getForCategory(categoryId)
        dao.save(
            existing?.copy(limitMinor = limitMinor)
                ?: BudgetEntity(categoryId = categoryId, limitMinor = limitMinor, createdAt = System.currentTimeMillis()),
        )
        existing == null
    }
}
