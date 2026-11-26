package com.mercan.maliyem.ui.breakdown

import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import java.time.YearMonth

/** Bir kategorinin ay içindeki toplamı. */
data class Slice(val categoryId: Long, val key: String?, val name: String?, val icon: String, val amountMinor: Long)

data class BreakdownUiState(val month: YearMonth, val type: TransactionType, val slices: List<Slice>) {
    val totalMinor: Long get() = slices.sumOf { it.amountMinor }

    /** Toplam içindeki pay, en yakın tam sayıya yuvarlanır. */
    fun percent(slice: Slice): Int = if (totalMinor == 0L) 0 else Math.round(slice.amountMinor * 100.0 / totalMinor).toInt()

    /** Çubuk boyu en büyük kategoriye göre (pano: "Çubuklar en büyük kategoriye göre ölçeklenir"). */
    fun barFraction(slice: Slice): Float = slices.firstOrNull()?.amountMinor?.takeIf { it > 0 }?.let { slice.amountMinor.toFloat() / it } ?: 0f
}

/** Seçili türün kategorileri büyükten küçüğe; kaydı olmayan kategori listelenmez. */
fun buildSlices(rows: List<TransactionWithCategory>, type: TransactionType): List<Slice> =
    rows.filter { it.transaction.type == type }
        .groupBy { it.category.id }
        .map { (id, items) ->
            val c = items.first().category
            Slice(id, c.key, c.name, c.icon, items.sumOf { it.transaction.amountMinor })
        }
        .sortedWith(compareByDescending<Slice> { it.amountMinor }.thenBy { it.categoryId })
