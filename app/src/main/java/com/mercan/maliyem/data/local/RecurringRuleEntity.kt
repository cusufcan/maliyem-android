package com.mercan.maliyem.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class RecurringFrequency { WEEKLY, MONTHLY, YEARLY }

/** [startDate] takvim çapası, [nextDate] henüz üretilmemiş ilk tarihtir. Tutar kuruş cinsindendir. */
@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId"), Index("accountId")],
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryId: Long,
    val accountId: Long,
    val note: String? = null,
    val frequency: RecurringFrequency,
    val startDate: LocalDate,
    val nextDate: LocalDate,
    val endDate: LocalDate? = null,
    val paused: Boolean = false,
    val createdAt: Long,
) {
    init {
        require(amountMinor > 0)
    }
}
