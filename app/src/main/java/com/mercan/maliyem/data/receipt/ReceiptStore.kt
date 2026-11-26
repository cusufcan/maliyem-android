package com.mercan.maliyem.data.receipt

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import com.mercan.maliyem.data.local.TransactionDao
import com.mercan.maliyem.data.local.TransactionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes file maintenance with Room writes that change receipt references. */
@Singleton
class ReceiptStore internal constructor(
    private val filesDirectory: File,
    private val transactions: TransactionDao,
) {
    @Inject constructor(@ApplicationContext context: Context, transactions: TransactionDao) : this(
        context.filesDir, transactions,
    )

    private val directory = File(filesDirectory, RECEIPTS_DIRECTORY)
    private val drafts = File(filesDirectory, RECEIPT_DRAFTS_DIRECTORY)
    private val lock = Mutex()
    private val undoPins = mutableMapOf<String, Int>()
    private val maintenance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun cleanOnStart() {
        enqueue {
            val abandonedBefore = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            drafts.cleanReceipts(modifiedBefore = abandonedBefore)
            File(filesDirectory, RECEIPT_CAMERA_DIRECTORY).cleanReceipts(modifiedBefore = abandonedBefore)
            reconcile()
        }
    }

    internal suspend fun reconcile() = locked {
        val missing = transactions.receiptPaths().filter { existingPath(it) == null }
        if (missing.isNotEmpty()) transactions.clearMissingReceipts(missing)
        cleanOrphans()
    }

    internal suspend fun cleanOrphans() = withContext(NonCancellable) {
        try {
            directory.cleanReceipts(transactions.receiptPaths().toSet() + undoPins.keys)
        } catch (e: SQLiteException) {
            Log.w("ReceiptStore", "Receipt cleanup deferred", e)
        }
    }

    internal suspend fun <T> locked(operation: suspend () -> T): T =
        withContext(Dispatchers.IO) { lock.withLock { operation() } }

    internal fun existingPath(path: String?): String? = directory.existingReceipt(path)?.name

    internal suspend fun <T> save(path: String?, write: suspend (String?) -> T): T =
        locked {
            val destination = directory.receiptFile(path) ?: return@locked write(null)
            try {
                if (existingPath(path) == null) {
                    destination.delete()
                    val draft = drafts.existingReceipt(path) ?: throw IOException("Receipt draft unavailable")
                    directory.mkdirs()
                    draft.copyTo(destination, overwrite = true)
                }
                write(path)
            } finally {
                cleanIfUnused(path)
            }
        }

    internal suspend fun cleanIfUnused(path: String?) {
        if (path == null || path in undoPins) return
        // A canceled caller may have committed the Room write before it was canceled.
        withContext(NonCancellable) {
            try {
                if (!transactions.referencesReceipt(path)) directory.receiptFile(path)?.delete()
            } catch (e: SQLiteException) {
                Log.w("ReceiptStore", "Receipt cleanup deferred", e)
            }
        }
    }

    internal fun retainForUndo(path: String?) {
        if (path != null) undoPins[path] = (undoPins[path] ?: 0) + 1
    }

    private suspend fun releaseUndo(path: String) {
        val count = undoPins[path] ?: return
        if (count == 1) undoPins.remove(path) else undoPins[path] = count - 1
        cleanIfUnused(path)
    }

    internal fun discardUndo(transaction: TransactionEntity) = transaction.receiptPath?.let { path ->
        enqueue { locked { releaseUndo(path) } }
    }

    private fun enqueue(operation: suspend () -> Unit) =
        maintenance.launch {
            try {
                operation()
            } catch (e: SQLiteException) {
                Log.w("ReceiptStore", "Receipt cleanup deferred", e)
            }
        }
}
