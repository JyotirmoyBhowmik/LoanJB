package com.jyotirmoyb.loan.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room database migration tests exercising EVERY individual schema version
 * step (1->2, 2->3, 3->4, 4->5) as well as full end-to-end 1->5 migration on real Android SQLite.
 */
@RunWith(AndroidJUnit4::class)
class RoomAllMigrationsInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun openAtVersion(dbName: String, version: Int, onCreateSql: (SupportSQLiteDatabase) -> Unit): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = onCreateSql(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
    }

    private fun createV1Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `saved_loans` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `nickname` TEXT NOT NULL,
                `lender` TEXT NOT NULL,
                `categoryId` TEXT NOT NULL,
                `categoryName` TEXT NOT NULL,
                `variantSummary` TEXT NOT NULL,
                `inputJson` TEXT NOT NULL,
                `paidMonthsCsv` TEXT NOT NULL,
                `completedChecklistCsv` TEXT NOT NULL,
                `reminderEnabled` INTEGER NOT NULL,
                `reminderDaysBefore` INTEGER NOT NULL,
                `createdAtMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `compare_scenarios` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `label` TEXT NOT NULL,
                `categoryId` TEXT NOT NULL,
                `variantSummary` TEXT NOT NULL,
                `inputJson` TEXT NOT NULL,
                `createdAtMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `catalog_override` (
                `id` INTEGER NOT NULL,
                `catalogJson` TEXT NOT NULL,
                `updatedAtMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }

    private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName)).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    @Test
    fun migrate_1_to_2_createsRemoteConfigTablesAndPreservesLoans() {
        val dbName = "instr-mig-1-2-${System.currentTimeMillis()}.db"
        val helper = openAtVersion(dbName, 1) { db ->
            createV1Schema(db)
            val cv = ContentValues().apply {
                put("nickname", "V1 Car Loan")
                put("lender", "HDFC")
                put("categoryId", "auto")
                put("categoryName", "Auto Loan")
                put("variantSummary", "Fixed")
                put("inputJson", "{}")
                put("paidMonthsCsv", "1")
                put("completedChecklistCsv", "")
                put("reminderEnabled", 1)
                put("reminderDaysBefore", 2)
                put("createdAtMillis", 1740000000000L)
            }
            db.insert("saved_loans", SQLiteDatabase.CONFLICT_REPLACE, cv)
        }
        try {
            val db = helper.writableDatabase
            LoanDatabase.MIGRATION_1_2.migrate(db)
            assertTrue(tableExists(db, "remote_config_cache"))
            assertTrue(tableExists(db, "manual_overrides"))
            assertTrue(tableExists(db, "sync_metadata"))
            db.query("SELECT nickname FROM saved_loans").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("V1 Car Loan", c.getString(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate_2_to_3_createsAmfiNavTablesAndIndexes() {
        val dbName = "instr-mig-2-3-${System.currentTimeMillis()}.db"
        val helper = openAtVersion(dbName, 2) { db ->
            createV1Schema(db)
            LoanDatabase.MIGRATION_1_2.migrate(db)
            db.execSQL(
                "INSERT INTO manual_overrides (`key`, valueText, updatedAtMillis, updatedOnDate) " +
                    "VALUES ('rbi_repo_rate', '6.25', 1, '2026-11-25')"
            )
        }
        try {
            val db = helper.writableDatabase
            LoanDatabase.MIGRATION_2_3.migrate(db)
            assertTrue(tableExists(db, "amfi_nav_schemes"))
            assertTrue(tableExists(db, "amfi_nav_sync"))
            db.query("SELECT valueText FROM manual_overrides WHERE `key`='rbi_repo_rate'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("6.25", c.getString(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate_3_to_4_createsMarketAndArticleBookmarkTables() {
        val dbName = "instr-mig-3-4-${System.currentTimeMillis()}.db"
        val helper = openAtVersion(dbName, 3) { db ->
            createV1Schema(db)
            LoanDatabase.MIGRATION_1_2.migrate(db)
            LoanDatabase.MIGRATION_2_3.migrate(db)
        }
        try {
            val db = helper.writableDatabase
            LoanDatabase.MIGRATION_3_4.migrate(db)
            assertTrue(tableExists(db, "market_quotes_cache"))
            assertTrue(tableExists(db, "market_history"))
            assertTrue(tableExists(db, "bank_fd_rates"))
            assertTrue(tableExists(db, "article_bookmarks"))
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate_4_to_5_addsAutoDebitNachAndEmiDayColumnsWithDefaults() {
        val dbName = "instr-mig-4-5-${System.currentTimeMillis()}.db"
        val helper = openAtVersion(dbName, 4) { db ->
            createV1Schema(db)
            LoanDatabase.MIGRATION_1_2.migrate(db)
            LoanDatabase.MIGRATION_2_3.migrate(db)
            LoanDatabase.MIGRATION_3_4.migrate(db)
            val cv = ContentValues().apply {
                put("nickname", "V4 Home Loan")
                put("lender", "SBI")
                put("categoryId", "home")
                put("categoryName", "Home Loan")
                put("variantSummary", "EBLR")
                put("inputJson", "{}")
                put("paidMonthsCsv", "1,2")
                put("completedChecklistCsv", "")
                put("reminderEnabled", 1)
                put("reminderDaysBefore", 3)
                put("createdAtMillis", 1740000000000L)
            }
            db.insert("saved_loans", SQLiteDatabase.CONFLICT_REPLACE, cv)
        }
        helper.writableDatabase.close()
        helper.close()

        val roomDb = Room.databaseBuilder(context, LoanDatabase::class.java, dbName)
            .addMigrations(LoanDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val loans = roomDb.loanDao().getAllSavedLoansOnce()
                assertEquals(1, loans.size)
                assertEquals("V4 Home Loan", loans.first().nickname)
                assertFalse(loans.first().autoDebitNach)
                assertEquals(5, loans.first().emiDayOfMonth)
            }
        } finally {
            roomDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate_1_to_5_fullChain_validatesRoomSchemaAndPreservesRows() {
        val dbName = "instr-mig-1-5-${System.currentTimeMillis()}.db"
        val helper = openAtVersion(dbName, 1) { db ->
            createV1Schema(db)
            val cv = ContentValues().apply {
                put("nickname", "Full Chain Loan")
                put("lender", "ICICI")
                put("categoryId", "home")
                put("categoryName", "Home Loan")
                put("variantSummary", "Floating")
                put("inputJson", "{}")
                put("paidMonthsCsv", "1,2,3")
                put("completedChecklistCsv", "")
                put("reminderEnabled", 1)
                put("reminderDaysBefore", 3)
                put("createdAtMillis", 1740000000000L)
            }
            db.insert("saved_loans", SQLiteDatabase.CONFLICT_REPLACE, cv)
        }
        helper.writableDatabase.close()
        helper.close()

        val roomDb = Room.databaseBuilder(context, LoanDatabase::class.java, dbName)
            .addMigrations(*LoanDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val loans = roomDb.loanDao().getAllSavedLoansOnce()
                assertEquals(1, loans.size)
                assertEquals("Full Chain Loan", loans.first().nickname)
                assertEquals(5, loans.first().emiDayOfMonth)

                roomDb.articlesDao().upsertBookmark(ArticleBookmarkEntity("how_emi_is_calculated", 1740000000000L))
                val ids = roomDb.articlesDao().observeBookmarkedIds().first()
                assertTrue(ids.contains("how_emi_is_calculated"))
                assertNotNull(roomDb.marketDao())
            }
        } finally {
            roomDb.close()
            context.deleteDatabase(dbName)
        }
    }
}
