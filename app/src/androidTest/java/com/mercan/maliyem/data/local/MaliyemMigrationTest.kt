package com.mercan.maliyem.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.mercan.maliyem.di.DatabaseModule
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class MaliyemMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "migration-${UUID.randomUUID()}.db"

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, MaliyemDatabase::class.java)

    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(name)
    }

    @Test fun playDatabaseMigratesOneVersionAtATime() {
        createDatabase(1)
        for (version in 2..6) {
            helper.runMigrationsAndValidate(name, version, true, ALL_MIGRATIONS[version - 2]).use {
                assertRows(it, startVersion = 1, currentVersion = version)
            }
        }
        assertOpenInRoom(1)
    }

    @Test fun playDatabaseMigratesDirectlyWithHelper() = migrateToLatest(1)

    @Test fun playDatabaseMigratesDirectlyOnProductionRoomOpen() {
        createDatabase(1)
        assertOpenInRoom(1)
    }

    @Test fun version2MigratesToLatest() = migrateToLatest(2)

    @Test fun version3MigratesToLatest() = migrateToLatest(3)

    @Test fun version4MigratesToLatest() = migrateToLatest(4)

    @Test fun version5MigratesToLatest() = migrateToLatest(5)

    private fun migrateToLatest(startVersion: Int) {
        createDatabase(startVersion)
        helper.runMigrationsAndValidate(name, 6, true, *ALL_MIGRATIONS).use {
            assertRows(it, startVersion, currentVersion = 6)
        }
        assertOpenInRoom(startVersion)
    }

    private fun assertOpenInRoom(startVersion: Int) {
        val room = DatabaseModule.buildDatabase(context, name)
        try {
            assertRows(room.openHelper.writableDatabase, startVersion, currentVersion = 6)
            val rows = runBlocking { room.transactionDao().getAll().sortedBy { it.id } }
            assertEquals(
                listOf(
                    TransactionEntity(
                        12, TransactionType.EXPENSE, 154_975, 1, LocalDate.ofEpochDay(20_410),
                        "Çiğ köfte, süt ve çay — öğle", 1_763_000_000_001,
                        if (startVersion >= 2) 4 else 1,
                        if (startVersion >= 3) 7 else null,
                    ),
                    TransactionEntity(
                        18, TransactionType.INCOME, 4_530_000, 8, LocalDate.ofEpochDay(20_411),
                        "Kasım maaşı: Ücret ödemesi", 1_763_000_000_002, 1,
                    ),
                    TransactionEntity(
                        25, TransactionType.EXPENSE, 9_990, 11, LocalDate.ofEpochDay(20_412),
                        null, 1_763_000_000_003, 1,
                    ),
                ),
                rows,
            )
        } finally {
            room.close()
        }
    }

    private fun createDatabase(version: Int) {
        helper.createDatabase(name, version).use { db ->
            for ((table, columnsAndRows) in fixture(version, version)) {
                val (columns, rows) = columnsAndRows
                val placeholders = columns.joinToString { "?" }
                val columnNames = columns.joinToString { "`$it`" }
                for (row in rows) {
                    db.execSQL("INSERT INTO $table ($columnNames) VALUES ($placeholders)", row.toTypedArray())
                }
            }
            // A deleted historical row must not make its ID available again.
            db.execSQL("UPDATE sqlite_sequence SET seq = 70 WHERE name = 'transactions'")
        }
    }

    private fun assertRows(db: SupportSQLiteDatabase, startVersion: Int, currentVersion: Int) {
        for ((table, columnsAndRows) in fixture(startVersion, currentVersion)) {
            val (columns, expected) = columnsAndRows
            val projection = columns.joinToString { "`$it`" }
            val actual = db.query("SELECT $projection FROM $table ORDER BY id").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(columns.indices.map { i -> if (cursor.isNull(i)) null else cursor.getString(i) })
                    }
                }
            }
            assertEquals("$table at v$currentVersion", expected.map { it.map { value -> value?.toString() } }, actual)
        }
        db.query("SELECT seq FROM sqlite_sequence WHERE name = 'transactions'").use {
            it.moveToFirst()
            assertEquals(70L, it.getLong(0))
        }
        db.query("PRAGMA foreign_key_check").use { assertFalse("Foreign key violations", it.moveToFirst()) }
    }

    private data class Table(val columns: List<String>, val rows: List<List<Any?>>)

    private fun fixture(start: Int, version: Int): Map<String, Table> = buildMap {
        fun table(name: String, addedIn: Int, columns: String, vararg rows: List<Any?>) {
            if (version >= addedIn) {
                put(name, Table(columns.split(' '), if (start >= addedIn) rows.toList() else emptyList()))
            }
        }

        table(
            "categories", 1, "id type key name icon sortOrder",
            listOf(1, "EXPENSE", "groceries", null, "cart", 0),
            listOf(2, "EXPENSE", "rent", null, "home", 1),
            listOf(3, "EXPENSE", "bills", null, "bolt", 2),
            listOf(4, "EXPENSE", "transport", null, "bus", 3),
            listOf(5, "EXPENSE", "dining", null, "fork", 4),
            listOf(6, "EXPENSE", "health", null, "heart", 5),
            listOf(7, "EXPENSE", "other_expense", null, "dots", 6),
            listOf(8, "INCOME", "salary", null, "briefcase", 0),
            listOf(9, "INCOME", "freelance", null, "laptop", 1),
            listOf(10, "INCOME", "other_income", null, "dots", 2),
            listOf(11, "EXPENSE", null, "Çocuk eğitimi", "book", 7),
        )
        if (version >= 2) {
            val accounts = buildList {
                add(listOf(1, "cash", null, "wallet", 0, 0, 0))
                if (start >= 2) add(listOf(4, null, "İş Bankası", "card", 1, -2500, 0))
            }
            put("accounts", Table("id key name icon sortOrder initialBalanceMinor archived".split(' '), accounts))
        }
        table(
            "transfers", 2, "id fromAccountId toAccountId amountMinor date note createdAt",
            listOf(6, 1, 4, 50_000, 20_410, "Aylık aktarım", 1_763_000_000_004L),
        )
        table(
            "recurring_rules", 3, "id type amountMinor categoryId accountId note frequency startDate nextDate endDate paused createdAt",
            listOf(7, "EXPENSE", 154_975, 1, 4, "Haftalık alışveriş", "WEEKLY", 20_410, 20_417, null, 0, 1_763_000_000_005L),
        )
        table("budgets", 4, "id categoryId limitMinor createdAt", listOf(10, 1, 900_000, 1_763_000_000_006L))
        table("overall_budget", 4, "id limitMinor", listOf(1, 1_500_000))
        table(
            "savings_goals", 5, "id name targetMinor deadline icon createdAt archived",
            listOf(15, "İzmir tatili", 2_500_000, 20_750, "plane", 1_763_000_000_007L, 0),
        )
        table(
            "goal_entries", 5, "id goalId amountMinor date note",
            listOf(20, 15, 250_000, 20_412, "İlk birikim"),
            listOf(21, 15, -10_000, 20_413, "Bilet ücreti"),
        )
        val transactionColumns = buildString {
            append("id type amountMinor categoryId date note createdAt")
            if (version >= 2) append(" accountId")
            if (version >= 3) append(" recurringRuleId")
            if (version >= 6) append(" receiptPath")
        }
        fun transaction(row: List<Any?>, account: Int = 1, rule: Int? = null) = buildList {
            addAll(row)
            if (version >= 2) add(account)
            if (version >= 3) add(rule)
            if (version >= 6) add(null)
        }
        table(
            "transactions", 1, transactionColumns,
            transaction(
                listOf(12, "EXPENSE", 154_975, 1, 20_410, "Çiğ köfte, süt ve çay — öğle", 1_763_000_000_001L),
                account = if (start >= 2) 4 else 1, rule = if (start >= 3) 7 else null,
            ),
            transaction(listOf(18, "INCOME", 4_530_000, 8, 20_411, "Kasım maaşı: Ücret ödemesi", 1_763_000_000_002L)),
            transaction(listOf(25, "EXPENSE", 9_990, 11, 20_412, null, 1_763_000_000_003L)),
        )
    }
}
