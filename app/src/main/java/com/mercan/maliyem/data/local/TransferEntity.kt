package com.mercan.maliyem.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** İki farklı hesap arasında pozitif kuruş tutarı taşır. */
@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["fromAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["toAccountId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("fromAccountId"), Index("toAccountId"), Index("date")],
)
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountMinor: Long,
    val date: LocalDate,
    val note: String? = null,
    val createdAt: Long,
) {
    init {
        require(fromAccountId != toAccountId)
        require(amountMinor > 0)
    }
}
