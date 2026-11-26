package com.mercan.maliyem.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeNoException
import org.junit.Test

/** Android çalıştırmadan, gerçek SQLite'ta geçişi ve Room şema çıktısını karşılaştırır. */
class MigrationsTest {
    @Test fun `migration preserves data and matches the exported Room schema`() {
        val v1 = schema(1)
        val v2 = schema(2)
        val metadata = metadata(v2)
        val fresh = sqlite(createTables(v2) + metadata)
        val migrated = sqlite(
            createTables(v1) + """
                INSERT INTO categories VALUES (6, 'EXPENSE', NULL, 'Custom', 'cart', 1);
                INSERT INTO categories VALUES (7, 'INCOME', 'salary', NULL, 'briefcase', 0);
                INSERT INTO transactions VALUES (9, 'EXPENSE', 12550, 6, 20000, 'Old note', 123);
                INSERT INTO transactions VALUES (11, 'INCOME', 9000, 7, 20001, NULL, 124);
                INSERT INTO transactions VALUES (50, 'EXPENSE', 1, 6, 20002, NULL, 125);
                DELETE FROM transactions WHERE id = 50;
                BEGIN;
            """.trimIndent() + "\n" + migrationSql() + """
                COMMIT;
                PRAGMA foreign_key_check;
                SELECT * FROM accounts;
                SELECT * FROM transactions ORDER BY id;
                SELECT * FROM categories ORDER BY id;
                SELECT seq FROM sqlite_sequence WHERE name = 'transactions';
                SELECT COUNT(*) FROM transfers;
            """.trimIndent() + "\n" + metadata,
        )
        assertEquals(
            """1|cash||wallet|0|0|0
                |9|EXPENSE|12550|6|20000|Old note|123|1
                |11|INCOME|9000|7|20001||124|1
                |6|EXPENSE||Custom|cart|1
                |7|INCOME|salary||briefcase|0
                |50
                |0
                |""".trimMargin() + fresh,
            migrated,
        )
    }

    @Test fun `empty v1 still gets exactly one cash account`() {
        val result = sqlite(
            createTables(schema(1)) + migrationSql() +
                "SELECT id, `key`, name FROM accounts; SELECT COUNT(*) FROM transactions;",
        )

        assertEquals("1|cash|\n0\n", result)
    }

    @Test fun `v2 to v3 keeps existing data sequences and matches Room schema`() {
        val v3 = schema(3)
        val metadata = metadata(v3)
        val fresh = sqlite(createTables(v3) + metadata)
        val migrated = sqlite(
            createTables(schema(2)) + """
                INSERT INTO accounts VALUES (1, 'cash', NULL, 'wallet', 0, 100, 0);
                INSERT INTO accounts VALUES (3, NULL, 'Card', 'card', 1, -125, 1);
                INSERT INTO categories VALUES (6, 'EXPENSE', NULL, 'Custom', 'cart', 1);
                INSERT INTO transactions VALUES (9, 'EXPENSE', 12550, 6, 20000, 'Old note', 123, 3);
                INSERT INTO transactions VALUES (50, 'EXPENSE', 1, 6, 20002, NULL, 125, 1);
                DELETE FROM transactions WHERE id = 50;
                INSERT INTO transfers VALUES (7, 1, 3, 1500, 20005, 'Transfer', 126);
                BEGIN;
            """.trimIndent() + "\n" + migrationSql(MIGRATION_2_3) + """
                COMMIT;
                PRAGMA foreign_key_check;
                SELECT * FROM accounts ORDER BY id;
                SELECT * FROM categories;
                SELECT * FROM transactions;
                SELECT * FROM transfers;
                SELECT name, seq FROM sqlite_sequence WHERE name != 'recurring_rules' ORDER BY name;
                SELECT COUNT(*) FROM recurring_rules;
            """.trimIndent() + "\n" + metadata,
        )
        assertEquals(
            """1|cash||wallet|0|100|0
                |3||Card|card|1|-125|1
                |6|EXPENSE||Custom|cart|1
                |9|EXPENSE|12550|6|20000|Old note|123|3|
                |7|1|3|1500|20005|Transfer|126
                |accounts|3
                |categories|6
                |transactions|50
                |transfers|7
                |0
                |""".trimMargin() + fresh,
            migrated,
        )
    }

    @Test fun `v1 can upgrade directly to v3 and deleting a rule detaches its transactions`() {
        val result = sqlite(
            createTables(schema(1)) + """
                INSERT INTO categories VALUES (6, 'EXPENSE', NULL, 'Custom', 'cart', 1);
                INSERT INTO transactions VALUES (9, 'EXPENSE', 12550, 6, 20000, 'Old note', 123);
                BEGIN;
            """.trimIndent() + "\n" + migrationSql() + migrationSql(MIGRATION_2_3) + """
                COMMIT;
                SELECT id, accountId, recurringRuleId FROM transactions;
                INSERT INTO recurring_rules VALUES (1, 'EXPENSE', 1500, 6, 1, NULL, 'WEEKLY', 20000, 20007, NULL, 0, 123);
                INSERT INTO transactions VALUES (10, 'EXPENSE', 1500, 6, 20000, NULL, 123, 1, 1);
                SELECT id, recurringRuleId FROM transactions WHERE id = 10;
                DELETE FROM recurring_rules WHERE id = 1;
                SELECT id, recurringRuleId FROM transactions WHERE id = 10;
                PRAGMA foreign_key_check;
            """.trimIndent(),
        )
        assertEquals("9|1|\n10|1\n10|\n", result)
    }

