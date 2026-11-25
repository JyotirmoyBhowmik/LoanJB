package com.jyotirmoyb.loan.domain

/**
 * Pure Kotlin domain models representing the configurable loan catalog.
 * Kept framework-agnostic so business rules can be tested on the JVM without Android dependencies.
 */
data class LoanCatalog(
    val version: Int = 1,
    val lastUpdated: String = "2026-11-25",
    val disclaimer: String = "Estimates only. Actual bank terms, rates and rules may differ.",
    val categories: List<LoanCategory> = emptyList()
)

data class LoanCategory(
    val id: String,
    val name: String,
    val shortName: String,
    val description: String = "",
    val tenureMinMonths: Int = 12,
    val tenureMaxMonths: Int = 360,
    val defaultTenureMonths: Int = 120,
    val rateRangeMin: Double = 8.0,
    val rateRangeMax: Double = 14.0,
    val defaultAmountInr: Double = 1_000_000.0,
    val defaultAssetValueInr: Double = 1_500_000.0,
    val ltvMinPercent: Double = 0.0,
    val ltvMaxPercent: Double = 80.0,
    val defaultProcessingFeePercent: Double = 0.5,
    val defaultPrepaymentFeeFixed: Double = 2.0,
    val defaultPrepaymentFeeFloating: Double = 0.0,
    val secured: Boolean = true,
    val variantGroups: List<VariantGroup> = emptyList()
)

data class VariantGroup(
    val id: String,
    val name: String,
    val required: Boolean = true,
    val variants: List<LoanVariant> = emptyList()
)

data class LoanVariant(
    val id: String,
    val name: String,
    val howItWorks: String,
    val bestFor: String,
    val ruleFlag: String,
    val rateRangeMin: Double = 8.0,
    val rateRangeMax: Double = 14.0,
    val typicalTenureLabel: String = "5 - 30 yrs",
    val prepaymentFeePercent: Double? = null,
    val ltvCapOverride: Double? = null
)

/**
 * Seed domain defaults for the Home Loan category (Step 1: Fixed-Rate Home Loan).
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
