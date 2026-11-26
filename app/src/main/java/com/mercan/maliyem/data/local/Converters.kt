package com.mercan.maliyem.data.local

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Room'un doğrudan saklayamadığı tipleri sütuna çevirir.
 * Tarih epochDay (1970-01-01'den beri gün sayısı) olarak tutulur: saat diliminden bağımsız (ADR-0005).
 */
class Converters {
    @TypeConverter fun dateToEpochDay(date: LocalDate): Long = date.toEpochDay()
    @TypeConverter fun epochDayToDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    @TypeConverter fun typeToName(type: TransactionType): String = type.name
    @TypeConverter fun nameToType(name: String): TransactionType = TransactionType.valueOf(name)
}
