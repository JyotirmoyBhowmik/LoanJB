package com.jyotirmoyb.loan.data.remote

import com.jyotirmoyb.loan.data.RemoteConfigCacheEntity
import com.jyotirmoyb.loan.data.RemoteConfigDao
import com.squareup.moshi.Moshi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles LoanJB Update Bundle preview generation, selective merge into imported cache,
 * multi-part incremental imports (PART 1/N ...), and last-5 undo history.
 */
class UpdateBundleManager(private val remoteConfigDao: RemoteConfigDao, private val moshi: Moshi = RemoteConfigMoshi.instance) {
    private val documentAdapter = moshi.adapter(RemoteConfigDocument::class.java).indent("  ")
    private val historyAdapter = moshi.adapter(ImportHistoryStore::class.java)

    fun extractImportedDocuments(caches: List<RemoteConfigCacheEntity>): Map<String, RemoteConfigDocument> {
        val map = LinkedHashMap<String, RemoteConfigDocument>()
        for (cache in caches) {
            if (!cache.fileName.startsWith(IMPORTED_PREFIX)) continue
            if (cache.fileName == IMPORT_HISTORY_CACHE_KEY) continue
            val targetFile = cache.fileName.removePrefix(IMPORTED_PREFIX)
            RemoteConfigValidator.parseAndValidate(cache.rawJson, moshi).getOrNull()?.let { doc ->
                map[targetFile] = doc
            }
        }
        return map
    }

    fun extractOnlineDocuments(caches: List<RemoteConfigCacheEntity>): Map<String, RemoteConfigDocument> {
        val map = LinkedHashMap<String, RemoteConfigDocument>()
        for (cache in caches) {
            if (cache.fileName.startsWith(IMPORTED_PREFIX)) continue
            RemoteConfigValidator.parseAndValidate(cache.rawJson, moshi).getOrNull()?.let { doc ->
                map[cache.fileName] = doc
            }
        }
        return map
    }

    fun readHistoryCount(caches: List<RemoteConfigCacheEntity>): Int {
        val raw = caches.firstOrNull { it.fileName == IMPORT_HISTORY_CACHE_KEY }?.rawJson ?: return 0
        return runCatching { historyAdapter.fromJson(raw)?.entries?.size ?: 0 }.getOrDefault(0)
    }

    fun readLatestHistorySummary(caches: List<RemoteConfigCacheEntity>): String? {
        val raw = caches.firstOrNull { it.fileName == IMPORT_HISTORY_CACHE_KEY }?.rawJson ?: return null
        return runCatching { historyAdapter.fromJson(raw)?.entries?.lastOrNull()?.summaryLabel }.getOrNull()
    }

    fun detectPartInfo(fileName: String?, bundle: LoanJbUpdateBundle): BundlePartInfo? {
        val candidates = buildList {
            if (!fileName.isNullOrBlank()) add(fileName)
            if (bundle.dataVersion.isNotBlank()) add(bundle.dataVersion)
            addAll(bundle.notes)
        }
        for (text in candidates) {
            val match = PART_REGEX.find(text) ?: continue
            val part = match.groupValues[1].toIntOrNull() ?: continue
            val total = match.groupValues[2].toIntOrNull() ?: continue
            if (part in 1..total && total >= 2) {
                return BundlePartInfo(partNumber = part, totalParts = total)
            }
        }
        return null
    }

