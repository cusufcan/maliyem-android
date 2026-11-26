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

    /** Boş limit bütçeyi kaldırır. Düzenlemede kimlik ve oluşturma zamanı korunur. */
    suspend fun setLimit(categoryId: Long?, limitMinor: Long?) {
        if (categoryId == null) {
            if (limitMinor == null) dao.deleteOverall() else dao.saveOverall(OverallBudgetEntity(limitMinor = limitMinor))
        } else if (limitMinor == null) {
            dao.delete(categoryId)
        } else {
            db.withTransaction {
                check(dao.categoryType(categoryId) == TransactionType.EXPENSE)
                val existing = dao.getForCategory(categoryId)
                dao.save(
                    existing?.copy(limitMinor = limitMinor)
                        ?: BudgetEntity(categoryId = categoryId, limitMinor = limitMinor, createdAt = System.currentTimeMillis()),
                )
            }
        }
    }
}
