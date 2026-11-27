package com.jyotirmoyb.loan.domain

import com.squareup.moshi.JsonClass

enum class RegionCode(val code: String, val label: String, val defaultCurrency: CurrencyCode) {
    IN("IN", "India (INR ₹)", CurrencyCode.INR),
    NP("NP", "Nepal (NPR Rs.)", CurrencyCode.NPR),
    US("US", "United States (USD $)", CurrencyCode.USD)
}

enum class CurrencyCode(val symbol: String, val code: String, val label: String) {
    INR("₹", "INR", "INR (₹ — Lakh / Crore)"),
    NPR("Rs.", "NPR", "NPR (Rs. — South Asian)"),
    USD("$", "USD", "USD ($ — US Grouping)")
}

enum class PrepaymentMode { REDUCE_TENURE, REDUCE_EMI }

enum class InterestRateType {
    FIXED,
    FLOATING,
    HYBRID
}

@JsonClass(generateAdapter = true)
data class RateChangeEvent(val afterMonth: Int, val newAnnualRate: Double)

@JsonClass(generateAdapter = true)
data class TrancheEntry(val monthNumber: Int, val amount: Double)

@JsonClass(generateAdapter = true)
data class FlexiEvent(val monthNumber: Int, val drawAmount: Double = 0.0, val repayAmount: Double = 0.0)

@JsonClass(generateAdapter = true)
data class PrepaymentRule(
    val startMonth: Int = 12,
    val amount: Double = 0.0,
    val isRecurring: Boolean = false,
    val intervalMonths: Int = 12,
    val mode: String = PrepaymentMode.REDUCE_TENURE.name
)

@JsonClass(generateAdapter = true)
data class BalanceTransferInput(
    val enabled: Boolean = false,
    val currentOutstanding: Double = 3000000.0,
    val currentAnnualRate: Double = 10.5,
    val remainingMonths: Int = 180,
    val newAnnualRate: Double = 8.75,
    val transferFees: Double = 25000.0
)

@JsonClass(generateAdapter = true)
data class TopUpInput(
    val enabled: Boolean = false,
    val existingOutstanding: Double = 3000000.0,
    val existingAnnualRate: Double = 8.75,
    val existingRemainingMonths: Int = 180,
    val topUpAmount: Double = 1000000.0,
    val topUpAnnualRate: Double = 9.5,
    val topUpTenureMonths: Int = 120
)

