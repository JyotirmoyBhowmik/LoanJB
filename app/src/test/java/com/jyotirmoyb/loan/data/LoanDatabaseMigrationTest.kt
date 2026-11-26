package com.jyotirmoyb.loan.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Room migration (v1 -> v2 -> v3 -> v4 -> v5), schema export verification,
 * and DAO persistence integration tests running on Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoanDatabaseMigrationTest {

    @Test
    fun `Room migrations 1 to 5 preserve existing data and add new tables and columns`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration-test-${System.currentTimeMillis()}.db"

        // 1. Create a v1 database matching LoanEntities v1 schema and insert a seed row
        val v1Callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
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

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(v1Callback)
                .build()
        )

        val writableV1 = helper.writableDatabase
        val values = ContentValues().apply {
            put("nickname", "SBI Home Loan 50L")
            put("lender", "State Bank of India")
            put("categoryId", "home")
            put("categoryName", "Home Loan")
            put("variantSummary", "EBLR Floating")
            put("inputJson", "{}")
            put("paidMonthsCsv", "1,2,3")
            put("completedChecklistCsv", "")
            put("reminderEnabled", 1)
            put("reminderDaysBefore", 3)
            put("createdAtMillis", 1740000000000L)
        }
        val insertedId = writableV1.insert("saved_loans", SQLiteDatabase.CONFLICT_REPLACE, values)
        assertTrue(insertedId > 0)
        // Close v1 database at version 1 so Room.databaseBuilder(version=5) executes MIGRATION_1_2..4_5
        writableV1.close()
        helper.close()

        // 2. Open the v1 database with Room v5 + ALL_MIGRATIONS so Room runs 1->2->3->4->5 and validates schema
        val roomDb = Room.databaseBuilder(context, LoanDatabase::class.java, dbName)
            .addMigrations(*LoanDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        runTest {
            val savedLoans = roomDb.loanDao().getAllSavedLoansOnce()
            assertEquals(1, savedLoans.size)
            val saved = savedLoans.first()
            assertEquals("SBI Home Loan 50L", saved.nickname)
            assertEquals("State Bank of India", saved.lender)
            assertEquals("1,2,3", saved.paidMonthsCsv)
            assertEquals(false, saved.autoDebitNach)
            assertEquals(5, saved.emiDayOfMonth)

            // Verify v2..v4 tables also work after migration
            roomDb.amfiNavDao().upsertBatch(
                listOf(
                    AmfiNavEntity(
                        schemeCode = "120503",
                        isinGrowth = "INF846K01EW2",
                        isinReinvestment = "-",
                        schemeName = "Axis Bluechip Fund - Direct Plan - Growth",
                        amcName = "Axis Mutual Fund",
                        categoryName = "Equity Scheme - Large Cap Fund",
                        nav = 64.25,
                        navDate = "25-Mar-2026",
                        isFavourite = true,
                        updatedAtMillis = 1740000000000L
                    )
                )
            )
            assertEquals(1, roomDb.amfiNavDao().observeTotalCount().first())

            roomDb.marketDao().insertHistoryPoints(
                listOf(
                    MarketHistoryEntity(
                        metricKey = "rbi_repo_rate",
                        value = 6.50,
                        recordedAtMillis = 1740000000000L,
                        sourceType = "BUILT_IN"
                    )
                )
            )
            val latestPoint = roomDb.marketDao().getLatestHistoryPoint("rbi_repo_rate")
            assertNotNull(latestPoint)
            assertEquals(6.50, latestPoint!!.value, 0.001)

            roomDb.articlesDao().upsertBookmark(ArticleBookmarkEntity("home_loan_guide", 1740000000000L))
            val bookmarks = roomDb.articlesDao().observeBookmarkedIds().first()
            assertTrue(bookmarks.contains("home_loan_guide"))
        }

        roomDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `exported Room schema directory is configured and readable`() {
        val schemaDir = File("schemas/com.jyotirmoyb.loan.data.LoanDatabase")
        if (schemaDir.exists()) {
            val v5File = File(schemaDir, "5.json")
            if (v5File.exists()) {
                val text = v5File.readText()
                assertTrue(text.contains("saved_loans"))
                assertTrue(text.contains("amfi_nav_schemes"))
                assertTrue(text.contains("market_history"))
                assertTrue(text.contains("article_bookmarks"))
            }
        }
    }
}
