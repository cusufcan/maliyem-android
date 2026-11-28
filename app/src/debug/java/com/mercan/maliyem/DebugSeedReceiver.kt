package com.mercan.maliyem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.util.Log
import com.mercan.maliyem.data.local.AccountDao
import com.mercan.maliyem.data.local.CategoryDao
import com.mercan.maliyem.data.local.DefaultAccounts
import com.mercan.maliyem.data.local.TransactionDao
import com.mercan.maliyem.data.local.TransactionEntity
import com.mercan.maliyem.data.local.TransactionType
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Panodaki Kasım örneğini içinde bulunulan aya (ya da `--ei monthOffset -3` ile 3 ay öncesine) ekler.
 * `--ei expensePercent 85` giderleri yüzde olarak ölçekler (grafikte aylar farklı görünsün diye).
 * Yalnız debug derlemesinde var.
 */
@AndroidEntryPoint
class DebugSeedReceiver : BroadcastReceiver() {
    @Inject lateinit var accounts: AccountDao
    @Inject lateinit var categories: CategoryDao
    @Inject lateinit var transactions: TransactionDao
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val accountId = accounts.getAll().first { it.key == DefaultAccounts.CASH }.id
                val byKey = categories.observeAll().first().associateBy { it.key }
                val month = YearMonth.now(clock).plusMonths(intent.getIntExtra("monthOffset", 0).toLong())
                val now = clock.millis()
                val expensePercent = intent.getIntExtra("expensePercent", 100)
                val rows = SAMPLE.mapIndexedNotNull { index, (day, key, note, amount) ->
                    val category = byKey[key] ?: return@mapIndexedNotNull null
                    TransactionEntity(
                        type = category.type,
                        amountMinor = if (category.type == TransactionType.EXPENSE) amount * expensePercent / 100 else amount,
                        categoryId = category.id,
                        date = month.atDay(day.coerceAtMost(month.lengthOfMonth())),
                        note = note,
                        createdAt = now + index,
                        accountId = accountId,
                    )
                }
                transactions.insertAll(rows)
                Log.i("DebugSeed", "${rows.size} kayıt eklendi: $month")
            } catch (error: SQLiteException) {
                Log.e("DebugSeed", "Örnek kayıtlar eklenemedi", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val SAMPLE = listOf(
            Seed(1, "salary", "Kasım maaşı", 2_850_000),
            Seed(1, "rent", "Ev kirası", 1_100_000),
            Seed(3, "groceries", "Haftalık alışveriş", 116_420),
            Seed(5, "bills", "İnternet", 39_900),
            Seed(8, "transport", "İzmirim kart", 50_000),
            Seed(10, "groceries", "Pazar", 109_890),
            Seed(12, "dining", "Akşam yemeği", 64_000),
            Seed(14, "health", "Eczane", 45_000),
            Seed(16, "bills", "Su", 30_200),
            Seed(17, "groceries", "Haftalık alışveriş", 102_465),
            Seed(19, "other_expense", "Kitap", 38_000),
            Seed(20, "bills", "Doğalgaz", 79_900),
            Seed(21, "dining", "Doğum günü yemeği", 114_550),
            Seed(22, "transport", "İzmirim kart", 50_000),
            Seed(23, "groceries", "Haftalık alışveriş", 68_480),
            Seed(24, "bills", "Elektrik", 64_075),
            Seed(25, "freelance", "Logo tasarımı", 320_000),
            Seed(25, "dining", "Kahve", 8_500),
            Seed(26, "transport", "İzmirim kart", 25_000),
            Seed(26, "other_expense", "Berber", 60_000),
            Seed(26, "groceries", "Migros", 41_285),
        )
    }

    private data class Seed(val day: Int, val key: String, val note: String, val amountMinor: Long)
}
