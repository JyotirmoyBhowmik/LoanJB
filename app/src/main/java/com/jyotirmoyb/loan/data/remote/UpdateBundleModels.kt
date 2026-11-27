package com.jyotirmoyb.loan.data.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateBundleSection(
    val items: List<RemoteConfigItem>? = null,
    val bankFdRates: List<BankFdRateSeed>? = null,
    val taxYears: List<TaxYearRules>? = null,
    val gstSlabs: List<Double>? = null,
    val advanceTaxThreshold: Double? = null,
    val seniorPensionSchemes: List<SeniorSchemeStatusRule>? = null,
    val apyTable: List<ApyContributionRow>? = null,
    val pmsymTable: List<PmsymContributionRow>? = null
)

@JsonClass(generateAdapter = true)
data class UnresolvedItemNote(val key: String, val reason: String)

@JsonClass(generateAdapter = true)
data class LoanJbUpdateBundle(
    val bundleVersion: Int = 1,
    val generatedOn: String = "",
    val generatedBy: String = "",
    val dataVersion: String = "",
    val rates: UpdateBundleSection? = null,
    val taxslabs: UpdateBundleSection? = null,
    val schemes: UpdateBundleSection? = null,
    val confirmedUnchanged: List<String> = emptyList(),
    val unresolved: List<UnresolvedItemNote> = emptyList(),
    val suggestedNewKeys: List<RemoteConfigItem> = emptyList(),
    val notes: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ImportHistoryEntry(
    val importedAtMillis: Long,
    val dataVersion: String,
    val summaryLabel: String,
    val previousImportedCaches: Map<String, String?> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class ImportHistoryStore(val entries: List<ImportHistoryEntry> = emptyList())

data class BundlePartInfo(val partNumber: Int, val totalParts: Int) {
    val label: String get() = "PART $partNumber/$totalParts"
}

data class BundleItemChange(
    val key: String,
    val label: String,
    val fileName: String,
    val oldValueDisplay: String,
    val newValueDisplay: String,
    val unit: String,
    val effectiveFrom: String,
    val sourceName: String,
    val sourceUrl: String,
    val lastVerified: String,
    val incomingItem: RemoteConfigItem
)

data class BundleTableChange(
    val id: String,
    val fileName: String,
    val label: String,
    val summary: String,
    val effectiveFrom: String,
    val sourceName: String
)

data class UpdateBundlePreview(
    val bundle: LoanJbUpdateBundle,
    val dataVersion: String,
    val generatedOn: String,
    val generatedBy: String,
    val partInfo: BundlePartInfo? = null,
    val itemChanges: List<BundleItemChange> = emptyList(),
    val tableChanges: List<BundleTableChange> = emptyList(),
    val confirmedUnchangedCount: Int = 0,
    val unresolved: List<UnresolvedItemNote> = emptyList(),
    val suggestedNewKeys: List<RemoteConfigItem> = emptyList(),
    val notes: List<String> = emptyList()
) {
    val totalChangeCount: Int
        get() = itemChanges.size + tableChanges.size
}
