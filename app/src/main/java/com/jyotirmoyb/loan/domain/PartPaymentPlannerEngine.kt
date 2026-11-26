package com.jyotirmoyb.loan.domain

import java.math.BigDecimal
import java.math.RoundingMode

data class PartPaymentPlanInput(
    val principal: Double = 4000000.0,
    val annualRatePercent: Double = 8.75,
    val remainingMonths: Int = 240,
    val startYear: Int = 2026,
    val startMonth: Int = 11, // 1..12
    val startDay: Int = 25,
    val targetPayoffYear: Int = 2041,
    val targetPayoffMonth: Int = 11 // 1..12
)

data class PartPaymentPlanResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val currentMonthlyEmi: Double = 0.0,
    val extraMonthlyNeeded: Double = 0.0,
    val totalMonthlyOutlay: Double = 0.0,
    val currentMonths: Int = 0,
    val targetMonths: Int = 0,
    val actualNewMonths: Int = 0,
    val monthsSaved: Int = 0,
    val baselineTotalInterest: Double = 0.0,
    val newTotalInterest: Double = 0.0,
    val totalInterestSaved: Double = 0.0,
    val currentLoanFreeDate: String = "",
    val newLoanFreeDate: String = "",
    val baselineYearlyBalances: List<Double> = emptyList(),
    val plannedYearlyBalances: List<Double> = emptyList()
)

/**
 * Pure domain engine for the Part-Payment Planner ("loan-free by <date>").
 * Computes the exact extra monthly contribution required to close the loan by the target date,
 * verified against full BigDecimal amortization so the closing balance ends at exactly 0.00.
 */
object PartPaymentPlannerEngine {

