package com.jyotirmoyb.loan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SavedLoanEntity::class,
        CompareScenarioEntity::class,
        CatalogOverrideEntity::class,
        RemoteConfigCacheEntity::class,
        ManualOverrideEntity::class,
        SyncMetadataEntity::class,
        AmfiNavEntity::class,
        AmfiNavSyncEntity::class,
        MarketQuoteCacheEntity::class,
        MarketHistoryEntity::class,
        BankFdRateEntity::class,
        ArticleBookmarkEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class LoanDatabase : RoomDatabase() {
    abstract fun loanDao(): LoanDao
    abstract fun remoteConfigDao(): RemoteConfigDao
    abstract fun amfiNavDao(): AmfiNavDao
    abstract fun marketDao(): MarketDao
    abstract fun articlesDao(): ArticlesDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `remote_config_cache` (
                        `fileName` TEXT NOT NULL,
                        `rawJson` TEXT NOT NULL,
                        `etag` TEXT,
                        `dataVersion` TEXT NOT NULL,
                        `publishedOn` TEXT NOT NULL,
                        `fetchedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`fileName`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `manual_overrides` (
                        `key` TEXT NOT NULL,
                        `valueText` TEXT NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        `updatedOnDate` TEXT NOT NULL,
                        PRIMARY KEY(`key`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_metadata` (
                        `id` INTEGER NOT NULL,
                        `lastSyncAttemptMillis` INTEGER NOT NULL,
                        `lastSyncSuccessMillis` INTEGER NOT NULL,
                        `lastResultMessage` TEXT NOT NULL,
                        `lastResultSuccess` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `amfi_nav_schemes` (
                        `schemeCode` TEXT NOT NULL,
                        `isinGrowth` TEXT NOT NULL,
                        `isinReinvestment` TEXT NOT NULL,
                        `schemeName` TEXT NOT NULL,
                        `amcName` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `nav` REAL NOT NULL,
                        `navDate` TEXT NOT NULL,
                        `isFavourite` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`schemeCode`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_amfi_nav_schemes_schemeName` ON `amfi_nav_schemes` (`schemeName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_amfi_nav_schemes_isFavourite` ON `amfi_nav_schemes` (`isFavourite`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `amfi_nav_sync` (
                        `id` INTEGER NOT NULL,
                        `lastSyncedMillis` INTEGER NOT NULL,
                        `latestNavDate` TEXT NOT NULL,
                        `totalSchemes` INTEGER NOT NULL,
                        `statusMessage` TEXT NOT NULL,
                        `lastSuccess` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `market_quotes_cache` (
                        `metricKey` TEXT NOT NULL,
                        `value` REAL NOT NULL,
                        `previousValue` REAL,
                        `unit` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `sourceName` TEXT NOT NULL,
                        `asOfMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`metricKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `market_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `metricKey` TEXT NOT NULL,
                        `value` REAL NOT NULL,
                        `recordedAtMillis` INTEGER NOT NULL,
                        `sourceType` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_market_history_metricKey_recordedAtMillis` ON `market_history` (`metricKey`, `recordedAtMillis`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bank_fd_rates` (
                        `bankId` TEXT NOT NULL,
                        `bankName` TEXT NOT NULL,
                        `generalRate` REAL NOT NULL,
                        `seniorRate` REAL NOT NULL,
                        `tenureLabel` TEXT NOT NULL,
                        `isManualOverride` INTEGER NOT NULL,
                        `updatedOnDate` TEXT NOT NULL,
                        PRIMARY KEY(`bankId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `article_bookmarks` (
                        `articleId` TEXT NOT NULL,
                        `bookmarkedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`articleId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `saved_loans` ADD COLUMN `autoDebitNach` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `saved_loans` ADD COLUMN `emiDayOfMonth` INTEGER NOT NULL DEFAULT 5")
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5
        )

        @Volatile
        private var INSTANCE: LoanDatabase? = null

        fun getInstance(context: Context): LoanDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LoanDatabase::class.java,
                    "loanjb_offline.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

