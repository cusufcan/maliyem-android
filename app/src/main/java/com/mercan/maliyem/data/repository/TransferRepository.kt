package com.mercan.maliyem.data.repository

import com.mercan.maliyem.data.local.TransferDao
import com.mercan.maliyem.data.local.TransferEntity
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Hesaplar arası hareketler, gelir/gider sorgularından ayrı tutulur. */
class TransferRepository @Inject constructor(private val dao: TransferDao) {
    fun observeMonth(month: YearMonth) = dao.observeRange(month.atDay(1), month.atEndOfMonth()).distinctUntilChanged()

    fun observeFiltered(from: LocalDate, to: LocalDate, accountIds: Set<Long>) =
        dao.observeFiltered(from, to, accountIds.toList(), accountIds.isEmpty()).distinctUntilChanged()

    fun observeMonthsWithData() = dao.observeDates()
        .map { dates -> dates.mapTo(HashSet(), YearMonth::from) }
        .distinctUntilChanged()

    fun observe(id: Long) = dao.observeById(id)
    suspend fun get(id: Long) = dao.getById(id)
    suspend fun add(transfer: TransferEntity) = dao.insert(transfer)
    suspend fun update(transfer: TransferEntity) = dao.update(transfer) > 0
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun deleteAndGet(id: Long) = dao.deleteAndGet(id)
}