@JsonClass(generateAdapter = true)
data class CalculationInput(
    val categoryId: String = "home",
    val selectedVariantsByGroup: Map<String, String> = emptyMap(),
    val ruleFlags: List<String> = listOf("RATE_FIXED", "EMI_STANDARD", "DISB_FULL", "PURPOSE_PURCHASE"),
    val principal: Double = 5000000.0,
    val assetValue: Double = 6500000.0,
    val annualRate: Double = 9.5,
    val tenureMonths: Int = 240,
    val startYear: Int = 2026,
    val startMonth: Int = 11, // 1..12
    val startDay: Int = 25,
    // EMI date of month & pro-rata first-month (broken-period) interest
    val emiDayOfMonth: Int = 5, // 1..31
    val proRataFirstMonthEnabled: Boolean = false,
    // Benchmark / MCLR / Hybrid
    val benchmarkRatePercent: Double = 6.5, // RBI Repo / EBLR benchmark (from rates.json)
    val spreadPercent: Double = 2.0, // Bank spread over EBLR
    val mclrResetMonths: Int = 6, // 6 or 12 months reset for legacy MCLR
    val rateChanges: List<RateChangeEvent> = emptyList(),
    val hybridFixedMonths: Int = 36,
    val hybridFloatingRate: Double = 10.25,
    // EMI variants
    val stepPercent: Double = 7.0,
    val stepIntervalMonths: Int = 12,
    val isStepDown: Boolean = false,
    val balloonPercent: Double = 25.0,
    val offsetBalance: Double = 500000.0,
    val moratoriumMonths: Int = 12,
    val moratoriumCompound: Boolean = false,
    val tranches: List<TrancheEntry> = listOf(
        TrancheEntry(1, 1500000.0),
        TrancheEntry(7, 1500000.0),
        TrancheEntry(13, 2000000.0)
    ),
    val flexiEvents: List<FlexiEvent> = listOf(
        FlexiEvent(1, drawAmount = 300000.0, repayAmount = 0.0),
        FlexiEvent(4, drawAmount = 150000.0, repayAmount = 0.0),
        FlexiEvent(8, drawAmount = 0.0, repayAmount = 200000.0),
        FlexiEvent(12, drawAmount = 0.0, repayAmount = 250000.0)
    ),
    // Fees, Insurance & GST (India 18% configurable from data)
    val processingFeePercent: Double = 0.5,
    val gstOnFeesEnabled: Boolean = false,
    val gstRatePercent: Double = 18.0,
    val insurancePremium: Double = 0.0,
    // PMAY-style interest subsidy (off by default)
    val pmaySubsidyEnabled: Boolean = false,
    val pmaySubsidyAmount: Double = 180000.0,
    val pmaySubsidyMonth: Int = 6,
    // Prepayment
    val prepaymentEnabled: Boolean = false,
    val prepayments: List<PrepaymentRule> = emptyList(),
    val prepaymentFeePercent: Double = 0.0,
    // Asset-backed / Gold / Business specialized inputs
    val fdRatePercent: Double = 7.25,
    val fdSpreadPercent: Double = 1.5,
    val shareValue: Double = 1200000.0,
    val minMarginPercent: Double = 30.0,
    val goldAuctionRiskLtvPercent: Double = 75.0,
    val billInvoiceAmount: Double = 1000000.0,
    val billAdvancePercent: Double = 80.0,
    val billDiscountRatePercent: Double = 12.0,
    val billDays: Int = 60,
    val lcCommissionPercent: Double = 0.75,
    val lcDays: Int = 90,
    // India Tax benefit inputs (caps come from taxslabs.json via DataSourceResolver)
    val taxBracketPercent: Double = 30.0,
    val section24bCap: Double = 200000.0,
    val section80cCap: Double = 150000.0,
    val section80eMaxYears: Int = 8,
    // Balance Transfer & Top-Up tools
    val balanceTransfer: BalanceTransferInput = BalanceTransferInput(),
    val topUp: TopUpInput = TopUpInput()
)

data class AmortizationRow(
    val monthNumber: Int,
    val monthLabel: String,
    val yearGroupIndex: Int,
    val yearRangeLabel: String,
    val openingBalance: Double,
    val emiPaid: Double,
    val principalPaid: Double,
    val interestPaid: Double,
    val extraPrepayment: Double,
    val closingBalance: Double,
    val activeRate: Double,
    val phaseLabel: String,
    val gstOnInterest: Double = 0.0
)

data class YearlySummary(
    val yearIndex: Int,
    val label: String,
    val principalPaid: Double,
    val interestPaid: Double,
    val totalPaid: Double,
    val endingBalance: Double,
    val rows: List<AmortizationRow>
)

data class RateSensitivityRow(
    val deltaPercent: Double,
    val effectiveRate: Double,
    val monthlyEmi: Double,
    val totalInterest: Double,
    val interestDiff: Double
)

data class FlatVsReducingComparison(
    val quotedRate: Double,
    val flatMonthlyEmi: Double,
    val reducingMonthlyEmi: Double,
    val flatTotalInterest: Double,
    val reducingTotalInterest: Double,
    val extraInterestCost: Double,
    val effectiveAnnualRatePercent: Double
)

data class PrepaymentImpact(
    val baselineTotalInterest: Double,
    val newTotalInterest: Double,
    val interestSaved: Double,
    val prepaymentFees: Double,
    val netSavings: Double,
    val baselineMonths: Int,
    val newMonths: Int,
    val monthsSaved: Int
)

data class BalanceTransferResult(
    val currentEmi: Double,
    val newEmi: Double,
    val monthlySavings: Double,
    val currentRemainingInterest: Double,
    val newTotalInterest: Double,
    val transferFees: Double,
    val netSavings: Double,
    val breakEvenMonth: Int?
)

data class TopUpResult(
    val existingEmi: Double,
    val topUpEmi: Double,
    val blendedEmi: Double,
    val combinedPrincipal: Double,
    val combinedTotalInterest: Double
)

data class FdNetCostResult(
    val fdValue: Double,
    val loanAmount: Double,
    val fdRatePercent: Double,
    val loanRatePercent: Double,
    val loanInterestPaid: Double,
    val fdInterestEarned: Double,
    val netInterestCost: Double,
    val effectiveNetRatePercent: Double
)

