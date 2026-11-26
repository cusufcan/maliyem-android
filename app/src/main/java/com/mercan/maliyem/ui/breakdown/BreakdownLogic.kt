package com.mercan.maliyem.ui.breakdown

import com.mercan.maliyem.data.local.TransactionType
import com.mercan.maliyem.data.local.TransactionWithCategory
import com.mercan.maliyem.data.local.TrendEntry
import com.mercan.maliyem.ui.trend.percentChange
import com.mercan.maliyem.util.roundedPercent
import java.math.BigInteger
import java.time.YearMonth

/** Bir kategorinin ay içindeki toplamı. */
data class Slice(val categoryId: Long, val key: String?, val name: String?, val icon: String, val amountMinor: BigInteger)

data class BreakdownUiState(
    val month: YearMonth,
    val type: TransactionType,
    val slices: List<Slice>,
    val budgetLimits: Map<Long, Long> = emptyMap(),
    val previousTotals: Map<Long, BigInteger> = emptyMap(),
) {
    val totalMinor: BigInteger = slices.sumOf { it.amountMinor }

    /** Toplam içindeki pay, en yakın tam sayıya yuvarlanır. */
    fun percent(slice: Slice): Int = if (totalMinor.signum() == 0) 0 else roundedPercent(slice.amountMinor, totalMinor).toInt()

    /** Önceki takvim ayında bu kategoride kayıt yoksa yüzde değişimi hesaplanmaz. */
    fun change(slice: Slice): BigInteger? = percentChange(slice.amountMinor, previousTotals[slice.categoryId] ?: BigInteger.ZERO)

    /** Çubuk boyu en büyük kategoriye göre (pano: "Çubuklar en büyük kategoriye göre ölçeklenir"). */
    fun barFraction(slice: Slice): Float = slices.firstOrNull()?.amountMinor?.takeIf { it.signum() > 0 }?.let { (slice.amountMinor.toDouble() / it.toDouble()).toFloat() } ?: 0f
}

/** Önceki ayın seçili türdeki tutarlarını kategoriye göre toplar. */
fun previousCategoryTotals(rows: List<TrendEntry>, type: TransactionType): Map<Long, BigInteger> =
    rows.filter { it.type == type }.groupBy { it.categoryId }
        .mapValues { (_, items) -> items.sumOf { it.amountMinor.toBigInteger() } }

/** Seçili türün kategorileri büyükten küçüğe; kaydı olmayan kategori listelenmez. */
fun buildSlices(rows: List<TransactionWithCategory>, type: TransactionType): List<Slice> =
    rows.filter { it.transaction.type == type }
        .groupBy { it.category.id }
        .map { (id, items) ->
            val c = items.first().category
            Slice(id, c.key, c.name, c.icon, items.sumOf { it.transaction.amountMinor.toBigInteger() })
        }
        .sortedWith(compareByDescending<Slice> { it.amountMinor }.thenBy { it.categoryId })
