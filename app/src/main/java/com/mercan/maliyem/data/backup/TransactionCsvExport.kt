package com.mercan.maliyem.data.backup

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import androidx.room.withTransaction
import com.mercan.maliyem.R
import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.data.local.DefaultCategories
import com.mercan.maliyem.data.local.MaliyemDatabase
import com.mercan.maliyem.data.local.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TransactionCsvExport @Inject constructor(
    private val db: MaliyemDatabase,
    @ApplicationContext context: Context,
) {
    private val resolver = context.contentResolver

    suspend fun save(uri: Uri, resources: Resources, year: Int? = null) = withContext(Dispatchers.IO) {
        val content = CsvExporter.encode(rows(resources, year))
        val output = resolver.openOutputStream(uri, "wt") ?: throw IOException()
        output.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    }

    internal suspend fun rows(resources: Resources, year: Int?): List<CsvRow> = db.withTransaction {
        val from = year?.let { LocalDate.of(it, 1, 1) } ?: LocalDate.MIN
        val through = year?.let { LocalDate.of(it, 12, 31) } ?: LocalDate.MAX
        val categories = db.categoryDao().getAll().associate { category ->
            category.id to (category.name ?: DefaultCategories.nameRes(category.key)?.let(resources::getString).orEmpty())
        }
        val accounts = db.accountDao().getAll().associate { account ->
            account.id to (account.name ?: DefaultAccounts.nameRes(account.key)?.let(resources::getString).orEmpty())
        }
        val dao = db.transactionReportDao()
        val expense = resources.getString(R.string.add_expense)
        val income = resources.getString(R.string.add_income)
        val transferLabel = resources.getString(R.string.add_transfer)
        dao.transactionsForExport(from, through).map { transaction ->
            CsvRow(
                transaction.date,
                if (transaction.type == TransactionType.EXPENSE) expense else income,
                categories.getValue(transaction.categoryId),
                transaction.amountMinor,
                transaction.note,
                accounts.getValue(transaction.accountId),
            )
        } + dao.transfersForExport(from, through).map { transfer ->
            CsvRow(
                transfer.date, transferLabel, "", transfer.amountMinor, transfer.note,
                resources.getString(
                    R.string.transfer_direction, accounts.getValue(transfer.fromAccountId), accounts.getValue(transfer.toAccountId),
                ),
            )
        }
    }
}
