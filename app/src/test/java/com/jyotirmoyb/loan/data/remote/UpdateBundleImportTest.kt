package com.jyotirmoyb.loan.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jyotirmoyb.loan.data.ManualOverrideEntity
import com.jyotirmoyb.loan.data.RemoteConfigCacheEntity
import com.jyotirmoyb.loan.data.RemoteConfigDao
import com.jyotirmoyb.loan.data.SyncMetadataEntity
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateBundleImportTest {

    private class InMemoryRemoteConfigDao : RemoteConfigDao {
        private val caches = MutableStateFlow<Map<String, RemoteConfigCacheEntity>>(emptyMap())
        private val overrides = MutableStateFlow<Map<String, ManualOverrideEntity>>(emptyMap())
        private val meta = MutableStateFlow<SyncMetadataEntity?>(null)

        override fun observeAllCaches(): Flow<List<RemoteConfigCacheEntity>> = caches.map { it.values.sortedBy { c -> c.fileName } }

        override suspend fun getCacheByFile(fileName: String): RemoteConfigCacheEntity? = caches.value[fileName]

        override suspend fun getAllCachesOnce(): List<RemoteConfigCacheEntity> = caches.value.values.toList()

        override suspend fun upsertCache(entity: RemoteConfigCacheEntity) {
            caches.value = caches.value + (entity.fileName to entity)
        }

        override suspend fun deleteCacheByFile(fileName: String) {
            caches.value = caches.value - fileName
        }

        override suspend fun clearAllCaches() {
            caches.value = emptyMap()
        }

        override fun observeManualOverrides(): Flow<List<ManualOverrideEntity>> = overrides.map { it.values.sortedBy { o -> o.key } }

        override suspend fun getAllManualOverridesOnce(): List<ManualOverrideEntity> = overrides.value.values.toList()

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

    private val dummyApi = object : RemoteConfigApi {
        override suspend fun fetchConfig(file: String, ifNoneMatch: String?): Response<ResponseBody> {
            val raw = okhttp3.Response.Builder()
                .request(Request.Builder().url("https://example.com/$file").build())
                .protocol(Protocol.HTTP_1_1)
                .code(304)
                .message("Not Modified")
                .build()
            return Response.error("".toResponseBody("application/json".toMediaType()), raw)
        }
    }

    private fun seedItems(): Map<String, RemoteConfigItem> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val seedProvider = AssetSeedConfigProvider(context)
        val map = LinkedHashMap<String, RemoteConfigItem>()
        for (file in REMOTE_CONFIG_FILES) {
            val raw = seedProvider.loadSeedJson(file) ?: continue
            val doc = RemoteConfigValidator.parseAndValidate(raw).getOrThrow()
            doc.items.forEach { map[it.key] = it }
        }
        return map
    }

    private fun sampleBundleJson(
        dataVersion: String = "2026.11.27",
        key: String = ConfigKeys.RBI_REPO_RATE,
        valueJson: String = "5.5",
        unit: String = "% p.a.",
        effectiveFrom: String = "2026-10-07",
        sourceName: String = "Reserve Bank of India",
        sourceUrl: String = "https://www.rbi.org.in/Scripts/BS_PressReleaseDisplay.aspx",
        lastVerified: String = "2026-11-27",
        extraRatesItems: String = "",
        taxYearsJson: String = "",
        confirmedUnchangedJson: String = "[]",
        notesJson: String = "[]"
    ): String {
        val ratesItems = if (extraRatesItems.isBlank()) {
            """
            {
              "key": "$key",
              "value": $valueJson,
              "unit": "$unit",
              "effectiveFrom": "$effectiveFrom",
              "sourceName": "$sourceName",
              "sourceUrl": "$sourceUrl",
              "lastVerified": "$lastVerified"
            }
            """.trimIndent()
        } else {
            """
            {
              "key": "$key",
              "value": $valueJson,
              "unit": "$unit",
              "effectiveFrom": "$effectiveFrom",
              "sourceName": "$sourceName",
              "sourceUrl": "$sourceUrl",
              "lastVerified": "$lastVerified"
            },
            $extraRatesItems
            """.trimIndent()
        }
        val taxSection = if (taxYearsJson.isBlank()) {
            ""
        } else {
            """
            "taxslabs": {
              "taxYears": $taxYearsJson
            },
            """.trimIndent()
        }
        return """
        {
          "bundleVersion": 1,
          "generatedOn": "2026-11-27",
          "generatedBy": "Test AI Bundle",
          "dataVersion": "$dataVersion",
          "rates": {
            "items": [
              $ratesItems
            ]
          },
          $taxSection
          "confirmedUnchanged": $confirmedUnchangedJson,
          "unresolved": [{"key": "loan.gold.rate_max", "reason": "Pending circular"}],
          "suggestedNewKeys": [],
          "notes": $notesJson
        }
        """.trimIndent()
    }

    @Test
    fun validator_rejectsBadJsonAndEmptyPayload() {
        val known = seedItems()
        assertTrue(RemoteConfigValidator.parseAndValidateUpdateBundle("{broken-json", known).isFailure)
        assertTrue(RemoteConfigValidator.parseAndValidateUpdateBundle("", known).isFailure)
    }

    @Test
    fun validator_rejectsOversizedFileOverOneMegabyte() {
        val known = seedItems()
        val padding = "a".repeat(RemoteConfigValidator.MAX_UPDATE_BUNDLE_BYTES + 128)
        val hugeJson = sampleBundleJson(notesJson = "[\"$padding\"]")
        val result = RemoteConfigValidator.parseAndValidateUpdateBundle(hugeJson, known)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("1 MB"))
    }

    @Test
    fun validator_rejectsUnknownKeysInItemsAndConfirmedUnchanged() {
        val known = seedItems()
        val badItemBundle = sampleBundleJson(key = "loan.unknown.phantom_rate")
        val itemResult = RemoteConfigValidator.parseAndValidateUpdateBundle(badItemBundle, known)
        assertTrue(itemResult.isFailure)
        assertTrue(itemResult.exceptionOrNull()!!.message!!.contains("Unknown key"))

        val badConfirmed = sampleBundleJson(confirmedUnchangedJson = "[\"tax.nonexistent_key\"]")
        val confirmedResult = RemoteConfigValidator.parseAndValidateUpdateBundle(badConfirmed, known)
        assertTrue(confirmedResult.isFailure)
        assertTrue(confirmedResult.exceptionOrNull()!!.message!!.contains("Unknown key"))
    }

    @Test
    fun validator_rejectsWrongTypeForNumericKey() {
        val known = seedItems()
        val nonNumericBundle = sampleBundleJson(valueJson = "\"five point five\"")
        val result = RemoteConfigValidator.parseAndValidateUpdateBundle(nonNumericBundle, known)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("numeric"))
    }

    @Test
    fun validator_rejectsUnitMismatch() {
        val known = seedItems()
        val wrongUnitBundle = sampleBundleJson(unit = "INR")
        val result = RemoteConfigValidator.parseAndValidateUpdateBundle(wrongUnitBundle, known)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Unit mismatch"))
    }

    @Test
    fun validator_rejectsHttpSourceUrlAndUnverifiedOrInvalidDates() {
        val known = seedItems()

        val httpBundle = sampleBundleJson(sourceUrl = "http://www.rbi.org.in/press")
        assertTrue(RemoteConfigValidator.parseAndValidateUpdateBundle(httpBundle, known).isFailure)

        val unverifiedBundle = sampleBundleJson(lastVerified = UNVERIFIED_MARKER)
        assertTrue(RemoteConfigValidator.parseAndValidateUpdateBundle(unverifiedBundle, known).isFailure)

        val badLastVerified = sampleBundleJson(lastVerified = "2026-02-30")
        assertTrue(RemoteConfigValidator.parseAndValidateUpdateBundle(badLastVerified, known).isFailure)

        val badEffectiveFrom = sampleBundleJson(effectiveFrom = "27-11-2026")
        assertTrue(RemoteConfigValidator.parseAndValidateUpdateBundle(badEffectiveFrom, known).isFailure)
    }

    @Test
    fun validator_rejectsMalformedTaxYearsSlabOrderAndOutOfRangeRate() {
        val known = seedItems()

        fun buildTaxYears(firstUpTo: Int, secondUpTo: Int, secondRate: Int): String = """
        [
          {
            "fyCode": "FY 2026-27",
            "ayCode": "AY 2027-28",
            "lawReference": "Finance Act 2026",
            "effectiveFrom": "2026-04-01",
            "cessPercent": 4.0,
            "newRegime": {
              "regimeId": "NEW",
              "displayName": "New Regime",
              "standardDeductionSalaried": 75000,
              "rebateIncomeLimit": 1200000,
              "maxRebateAmount": 60000,
              "rebateMarginalRelief": true,
              "slabsGeneral": [
                {"upTo": $firstUpTo, "ratePercent": 0},
                {"upTo": $secondUpTo, "ratePercent": $secondRate},
                {"upTo": null, "ratePercent": 30}
              ]
            },
            "oldRegime": {
              "regimeId": "OLD",
              "displayName": "Old Regime",
              "standardDeductionSalaried": 50000,
              "rebateIncomeLimit": 500000,
              "maxRebateAmount": 12500,
              "rebateMarginalRelief": false,
              "slabsGeneral": [
                {"upTo": 250000, "ratePercent": 0},
                {"upTo": null, "ratePercent": 30}
              ]
            },
            "deductions": []
          }
        ]
        """.trimIndent()

        val orderRes = RemoteConfigValidator.parseAndValidateUpdateBundle(
            sampleBundleJson(taxYearsJson = buildTaxYears(firstUpTo = 800000, secondUpTo = 400000, secondRate = 5)),
            known
        )
        assertTrue(orderRes.isFailure)
        assertTrue(
            "Expected ascending order error, got: ${orderRes.exceptionOrNull()?.message}",
            orderRes.exceptionOrNull()!!.message!!.contains("ascending")
        )

        val rateRes = RemoteConfigValidator.parseAndValidateUpdateBundle(
            sampleBundleJson(taxYearsJson = buildTaxYears(firstUpTo = 400000, secondUpTo = 800000, secondRate = 125)),
            known
        )
        assertTrue(rateRes.isFailure)
        assertTrue(
            "Expected slab rate error, got: ${rateRes.exceptionOrNull()?.message}",
            rateRes.exceptionOrNull()!!.message!!.contains("slab rate")
        )
    }

    @Test
    fun mergePriority_manualBeatsImportedAndOnlineByNewestDataVersionBeatsBuiltIn() {
        val seedDoc = RemoteConfigDocument(
            schemaVersion = 1,
            dataVersion = "2026.10.01",
            publishedOn = "2026-10-01",
            items = listOf(
                RemoteConfigItem(
                    key = ConfigKeys.RBI_REPO_RATE,
                    label = "RBI Policy Repo Rate",
                    category = "MARKET_DATA",
                    value = ConfigScalar("6.5", 6.5),
                    unit = "% p.a.",
                    effectiveFrom = "2026-04-01",
                    sourceName = "RBI",
                    sourceUrl = "https://www.rbi.org.in",
                    lastVerified = UNVERIFIED_MARKER
                )
            )
        )
        val olderOnline = seedDoc.copy(
            dataVersion = "2026.11.01",
            publishedOn = "2026-11-01",
            items = listOf(
                seedDoc.items.first().copy(
                    value = ConfigScalar("6.0", 6.0),
                    lastVerified = "2026-11-01"
                )
            )
        )
        val newerImported = seedDoc.copy(
            dataVersion = "2026.11.27",
            publishedOn = "2026-11-27",
            items = listOf(
                seedDoc.items.first().copy(
                    value = ConfigScalar("5.5", 5.5),
                    lastVerified = "2026-11-27"
                )
            )
        )
        val newerOnline = seedDoc.copy(
            dataVersion = "2026.12.15",
            publishedOn = "2026-12-15",
            items = listOf(
                seedDoc.items.first().copy(
                    value = ConfigScalar("5.25", 5.25),
                    lastVerified = "2026-12-15"
                )
            )
        )

        // Newer Imported beats older Online
        val importedWins = DataSourceResolver.resolveAll(
            seedDocuments = mapOf("rates.json" to seedDoc),
            onlineDocuments = mapOf("rates.json" to olderOnline),
            manualOverrides = emptyMap(),
            useOnlineData = true,
            importedDocuments = mapOf("rates.json" to newerImported)
        ).associateBy { it.key }
        assertEquals(5.5, importedWins[ConfigKeys.RBI_REPO_RATE]!!.numericValue!!, 0.001)
        assertEquals(ConfigValueSource.IMPORTED, importedWins[ConfigKeys.RBI_REPO_RATE]!!.source)

        // Newer Online beats older Imported
        val onlineWins = DataSourceResolver.resolveAll(
            seedDocuments = mapOf("rates.json" to seedDoc),
            onlineDocuments = mapOf("rates.json" to newerOnline),
            manualOverrides = emptyMap(),
            useOnlineData = true,
            importedDocuments = mapOf("rates.json" to newerImported)
        ).associateBy { it.key }
        assertEquals(5.25, onlineWins[ConfigKeys.RBI_REPO_RATE]!!.numericValue!!, 0.001)
        assertEquals(ConfigValueSource.ONLINE, onlineWins[ConfigKeys.RBI_REPO_RATE]!!.source)

        // Manual override beats both Imported and Online
        val manual = ManualOverrideEntity(
            key = ConfigKeys.RBI_REPO_RATE,
            valueText = "5.0",
            updatedAtMillis = 1_000L,
            updatedOnDate = "2026-11-28"
        )
        val manualWins = DataSourceResolver.resolveAll(
            seedDocuments = mapOf("rates.json" to seedDoc),
            onlineDocuments = mapOf("rates.json" to newerOnline),
            manualOverrides = mapOf(manual.key to manual),
            useOnlineData = true,
            importedDocuments = mapOf("rates.json" to newerImported)
        ).associateBy { it.key }
        assertEquals(5.0, manualWins[ConfigKeys.RBI_REPO_RATE]!!.numericValue!!, 0.001)
        assertEquals(ConfigValueSource.MANUAL, manualWins[ConfigKeys.RBI_REPO_RATE]!!.source)
    }

    @Test
    fun multiPartImport_selectiveUntick_andLastFiveUndoHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesRepository(context)
        prefs.setUseOnlineData(false)

        val dao = InMemoryRemoteConfigDao()
        val seedProvider = AssetSeedConfigProvider(context)
        val repo = RatesRepository(dao, prefs, seedProvider, dummyApi)

        val baselineRepoRate = repo.resolvedMapFlow.first()[ConfigKeys.RBI_REPO_RATE]!!.numericValue!!
        val baselineHomeMin = repo.resolvedMapFlow.first()[ConfigKeys.HOME_RATE_MIN]!!.numericValue!!

        // Part 1/2: updates RBI Repo Rate + Home Min Rate (untick Home Min Rate to test selective apply)
        val part1Json = sampleBundleJson(
            dataVersion = "2026.11.27",
            key = ConfigKeys.RBI_REPO_RATE,
            valueJson = "5.40",
            extraRatesItems = """
            {
              "key": "${ConfigKeys.HOME_RATE_MIN}",
              "value": 7.45,
              "unit": "% p.a.",
              "effectiveFrom": "2026-10-15",
              "sourceName": "SBI",
              "sourceUrl": "https://sbi.co.in/",
              "lastVerified": "2026-11-27"
            }
            """.trimIndent(),
            notesJson = "[\"PART 1/2 rates\"]"
        )
        val preview1 = repo.previewUpdateBundle(part1Json, "loanjb-PART 1 of 2.json").getOrThrow()
        val part1 = preview1.partInfo
        assertNotNull(part1)
        assertEquals(1, part1!!.partNumber)
        assertEquals(2, part1.totalParts)
        assertEquals(2, preview1.itemChanges.size)

        // Untick HOME_RATE_MIN, apply only RBI_REPO_RATE
        repo.applyUpdateBundlePreview(
            preview = preview1,
            selectedItemKeys = setOf(ConfigKeys.RBI_REPO_RATE),
            selectedTableIds = emptySet(),
            applyConfirmedUnchanged = false
        ).getOrThrow()

        val afterPart1 = repo.resolvedMapFlow.first()
        assertEquals(5.40, afterPart1[ConfigKeys.RBI_REPO_RATE]!!.numericValue!!, 0.001)
        assertEquals(ConfigValueSource.IMPORTED, afterPart1[ConfigKeys.RBI_REPO_RATE]!!.source)
        assertEquals(baselineHomeMin, afterPart1[ConfigKeys.HOME_RATE_MIN]!!.numericValue!!, 0.001)

        // Part 2/2: updates HOME_RATE_MIN while preserving Part 1's RBI_REPO_RATE
        val part2Json = sampleBundleJson(
            dataVersion = "2026.11.27",
            key = ConfigKeys.HOME_RATE_MIN,
            valueJson = "7.45",
            notesJson = "[\"PART 2/2 home loans\"]"
        )
        val preview2 = repo.previewUpdateBundle(part2Json, "loanjb-PART 2/2.json").getOrThrow()
        val part2 = preview2.partInfo
        assertNotNull(part2)
        assertEquals(2, part2!!.partNumber)
        assertEquals(2, part2.totalParts)
        repo.applyUpdateBundlePreview(preview2).getOrThrow()

        val afterPart2 = repo.resolvedMapFlow.first()
        assertEquals(5.40, afterPart2[ConfigKeys.RBI_REPO_RATE]!!.numericValue!!, 0.001)
        assertEquals(7.45, afterPart2[ConfigKeys.HOME_RATE_MIN]!!.numericValue!!, 0.001)

        // Undo Part 2/2 -> restores Part 1/2 state
        repo.undoLastImport().getOrThrow()
        val afterUndoPart2 = repo.resolvedMapFlow.first()
        assertEquals(5.40, afterUndoPart2[ConfigKeys.RBI_REPO_RATE]!!.numericValue!!, 0.001)
        assertEquals(baselineHomeMin, afterUndoPart2[ConfigKeys.HOME_RATE_MIN]!!.numericValue!!, 0.001)

        // Undo Part 1/2 -> restores built-in seed state
        repo.undoLastImport().getOrThrow()
        val afterUndoPart1 = repo.resolvedMapFlow.first()
        assertEquals(baselineRepoRate, afterUndoPart1[ConfigKeys.RBI_REPO_RATE]!!.numericValue!!, 0.001)
        assertEquals(ConfigValueSource.BUILT_IN, afterUndoPart1[ConfigKeys.RBI_REPO_RATE]!!.source)

        // Push 6 sequential imports -> undo history caps at 5
        for (i in 1..6) {
            val rate = 5.0 + i * 0.1
            val json = sampleBundleJson(dataVersion = "2026.11.2$i", valueJson = "%.2f".format(Locale.US, rate))
            val p = repo.previewUpdateBundle(json, "import_$i.json").getOrThrow()
            repo.applyUpdateBundlePreview(p).getOrThrow()
        }
        assertEquals(UpdateBundleManager.MAX_IMPORT_HISTORY, repo.importHistoryCountFlow.first())
    }

    @Test
    fun staleDataEvaluator_checksThresholdAcrossTaxAndLoanKeys() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val nowMillis = sdf.parse("2026-11-27")!!.time

        fun makeItem(key: String, category: ConfigCategory, lastVerified: String) = ResolvedConfigItem(
            key = key,
            label = key,
            category = category,
            fileName = "rates.json",
            valueText = "8.5",
            numericValue = 8.5,
            unit = "% p.a.",
            effectiveFrom = "2026-04-01",
            asOfDate = "27 Nov 2026",
            sourceName = "RBI",
            sourceUrl = "https://www.rbi.org.in",
            lastVerified = lastVerified,
            source = ConfigValueSource.BUILT_IN,
            defaultValueText = "8.5",
            defaultNumericValue = 8.5
        )

        // Fresh (30 days old, threshold 90 days) -> not stale
        val freshList = listOf(
            makeItem(ConfigKeys.HOME_RATE_MIN, ConfigCategory.LOAN_RATES, "2026-10-28"),
            makeItem(ConfigKeys.TAX_NEW_REBATE_LIMIT, ConfigCategory.TAX_SLABS, "2026-04-01")
        )
        assertFalse(DataSourceResolver.isTaxOrLoanDataStale(freshList, thresholdDays = 90, nowMillis = nowMillis))

        // Same list with threshold 20 days -> stale
        assertTrue(DataSourceResolver.isTaxOrLoanDataStale(freshList, thresholdDays = 20, nowMillis = nowMillis))

        // Older than 90 days -> stale
        val staleList = listOf(
            makeItem(ConfigKeys.HOME_RATE_MIN, ConfigCategory.LOAN_RATES, "2026-07-01"),
            makeItem(ConfigKeys.TAX_NEW_REBATE_LIMIT, ConfigCategory.TAX_SLABS, "2026-06-15")
        )
        assertTrue(DataSourceResolver.isTaxOrLoanDataStale(staleList, thresholdDays = 90, nowMillis = nowMillis))

        // All UNVERIFIED -> stale
        val unverifiedList = listOf(
            makeItem(ConfigKeys.HOME_RATE_MIN, ConfigCategory.LOAN_RATES, UNVERIFIED_MARKER)
        )
        assertTrue(DataSourceResolver.isTaxOrLoanDataStale(unverifiedList, thresholdDays = 90, nowMillis = nowMillis))
    }
}