    fun buildPreview(
        rawJson: String,
        fileName: String?,
        seedDocuments: Map<String, RemoteConfigDocument>,
        currentResolved: Map<String, ResolvedConfigItem>
    ): Result<UpdateBundlePreview> {
        val knownSeedItems = LinkedHashMap<String, RemoteConfigItem>()
        for (doc in seedDocuments.values) {
            for (item in doc.items) {
                knownSeedItems[item.key] = item
            }
        }

        val validated = RemoteConfigValidator.parseAndValidateUpdateBundle(rawJson, knownSeedItems, moshi)
        if (validated.isFailure) {
            return Result.failure(validated.exceptionOrNull()!!)
        }
        val bundle = validated.getOrThrow()
        val generatedOn = bundle.generatedOn.ifBlank { currentIsoDate() }
        val dataVersion = bundle.dataVersion.ifBlank { "$generatedOn.1" }
        val partInfo = detectPartInfo(fileName, bundle)

        val itemChanges = mutableListOf<BundleItemChange>()
        val tableChanges = mutableListOf<BundleTableChange>()

        fun collectItems(targetFile: String, section: UpdateBundleSection?) {
            for (incoming in section?.items.orEmpty()) {
                val existing = currentResolved[incoming.key]
                val seedItem = knownSeedItems[incoming.key]
                val oldVal = existing?.valueText ?: seedItem?.value?.formattedNumber() ?: "—"
                val newVal = incoming.value.formattedNumber()
                val label = existing?.label
                    ?: incoming.label?.takeIf { it.isNotBlank() }
                    ?: seedItem?.label?.takeIf { it.isNotBlank() }
                    ?: incoming.key
                itemChanges.add(
                    BundleItemChange(
                        key = incoming.key,
                        label = label,
                        fileName = targetFile,
                        oldValueDisplay = formatWithUnit(oldVal, incoming.unit),
                        newValueDisplay = formatWithUnit(newVal, incoming.unit),
                        unit = incoming.unit,
                        effectiveFrom = incoming.effectiveFrom,
                        sourceName = incoming.sourceName,
                        sourceUrl = incoming.sourceUrl,
                        lastVerified = incoming.lastVerified,
                        incomingItem = incoming
                    )
                )
            }
        }

        collectItems("rates.json", bundle.rates)
        collectItems("taxslabs.json", bundle.taxslabs)
        collectItems("schemes.json", bundle.schemes)

        val defaultSource = bundle.generatedBy.ifBlank { "Update bundle" }
        bundle.rates?.bankFdRates?.takeIf { it.isNotEmpty() }?.let { banks ->
            tableChanges.add(
                BundleTableChange(
                    id = TABLE_BANK_FD_RATES,
                    fileName = "rates.json",
                    label = "Bank FD Rates Table",
                    summary = "${banks.size} bank FD slab rows",
                    effectiveFrom = generatedOn,
                    sourceName = defaultSource
                )
            )
        }
        bundle.taxslabs?.taxYears?.takeIf { it.isNotEmpty() }?.let { years ->
            tableChanges.add(
                BundleTableChange(
                    id = TABLE_TAX_YEARS,
                    fileName = "taxslabs.json",
                    label = "Income Tax Years & Slabs",
                    summary = years.joinToString(", ") { it.fyCode },
                    effectiveFrom = years.first().effectiveFrom,
                    sourceName = defaultSource
                )
            )
        }
        bundle.taxslabs?.gstSlabs?.takeIf { it.isNotEmpty() }?.let { slabs ->
            tableChanges.add(
                BundleTableChange(
                    id = TABLE_GST_SLABS,
                    fileName = "taxslabs.json",
                    label = "GST Rate Slabs",
                    summary = slabs.joinToString(", ") { "${ConfigScalar(it.toString(), it).formattedNumber()}%" },
                    effectiveFrom = generatedOn,
                    sourceName = defaultSource
                )
            )
        }
        bundle.taxslabs?.advanceTaxThreshold?.let { threshold ->
            tableChanges.add(
                BundleTableChange(
                    id = TABLE_ADVANCE_TAX_THRESHOLD,
                    fileName = "taxslabs.json",
                    label = "Advance Tax Threshold",
                    summary = "₹${ConfigScalar(threshold.toString(), threshold).formattedNumber()}",
                    effectiveFrom = generatedOn,
                    sourceName = defaultSource
                )
            )
        }
        bundle.schemes?.seniorPensionSchemes?.takeIf { it.isNotEmpty() }?.let { schemes ->
            tableChanges.add(
                BundleTableChange(
                    id = TABLE_SENIOR_SCHEMES,
                    fileName = "schemes.json",
                    label = "Senior Citizen Pension Schemes",
                    summary = schemes.joinToString(", ") { it.shortName },
                    effectiveFrom = generatedOn,
                    sourceName = defaultSource
                )
            )
        }
        bundle.schemes?.apyTable?.takeIf { it.isNotEmpty() }?.let { rows ->
            tableChanges.add(
                BundleTableChange(
                    id = TABLE_APY_TABLE,
                    fileName = "schemes.json",
                    label = "APY Contribution Table",
                    summary = "${rows.size} age rows",
                    effectiveFrom = generatedOn,
                    sourceName = defaultSource
                )
            )
        }
        bundle.schemes?.pmsymTable?.takeIf { it.isNotEmpty() }?.let { rows ->
            tableChanges.add(
                BundleTableChange(
                    id = TABLE_PMSYM_TABLE,
                    fileName = "schemes.json",
                    label = "PM-SYM Contribution Table",
                    summary = "${rows.size} age rows",
                    effectiveFrom = generatedOn,
                    sourceName = defaultSource
                )
            )
        }

        return Result.success(
            UpdateBundlePreview(
                bundle = bundle,
                dataVersion = dataVersion,
                generatedOn = generatedOn,
                generatedBy = bundle.generatedBy,
                partInfo = partInfo,
                itemChanges = itemChanges,
                tableChanges = tableChanges,
                confirmedUnchangedCount = bundle.confirmedUnchanged.size,
                unresolved = bundle.unresolved,
                suggestedNewKeys = bundle.suggestedNewKeys,
                notes = bundle.notes
            )
        )
    }

