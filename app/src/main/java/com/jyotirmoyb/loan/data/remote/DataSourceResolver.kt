package com.jyotirmoyb.loan.data.remote

import com.jyotirmoyb.loan.data.ManualOverrideEntity
import com.jyotirmoyb.loan.domain.LoanCatalog
import com.jyotirmoyb.loan.domain.LoanCategory
import com.jyotirmoyb.loan.domain.LoanVariant
import com.jyotirmoyb.loan.domain.MoneyMath
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pure resolution engine implementing strict priority:
 * (1) User's manual override -> (2) Online cached remote config (if enabled) -> (3) Bundled seed JSON.
 */
object DataSourceResolver {

    fun resolveAll(
        seedDocuments: Map<String, RemoteConfigDocument>,
        onlineDocuments: Map<String, RemoteConfigDocument>,
        manualOverrides: Map<String, ManualOverrideEntity>,
        useOnlineData: Boolean
    ): List<ResolvedConfigItem> {
        val result = LinkedHashMap<String, ResolvedConfigItem>()

        for (fileName in REMOTE_CONFIG_FILES) {
            val seedDoc = seedDocuments[fileName]
            val onlineDoc = if (useOnlineData) onlineDocuments[fileName] else null

            val seedByKey = seedDoc?.items?.associateBy { it.key }.orEmpty()
            val onlineByKey = onlineDoc?.items?.associateBy { it.key }.orEmpty()

            val orderedKeys = LinkedHashSet<String>()
            seedDoc?.items?.forEach { orderedKeys.add(it.key) }
            onlineDoc?.items?.forEach { orderedKeys.add(it.key) }

            for (key in orderedKeys) {
                val seedItem = seedByKey[key]
                val onlineItem = onlineByKey[key]
                val baseItem = onlineItem ?: seedItem ?: continue
                val baseSource = if (onlineItem != null) ConfigValueSource.ONLINE else ConfigValueSource.BUILT_IN
                val basePublishedOn = if (onlineItem != null) {
                    onlineDoc?.publishedOn ?: onlineItem.effectiveFrom
                } else {
                    seedDoc?.publishedOn ?: seedItem?.effectiveFrom ?: baseItem.effectiveFrom
                }

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
                    lastVerified = if (manual != null) "Manual" else baseItem.lastVerified,
                    source = source,
                    defaultValueText = baseItem.value.formattedNumber(),
                    defaultNumericValue = baseItem.value.numericValue
                )
            }
        }
        return result.values.toList()
    }

    fun resolveDouble(
        resolvedMap: Map<String, ResolvedConfigItem>,
        key: String,
        fallback: Double
    ): Double = resolvedMap[key]?.numericValue ?: fallback

    fun resolveInt(
        resolvedMap: Map<String, ResolvedConfigItem>,
        key: String,
        fallback: Int
    ): Int = resolvedMap[key]?.numericValue?.toInt() ?: fallback

    fun resolveText(
        resolvedMap: Map<String, ResolvedConfigItem>,
        key: String,
        fallback: String
    ): String = resolvedMap[key]?.valueText?.takeIf { it.isNotBlank() } ?: fallback

    /**
     * Hydrates every LoanCategory and LoanVariant in the catalog with rate ranges and LTV caps
     * resolved from `rates.json` (manual override > online > bundled seed).
     */
    fun resolveCatalog(
        catalog: LoanCatalog,
        resolvedMap: Map<String, ResolvedConfigItem>
    ): LoanCatalog {
        if (resolvedMap.isEmpty()) return catalog
        val resolvedCategories = catalog.categories.map { category ->
            resolveCategory(category, resolvedMap)
        }
        return catalog.copy(categories = resolvedCategories)
    }

    private fun resolveCategory(
        category: LoanCategory,
        resolvedMap: Map<String, ResolvedConfigItem>
    ): LoanCategory {
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

    private fun resolveVariant(
        variant: LoanVariant,
        resolvedMap: Map<String, ResolvedConfigItem>
    ): LoanVariant {
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

    private fun humanizeKey(key: String): String {
        return key.split('.', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
    }
}
