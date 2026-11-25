package com.jyotirmoyb.loan.domain

/**
 * Pure Kotlin domain models for loan inputs, amortization schedules, and calculation results.
 */
enum class InterestRateType {
    FIXED,
    FLOATING,
    HYBRID
}

data class FixedRateLoanRequest(
    val categoryId: String = HomeLoanCatalogDefaults.CATEGORY_ID_HOME,
    val principal: Double,
    val annualInterestRatePercent: Double,
    val tenureMonths: Int,
    val propertyValue: Double = 0.0,
    val processingFeePercent: Double = 0.0,
    val startYear: Int = 2026,
    val startMonth: Int = 1, // 1..12
    val rateType: InterestRateType = InterestRateType.FIXED
)

data class AmortizationScheduleItem(
    val monthNumber: Int,
    val yearIndex: Int,
    val calendarYear: Int,
    val calendarMonth: Int, // 1..12
    val monthLabel: String,
    val openingBalance: Double,
    val emiAmount: Double,
    val principalComponent: Double,
    val interestComponent: Double,
    val closingBalance: Double,
    val appliedAnnualRatePercent: Double
)

data class YearlyAmortizationSummary(
    val yearIndex: Int,
    val label: String,
    val totalPrincipalPaid: Double,
    val totalInterestPaid: Double,
    val totalPayment: Double,
    val endingBalance: Double,
    val months: List<AmortizationScheduleItem>
)

data class FixedRateLoanResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val monthlyEmi: Double = 0.0,
    val totalPrincipal: Double = 0.0,
    val totalInterest: Double = 0.0,
    val totalPayment: Double = 0.0,
    val processingFeeAmount: Double = 0.0,
    val effectiveTotalCost: Double = 0.0,
    val ltvPercent: Double = 0.0,
    val ltvCapPercent: Double = 80.0,
    val isLtvExceeded: Boolean = false,
    val downPayment: Double = 0.0,
    val schedule: List<AmortizationScheduleItem> = emptyList(),
    val yearlySummaries: List<YearlyAmortizationSummary> = emptyList()
)