    suspend fun applyPreview(
        preview: UpdateBundlePreview,
        selectedItemKeys: Set<String>,
        selectedTableIds: Set<String>,
        applyConfirmedUnchanged: Boolean,
        seedDocuments: Map<String, RemoteConfigDocument>,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<Int> {
        val bundle = preview.bundle
        val verifiedDate = preview.generatedOn.ifBlank { currentIsoDate() }
        val targetVersion = preview.dataVersion.ifBlank { "$verifiedDate.1" }
        val confirmedSet = if (applyConfirmedUnchanged) bundle.confirmedUnchanged.toSet() else emptySet()

        // 1. Capture pre-import snapshot of imported caches for Undo (keep last 5)
        val previousSnapshot = LinkedHashMap<String, String?>()
        for (fileName in BUNDLE_SECTION_FILES) {
            previousSnapshot[fileName] = remoteConfigDao.getCacheByFile(importedKey(fileName))?.rawJson
        }

        var appliedCount = 0

        suspend fun mergeSection(fileName: String, section: UpdateBundleSection?) {
            val existingImported = remoteConfigDao.getCacheByFile(importedKey(fileName))
            val baseDoc = existingImported?.let {
                RemoteConfigValidator.parseAndValidate(it.rawJson, moshi).getOrNull()
            } ?: seedDocuments[fileName] ?: return

            val incomingByKey = section?.items.orEmpty()
                .filter { selectedItemKeys.contains(it.key) }
                .associateBy { it.key }

            val hasConfirmedInFile = baseDoc.items.any { confirmedSet.contains(it.key) }
            val hasBankFds = fileName == "rates.json" &&
                selectedTableIds.contains(TABLE_BANK_FD_RATES) &&
                !section?.bankFdRates.isNullOrEmpty()
            val hasTaxYears = fileName == "taxslabs.json" &&
                selectedTableIds.contains(TABLE_TAX_YEARS) &&
                !section?.taxYears.isNullOrEmpty()
            val hasGstSlabs = fileName == "taxslabs.json" &&
                selectedTableIds.contains(TABLE_GST_SLABS) &&
                !section?.gstSlabs.isNullOrEmpty()
            val hasAdvanceTax = fileName == "taxslabs.json" &&
                selectedTableIds.contains(TABLE_ADVANCE_TAX_THRESHOLD) &&
                section?.advanceTaxThreshold != null
            val hasSenior = fileName == "schemes.json" &&
                selectedTableIds.contains(TABLE_SENIOR_SCHEMES) &&
                !section?.seniorPensionSchemes.isNullOrEmpty()
            val hasApy = fileName == "schemes.json" &&
                selectedTableIds.contains(TABLE_APY_TABLE) &&
                !section?.apyTable.isNullOrEmpty()
            val hasPmsym = fileName == "schemes.json" &&
                selectedTableIds.contains(TABLE_PMSYM_TABLE) &&
                !section?.pmsymTable.isNullOrEmpty()

            val fileTouched = incomingByKey.isNotEmpty() ||
                hasConfirmedInFile ||
                hasBankFds ||
                hasTaxYears ||
                hasGstSlabs ||
                hasAdvanceTax ||
                hasSenior ||
                hasApy ||
                hasPmsym

            if (!fileTouched) return

            val mergedItems = baseDoc.items.map { existing ->
                val incoming = incomingByKey[existing.key]
                when {
                    incoming != null -> {
                        appliedCount++
                        existing.copy(
                            value = incoming.value,
                            unit = incoming.unit,
                            effectiveFrom = incoming.effectiveFrom,
                            sourceName = incoming.sourceName,
                            sourceUrl = incoming.sourceUrl,
                            lastVerified = incoming.lastVerified,
                            previousValue = incoming.previousValue ?: existing.value.numericValue ?: existing.previousValue
                        )
                    }
                    confirmedSet.contains(existing.key) -> {
                        existing.copy(lastVerified = verifiedDate)
                    }
                    else -> existing
                }
            }

            val updatedBankFds = if (hasBankFds) {
                appliedCount += section!!.bankFdRates!!.size
                section.bankFdRates
            } else {
                baseDoc.bankFdRates
            }
            val updatedTaxYears = if (hasTaxYears) {
                appliedCount += section!!.taxYears!!.size
                section.taxYears
            } else {
                baseDoc.taxYears
            }
            val updatedGstSlabs = if (hasGstSlabs) {
                appliedCount += 1
                section!!.gstSlabs
            } else {
                baseDoc.gstSlabs
            }
            val updatedAdvanceTax = if (hasAdvanceTax) {
                appliedCount += 1
                section!!.advanceTaxThreshold
            } else {
                baseDoc.advanceTaxThreshold
            }
            val updatedSenior = if (hasSenior) {
                appliedCount += section!!.seniorPensionSchemes!!.size
                section.seniorPensionSchemes
            } else {
                baseDoc.seniorPensionSchemes
            }
            val updatedApy = if (hasApy) {
                appliedCount += 1
                section!!.apyTable
            } else {
                baseDoc.apyTable
            }
            val updatedPmsym = if (hasPmsym) {
                appliedCount += 1
                section!!.pmsymTable
            } else {
                baseDoc.pmsymTable
            }

            // Preserve the higher dataVersion when applying multi-part updates
            val mergedVersion = if (
                existingImported != null &&
                DataSourceResolver.compareDataVersions(baseDoc.dataVersion, targetVersion) > 0
            ) {
                baseDoc.dataVersion
            } else {
                targetVersion
            }

            val mergedDoc = baseDoc.copy(
                dataVersion = mergedVersion,
                publishedOn = verifiedDate,
                items = mergedItems,
                bankFdRates = updatedBankFds,
                taxYears = updatedTaxYears,
                gstSlabs = updatedGstSlabs,
                advanceTaxThreshold = updatedAdvanceTax,
                seniorPensionSchemes = updatedSenior,
                apyTable = updatedApy,
                pmsymTable = updatedPmsym
            )

            val serialized = documentAdapter.toJson(mergedDoc)
            val revalidated = RemoteConfigValidator.parseAndValidate(serialized, moshi)
            if (revalidated.isFailure) {
                throw revalidated.exceptionOrNull()!!
            }

            remoteConfigDao.upsertCache(
                RemoteConfigCacheEntity(
                    fileName = importedKey(fileName),
                    rawJson = serialized,
                    etag = null,
                    dataVersion = mergedDoc.dataVersion,
                    publishedOn = mergedDoc.publishedOn,
                    fetchedAtMillis = nowMillis
                )
            )
        }

        val mergeResult = runCatching {
            mergeSection("rates.json", bundle.rates)
            mergeSection("taxslabs.json", bundle.taxslabs)
            mergeSection("schemes.json", bundle.schemes)
        }
        if (mergeResult.isFailure) {
            return Result.failure(mergeResult.exceptionOrNull()!!)
        }

        val partSuffix = preview.partInfo?.let { " (${it.label})" }.orEmpty()
        pushHistoryEntry(
            ImportHistoryEntry(
                importedAtMillis = nowMillis,
                dataVersion = targetVersion,
                summaryLabel = "$targetVersion$partSuffix",
                previousImportedCaches = previousSnapshot
            ),
            nowMillis
        )

        return Result.success(appliedCount)
    }

    suspend fun undoLastImport(nowMillis: Long = System.currentTimeMillis()): Result<String> {
        val currentHistory = loadHistoryStore()
        if (currentHistory.entries.isEmpty()) {
            return Result.failure(IllegalStateException("No previous import to undo."))
        }

        val popped = currentHistory.entries.last()
        val remaining = currentHistory.entries.dropLast(1)

        for (fileName in BUNDLE_SECTION_FILES) {
            val prevJson = popped.previousImportedCaches[fileName]
            if (prevJson == null) {
                remoteConfigDao.deleteCacheByFile(importedKey(fileName))
            } else {
                val parsed = RemoteConfigValidator.parseAndValidate(prevJson, moshi).getOrNull()
                remoteConfigDao.upsertCache(
                    RemoteConfigCacheEntity(
                        fileName = importedKey(fileName),
                        rawJson = prevJson,
                        etag = null,
                        dataVersion = parsed?.dataVersion.orEmpty(),
                        publishedOn = parsed?.publishedOn.orEmpty(),
                        fetchedAtMillis = nowMillis
                    )
                )
            }
        }

        saveHistoryStore(ImportHistoryStore(entries = remaining), nowMillis)
        return Result.success(popped.summaryLabel)
    }

    private suspend fun loadHistoryStore(): ImportHistoryStore {
        val raw = remoteConfigDao.getCacheByFile(IMPORT_HISTORY_CACHE_KEY)?.rawJson ?: return ImportHistoryStore()
        return runCatching { historyAdapter.fromJson(raw) }.getOrNull() ?: ImportHistoryStore()
    }

    private suspend fun pushHistoryEntry(entry: ImportHistoryEntry, nowMillis: Long) {
        val current = loadHistoryStore()
        val updated = (current.entries + entry).takeLast(MAX_IMPORT_HISTORY)
        saveHistoryStore(ImportHistoryStore(entries = updated), nowMillis)
    }

    private suspend fun saveHistoryStore(store: ImportHistoryStore, nowMillis: Long) {
        val json = historyAdapter.toJson(store)
        remoteConfigDao.upsertCache(
            RemoteConfigCacheEntity(
                fileName = IMPORT_HISTORY_CACHE_KEY,
                rawJson = json,
                etag = null,
                dataVersion = "history",
                publishedOn = currentIsoDate(),
                fetchedAtMillis = nowMillis
            )
        )
    }

    private fun formatWithUnit(valueText: String, unit: String): String {
        val trimmedUnit = unit.trim()
        return if (trimmedUnit.equals("text", ignoreCase = true) || trimmedUnit.isEmpty()) {
            valueText
        } else {
            "$valueText $trimmedUnit"
        }
    }

    private fun currentIsoDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        const val IMPORTED_PREFIX = "imported:"
        const val IMPORT_HISTORY_CACHE_KEY = "imported:history_v1"
        const val MAX_IMPORT_HISTORY = 5

        const val TABLE_BANK_FD_RATES = "table_bank_fd_rates"
        const val TABLE_TAX_YEARS = "table_tax_years"
        const val TABLE_GST_SLABS = "table_gst_slabs"
        const val TABLE_ADVANCE_TAX_THRESHOLD = "table_advance_tax_threshold"
        const val TABLE_SENIOR_SCHEMES = "table_senior_schemes"
        const val TABLE_APY_TABLE = "table_apy_table"
        const val TABLE_PMSYM_TABLE = "table_pmsym_table"

        private val BUNDLE_SECTION_FILES = listOf("rates.json", "taxslabs.json", "schemes.json")
        private val PART_REGEX = Regex("(?i)\\bPART\\s*(\\d+)\\s*(?:/|of)\\s*(\\d+)\\b")

        fun importedKey(fileName: String): String = "$IMPORTED_PREFIX$fileName"
    }
}
