package com.jyotirmoyb.loan.domain

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

/**
 * Pure Kotlin domain calculation engine implementing the core reducing-balance EMI formula
 * and fixed-rate amortization schedule logic for the Home Loan category.
 */
object LoanCalculationEngine {

    private val MONTH_NAMES = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    /**
     * Rounds a monetary amount to 2 decimal places (paise / cents).
     */
    fun roundCurrency(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return round(value * 100.0) / 100.0
    }

    /**
     * Core reducing-balance EMI formula:
     *   EMI = P * r * (1 + r)^n / ((1 + r)^n - 1)
     * where:
     *   P = loan principal
     *   r = monthly interest rate = annualRatePercent / 12 / 100
     *   n = tenure in months
     */
    fun calculateEmi(
        principal: Double,
        annualRatePercent: Double,
        tenureMonths: Int
    ): Double {
        if (principal <= 0.0 || tenureMonths <= 0) return 0.0
        val monthlyRate = annualRatePercent / 12.0 / 100.0
        if (monthlyRate <= 0.0) {
            return roundCurrency(principal / tenureMonths)
        }
        val compoundFactor = (1.0 + monthlyRate).pow(tenureMonths)
        val rawEmi = principal * monthlyRate * compoundFactor / (compoundFactor - 1.0)
        return roundCurrency(rawEmi)
    }

    /**
     * Validates inputs against general financial bounds and the provided [LoanCategory] rules
     * (e.g., Home Loan tenure 60–360 months and LTV cap up to 80%).
     */
    fun validateFixedRateRequest(
        request: FixedRateLoanRequest,
        category: LoanCategory? = HomeLoanCatalogDefaults.homeLoanCategory
    ): List<String> {
        val errors = mutableListOf<String>()

        if (request.principal <= 0.0) {
            errors.add("Loan principal must be greater than 0.")
        }
        if (request.annualInterestRatePercent < 0.0 || request.annualInterestRatePercent > 40.0) {
            errors.add("Annual interest rate must be between 0% and 40%.")
        }
        if (request.tenureMonths <= 0) {
            errors.add("Loan tenure must be at least 1 month.")
        } else if (category != null) {
            if (request.tenureMonths < category.tenureMinMonths || request.tenureMonths > category.tenureMaxMonths) {
                errors.add(
                    "Tenure for ${category.name} must be between ${category.tenureMinMonths} and ${category.tenureMaxMonths} months."
                )
            }
        }
        if (request.processingFeePercent < 0.0 || request.processingFeePercent > 15.0) {
            errors.add("Processing fee must be between 0% and 15%.")
        }
        if (category?.secured == true && request.propertyValue > 0.0) {
            val ltv = (request.principal / request.propertyValue) * 100.0
            if (ltv > category.ltvMaxPercent + 0.01) {
                errors.add(
                    "LTV (${String.format("%.1f", ltv)}%) exceeds ${String.format("%.0f", category.ltvMaxPercent)}% cap for ${category.name}."
                )
            }
        }
        return errors
    }