data class BillDiscountResult(
    val invoiceAmount: Double,
    val advanceAmount: Double,
    val discountFee: Double,
    val netUpfrontDisbursed: Double,
    val days: Int,
    val effectiveAnnualizedCostPercent: Double
)

data class MarginCallResult(
    val pledgedShareValue: Double,
    val loanAmount: Double,
    val currentLtvPercent: Double,
    val maxAllowedLtvPercent: Double,
    val minMarginPercent: Double,
    val marginCallTriggerValue: Double,
    val dropPercentToMarginCall: Double,
    val isMarginCallTriggered: Boolean
)

data class GoldLoanSummary(
    val modeLabel: String,
    val monthlyOutflow: Double,
    val maturityLumpSum: Double,
    val totalInterest: Double,
    val totalPayable: Double,
    val peakLtvPercent: Double = 0.0,
    val isAuctionRiskAlert: Boolean = false,
    val auctionRiskThresholdLtv: Double = 75.0
)

data class TaxBenefitSummary(
    val sectionTitle: String,
    val firstYearInterestEligible: Double,
    val firstYearPrincipalEligible: Double,
    val estimatedAnnualTaxSaved: Double,
    val estimatedTotalTaxSaved: Double,
    val notes: String
)

data class VariantInsights(
    val sensitivityRows: List<RateSensitivityRow> = emptyList(),
    val offsetInterestSaved: Double = 0.0,
    val moratoriumExtraInterest: Double = 0.0,
    val flatVsReducing: FlatVsReducingComparison? = null,
    val prepaymentImpact: PrepaymentImpact? = null,
    val balanceTransferResult: BalanceTransferResult? = null,
    val topUpResult: TopUpResult? = null,
    val fdNetCost: FdNetCostResult? = null,
    val billDiscountResult: BillDiscountResult? = null,
    val marginCallResult: MarginCallResult? = null,
    val goldSummary: GoldLoanSummary? = null,
    val flexiPeakBalance: Double = 0.0,
    val lcCommissionFee: Double = 0.0,
    val pmaySubsidyApplied: Double = 0.0,
    val pmayInterestSaved: Double = 0.0,
    val taxBenefitSummary: TaxBenefitSummary? = null
)

data class CalculationResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val monthlyEmi: Double = 0.0,
    val firstEmi: Double = 0.0,
    val lastEmi: Double = 0.0,
    val totalPrincipal: Double = 0.0,
    val totalInterest: Double = 0.0,
    val totalPayment: Double = 0.0,
    val processingFeeAmount: Double = 0.0,
    val gstOnFeeAmount: Double = 0.0,
    val gstOnInterestAmount: Double = 0.0,
    val brokenPeriodInterest: Double = 0.0,
    val insuranceAmount: Double = 0.0,
    val prepaymentFeeAmount: Double = 0.0,
    val totalFees: Double = 0.0,
    val effectiveTotalCost: Double = 0.0,
    val interestToPrincipalPercent: Double = 0.0,
    val loanFreeDateLabel: String = "",
    val actualTenureMonths: Int = 0,
    val ltvPercent: Double = 0.0,
    val ltvCapPercent: Double = 80.0,
    val isLtvExceeded: Boolean = false,
    val downPayment: Double = 0.0,
    val schedule: List<AmortizationRow> = emptyList(),
    val yearlySummaries: List<YearlySummary> = emptyList(),
    val insights: VariantInsights = VariantInsights()
)

// Pure Fixed-Rate Helper Models (kept from earlier domain layer for backward compatibility)
data class FixedRateLoanRequest(
    val categoryId: String = HomeLoanCatalogDefaults.CATEGORY_ID_HOME,
    val principal: Double,
    val annualInterestRatePercent: Double,
    val tenureMonths: Int,
    val propertyValue: Double = 0.0,
    val processingFeePercent: Double = 0.0,
    val startYear: Int = 2026,
    val startMonth: Int = 1,
    val rateType: InterestRateType = InterestRateType.FIXED
)

data class AmortizationScheduleItem(
    val monthNumber: Int,
    val yearIndex: Int,
    val calendarYear: Int,
    val calendarMonth: Int,
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
