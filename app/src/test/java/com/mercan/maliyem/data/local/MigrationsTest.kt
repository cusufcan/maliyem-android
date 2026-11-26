package com.mercan.maliyem.data.local

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
        val metadata = v2.getValue("entities").jsonArray.joinToString("\n") { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            """
                SELECT '$table';
                SELECT name, type, "notnull", dflt_value, pk FROM pragma_table_info('$table') ORDER BY name;
                SELECT * FROM pragma_foreign_key_list('$table') ORDER BY "from";
                SELECT name, "unique", origin, partial FROM pragma_index_list('$table') ORDER BY name;
                SELECT il.name, ii.seqno, ii.name, ii."desc", ii.coll, ii."key"
                    FROM pragma_index_list('$table') AS il, pragma_index_xinfo(il.name) AS ii
                    ORDER BY il.name, ii.seqno;
            """.trimIndent()
        }
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

    private fun migrationSql(): String = buildString {
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
        MIGRATION_1_2.migrate(db)
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