    /**
     * Calculates the complete fixed-rate Home Loan EMI, cost summary, monthly amortization
     * schedule, and yearly rollups. Guarantees exact 0.0 closing balance on the final installment.
     */
    fun calculateFixedRateLoan(
        request: FixedRateLoanRequest,
        category: LoanCategory? = HomeLoanCatalogDefaults.homeLoanCategory
    ): FixedRateLoanResult {
        val ltvCap = category?.ltvMaxPercent ?: 80.0
        val ltvPercent = if (request.propertyValue > 0.0) {
            roundCurrency((request.principal / request.propertyValue) * 100.0)
        } else 0.0
        val downPayment = if (request.propertyValue > request.principal) {
            roundCurrency(request.propertyValue - request.principal)
        } else 0.0
        val isLtvExceeded = (category?.secured == true) &&
            request.propertyValue > 0.0 &&
            ltvPercent > ltvCap + 0.01

        val errors = validateFixedRateRequest(request, category)
        if (errors.isNotEmpty()) {
            return FixedRateLoanResult(
                isValid = false,
                validationErrors = errors,
                ltvPercent = ltvPercent,
                ltvCapPercent = ltvCap,
                isLtvExceeded = isLtvExceeded,
                downPayment = downPayment
            )
        }

        val monthlyEmi = calculateEmi(
            principal = request.principal,
            annualRatePercent = request.annualInterestRatePercent,
            tenureMonths = request.tenureMonths
        )
        val monthlyRate = request.annualInterestRatePercent / 12.0 / 100.0

        val schedule = ArrayList<AmortizationScheduleItem>(request.tenureMonths)
        var remainingBalance = roundCurrency(request.principal)
        var totalInterest = 0.0
        var totalPrincipalPaid = 0.0
        var totalPayment = 0.0

        val startMonthZeroBased = (request.startMonth - 1).coerceIn(0, 11)

        for (monthNumber in 1..request.tenureMonths) {
            val openingBalance = remainingBalance
            val interestComponent = roundCurrency(openingBalance * monthlyRate)

            val isFinalMonth = monthNumber == request.tenureMonths
            val principalComponent: Double
            val emiForMonth: Double

            if (isFinalMonth) {
                // Close out any sub-rupee rounding drift cleanly on the final month
                principalComponent = openingBalance
                emiForMonth = roundCurrency(principalComponent + interestComponent)
                remainingBalance = 0.0
            } else {
                principalComponent = roundCurrency(max(0.0, monthlyEmi - interestComponent))
                    .coerceAtMost(openingBalance)
                emiForMonth = roundCurrency(principalComponent + interestComponent)
                remainingBalance = roundCurrency(max(0.0, openingBalance - principalComponent))
            }

            totalPrincipalPaid = roundCurrency(totalPrincipalPaid + principalComponent)
            totalInterest = roundCurrency(totalInterest + interestComponent)
            totalPayment = roundCurrency(totalPayment + emiForMonth)

            val offset = startMonthZeroBased + (monthNumber - 1)
            val calYear = request.startYear + (offset / 12)
            val calMonth = (offset % 12) + 1
            val monthLabel = "${MONTH_NAMES[calMonth - 1]} $calYear"
            val yearIndex = ((monthNumber - 1) / 12) + 1

            schedule.add(
                AmortizationScheduleItem(
                    monthNumber = monthNumber,
                    yearIndex = yearIndex,
                    calendarYear = calYear,
                    calendarMonth = calMonth,
                    monthLabel = monthLabel,
                    openingBalance = openingBalance,
                    emiAmount = emiForMonth,
                    principalComponent = principalComponent,
                    interestComponent = interestComponent,
                    closingBalance = remainingBalance,
                    appliedAnnualRatePercent = request.annualInterestRatePercent
                )
            )
        }

        val yearlySummaries = schedule
            .groupBy { it.yearIndex }
            .entries
            .sortedBy { it.key }
            .map { (yrIdx, rows) ->
                val firstShort = rows.first().monthLabel.substringBefore(" ")
                val lastShort = rows.last().monthLabel.substringBefore(" ")
                YearlyAmortizationSummary(
                    yearIndex = yrIdx,
                    label = "YEAR $yrIdx · $firstShort – $lastShort",
                    totalPrincipalPaid = roundCurrency(rows.sumOf { it.principalComponent }),
                    totalInterestPaid = roundCurrency(rows.sumOf { it.interestComponent }),
                    totalPayment = roundCurrency(rows.sumOf { it.emiAmount }),
                    endingBalance = rows.last().closingBalance,
                    months = rows
                )
            }

        val processingFeeAmount = roundCurrency(request.principal * (request.processingFeePercent / 100.0))
        val effectiveTotalCost = roundCurrency(totalPayment + processingFeeAmount)

        return FixedRateLoanResult(
            isValid = true,
            monthlyEmi = monthlyEmi,
            totalPrincipal = totalPrincipalPaid,
            totalInterest = totalInterest,
            totalPayment = totalPayment,
            processingFeeAmount = processingFeeAmount,
            effectiveTotalCost = effectiveTotalCost,
            ltvPercent = ltvPercent,
            ltvCapPercent = ltvCap,
            isLtvExceeded = isLtvExceeded,
            downPayment = downPayment,
            schedule = schedule,
            yearlySummaries = yearlySummaries
        )
    }
}
