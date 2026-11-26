package com.mercan.maliyem.data.repository

import com.mercan.maliyem.data.local.TransactionDao
import com.mercan.maliyem.data.local.TransactionWithCategory
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Kayıtlara ekranların tek kapısı; testte sahtesi verilebilsin diye arayüz. */
interface TransactionRepository {
    /** [month] içindeki kayıtlar, en yeni başta. */
    fun observeMonth(month: YearMonth): Flow<List<TransactionWithCategory>>
}

class RoomTransactionRepository @Inject constructor(
    private val dao: TransactionDao,
) : TransactionRepository {
    override fun observeMonth(month: YearMonth) = dao.observeRange(month.atDay(1), month.atEndOfMonth())
}