    fun plan(input: PartPaymentPlanInput): PartPaymentPlanResult {
        val errors = mutableListOf<String>()
        if (input.principal <= 0.0) errors.add("Loan principal must be greater than 0.")
        if (input.annualRatePercent < 0.0 || input.annualRatePercent > 40.0) {
            errors.add("Interest rate must be between 0% and 40%.")
        }
        if (input.remainingMonths < 2 || input.remainingMonths > 420) {
            errors.add("Remaining tenure must be between 2 and 420 months.")
        }

        val targetMonths = ((input.targetPayoffYear - input.startYear) * 12) +
            (input.targetPayoffMonth - input.startMonth)

        if (targetMonths < 1) {
            errors.add("Target loan-free date must be at least 1 month after the start date.")
        } else if (targetMonths >= input.remainingMonths) {
            errors.add("Target date must be earlier than your current payoff date (${input.remainingMonths} months).")
        }

        if (errors.isNotEmpty()) {
            return PartPaymentPlanResult(isValid = false, validationErrors = errors)
        }

        val baseCalcInput = CalculationInput(
            categoryId = "home",
            ruleFlags = listOf("RATE_FIXED", "EMI_STANDARD", "DISB_FULL"),
            principal = input.principal,
            assetValue = input.principal * 1.25,
            annualRate = input.annualRatePercent,
            tenureMonths = input.remainingMonths,
            startYear = input.startYear,
            startMonth = input.startMonth,
            startDay = input.startDay,
            processingFeePercent = 0.0,
            prepaymentEnabled = false
        )
        val baseline = LoanCalculationEngine.calculate(baseCalcInput)

        val principalBd = MoneyMath.bd(input.principal)
        val rateBd = MoneyMath.bd(input.annualRatePercent)
        val baselineEmiBd = MoneyMath.bd(baseline.monthlyEmi)

        // Exact analytical EMI for targetMonths gives an ultra-tight starting estimate
        val targetEmiBd = MoneyMath.standardEmiBd(principalBd, rateBd, targetMonths)
        val analyticalExtra = targetEmiBd.subtract(baselineEmiBd).max(BigDecimal.ZERO)

        // Refine with exact schedule binary search (to nearest ₹1) so closing balance hits exact 0.00 on or before targetMonths
        var low = BigDecimal.ZERO
        var high = analyticalExtra.add(BigDecimal("1000")).max(BigDecimal("100"))
        // Ensure high reaches targetMonths
        while (simulateMonths(baseCalcInput, high.toDouble()) > targetMonths) {
            high = high.multiply(BigDecimal("2"))
            if (high > principalBd) {
                high = principalBd
                break
            }
        }

        repeat(32) {
            val mid = low.add(high).divide(BigDecimal("2"), 0, RoundingMode.HALF_UP)
            if (mid == low || mid == high) return@repeat
            val monthsNeeded = simulateMonths(baseCalcInput, mid.toDouble())
            if (monthsNeeded <= targetMonths) {
                high = mid
            } else {
                low = mid
            }
        }

        val extraMonthlyBd = MoneyMath.roundPaiseBd(high)
        val acceleratedInput = baseCalcInput.copy(
            prepaymentEnabled = true,
            prepayments = listOf(
                PrepaymentRule(
                    startMonth = 1,
                    amount = extraMonthlyBd.toDouble(),
                    isRecurring = true,
                    intervalMonths = 1,
                    mode = PrepaymentMode.REDUCE_TENURE.name
                )
            )
        )
        val planned = LoanCalculationEngine.calculate(acceleratedInput)
        val totalOutlayBd = MoneyMath.roundPaiseBd(baselineEmiBd.add(extraMonthlyBd))
        val interestSavedBd = MoneyMath.roundPaiseBd(
            MoneyMath.bd(baseline.totalInterest).subtract(MoneyMath.bd(planned.totalInterest)).max(BigDecimal.ZERO)
        )

        val baselineBalances = buildYearlyBalances(input.principal, baseline)
        val plannedBalances = buildYearlyBalances(input.principal, planned)

        return PartPaymentPlanResult(
            isValid = true,
            currentMonthlyEmi = baseline.monthlyEmi,
            extraMonthlyNeeded = extraMonthlyBd.toDouble(),
            totalMonthlyOutlay = totalOutlayBd.toDouble(),
            currentMonths = baseline.actualTenureMonths,
            targetMonths = targetMonths,
            actualNewMonths = planned.actualTenureMonths,
            monthsSaved = (baseline.actualTenureMonths - planned.actualTenureMonths).coerceAtLeast(0),
            baselineTotalInterest = baseline.totalInterest,
            newTotalInterest = planned.totalInterest,
            totalInterestSaved = interestSavedBd.toDouble(),
            currentLoanFreeDate = baseline.loanFreeDateLabel,
            newLoanFreeDate = planned.loanFreeDateLabel,
            baselineYearlyBalances = baselineBalances,
            plannedYearlyBalances = plannedBalances
        )
    }

    private fun simulateMonths(baseInput: CalculationInput, extraMonthly: Double): Int {
        val result = LoanCalculationEngine.calculate(
            baseInput.copy(
                prepaymentEnabled = true,
                prepayments = listOf(
                    PrepaymentRule(
                        startMonth = 1,
                        amount = extraMonthly,
                        isRecurring = true,
                        intervalMonths = 1,
                        mode = PrepaymentMode.REDUCE_TENURE.name
                    )
                )
            )
        )
        return result.actualTenureMonths
    }

    private fun buildYearlyBalances(principal: Double, result: CalculationResult): List<Double> {
        val list = mutableListOf(MoneyMath.roundPaise(principal))
        result.yearlySummaries.forEach { yr ->
            list.add(MoneyMath.roundPaise(yr.endingBalance))
        }
        return list
    }

    fun addMonthsToYearMonth(startYear: Int, startMonth: Int, monthsToAdd: Int): Pair<Int, Int> {
        val totalZeroBased = (startYear * 12 + (startMonth - 1)) + monthsToAdd
        val year = totalZeroBased / 12
        val month = (totalZeroBased % 12) + 1
        return year to month
    }
}
