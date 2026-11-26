package com.jyotirmoyb.loan.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jyotirmoyb.loan.data.ManualOverrideEntity
import com.jyotirmoyb.loan.data.RemoteConfigCacheEntity
import com.jyotirmoyb.loan.data.RemoteConfigDao
import com.jyotirmoyb.loan.data.SyncMetadataEntity
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.domain.LoanCatalog
import com.jyotirmoyb.loan.domain.LoanCategory
import com.jyotirmoyb.loan.domain.LoanVariant
import com.jyotirmoyb.loan.domain.VariantGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteConfigAndResolverTest {

    private class FakeRemoteConfigDao : RemoteConfigDao {
        private val caches = MutableStateFlow<Map<String, RemoteConfigCacheEntity>>(emptyMap())
        private val overrides = MutableStateFlow<Map<String, ManualOverrideEntity>>(emptyMap())
        private val meta = MutableStateFlow<SyncMetadataEntity?>(null)

        override fun observeAllCaches(): Flow<List<RemoteConfigCacheEntity>> =
            caches.map { it.values.sortedBy { c -> c.fileName } }

        override suspend fun getCacheByFile(fileName: String): RemoteConfigCacheEntity? =
            caches.value[fileName]

        override suspend fun getAllCachesOnce(): List<RemoteConfigCacheEntity> =
            caches.value.values.toList()

        override suspend fun upsertCache(entity: RemoteConfigCacheEntity) {
            caches.value = caches.value + (entity.fileName to entity)
        }

        override suspend fun clearAllCaches() {
            caches.value = emptyMap()
        }

        override fun observeManualOverrides(): Flow<List<ManualOverrideEntity>> =
            overrides.map { it.values.sortedBy { o -> o.key } }

        override suspend fun getAllManualOverridesOnce(): List<ManualOverrideEntity> =
            overrides.value.values.toList()

        override suspend fun upsertManualOverride(entity: ManualOverrideEntity) {
            overrides.value = overrides.value + (entity.key to entity)
        }

        override suspend fun deleteManualOverride(key: String) {
            overrides.value = overrides.value - key
        }

        override suspend fun clearAllManualOverrides() {
            overrides.value = emptyMap()
        }

        override fun observeSyncMetadata(): Flow<SyncMetadataEntity?> = meta

        override suspend fun getSyncMetadataOnce(): SyncMetadataEntity? = meta.value

        override suspend fun upsertSyncMetadata(entity: SyncMetadataEntity) {
            meta.value = entity
        }
    }

    private class FakeSeedProvider(private val files: Map<String, String>) : SeedConfigProvider {
        override fun loadSeedJson(fileName: String): String? = files[fileName]
    }

    private class FakeRemoteConfigApi(
        private val handler: (file: String, ifNoneMatch: String?) -> Response<ResponseBody>
    ) : RemoteConfigApi {
        val seenIfNoneMatch = mutableMapOf<String, MutableList<String?>>()
        override suspend fun fetchConfig(file: String, ifNoneMatch: String?): Response<ResponseBody> {
            seenIfNoneMatch.getOrPut(file) { mutableListOf() }.add(ifNoneMatch)
            return handler(file, ifNoneMatch)
        }
    }

    private fun validRatesJson(repoRate: Double, publishedOn: String = "2026-11-20", lastVerified: String = "2026-11-20"): String =
        """
        {
          "schemaVersion": 1,
          "dataVersion": "2026.11.test",
          "publishedOn": "$publishedOn",
          "items": [
            {
              "key": "market.rbi_repo_rate",
              "label": "RBI Policy Repo Rate",
              "category": "MARKET_DATA",
              "value": $repoRate,
              "unit": "% p.a.",
              "effectiveFrom": "$publishedOn",
              "sourceName": "RBI",
              "sourceUrl": "https://www.rbi.org.in/",
              "lastVerified": "$lastVerified"
            },
            {
              "key": "loan.home.rate_min",
              "label": "Home Loan Min Rate",
              "category": "LOAN_RATES",
              "value": 8.25,
              "unit": "% p.a.",
              "effectiveFrom": "$publishedOn",
              "sourceName": "SBI",
              "sourceUrl": "https://sbi.co.in/",
              "lastVerified": "$lastVerified"
            },
            {
              "key": "loan.home.rate_max",
              "label": "Home Loan Max Rate",
              "category": "LOAN_RATES",
              "value": 10.15,
              "unit": "% p.a.",
              "effectiveFrom": "$publishedOn",
              "sourceName": "HDFC",
              "sourceUrl": "https://www.hdfc.com/",
              "lastVerified": "$lastVerified"
            },
            {
              "key": "loan.home.ltv_cap",
              "label": "Home Loan LTV Cap",
              "category": "LOAN_RATES",
              "value": 85.0,
              "unit": "%",
              "effectiveFrom": "$publishedOn",
              "sourceName": "RBI",
              "sourceUrl": "https://www.rbi.org.in/",
              "lastVerified": "$lastVerified"
            }
          ]
        }
        """.trimIndent()

    private fun okJsonResponse(json: String, etag: String): Response<ResponseBody> {
        val raw = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://raw.githubusercontent.com/test/rates.json").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .headers(Headers.headersOf("ETag", etag))
            .build()
        return Response.success(json.toResponseBody("application/json".toMediaType()), raw)
    }

    private fun notModifiedResponse(etag: String): Response<ResponseBody> {
        val raw = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://raw.githubusercontent.com/test/rates.json").build())
            .protocol(Protocol.HTTP_1_1)
            .code(304)
            .message("Not Modified")
            .headers(Headers.headersOf("ETag", etag))
            .build()
        return Response.error("".toResponseBody("application/json".toMediaType()), raw)
    }

    @Test
    fun resolverPriority_manualOverrideBeatsOnlineWhichBeatsSeed() {
        val seedDoc = RemoteConfigValidator.parseAndValidate(
            validRatesJson(repoRate = 6.50, publishedOn = "2026-10-01", lastVerified = UNVERIFIED_MARKER)
        ).getOrThrow()
        val onlineDoc = RemoteConfigValidator.parseAndValidate(
            validRatesJson(repoRate = 6.25, publishedOn = "2026-11-15", lastVerified = "2026-11-15")
        ).getOrThrow()

        // 1. Seed only
        val seedOnly = DataSourceResolver.resolveAll(
            seedDocuments = mapOf("rates.json" to seedDoc),
            onlineDocuments = emptyMap(),
            manualOverrides = emptyMap(),
            useOnlineData = true
        ).associateBy { it.key }
        val repoSeed = seedOnly[ConfigKeys.RBI_REPO_RATE]!!
        assertEquals(6.50, repoSeed.numericValue!!, 0.0001)
        assertEquals(ConfigValueSource.BUILT_IN, repoSeed.source)
        assertTrue(repoSeed.isUnverified)

        // 2. Online beats seed when useOnlineData = true
        val onlineWins = DataSourceResolver.resolveAll(
            seedDocuments = mapOf("rates.json" to seedDoc),
            onlineDocuments = mapOf("rates.json" to onlineDoc),
            manualOverrides = emptyMap(),
            useOnlineData = true
        ).associateBy { it.key }
        val repoOnline = onlineWins[ConfigKeys.RBI_REPO_RATE]!!
        assertEquals(6.25, repoOnline.numericValue!!, 0.0001)
        assertEquals(ConfigValueSource.ONLINE, repoOnline.source)
        assertFalse(repoOnline.isUnverified)
        assertEquals("15 Nov 2026", repoOnline.asOfDate)

        // 3. Manual override beats both online and seed
        val manual = ManualOverrideEntity(
            key = ConfigKeys.RBI_REPO_RATE,
            valueText = "5.75",
            updatedAtMillis = 123456L,
            updatedOnDate = "2026-11-25"
        )
        val manualWins = DataSourceResolver.resolveAll(
            seedDocuments = mapOf("rates.json" to seedDoc),
            onlineDocuments = mapOf("rates.json" to onlineDoc),
            manualOverrides = mapOf(manual.key to manual),
            useOnlineData = true
        ).associateBy { it.key }
        val repoManual = manualWins[ConfigKeys.RBI_REPO_RATE]!!
        assertEquals(5.75, repoManual.numericValue!!, 0.0001)
        assertEquals(ConfigValueSource.MANUAL, repoManual.source)
        assertFalse(repoManual.isUnverified)
        assertEquals("25 Nov 2026", repoManual.asOfDate)

        // 4. When useOnlineData = false, online is ignored, seed is used for non-overridden keys, manual stays active
        val offlinePref = DataSourceResolver.resolveAll(
            seedDocuments = mapOf("rates.json" to seedDoc),
            onlineDocuments = mapOf("rates.json" to onlineDoc),
            manualOverrides = mapOf(manual.key to manual),
            useOnlineData = false
        ).associateBy { it.key }
        assertEquals(ConfigValueSource.MANUAL, offlinePref[ConfigKeys.RBI_REPO_RATE]!!.source)
        assertEquals(ConfigValueSource.BUILT_IN, offlinePref[ConfigKeys.HOME_RATE_MIN]!!.source)
    }

    @Test
    fun resolveCatalog_hydratesCategoryAndVariantRatesFromResolvedData() {
        val doc = RemoteConfigValidator.parseAndValidate(validRatesJson(repoRate = 6.25)).getOrThrow()
        val resolved = DataSourceResolver.resolveAll(
            seedDocuments = mapOf("rates.json" to doc),
            onlineDocuments = emptyMap(),
            manualOverrides = emptyMap(),
            useOnlineData = true
        ).associateBy { it.key }

        val rawCatalog = LoanCatalog(
            categories = listOf(
                LoanCategory(
                    id = "home",
                    name = "Home Loan",
                    shortName = "Home",
                    rateRangeMin = 9.0,
                    rateRangeMax = 11.0,
                    rateMinKey = ConfigKeys.HOME_RATE_MIN,
                    rateMaxKey = ConfigKeys.HOME_RATE_MAX,
                    ltvMaxPercent = 80.0,
                    ltvCapKey = ConfigKeys.HOME_LTV_CAP,
                    variantGroups = listOf(
                        VariantGroup(
                            id = "rate_type",
                            name = "Rate",
                            variants = listOf(
                                LoanVariant(
                                    id = "eblr",
                                    name = "EBLR",
                                    howItWorks = "Repo linked",
                                    bestFor = "Buyers",
                                    ruleFlag = "RATE_EBLR",
                                    rateRangeMin = 9.0,
                                    rateRangeMax = 11.0,
                                    rateMinKey = ConfigKeys.HOME_RATE_MIN,
                                    rateMaxKey = ConfigKeys.HOME_RATE_MAX
                                )
                            )
                        )
                    )
                )
            )
        )

        val hydrated = DataSourceResolver.resolveCatalog(rawCatalog, resolved)
        val home = hydrated.categories.first()
        assertEquals(8.25, home.rateRangeMin, 0.001)
        assertEquals(10.15, home.rateRangeMax, 0.001)
        assertEquals(85.0, home.ltvMaxPercent, 0.001)
        assertEquals(8.25, home.variantGroups.first().variants.first().rateRangeMin, 0.001)
    }

    @Test
    fun ratesRepository_etagCaching_200Then304PreservesCacheAndSendsIfNoneMatch() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesRepository(context)
        prefs.setUseOnlineData(true)

        val dao = FakeRemoteConfigDao()
        val seedProvider = FakeSeedProvider(
            mapOf(
                "rates.json" to validRatesJson(repoRate = 6.50, lastVerified = UNVERIFIED_MARKER),
                "taxslabs.json" to validRatesJson(repoRate = 6.50),
                "schemes.json" to validRatesJson(repoRate = 6.50),
                "articles.json" to validRatesJson(repoRate = 6.50)
            )
        )

        val etagV1 = "\"etag-v1-rates\""
        val onlineJson = validRatesJson(repoRate = 6.00, publishedOn = "2026-11-24", lastVerified = "2026-11-24")
        val api = FakeRemoteConfigApi { file, ifNoneMatch ->
            if (ifNoneMatch == etagV1) {
                notModifiedResponse(etagV1)
            } else {
                okJsonResponse(onlineJson, etagV1)
            }
        }

        val repo = RatesRepository(dao, prefs, seedProvider, api)

        // First refresh: receives 200 OK and stores ETag + body
        val firstRefresh = repo.refreshNow()
        assertTrue(firstRefresh.success)
        assertEquals(4, firstRefresh.updatedFiles)
        assertEquals(0, firstRefresh.notModifiedFiles)
        assertEquals(null, api.seenIfNoneMatch["rates.json"]?.first())

        val cachedAfterFirst = dao.getCacheByFile("rates.json")
        assertNotNull(cachedAfterFirst)
        assertEquals(etagV1, cachedAfterFirst!!.etag)

        val resolvedFirst = repo.resolvedMapFlow.first()
        assertEquals(6.00, resolvedFirst[ConfigKeys.RBI_REPO_RATE]!!.numericValue!!, 0.001)
        assertEquals(ConfigValueSource.ONLINE, resolvedFirst[ConfigKeys.RBI_REPO_RATE]!!.source)

        // Second refresh: sends If-None-Match header and receives 304 Not Modified
        val secondRefresh = repo.refreshNow()
        assertTrue(secondRefresh.success)
        assertEquals(0, secondRefresh.updatedFiles)
        assertEquals(4, secondRefresh.notModifiedFiles)
        assertEquals(etagV1, api.seenIfNoneMatch["rates.json"]?.last())

        // Cached data remains intact after 304
        val resolvedSecond = repo.resolvedMapFlow.first()
        assertEquals(6.00, resolvedSecond[ConfigKeys.RBI_REPO_RATE]!!.numericValue!!, 0.001)
        assertEquals(ConfigValueSource.ONLINE, resolvedSecond[ConfigKeys.RBI_REPO_RATE]!!.source)
    }

    @Test
    fun ratesRepository_schemaFailureRejectsInvalidPayloadAndFallsBackToSeed() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesRepository(context)
        prefs.setUseOnlineData(true)

        val dao = FakeRemoteConfigDao()
        val seedProvider = FakeSeedProvider(
            mapOf("rates.json" to validRatesJson(repoRate = 6.50, lastVerified = UNVERIFIED_MARKER))
        )

        val invalidSchemaJson = """
            {
              "schemaVersion": 0,
              "dataVersion": "bad",
              "publishedOn": "2026-11-24",
              "items": []
            }
        """.trimIndent()

        val api = FakeRemoteConfigApi { _, _ -> okJsonResponse(invalidSchemaJson, "\"bad-etag\"") }
        val repo = RatesRepository(dao, prefs, seedProvider, api)

        val summary = repo.refreshNow()
        assertFalse(summary.success)
        assertEquals(0, summary.updatedFiles)
        assertEquals(4, summary.failedFiles)
        assertEquals(null, dao.getCacheByFile("rates.json"))

        // Still safely resolves from bundled seed
        val resolved = repo.resolvedMapFlow.first()
        assertEquals(6.50, resolved[ConfigKeys.RBI_REPO_RATE]!!.numericValue!!, 0.001)
        assertEquals(ConfigValueSource.BUILT_IN, resolved[ConfigKeys.RBI_REPO_RATE]!!.source)
    }

    @Test
    fun validator_rejectsNonHttpsSourceUrlAndInvalidJson() {
        val httpPayload = validRatesJson(6.5).replace("https://www.rbi.org.in/", "http://insecure.example.com/")
        assertTrue(RemoteConfigValidator.parseAndValidate(httpPayload).isFailure)

        val jsPayload = validRatesJson(6.5).replace("https://www.rbi.org.in/", "javascript:alert(1)")
        assertTrue(RemoteConfigValidator.parseAndValidate(jsPayload).isFailure)

        assertTrue(RemoteConfigValidator.parseAndValidate("{not-json").isFailure)
        assertTrue(RemoteConfigValidator.parseAndValidate("").isFailure)
    }

    @Test
    fun bundledAndRepoRemoteConfigFiles_allPassStrictSchemaValidation() {
        val assetDir = File("src/main/assets/remote-config").let { f ->
            if (f.exists()) f else File("app/src/main/assets/remote-config")
        }
        val repoDir = File("../remote-config").let { f ->
            if (f.exists()) f else File("remote-config")
        }
        assertTrue("Bundled remote-config dir must exist", assetDir.exists())
        assertTrue("Repo-root remote-config dir must exist", repoDir.exists())

        for (fileName in REMOTE_CONFIG_FILES) {
            val assetFile = File(assetDir, fileName)
            assertTrue("Missing bundled seed $fileName", assetFile.exists())
            val assetValidation = RemoteConfigValidator.parseAndValidate(assetFile.readText())
            assertTrue("Bundled $fileName failed validation: ${assetValidation.exceptionOrNull()}", assetValidation.isSuccess)

            val repoFile = File(repoDir, fileName)
            assertTrue("Missing repo remote-config/$fileName", repoFile.exists())
            val repoValidation = RemoteConfigValidator.parseAndValidate(repoFile.readText())
            assertTrue("Repo $fileName failed validation: ${repoValidation.exceptionOrNull()}", repoValidation.isSuccess)
        }
    }

    @Test
    fun manualOverride_exportImportAndResetToDefaultWorkCleanly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesRepository(context)
        val dao = FakeRemoteConfigDao()
        val seedProvider = FakeSeedProvider(
            mapOf("rates.json" to validRatesJson(repoRate = 6.50))
        )
        val api = FakeRemoteConfigApi { _, _ -> notModifiedResponse("\"none\"") }
        val repo = RatesRepository(dao, prefs, seedProvider, api)

        val initial = repo.resolvedMapFlow.first()[ConfigKeys.RBI_REPO_RATE]!!
        assertTrue(repo.setManualOverride(initial, "5.85").isSuccess)
        assertEquals(5.85, repo.resolvedMapFlow.first()[ConfigKeys.RBI_REPO_RATE]!!.numericValue!!, 0.001)

        // Export JSON, reset all, then re-import
        val exported = repo.exportOverridesJson()
        assertTrue(exported.contains("5.85"))
        repo.resetAllOverrides()
        assertEquals(6.50, repo.resolvedMapFlow.first()[ConfigKeys.RBI_REPO_RATE]!!.numericValue!!, 0.001)

        val importResult = repo.importOverridesJson(exported)
        assertTrue(importResult.isSuccess)
        assertEquals(1, importResult.getOrThrow())
        assertEquals(5.85, repo.resolvedMapFlow.first()[ConfigKeys.RBI_REPO_RATE]!!.numericValue!!, 0.001)

        // Reset single key reverts to seed
        repo.resetToDefault(ConfigKeys.RBI_REPO_RATE)
        assertEquals(ConfigValueSource.BUILT_IN, repo.resolvedMapFlow.first()[ConfigKeys.RBI_REPO_RATE]!!.source)
    }
}
