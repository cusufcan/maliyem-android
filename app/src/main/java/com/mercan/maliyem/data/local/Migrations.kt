package com.mercan.maliyem.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Mevcut kayıtları nakit hesabına bağlar; kimlikleri ve eski sıra sayacını korur. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `key` TEXT, name TEXT,
                icon TEXT NOT NULL, sortOrder INTEGER NOT NULL,
                initialBalanceMinor INTEGER NOT NULL, archived INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX index_accounts_key ON accounts (`key`)")
        DefaultAccounts.seed(db)

        db.execSQL("""
            CREATE TABLE transactions_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL,
                amountMinor INTEGER NOT NULL, categoryId INTEGER NOT NULL, date INTEGER NOT NULL,
                note TEXT, createdAt INTEGER NOT NULL, accountId INTEGER NOT NULL,
                FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO transactions_new (id, type, amountMinor, categoryId, date, note, createdAt, accountId)
            SELECT id, type, amountMinor, categoryId, date, note, createdAt, ? FROM transactions
        """.trimIndent(), arrayOf(DefaultAccounts.CASH_ID))
        db.execSQL("""
            INSERT INTO sqlite_sequence (name, seq)
            SELECT 'transactions_new', seq FROM sqlite_sequence WHERE name = 'transactions'
                AND NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = 'transactions_new')
        """.trimIndent())
        db.execSQL("""
            UPDATE sqlite_sequence SET seq = MAX(seq, COALESCE(
                (SELECT seq FROM sqlite_sequence WHERE name = 'transactions'), 0
            )) WHERE name = 'transactions_new'
        """.trimIndent())
        db.execSQL("DROP TABLE transactions")
        db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
        db.execSQL("CREATE INDEX index_transactions_categoryId ON transactions (categoryId)")
        db.execSQL("CREATE INDEX index_transactions_date ON transactions (date)")
        db.execSQL("CREATE INDEX index_transactions_accountId ON transactions (accountId)")

        db.execSQL("""
            CREATE TABLE transfers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fromAccountId INTEGER NOT NULL,
                toAccountId INTEGER NOT NULL, amountMinor INTEGER NOT NULL, date INTEGER NOT NULL,
                note TEXT, createdAt INTEGER NOT NULL,
                FOREIGN KEY(fromAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(toAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_transfers_fromAccountId ON transfers (fromAccountId)")
        db.execSQL("CREATE INDEX index_transfers_toAccountId ON transfers (toAccountId)")
        db.execSQL("CREATE INDEX index_transfers_date ON transfers (date)")
    }
}
