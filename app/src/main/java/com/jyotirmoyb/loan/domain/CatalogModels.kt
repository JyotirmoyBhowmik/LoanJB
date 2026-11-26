package com.jyotirmoyb.loan.domain

import com.squareup.moshi.JsonClass

const val STANDARD_PLANNING_DISCLAIMER =
    "Estimates for planning only. Not financial, tax or investment advice. Verify with your bank or a qualified professional."

@JsonClass(generateAdapter = true)
data class LoanCatalog(
    val version: Int = 2,
    val lastUpdated: String = "2026-11-25",
    val disclaimer: String = STANDARD_PLANNING_DISCLAIMER,
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
    val rateMinKey: String? = null,
    val rateMaxKey: String? = null,
    val defaultAmountInr: Double = 1000000.0,
    val defaultAssetValueInr: Double = 1500000.0,
    val ltvMinPercent: Double = 0.0,
    val ltvMaxPercent: Double = 80.0,
    val ltvCapKey: String? = null,
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
    val rateMinKey: String? = null,
    val rateMaxKey: String? = null,
    val typicalTenureLabel: String = "5 - 20 yrs",
    val prepaymentFeePercent: Double? = null,
    val ltvCapOverride: Double? = null,
    val ltvCapKey: String? = null
)

/**
 * Pure Kotlin seed defaults for the Home Loan category (used by fixed-rate helper & tests).
 */
object HomeLoanCatalogDefaults {
    const val CATEGORY_ID_HOME = "home"
    const val RULE_RATE_FIXED = "RATE_FIXED"
    const val RULE_EMI_STANDARD = "EMI_STANDARD"

    val fixedRateVariant = LoanVariant(
        id = "home_rate_fixed",
        name = "Fixed Rate",
        howItWorks = "Interest rate and monthly EMI remain constant for the full loan tenure.",
        bestFor = "Borrowers seeking predictable monthly cash flow protected from rate hikes.",
        ruleFlag = RULE_RATE_FIXED,
        rateRangeMin = 8.5,
        rateRangeMax = 11.5,
        typicalTenureLabel = "5 - 30 yrs",
        prepaymentFeePercent = 2.0
    )

    val homeLoanCategory = LoanCategory(
        id = CATEGORY_ID_HOME,
        name = "Home Loan",
        shortName = "Home",
        description = "Long-term secured housing loan (5–30 years; 60–80% LTV).",
        tenureMinMonths = 60,
        tenureMaxMonths = 360,
        defaultTenureMonths = 240,
        rateRangeMin = 8.25,
        rateRangeMax = 11.5,
        defaultAmountInr = 5_000_000.0,
        defaultAssetValueInr = 6_500_000.0,
        ltvMinPercent = 60.0,
        ltvMaxPercent = 80.0,
        defaultProcessingFeePercent = 0.5,
        defaultPrepaymentFeeFixed = 2.0,
        defaultPrepaymentFeeFloating = 0.0,
        secured = true,
        variantGroups = listOf(
            VariantGroup(
                id = "rate_type",
                name = "Interest Rate Type",
                required = true,
                variants = listOf(fixedRateVariant)
            )
        )
    )
}
