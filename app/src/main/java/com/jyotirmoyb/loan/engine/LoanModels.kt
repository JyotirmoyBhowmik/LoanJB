package com.jyotirmoyb.loan.engine

import com.squareup.moshi.JsonClass

enum class CurrencyCode(val symbol: String, val code: String, val label: String) {
    INR("₹", "INR", "INR (₹ — Indian Grouping)"),
    USD("$", "USD", "USD ($ — US Grouping)")
}

enum class PrepaymentMode { REDUCE_TENURE, REDUCE_EMI }

@JsonClass(generateAdapter = true)
data class RateChangeEvent(
    val afterMonth: Int,
    val newAnnualRate: Double
)

@JsonClass(generateAdapter = true)
data class TrancheEntry(
    val monthNumber: Int,
    val amount: Double
)

@JsonClass(generateAdapter = true)
data class FlexiEvent(
    val monthNumber: Int,
    val drawAmount: Double = 0.0,
    val repayAmount: Double = 0.0
)

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
    // Variant-specific parameters
    val rateChanges: List<RateChangeEvent> = emptyList(),
    val hybridFixedMonths: Int = 36,
    val hybridFloatingRate: Double = 10.25,
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
    // Fees & Insurance
    val processingFeePercent: Double = 0.5,
    val insurancePremium: Double = 0.0,
    // Prepayment
    val prepaymentEnabled: Boolean = false,
    val prepayments: List<PrepaymentRule> = emptyList(),
    val prepaymentFeePercent: Double = 0.0,
    // Asset-backed / Business specialized inputs
    val fdRatePercent: Double = 7.25,
    val fdSpreadPercent: Double = 1.5,
    val shareValue: Double = 1200000.0,
    val minMarginPercent: Double = 30.0,
    val billInvoiceAmount: Double = 1000000.0,
    val billAdvancePercent: Double = 80.0,
    val billDiscountRatePercent: Double = 12.0,
    val billDays: Int = 60,
    val lcCommissionPercent: Double = 0.75,
    val lcDays: Int = 90,
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
    val phaseLabel: String
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
    val totalPayable: Double
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
    val lcCommissionFee: Double = 0.0
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
    val insuranceAmount: Double = 0.0,
    val prepaymentFeeAmount: Double = 0.0,
    val totalFees: Double = 0.0,
    val effectiveTotalCost: Double = 0.0,
    val actualTenureMonths: Int = 0,
    val ltvPercent: Double = 0.0,
    val ltvCapPercent: Double = 80.0,
    val isLtvExceeded: Boolean = false,
    val downPayment: Double = 0.0,
    val schedule: List<AmortizationRow> = emptyList(),
    val yearlySummaries: List<YearlySummary> = emptyList(),
    val insights: VariantInsights = VariantInsights()
)
