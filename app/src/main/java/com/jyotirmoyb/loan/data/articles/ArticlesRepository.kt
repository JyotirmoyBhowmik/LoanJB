package com.jyotirmoyb.loan.data.articles

import android.content.Context
import com.jyotirmoyb.loan.data.ArticleBookmarkEntity
import com.jyotirmoyb.loan.data.ArticlesDao
import com.jyotirmoyb.loan.data.remote.ConfigValueSource
import com.jyotirmoyb.loan.data.remote.RatesRepository
import com.jyotirmoyb.loan.domain.ArticleCategory
import com.jyotirmoyb.loan.domain.ArticleItem
import com.jyotirmoyb.loan.domain.ReadingTimeEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class ArticlesRepository(private val context: Context, private val articlesDao: ArticlesDao, private val ratesRepository: RatesRepository) {
    private val appContext = context.applicationContext
    private val assetCache = HashMap<String, String>()

    val articlesFlow: Flow<List<ArticleItem>> = combine(
        ratesRepository.remoteArticlesFlow,
        articlesDao.observeBookmarkedIds()
    ) { (records, source), bookmarkedIds ->
        val bookmarkedSet = bookmarkedIds.toHashSet()
        val isOnline = source == ConfigValueSource.ONLINE

        records.mapNotNull { rec ->
            val body = resolveMarkdownBody(rec.markdownBody, rec.assetPath)
                ?: return@mapNotNull null
            ArticleItem(
                id = rec.id,
                title = rec.title,
                summary = rec.summary,
                category = ArticleCategory.fromWireName(rec.category),
                lastReviewed = rec.lastReviewed,
                tags = rec.tags,
                markdownBody = body,
                readingTimeMinutes = ReadingTimeEstimator.estimateMinutes(body),
                sourceName = rec.sourceName.ifBlank { "LoanJB Editorial" },
                isFromOnlineConfig = isOnline,
                isBookmarked = rec.id in bookmarkedSet
            )
        }
    }

    suspend fun toggleBookmark(articleId: String): Boolean = withContext(Dispatchers.IO) {
        val currentlyBookmarked = articlesDao.isBookmarked(articleId)
        if (currentlyBookmarked) {
            articlesDao.deleteBookmark(articleId)
            false
        } else {
            articlesDao.upsertBookmark(
                ArticleBookmarkEntity(
                    articleId = articleId,
                    bookmarkedAtMillis = System.currentTimeMillis()
                )
            )
            true
        }
    }

    suspend fun refreshFromRemote(): String = withContext(Dispatchers.IO) {
        ratesRepository.refreshNow().message
    }

    private fun resolveMarkdownBody(remoteBody: String?, assetPath: String?): String? {
        if (!remoteBody.isNullOrBlank()) return remoteBody
        val cleanPath = assetPath?.trim()?.removePrefix("/") ?: return null
        if (cleanPath.contains("..")) return null
        synchronized(assetCache) {
            assetCache[cleanPath]?.let { return it }
        }
        val loaded = runCatching {
            appContext.assets.open(cleanPath).bufferedReader().use { it.readText() }
        }.getOrNull()
        if (!loaded.isNullOrBlank()) {
            synchronized(assetCache) {
                assetCache[cleanPath] = loaded
            }
        }
        return loaded
    }
}
