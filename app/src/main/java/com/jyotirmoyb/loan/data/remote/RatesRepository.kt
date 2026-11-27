package com.jyotirmoyb.loan.data.remote

import android.content.Context
import com.jyotirmoyb.loan.data.ManualOverrideEntity
import com.jyotirmoyb.loan.data.RemoteConfigCacheEntity
import com.jyotirmoyb.loan.data.RemoteConfigDao
import com.jyotirmoyb.loan.data.SyncMetadataEntity
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit

interface SeedConfigProvider {
    fun loadSeedJson(fileName: String): String?
}

class AssetSeedConfigProvider(private val context: Context) : SeedConfigProvider {
    override fun loadSeedJson(fileName: String): String? = runCatching {
        context.assets.open("remote-config/$fileName").bufferedReader().use { it.readText() }
    }.getOrNull()
}

data class RefreshSummary(val updatedFiles: Int, val notModifiedFiles: Int, val failedFiles: Int, val message: String, val success: Boolean)

class RatesRepository(
    private val remoteConfigDao: RemoteConfigDao,
    private val prefsRepo: UserPreferencesRepository,
    private val seedProvider: SeedConfigProvider,
    private val api: RemoteConfigApi = createDefaultApi()
) {
    private val moshi = RemoteConfigMoshi.instance
    private val overridesExportAdapter = moshi.adapter(ManualOverridesExport::class.java).indent("  ")
    private val bundleManager = UpdateBundleManager(remoteConfigDao, moshi)
    private val isSyncingFlow = MutableStateFlow(false)

    private val seedDocuments: Map<String, RemoteConfigDocument> by lazy {
        val map = LinkedHashMap<String, RemoteConfigDocument>()
        for (fileName in REMOTE_CONFIG_FILES) {
            val raw = seedProvider.loadSeedJson(fileName) ?: continue
            RemoteConfigValidator.parseAndValidate(raw, moshi).getOrNull()?.let { doc ->
                map[fileName] = doc
            }
        }
        map
    }

    val resolvedItemsFlow: Flow<List<ResolvedConfigItem>> = combine(
        remoteConfigDao.observeAllCaches(),
        remoteConfigDao.observeManualOverrides(),
        prefsRepo.preferencesFlow.map { it.useOnlineData }
    ) { caches, overrides, useOnlineData ->
        val onlineDocs = bundleManager.extractOnlineDocuments(caches)
        val importedDocs = bundleManager.extractImportedDocuments(caches)
        val overrideMap = overrides.associateBy { it.key }
        DataSourceResolver.resolveAll(
            seedDocuments = seedDocuments,
            onlineDocuments = onlineDocs,
            manualOverrides = overrideMap,
            useOnlineData = useOnlineData,
            importedDocuments = importedDocs
        )
    }

    val resolvedMapFlow: Flow<Map<String, ResolvedConfigItem>> =
        resolvedItemsFlow.map { list -> list.associateBy { it.key } }

    val importHistoryCountFlow: Flow<Int> =
        remoteConfigDao.observeAllCaches().map { caches -> bundleManager.readHistoryCount(caches) }

    val latestImportSummaryFlow: Flow<String?> =
        remoteConfigDao.observeAllCaches().map { caches -> bundleManager.readLatestHistorySummary(caches) }

    /**
     * Emits the versioned per-FY Taxation catalog from `taxslabs.json`
     * (Imported / Online with newest dataVersion -> Bundled seed fallback),
     * merged with any user manual overrides on scalar keys.
     */
    val taxRulesCatalogFlow: Flow<TaxRulesCatalog> = combine(
        remoteConfigDao.observeAllCaches(),
        resolvedMapFlow,
        prefsRepo.preferencesFlow.map { it.useOnlineData }
    ) { caches, resolvedMap, useOnlineData ->
        val seedDoc = seedDocuments["taxslabs.json"]
        val onlineDoc = bundleManager.extractOnlineDocuments(caches)["taxslabs.json"]
        val importedDoc = bundleManager.extractImportedDocuments(caches)["taxslabs.json"]

        val (winningDoc, winningSource) = DataSourceResolver.selectWinningDocument(
            seedDoc = seedDoc,
            onlineDoc = onlineDoc,
            importedDoc = importedDoc,
            useOnlineData = useOnlineData
        )
        val activeDoc = if (winningDoc != null && !winningDoc.taxYears.isNullOrEmpty()) winningDoc else seedDoc
        val baseSource = if (activeDoc === winningDoc && winningDoc != null) winningSource else ConfigValueSource.BUILT_IN

        val cessOverride = resolvedMap[ConfigKeys.TAX_CESS_PERCENT]?.numericValue
        val rawYears = activeDoc?.taxYears.orEmpty()
        val effectiveYears = if (cessOverride != null) {
            rawYears.map { fy -> fy.copy(cessPercent = cessOverride) }
        } else {
            rawYears
        }

        val sampleItem = activeDoc?.items?.firstOrNull { it.key.startsWith("tax.") }
        val publishedOn = activeDoc?.publishedOn ?: "2026-04-01"
        TaxRulesCatalog(
            taxYears = effectiveYears,
            gstSlabs = activeDoc?.gstSlabs?.takeIf { it.isNotEmpty() } ?: listOf(0.0, 3.0, 5.0, 12.0, 18.0, 28.0),
            advanceTaxThreshold = DataSourceResolver.resolveDouble(
                resolvedMap,
                ConfigKeys.TAX_ADVANCE_TAX_THRESHOLD,
                activeDoc?.advanceTaxThreshold ?: 10000.0
            ),
            advanceTaxSchedule = activeDoc?.advanceTaxSchedule.orEmpty(),
            hraMetroPercent = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.TAX_HRA_METRO_PERCENT, 50.0),
            hraNonMetroPercent = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.TAX_HRA_NON_METRO_PERCENT, 40.0),
            fdTdsGeneralThreshold = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.TDS_FD_GENERAL_THRESHOLD, 50000.0),
            fdTdsSeniorThreshold = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.TDS_FD_SENIOR_THRESHOLD, 100000.0),
            tdsRateWithPan = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.TDS_RATE_WITH_PAN, 10.0),
            tdsRateWithoutPan = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.TDS_RATE_WITHOUT_PAN, 20.0),
            dataVersion = activeDoc?.dataVersion ?: "2026.11.1",
            publishedOn = publishedOn,
            asOfDateFormatted = DataSourceResolver.formatIsoToIndiaDate(publishedOn),
            sourceName = sampleItem?.sourceName ?: "Income Tax Act & Finance Act India",
            sourceUrl = sampleItem?.sourceUrl ?: "https://www.incometax.gov.in",
            lastVerified = sampleItem?.lastVerified ?: UNVERIFIED_MARKER,
            source = baseSource
        )
    }

    /**
     * Emits the data-driven Retirement, Government Pension (APY, PM-SYM, PMVVY/Senior Schemes),
     * and Asset Allocation catalog from `schemes.json`
     * (Imported / Online with newest dataVersion -> Bundled seed fallback).
     */
    val retirementSchemesCatalogFlow: Flow<RetirementSchemesCatalog> = combine(
        remoteConfigDao.observeAllCaches(),
        resolvedMapFlow,
        prefsRepo.preferencesFlow.map { it.useOnlineData }
    ) { caches, resolvedMap, useOnlineData ->
        val seedDoc = seedDocuments["schemes.json"]
        val onlineDoc = bundleManager.extractOnlineDocuments(caches)["schemes.json"]
        val importedDoc = bundleManager.extractImportedDocuments(caches)["schemes.json"]

        val (winningDoc, winningSource) = DataSourceResolver.selectWinningDocument(
            seedDoc = seedDoc,
            onlineDoc = onlineDoc,
            importedDoc = importedDoc,
            useOnlineData = useOnlineData
        )
        val activeDoc = if (winningDoc != null && !winningDoc.apyTable.isNullOrEmpty()) winningDoc else seedDoc
        val baseSource = if (activeDoc === winningDoc && winningDoc != null) winningSource else ConfigValueSource.BUILT_IN

        val sampleItem = activeDoc?.items?.firstOrNull { it.key.startsWith("scheme.") }
        val publishedOn = activeDoc?.publishedOn ?: "2026-11-25"
        RetirementSchemesCatalog(
            apyTable = activeDoc?.apyTable ?: seedDoc?.apyTable.orEmpty(),
            apyCorpusMultipliers = mapOf(
                1000 to 170000.0,
                2000 to 340000.0,
                3000 to 510000.0,
                4000 to 680000.0,
                5000 to 850000.0
            ),
            pmsymTable = activeDoc?.pmsymTable ?: seedDoc?.pmsymTable.orEmpty(),
            pmsymAssuredPension = DataSourceResolver.resolveDouble(
                resolvedMap,
                ConfigKeys.SCHEME_PMSYM_ASSURED_PENSION,
                3000.0
            ),
            pmsymIncomeCeiling = DataSourceResolver.resolveDouble(
                resolvedMap,
                ConfigKeys.SCHEME_PMSYM_INCOME_CEILING,
                15000.0
            ),
            seniorPensionSchemes = activeDoc?.seniorPensionSchemes ?: seedDoc?.seniorPensionSchemes.orEmpty(),
            assetAllocationMatrix = activeDoc?.assetAllocationMatrix ?: seedDoc?.assetAllocationMatrix.orEmpty(),
            dataVersion = activeDoc?.dataVersion ?: "2026.11.2",
            publishedOn = publishedOn,
            asOfDateFormatted = DataSourceResolver.formatIsoToIndiaDate(publishedOn),
            sourceName = sampleItem?.sourceName ?: "PFRDA / EPFO / Ministry of Finance India",
            lastVerified = sampleItem?.lastVerified ?: UNVERIFIED_MARKER,
            source = baseSource
        )
    }

    val bankFdSeedFlow: Flow<List<BankFdRateSeed>> = combine(
        remoteConfigDao.observeAllCaches(),
        prefsRepo.preferencesFlow.map { it.useOnlineData }
    ) { caches, useOnlineData ->
        val seedDoc = seedDocuments["rates.json"]
        val onlineDoc = bundleManager.extractOnlineDocuments(caches)["rates.json"]
        val importedDoc = bundleManager.extractImportedDocuments(caches)["rates.json"]
        val (winningDoc, _) = DataSourceResolver.selectWinningDocument(seedDoc, onlineDoc, importedDoc, useOnlineData)
        winningDoc?.bankFdRates?.takeIf { it.isNotEmpty() } ?: seedDoc?.bankFdRates.orEmpty()
    }

    val marketPreviousValuesFlow: Flow<Map<String, Double>> = combine(
        remoteConfigDao.observeAllCaches(),
        prefsRepo.preferencesFlow.map { it.useOnlineData }
    ) { caches, useOnlineData ->
        val seedDoc = seedDocuments["rates.json"]
        val onlineDoc = bundleManager.extractOnlineDocuments(caches)["rates.json"]
        val importedDoc = bundleManager.extractImportedDocuments(caches)["rates.json"]
        val (winningDoc, _) = DataSourceResolver.selectWinningDocument(seedDoc, onlineDoc, importedDoc, useOnlineData)
        val map = LinkedHashMap<String, Double>()
        seedDoc?.items.orEmpty().forEach { item -> item.previousValue?.let { map[item.key] = it } }
        winningDoc?.items.orEmpty().forEach { item -> item.previousValue?.let { map[item.key] = it } }
        map
    }

    val remoteArticlesFlow: Flow<Pair<List<RemoteArticleRecord>, ConfigValueSource>> = combine(
        remoteConfigDao.observeAllCaches(),
        prefsRepo.preferencesFlow.map { it.useOnlineData }
    ) { caches, useOnlineData ->
        val seedDoc = seedDocuments["articles.json"]
        val onlineDoc = if (useOnlineData) {
            bundleManager.extractOnlineDocuments(caches)["articles.json"]
        } else {
            null
        }
        if (onlineDoc != null && !onlineDoc.articles.isNullOrEmpty()) {
            onlineDoc.articles to ConfigValueSource.ONLINE
        } else {
            seedDoc?.articles.orEmpty() to ConfigValueSource.BUILT_IN
        }
    }

    val syncStatusFlow: Flow<SyncStatus> = combine(
        remoteConfigDao.observeSyncMetadata(),
        isSyncingFlow
    ) { meta, syncing ->
        SyncStatus(
            lastSyncAttemptMillis = meta?.lastSyncAttemptMillis ?: 0L,
            lastSyncSuccessMillis = meta?.lastSyncSuccessMillis ?: 0L,
            lastResultMessage = meta?.lastResultMessage ?: "Using built-in seed data",
            lastResultSuccess = meta?.lastResultSuccess ?: true,
            isSyncing = syncing
        )
    }

    suspend fun refreshNow(): RefreshSummary = withContext(Dispatchers.IO) {
        val useOnline = prefsRepo.preferencesFlow.first().useOnlineData
        val now = System.currentTimeMillis()
        if (!useOnline) {
            val msg = "Online data disabled; using built-in seed & manual values"
            recordSyncMetadata(now, success = true, message = msg, updateSuccessTimestamp = false)
            return@withContext RefreshSummary(0, 0, 0, msg, true)
        }

        isSyncingFlow.value = true
        var updated = 0
        var notModified = 0
        var failed = 0
        val errors = mutableListOf<String>()

        try {
            for (fileName in REMOTE_CONFIG_FILES) {
                val existingCache = remoteConfigDao.getCacheByFile(fileName)
                val response = runCatching {
                    api.fetchConfig(fileName, existingCache?.etag)
                }.getOrElse { err ->
                    failed++
                    errors.add("$fileName: ${err.javaClass.simpleName}")
                    continue
                }

                when (response.code()) {
                    304 -> {
                        notModified++
                        if (existingCache != null) {
                            remoteConfigDao.upsertCache(existingCache.copy(fetchedAtMillis = now))
                        }
                    }
                    in 200..299 -> {
                        val bodyText = response.body()?.string().orEmpty()
                        val validated = RemoteConfigValidator.parseAndValidate(bodyText, moshi)
                        if (validated.isSuccess) {
                            val doc = validated.getOrThrow()
                            val etag = response.headers()["ETag"] ?: existingCache?.etag
                            remoteConfigDao.upsertCache(
                                RemoteConfigCacheEntity(
                                    fileName = fileName,
                                    rawJson = bodyText,
                                    etag = etag,
                                    dataVersion = doc.dataVersion,
                                    publishedOn = doc.publishedOn,
                                    fetchedAtMillis = now
                                )
                            )
                            updated++
                        } else {
                            failed++
                            errors.add("$fileName: schema invalid")
                        }
                    }
                    else -> {
                        failed++
                        errors.add("$fileName: HTTP ${response.code()}")
                    }
                }
            }

            val allOk = failed == 0
            val message = when {
                updated > 0 && failed == 0 -> "Updated $updated file(s) ($notModified unchanged)"
                notModified > 0 && failed == 0 -> "Up to date ($notModified unchanged)"
                updated > 0 || notModified > 0 -> "Partial sync: $updated updated, $failed failed (using fallback)"
                else -> "Offline or unavailable — using cached/built-in data (${errors.firstOrNull() ?: "network error"})"
            }
            recordSyncMetadata(
                attemptMillis = now,
                success = allOk,
                message = message,
                updateSuccessTimestamp = updated > 0 || notModified > 0
            )
            RefreshSummary(updated, notModified, failed, message, allOk)
        } finally {
            isSyncingFlow.value = false
        }
    }

    suspend fun setManualOverride(item: ResolvedConfigItem, rawValue: String): Result<Unit> {
        val validated = RemoteConfigValidator.validateManualOverride(item, rawValue)
        if (validated.isFailure) {
            return Result.failure(validated.exceptionOrNull() ?: IllegalArgumentException("Invalid value"))
        }
        val cleanValue = validated.getOrThrow()
        val todayIso = currentIsoDate()
        remoteConfigDao.upsertManualOverride(
            ManualOverrideEntity(
                key = item.key,
                valueText = cleanValue,
                updatedAtMillis = System.currentTimeMillis(),
                updatedOnDate = todayIso
            )
        )
        return Result.success(Unit)
    }

    suspend fun setManualOverrideByKey(key: String, numericValue: Double): Result<Unit> {
        if (!numericValue.isFinite() || numericValue < 0.0) {
            return Result.failure(IllegalArgumentException("Value must be non-negative."))
        }
        val formatted = ConfigScalar(numericValue.toString(), numericValue).formattedNumber()
        remoteConfigDao.upsertManualOverride(
            ManualOverrideEntity(
                key = key,
                valueText = formatted,
                updatedAtMillis = System.currentTimeMillis(),
                updatedOnDate = currentIsoDate()
            )
        )
        return Result.success(Unit)
    }

    suspend fun resetToDefault(key: String) {
        remoteConfigDao.deleteManualOverride(key)
    }

    suspend fun resetAllOverrides() {
        remoteConfigDao.clearAllManualOverrides()
    }

    suspend fun setUseOnlineData(enabled: Boolean) {
        prefsRepo.setUseOnlineData(enabled)
    }

    suspend fun exportOverridesJson(): String {
        val overrides = remoteConfigDao.getAllManualOverridesOnce()
        val payload = ManualOverridesExport(
            schemaVersion = 1,
            exportedOn = currentIsoDate(),
            overrides = overrides.map {
                ManualOverrideRecord(
                    key = it.key,
                    value = it.valueText,
                    updatedOnDate = it.updatedOnDate.ifBlank { currentIsoDate() }
                )
            }
        )
        return overridesExportAdapter.toJson(payload)
    }

    /**
     * Validates a candidate update bundle (max 1 MB) without modifying any stored data,
     * returning a structured [UpdateBundlePreview] for user review.
     */
    suspend fun previewUpdateBundle(rawJson: String, fileName: String? = null): Result<UpdateBundlePreview> {
        val currentResolved = resolvedMapFlow.first()
        return bundleManager.buildPreview(
            rawJson = rawJson,
            fileName = fileName,
            seedDocuments = seedDocuments,
            currentResolved = currentResolved
        )
    }

    /**
     * Applies user-selected changes from a validated [UpdateBundlePreview] into the
     * `"Imported"` cache layer (retaining up to 5 undo snapshots).
     */
    suspend fun applyUpdateBundlePreview(
        preview: UpdateBundlePreview,
        selectedItemKeys: Set<String> = preview.itemChanges.map { it.key }.toSet(),
        selectedTableIds: Set<String> = preview.tableChanges.map { it.id }.toSet(),
        applyConfirmedUnchanged: Boolean = true
    ): Result<Int> {
        val now = System.currentTimeMillis()
        val result = bundleManager.applyPreview(
            preview = preview,
            selectedItemKeys = selectedItemKeys,
            selectedTableIds = selectedTableIds,
            applyConfirmedUnchanged = applyConfirmedUnchanged,
            seedDocuments = seedDocuments,
            nowMillis = now
        )
        if (result.isSuccess) {
            val partSuffix = preview.partInfo?.let { " (${it.label})" }.orEmpty()
            recordSyncMetadata(
                attemptMillis = now,
                success = true,
                message = "Imported update bundle (${preview.dataVersion}$partSuffix)",
                updateSuccessTimestamp = true
            )
        }
        return result
    }

    /**
     * Reverts the most recent imported update bundle (last 5 imports retained).
     */
    suspend fun undoLastImport(): Result<String> {
        val now = System.currentTimeMillis()
        val result = bundleManager.undoLastImport(now)
        if (result.isSuccess) {
            recordSyncMetadata(
                attemptMillis = now,
                success = true,
                message = "Undid import (${result.getOrThrow()})",
                updateSuccessTimestamp = false
            )
        }
        return result
    }

    suspend fun importOverridesJson(rawJson: String, fileName: String? = null): Result<Int> {
        if (rawJson.isBlank()) {
            return Result.failure(IllegalArgumentException("Import JSON is empty."))
        }
        if (rawJson.toByteArray(Charsets.UTF_8).size > RemoteConfigValidator.MAX_UPDATE_BUNDLE_BYTES) {
            return Result.failure(IllegalArgumentException("Import JSON exceeds the 1 MB file size limit."))
        }

        // 1. Update bundle payload -> strictly validate and merge into Imported cache
        if (isUpdateBundlePayload(rawJson)) {
            val preview = previewUpdateBundle(rawJson, fileName)
            if (preview.isFailure) return Result.failure(preview.exceptionOrNull()!!)
            return applyUpdateBundlePreview(preview.getOrThrow())
        }

        // 2. Otherwise parse as ManualOverridesExport
        val payload = runCatching { overridesExportAdapter.fromJson(rawJson) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Could not parse overrides JSON."))
        if (payload.schemaVersion < 1) {
            return Result.failure(IllegalArgumentException("Unsupported overrides schemaVersion."))
        }

        val currentResolved = resolvedMapFlow.first()
        var imported = 0
        for (record in payload.overrides) {
            val targetItem = currentResolved[record.key] ?: continue
            val validated = RemoteConfigValidator.validateManualOverride(targetItem, record.value)
            if (validated.isSuccess) {
                remoteConfigDao.upsertManualOverride(
                    ManualOverrideEntity(
                        key = record.key,
                        valueText = validated.getOrThrow(),
                        updatedAtMillis = System.currentTimeMillis(),
                        updatedOnDate = record.updatedOnDate.ifBlank { currentIsoDate() }
                    )
                )
                imported++
            }
        }
        return Result.success(imported)
    }

    fun isUpdateBundlePayload(rawJson: String): Boolean = rawJson.contains("\"bundleVersion\"") ||
        (rawJson.contains("\"rates\"") && rawJson.contains("\"confirmedUnchanged\""))

    private suspend fun recordSyncMetadata(attemptMillis: Long, success: Boolean, message: String, updateSuccessTimestamp: Boolean) {
        val prev = remoteConfigDao.getSyncMetadataOnce()
        remoteConfigDao.upsertSyncMetadata(
            SyncMetadataEntity(
                id = 1,
                lastSyncAttemptMillis = attemptMillis,
                lastSyncSuccessMillis = if (updateSuccessTimestamp) attemptMillis else (prev?.lastSyncSuccessMillis ?: 0L),
                lastResultMessage = message,
                lastResultSuccess = success
            )
        )
    }

    private fun currentIsoDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        fun createDefaultApi(baseUrl: String = REMOTE_CONFIG_BASE_URL): RemoteConfigApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .build()
                .create(RemoteConfigApi::class.java)
        }
    }
}
