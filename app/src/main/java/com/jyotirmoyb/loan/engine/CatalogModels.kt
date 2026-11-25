package com.jyotirmoyb.loan.engine

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoanCatalog(
    val version: Int = 1,
    val lastUpdated: String = "2026-11-25",
    val disclaimer: String = "Estimates only. Actual bank terms, rates and rules (including Nepal Rastra Bank limits) may differ.",
    val categories: List<LoanCategory> = emptyList()
)

@JsonClass(generateAdapter = true)
data class LoanCategory(
    val id: String,
    val name: String,
    val shortName: String,
    val icon: String = "account_balance",
    val accentHex: String = "#0B8A7B",
    val description: String = "",
    val tenureMinMonths: Int = 12,
    val tenureMaxMonths: Int = 360,
    val defaultTenureMonths: Int = 120,
    val rateRangeMin: Double = 8.0,
    val rateRangeMax: Double = 14.0,
    val defaultAmountInr: Double = 1000000.0,
    val defaultAssetValueInr: Double = 1500000.0,
    val ltvMinPercent: Double = 0.0,
    val ltvMaxPercent: Double = 80.0,
    val defaultProcessingFeePercent: Double = 0.5,
    val defaultPrepaymentFeeFixed: Double = 2.0,
    val defaultPrepaymentFeeFloating: Double = 0.0,
    val secured: Boolean = true,
    val checklistItems: List<String> = emptyList(),
    val variantGroups: List<VariantGroup> = emptyList()
)

@JsonClass(generateAdapter = true)
data class VariantGroup(
    val id: String,
    val name: String,
    val required: Boolean = true,
    val variants: List<LoanVariant> = emptyList()
)

@JsonClass(generateAdapter = true)
data class LoanVariant(
    val id: String,
    val name: String,
    val howItWorks: String,
    val bestFor: String,
    val ruleFlag: String,
    val rateRangeMin: Double = 8.0,
    val rateRangeMax: Double = 14.0,
    val typicalTenureLabel: String = "5 - 20 yrs",
    val prepaymentFeePercent: Double? = null,
    val ltvCapOverride: Double? = null
)
