package com.jyotirmoyb.loan.data.remote

import com.jyotirmoyb.loan.data.ManualOverrideEntity
import com.jyotirmoyb.loan.domain.LoanCatalog
import com.jyotirmoyb.loan.domain.LoanCategory
import com.jyotirmoyb.loan.domain.LoanVariant
import com.jyotirmoyb.loan.domain.MoneyMath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure resolution engine implementing strict priority:
 * (1) User's manual override ->
 * (2) Imported or Online cached remote config with newest dataVersion (Online only when enabled) ->
 * (3) Bundled seed JSON.
 */
object DataSourceResolver {

    private const val MILLIS_PER_DAY = 86_400_000L

    fun resolveAll(
        seedDocuments: Map<String, RemoteConfigDocument>,
        onlineDocuments: Map<String, RemoteConfigDocument>,
        manualOverrides: Map<String, ManualOverrideEntity>,
        useOnlineData: Boolean,
        importedDocuments: Map<String, RemoteConfigDocument> = emptyMap()
    ): List<ResolvedConfigItem> {
        val result = LinkedHashMap<String, ResolvedConfigItem>()

        for (fileName in REMOTE_CONFIG_FILES) {
            val seedDoc = seedDocuments[fileName]
            val onlineDoc = if (useOnlineData) onlineDocuments[fileName] else null
            val importedDoc = importedDocuments[fileName]

            val seedByKey = seedDoc?.items?.associateBy { it.key }.orEmpty()
            val onlineByKey = onlineDoc?.items?.associateBy { it.key }.orEmpty()
            val importedByKey = importedDoc?.items?.associateBy { it.key }.orEmpty()

            val orderedKeys = LinkedHashSet<String>()
            seedDoc?.items?.forEach { orderedKeys.add(it.key) }
            onlineDoc?.items?.forEach { orderedKeys.add(it.key) }
            importedDoc?.items?.forEach { orderedKeys.add(it.key) }

            for (key in orderedKeys) {
                val seedItem = seedByKey[key]
                val onlineItem = onlineByKey[key]
                val importedItem = importedByKey[key]

                val importedChanged = importedItem != null &&
                    (seedItem == null || hasItemDiff(importedItem, seedItem))

                val (baseItem, baseSource, basePublishedOn) = selectBaseItem(
                    seedItem = seedItem,
                    seedDoc = seedDoc,
                    onlineItem = onlineItem,
                    onlineDoc = onlineDoc,
                    importedItem = if (importedChanged) importedItem else null,
                    importedDoc = if (importedChanged) importedDoc else null
                ) ?: continue

                val manual = manualOverrides[key]
                val resolvedValueText = manual?.valueText ?: baseItem.value.formattedNumber()
                val resolvedNumeric = if (manual != null) {
                    manual.valueText.toDoubleOrNull()
                } else {
                    baseItem.value.numericValue
                }

                val source = if (manual != null) ConfigValueSource.MANUAL else baseSource
                val asOfIso = if (manual != null && manual.updatedOnDate.isNotBlank()) {
                    manual.updatedOnDate
                } else {
                    basePublishedOn
                }
                val lastVerified = if (manual != null) {
                    manual.updatedOnDate.ifBlank { baseItem.lastVerified }
                } else {
                    baseItem.lastVerified
                }

                val label = baseItem.label?.takeIf { it.isNotBlank() } ?: humanizeKey(key)
                val category = ConfigCategory.fromWireName(baseItem.category, fileName)

                result[key] = ResolvedConfigItem(
                    key = key,
                    label = label,
                    category = category,
                    fileName = fileName,
                    valueText = resolvedValueText,
                    numericValue = resolvedNumeric,
                    unit = baseItem.unit,
                    effectiveFrom = baseItem.effectiveFrom,
                    asOfDate = formatIsoToIndiaDate(asOfIso),
                    sourceName = if (manual != null) "Manual override" else baseItem.sourceName,
                    sourceUrl = baseItem.sourceUrl,
                    lastVerified = lastVerified,
                    source = source,
                    defaultValueText = baseItem.value.formattedNumber(),
                    defaultNumericValue = baseItem.value.numericValue
                )
            }
        }
        return result.values.toList()
    }

