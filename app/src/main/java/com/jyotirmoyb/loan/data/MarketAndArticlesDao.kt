package com.jyotirmoyb.loan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketDao {

    @Query("SELECT * FROM market_quotes_cache")
    fun observeAllQuotes(): Flow<List<MarketQuoteCacheEntity>>

    @Query("SELECT * FROM market_quotes_cache")
    suspend fun getAllQuotesOnce(): List<MarketQuoteCacheEntity>

    @Query("SELECT * FROM market_quotes_cache WHERE metricKey = :metricKey LIMIT 1")
    suspend fun getQuote(metricKey: String): MarketQuoteCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuote(entity: MarketQuoteCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuotes(entities: List<MarketQuoteCacheEntity>)

    @Query("DELETE FROM market_quotes_cache WHERE metricKey = :metricKey AND sourceType = 'MANUAL'")
    suspend fun deleteManualQuote(metricKey: String)

    @Query("SELECT * FROM market_history WHERE metricKey = :metricKey ORDER BY recordedAtMillis ASC")
    fun observeHistory(metricKey: String): Flow<List<MarketHistoryEntity>>

    @Query("SELECT * FROM market_history ORDER BY recordedAtMillis ASC")
    fun observeAllHistory(): Flow<List<MarketHistoryEntity>>

    @Query("SELECT * FROM market_history WHERE metricKey = :metricKey ORDER BY recordedAtMillis DESC LIMIT 1")
    suspend fun getLatestHistoryPoint(metricKey: String): MarketHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryPoint(point: MarketHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryPoints(points: List<MarketHistoryEntity>)

    @Query("SELECT COUNT(*) FROM market_history WHERE metricKey = :metricKey")
    suspend fun countHistory(metricKey: String): Int

    @Query("SELECT * FROM bank_fd_rates ORDER BY generalRate DESC")
    fun observeBankFdRates(): Flow<List<BankFdRateEntity>>

    @Query("SELECT * FROM bank_fd_rates")
    suspend fun getBankFdRatesOnce(): List<BankFdRateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBankFdRate(entity: BankFdRateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBankFdRates(entities: List<BankFdRateEntity>)

    @Query("DELETE FROM bank_fd_rates WHERE bankId = :bankId")
    suspend fun deleteBankFdRate(bankId: String)

    @Query("DELETE FROM bank_fd_rates")
    suspend fun clearAllBankFdRates()
}

@Dao
interface ArticlesDao {

    @Query("SELECT articleId FROM article_bookmarks")
    fun observeBookmarkedIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(entity: ArticleBookmarkEntity)

    @Query("DELETE FROM article_bookmarks WHERE articleId = :articleId")
    suspend fun deleteBookmark(articleId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM article_bookmarks WHERE articleId = :articleId)")
    suspend fun isBookmarked(articleId: String): Boolean
}