    @Test fun `v3 to v4 keeps existing rows and only cascades the deleted category budget`() {
        val schema4 = schema(4)
        val fresh = sqlite(createTables(schema4) + metadata(schema4))
        val migrated = sqlite(
            createTables(schema(3)) + """
                INSERT INTO accounts VALUES (1, 'cash', NULL, 'wallet', 0, 300, 0);
                INSERT INTO accounts VALUES (2, NULL, 'Card', 'card', 1, 200, 0);
                INSERT INTO categories VALUES (6, 'EXPENSE', NULL, 'Market', 'cart', 1);
                INSERT INTO categories VALUES (7, 'EXPENSE', 'other_expense', NULL, 'dots', 2);
                INSERT INTO recurring_rules VALUES (8, 'EXPENSE', 1500, 6, 1, NULL, 'WEEKLY', 20000, 20007, NULL, 0, 123);
                INSERT INTO transactions (id, type, amountMinor, categoryId, date, createdAt, accountId, recurringRuleId)
                    VALUES (9, 'EXPENSE', 1500, 6, 20000, 123, 1, 8);
                INSERT INTO transfers VALUES (10, 1, 2, 125, 20000, NULL, 124);
                BEGIN;
            """.trimIndent() + "\n" + migrationSql(MIGRATION_3_4) + """
                COMMIT;
                SELECT COUNT(*) FROM budgets;
                SELECT COUNT(*) FROM overall_budget;
                SELECT id, initialBalanceMinor FROM accounts ORDER BY id;
                SELECT id, categoryId, nextDate FROM recurring_rules;
                SELECT id, amountMinor, categoryId, accountId, recurringRuleId FROM transactions;
                SELECT id, fromAccountId, toAccountId, amountMinor FROM transfers;
                INSERT INTO budgets VALUES (1, 6, 2000, 125);
                INSERT OR IGNORE INTO budgets VALUES (2, 6, 1000, 125);
                SELECT COUNT(*) FROM budgets;
                INSERT INTO budgets VALUES (3, 7, 5000, 125);
                INSERT INTO overall_budget VALUES (1, 8000);
                BEGIN;
                UPDATE transactions SET categoryId = 7 WHERE categoryId = 6;
                UPDATE recurring_rules SET categoryId = 7 WHERE categoryId = 6;
                DELETE FROM categories WHERE id = 6;
                COMMIT;
                SELECT * FROM budgets;
                SELECT * FROM overall_budget;
                SELECT categoryId FROM transactions;
                SELECT categoryId FROM recurring_rules;
                PRAGMA foreign_key_check;
            """.trimIndent() + "\n" + metadata(schema4),
        )
        assertEquals(
            """
                |0
                |0
                |1|300
                |2|200
                |8|6|20007
                |9|1500|6|1|8
                |10|1|2|125
                |1
                |3|7|5000|125
                |1|8000
                |7
                |7
                |""".trimMargin() + fresh,
            migrated,
        )
    }

    private fun metadata(schema: JsonObject): String =
        schema.getValue("entities").jsonArray.joinToString("\n") { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            """
                SELECT '$table';
                SELECT name, type, "notnull", dflt_value, pk FROM pragma_table_info('$table') ORDER BY name;
                SELECT "table", "from", "to", on_update, on_delete, "match" FROM pragma_foreign_key_list('$table') ORDER BY "from";
                SELECT name, "unique", origin, partial FROM pragma_index_list('$table') ORDER BY name;
                SELECT il.name, ii.seqno, ii.name, ii."desc", ii.coll, ii."key"
                    FROM pragma_index_list('$table') AS il, pragma_index_xinfo(il.name) AS ii
                    ORDER BY il.name, ii.seqno;
            """.trimIndent()
        }
    private fun schema(version: Int): JsonObject {
        val path = "schemas/${MaliyemDatabase::class.java.name}/$version.json"
        val file = listOf(File(path), File("app/$path")).first { it.isFile }
        return Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
    }

    private fun createTables(schema: JsonObject): String =
        schema.getValue("entities").jsonArray.joinToString("\n", postfix = "\n") { entry ->
            val entity = entry.jsonObject
            val table = entity.getValue("tableName").jsonPrimitive.content
            val statements = listOf(entity) + entity["indices"]?.jsonArray.orEmpty().map { it.jsonObject }
            statements.joinToString("\n") {
                it.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table) + ";"
            }
        }

    private fun migrationSql(migration: Migration = MIGRATION_1_2): String = buildString {
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            check(method.name == "execSQL")
            var sql = args[0] as String
            (args.getOrNull(1) as? Array<*>)?.forEach { value ->
                val literal = when (value) {
                    is Number -> value.toString()
                    is String -> "'${value.replace("'", "''")}'"
                    else -> error("Beklenmeyen SQL parametresi")
                }
                sql = sql.replaceFirst("?", literal)
            }
            appendLine("$sql;")
            null
        } as SupportSQLiteDatabase
        migration.migrate(db)
    }

    private fun sqlite(script: String): String {
        val process = try {
            ProcessBuilder("sqlite3", "-batch", "-bail", ":memory:").redirectErrorStream(true).start()
        } catch (e: IOException) {
            assumeNoException("SQLite komut satırı aracı bulunamadı", e)
            throw e
        }
        process.outputStream.bufferedWriter().use { it.write("PRAGMA foreign_keys = ON;\n$script\n") }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, 0, process.waitFor())
        return output
    }
}
