package com.jyotirmoyb.loan.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "saved_loans")
data class SavedLoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val lender: String,
    val categoryId: String,
    val categoryName: String,
    val variantSummary: String,
    val inputJson: String,
    val paidMonthsCsv: String = "",
    val completedChecklistCsv: String = "",
    val reminderEnabled: Boolean = false,
    val reminderDaysBefore: Int = 3,
    val autoDebitNach: Boolean = false,
    val emiDayOfMonth: Int = 5,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "compare_scenarios")
data class CompareScenarioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val categoryId: String,
    val variantSummary: String,
    val inputJson: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "catalog_override")
data class CatalogOverrideEntity(
    @PrimaryKey val id: Int = 1,
    val catalogJson: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "remote_config_cache")
data class RemoteConfigCacheEntity(
    @PrimaryKey val fileName: String,
    val rawJson: String,
    val etag: String? = null,
    val dataVersion: String = "",
    val publishedOn: String = "",
    val fetchedAtMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "manual_overrides")
data class ManualOverrideEntity(
    @PrimaryKey val key: String,
    val valueText: String,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val updatedOnDate: String = ""
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val lastSyncAttemptMillis: Long = 0L,
    val lastSyncSuccessMillis: Long = 0L,
    val lastResultMessage: String = "Using built-in seed data",
    val lastResultSuccess: Boolean = true
)

@Entity(
    tableName = "amfi_nav_schemes",
    indices = [
        androidx.room.Index(value = ["schemeName"]),
        androidx.room.Index(value = ["isFavourite"])
    ]
)
data class AmfiNavEntity(
    @PrimaryKey val schemeCode: String,
    val isinGrowth: String = "",
    val isinReinvestment: String = "",
    val schemeName: String,
    val amcName: String = "",
    val categoryName: String = "",
    val nav: Double,
    val navDate: String,
    val isFavourite: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "amfi_nav_sync")
data class AmfiNavSyncEntity(
    @PrimaryKey val id: Int = 1,
    val lastSyncedMillis: Long = 0L,
    val latestNavDate: String = "",
    val totalSchemes: Int = 0,
    val statusMessage: String = "Not synced yet (offline)",
    val lastSuccess: Boolean = false
)

@Entity(tableName = "market_quotes_cache")
data class MarketQuoteCacheEntity(
    @PrimaryKey val metricKey: String,
    val value: Double,
    val previousValue: Double? = null,
    val unit: String,
    val sourceType: String, // "MANUAL", "LIVE_API", "ONLINE", "BUILT_IN"
    val sourceName: String,
    val asOfMillis: Long,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "market_history",
    indices = [androidx.room.Index(value = ["metricKey", "recordedAtMillis"])]
)
data class MarketHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metricKey: String,
    val value: Double,
    val recordedAtMillis: Long,
    val sourceType: String
)

@Entity(tableName = "bank_fd_rates")
data class BankFdRateEntity(
    @PrimaryKey val bankId: String,
    val bankName: String,
    val generalRate: Double,
    val seniorRate: Double,
    val tenureLabel: String,
    val isManualOverride: Boolean = false,
    val updatedOnDate: String = ""
)

@Entity(tableName = "article_bookmarks")
data class ArticleBookmarkEntity(
    @PrimaryKey val articleId: String,
    val bookmarkedAtMillis: Long = System.currentTimeMillis()
)



