package com.jyotirmoyb.loan.data.nav

import com.jyotirmoyb.loan.data.AmfiNavDao
import com.jyotirmoyb.loan.data.AmfiNavEntity
import com.jyotirmoyb.loan.data.AmfiNavSyncEntity
import com.jyotirmoyb.loan.domain.AmfiNavParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class AmfiSyncOutcome(
    val success: Boolean,
    val totalParsed: Int,
    val latestNavDate: String,
    val message: String
)

/**
 * Offline-first repository for AMFI India's public NAVAll.txt feed.
 * Streams the multi-MB text file directly from OkHttp's byteStream() through [AmfiNavParser]
 * in small chunks into Room — never loading the entire file into memory.
 */
class AmfiNavRepository(
    private val dao: AmfiNavDao,
    private val client: OkHttpClient = defaultClient()
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    val favouritesFlow: Flow<List<AmfiNavEntity>> = dao.observeFavourites()
    val syncMetaFlow: Flow<AmfiNavSyncEntity?> = dao.observeSyncMeta()
    val totalCountFlow: Flow<Int> = dao.observeTotalCount()

    fun searchSchemes(query: String): Flow<List<AmfiNavEntity>> {
        val cleaned = query.trim()
        return if (cleaned.isEmpty()) {
            dao.observeTopSchemes(80)
        } else {
            dao.searchSchemes(cleaned, 80)
        }
    }

    suspend fun toggleFavourite(schemeCode: String, current: Boolean) = withContext(Dispatchers.IO) {
        dao.setFavourite(schemeCode, !current)
    }

    suspend fun syncLiveNav(): AmfiSyncOutcome = withContext(Dispatchers.IO) {
        if (_isSyncing.value) {
            return@withContext AmfiSyncOutcome(false, 0, "", "Sync already in progress")
        }
        _isSyncing.value = true
        try {
            val existingFavs = dao.getFavouriteCodes().toHashSet()
            var lastError = "Unable to connect to AMFI"

            for (url in AMFI_NAV_URLS) {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "LoanJB-Android/1.0")
                    .build()

                val outcome = runCatching {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("HTTP ${response.code}")
                        }
                        val body = response.body ?: throw IllegalStateException("Empty NAV response")
                        val now = System.currentTimeMillis()
                        val reader = BufferedReader(
                            InputStreamReader(body.byteStream(), StandardCharsets.UTF_8),
                            32 * 1024
                        )
                        val summary = AmfiNavParser.parseStream(reader = reader, batchSize = 500) { batch ->
                            val entities = batch.map { item ->
                                AmfiNavEntity(
                                    schemeCode = item.schemeCode,
                                    isinGrowth = item.isinGrowth,
                                    isinReinvestment = item.isinReinvestment,
                                    schemeName = item.schemeName,
                                    amcName = item.amcName,
                                    categoryName = item.categoryName,
                                    nav = item.nav,
                                    navDate = item.navDate,
                                    isFavourite = existingFavs.contains(item.schemeCode),
                                    updatedAtMillis = now
                                )
                            }
                            runBlocking { dao.upsertBatch(entities) }
                        }

                        if (summary.totalParsed == 0) {
                            throw IllegalStateException("No valid NAV rows found")
                        }

                        val msg = "Synced ${summary.totalParsed} schemes (NAV date ${summary.latestNavDate})"
                        dao.upsertSyncMeta(
                            AmfiNavSyncEntity(
                                id = 1,
                                lastSyncedMillis = now,
                                latestNavDate = summary.latestNavDate,
                                totalSchemes = summary.totalParsed,
                                statusMessage = msg,
                                lastSuccess = true
                            )
                        )
                        AmfiSyncOutcome(
                            success = true,
                            totalParsed = summary.totalParsed,
                            latestNavDate = summary.latestNavDate,
                            message = msg
                        )
                    }
                }

                if (outcome.isSuccess) {
                    return@withContext outcome.getOrThrow()
                } else {
                    lastError = outcome.exceptionOrNull()?.localizedMessage ?: lastError
                }
            }

            // Preserve previous cache metadata if offline/failed
            val existingMeta = dao.getSyncMetaOnce()
            dao.upsertSyncMeta(
                AmfiNavSyncEntity(
                    id = 1,
                    lastSyncedMillis = existingMeta?.lastSyncedMillis ?: 0L,
                    latestNavDate = existingMeta?.latestNavDate.orEmpty(),
                    totalSchemes = existingMeta?.totalSchemes ?: 0,
                    statusMessage = "Offline — showing cached NAVs ($lastError)",
                    lastSuccess = false
                )
            )
            AmfiSyncOutcome(false, existingMeta?.totalSchemes ?: 0, existingMeta?.latestNavDate.orEmpty(), "Offline — showing cached NAVs ($lastError)")
        } finally {
            _isSyncing.value = false
        }
    }

    companion object {
        val AMFI_NAV_URLS = listOf(
            "https://www.amfiindia.com/spages/NAVAll.txt",
            "https://portal.amfiindia.com/spages/NAVAll.txt"
        )

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
