package com.mercan.maliyem.data.backup

import androidx.room.withTransaction
import com.mercan.maliyem.data.local.CategoryDao
import com.mercan.maliyem.data.local.CategoryEntity
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.local.TransactionDao
import com.mercan.maliyem.data.local.TransactionEntity
import java.time.LocalDate
import javax.inject.Inject

interface BackupRepository {
    suspend fun snapshot(): BackupFile

    suspend fun replaceAll(validated: BackupDecodeResult.Valid)
}

class RoomBackupRepository @Inject constructor(
    private val db: MaliyemDatabase,
    private val categories: CategoryDao,
    private val transactions: TransactionDao,
) : BackupRepository {
    /** İki tablo aynı anlık görüntüden okunur; dışa aktarımda kategori bağlantıları korunur. */
    override suspend fun snapshot(): BackupFile {
        val (categoryRows, transactionRows) = db.withTransaction {
            categories.getAll() to transactions.getAll()
        }
        return BackupFile(
            format = BackupFile.FORMAT,
            version = BackupFile.VERSION,
            exportedAt = System.currentTimeMillis(),
            categories = categoryRows.map { BackupCategory(it.id, it.type, it.key, it.name, it.icon, it.sortOrder) },
            transactions = transactionRows.map {
                BackupTransaction(it.id, it.type, it.amountMinor, it.categoryId, it.date.toString(), it.note, it.createdAt)
            },
        )
    }

    /** Dış anahtar sırasıyla silip ekler; yazımlardan biri başarısız olursa tamamı geri alınır. */
    override suspend fun replaceAll(validated: BackupDecodeResult.Valid) {
        val backup = validated.backup
        val categoryRows = backup.categories.map { CategoryEntity(it.id, it.type, it.key, it.name, it.icon, it.sortOrder) }
        val transactionRows = backup.transactions.map {
            TransactionEntity(it.id, it.type, it.amountMinor, it.categoryId, LocalDate.parse(it.date), it.note, it.createdAt)
        }
        db.withTransaction {
            transactions.deleteAll()
            categories.deleteAll()
            categories.insertAll(categoryRows)
            transactions.insertAll(transactionRows)
        }
    }
}