    fun selectWinningDocument(
        seedDoc: RemoteConfigDocument?,
        onlineDoc: RemoteConfigDocument?,
        importedDoc: RemoteConfigDocument?,
        useOnlineData: Boolean
    ): Pair<RemoteConfigDocument?, ConfigValueSource> {
        val activeOnline = if (useOnlineData) onlineDoc else null
        return when {
            importedDoc != null && activeOnline != null -> {
                if (isImportedPreferredOverOnline(importedDoc, activeOnline)) {
                    importedDoc to ConfigValueSource.IMPORTED
                } else {
                    activeOnline to ConfigValueSource.ONLINE
                }
            }
            importedDoc != null -> importedDoc to ConfigValueSource.IMPORTED
            activeOnline != null -> activeOnline to ConfigValueSource.ONLINE
            else -> seedDoc to ConfigValueSource.BUILT_IN
        }
    }

    /**
     * Compares two dot/dash-separated dataVersion strings numerically per segment
     * (e.g. "2026.11.27" > "2026.11.2").
     */
    fun compareDataVersions(a: String, b: String): Int {
        val partsA = a.trim().split(Regex("[.\\-_]")).filter { it.isNotEmpty() }
        val partsB = b.trim().split(Regex("[.\\-_]")).filter { it.isNotEmpty() }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val segA = partsA.getOrNull(i) ?: "0"
            val segB = partsB.getOrNull(i) ?: "0"
            val numA = segA.toLongOrNull()
            val numB = segB.toLongOrNull()
            val cmp = if (numA != null && numB != null) {
                numA.compareTo(numB)
            } else {
                segA.compareTo(segB, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }

    private fun isImportedPreferredOverOnline(importedDoc: RemoteConfigDocument, onlineDoc: RemoteConfigDocument): Boolean {
        val versionCmp = compareDataVersions(importedDoc.dataVersion, onlineDoc.dataVersion)
        if (versionCmp != 0) return versionCmp > 0
        val dateCmp = importedDoc.publishedOn.compareTo(onlineDoc.publishedOn)
        return dateCmp >= 0
    }

    private fun hasItemDiff(candidate: RemoteConfigItem, seed: RemoteConfigItem): Boolean =
        candidate.value.formattedNumber() != seed.value.formattedNumber() ||
            candidate.lastVerified != seed.lastVerified ||
            candidate.effectiveFrom != seed.effectiveFrom ||
            candidate.sourceName != seed.sourceName ||
            candidate.sourceUrl != seed.sourceUrl

    private fun selectBaseItem(
        seedItem: RemoteConfigItem?,
        seedDoc: RemoteConfigDocument?,
        onlineItem: RemoteConfigItem?,
        onlineDoc: RemoteConfigDocument?,
        importedItem: RemoteConfigItem?,
        importedDoc: RemoteConfigDocument?
    ): Triple<RemoteConfigItem, ConfigValueSource, String>? {
        val importedWins = when {
            importedItem != null && importedDoc != null && onlineItem != null && onlineDoc != null ->
                isImportedPreferredOverOnline(importedDoc, onlineDoc)
            importedItem != null -> true
            else -> false
        }

        if (importedWins && importedItem != null) {
            val pub = importedItem.lastVerified.takeIf { RemoteConfigValidator.isValidIsoCalendarDate(it) }
                ?: importedDoc?.publishedOn
                ?: importedItem.effectiveFrom
            return Triple(importedItem, ConfigValueSource.IMPORTED, pub)
        }
        if (onlineItem != null) {
            val pub = onlineDoc?.publishedOn ?: onlineItem.effectiveFrom
            return Triple(onlineItem, ConfigValueSource.ONLINE, pub)
        }
        if (seedItem != null) {
            val pub = seedDoc?.publishedOn ?: seedItem.effectiveFrom
            return Triple(seedItem, ConfigValueSource.BUILT_IN, pub)
        }
        return null
    }

    fun resolveDouble(resolvedMap: Map<String, ResolvedConfigItem>, key: String, fallback: Double): Double =
        resolvedMap[key]?.numericValue ?: fallback

    fun resolveInt(resolvedMap: Map<String, ResolvedConfigItem>, key: String, fallback: Int): Int =
        resolvedMap[key]?.numericValue?.toInt() ?: fallback

    fun resolveText(resolvedMap: Map<String, ResolvedConfigItem>, key: String, fallback: String): String =
        resolvedMap[key]?.valueText?.takeIf { it.isNotBlank() } ?: fallback

    fun resolveCatalog(catalog: LoanCatalog, resolvedMap: Map<String, ResolvedConfigItem>): LoanCatalog {
        if (resolvedMap.isEmpty()) return catalog
        val resolvedCategories = catalog.categories.map { category ->
            resolveCategory(category, resolvedMap)
        }
        return catalog.copy(categories = resolvedCategories)
    }

    private fun resolveCategory(category: LoanCategory, resolvedMap: Map<String, ResolvedConfigItem>): LoanCategory {
        val minRate = category.rateMinKey?.let { resolvedMap[it]?.numericValue } ?: category.rateRangeMin
        val maxRate = category.rateMaxKey?.let { resolvedMap[it]?.numericValue } ?: category.rateRangeMax
        val ltvMax = category.ltvCapKey?.let { resolvedMap[it]?.numericValue } ?: category.ltvMaxPercent

        val resolvedGroups = category.variantGroups.map { group ->
            group.copy(
                variants = group.variants.map { variant ->
                    resolveVariant(variant, resolvedMap)
                }
            )
        }

        return category.copy(
            rateRangeMin = minRate,
            rateRangeMax = maxOf(minRate, maxRate),
            ltvMaxPercent = ltvMax,
            variantGroups = resolvedGroups
        )
    }

    private fun resolveVariant(variant: LoanVariant, resolvedMap: Map<String, ResolvedConfigItem>): LoanVariant {
        val minRate = variant.rateMinKey?.let { resolvedMap[it]?.numericValue } ?: variant.rateRangeMin
        val maxRate = variant.rateMaxKey?.let { resolvedMap[it]?.numericValue } ?: variant.rateRangeMax
        val ltvCap = variant.ltvCapKey?.let { resolvedMap[it]?.numericValue } ?: variant.ltvCapOverride

        return variant.copy(
            rateRangeMin = minRate,
            rateRangeMax = maxOf(minRate, maxRate),
            ltvCapOverride = ltvCap
        )
    }

    fun formatIsoToIndiaDate(iso: String): String {
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val date = parser.parse(iso) ?: return iso
            val cal = java.util.Calendar.getInstance().apply { time = date }
            MoneyMath.formatDateDdMmmYyyy(
                day = cal.get(java.util.Calendar.DAY_OF_MONTH),
                monthOneBased = cal.get(java.util.Calendar.MONTH) + 1,
                year = cal.get(java.util.Calendar.YEAR)
            )
        }.getOrDefault(iso)
    }

    /**
     * Formats ISO YYYY-MM-DD or dd MMM yyyy into compact "27 Nov 26" style.
     */
    fun formatToCompactDate(dateText: String): String {
        val trimmed = dateText.trim()
        if (trimmed.isEmpty()) return ""
        val patterns = listOf("yyyy-MM-dd", "dd MMM yyyy", "d MMM yyyy", "dd MMM yy")
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(trimmed)
            }.getOrNull()
            if (parsed != null) {
                return SimpleDateFormat("d MMM yy", Locale.US).format(parsed)
            }
        }
        return trimmed
    }

    fun formatMillisToCompactDate(millis: Long): String {
        if (millis <= 0L) return ""
        return SimpleDateFormat("d MMM yy", Locale.US).format(Date(millis))
    }

    /**
     * Returns the newest ISO YYYY-MM-DD `lastVerified` date across all tax and loan items,
     * or null if no tax/loan item has a verified ISO date yet.
     */
    fun computeNewestTaxAndLoanVerifiedIso(items: List<ResolvedConfigItem>): String? = items
        .asSequence()
        .filter { item ->
            item.category == ConfigCategory.TAX_SLABS ||
                item.category == ConfigCategory.LOAN_RATES ||
                item.key.startsWith("tax.") ||
                item.key.startsWith("loan.")
        }
        .map { it.lastVerified.trim() }
        .filter { RemoteConfigValidator.isValidIsoCalendarDate(it) }
        .maxOrNull()

    /**
     * Returns true if the newest verified date across tax & loan data is missing or
     * strictly older than [thresholdDays] (default 90 days).
     */
    fun isTaxOrLoanDataStale(
        items: List<ResolvedConfigItem>,
        thresholdDays: Int = 90,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (items.isEmpty()) return false
        val newestIso = computeNewestTaxAndLoanVerifiedIso(items) ?: return true
        val verifiedMillis = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(newestIso)?.time
        }.getOrNull() ?: return true
        val safeDays = thresholdDays.coerceAtLeast(1)
        return (nowMillis - verifiedMillis) > safeDays * MILLIS_PER_DAY
    }

    private fun humanizeKey(key: String): String = key.split('.', '_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }
}
