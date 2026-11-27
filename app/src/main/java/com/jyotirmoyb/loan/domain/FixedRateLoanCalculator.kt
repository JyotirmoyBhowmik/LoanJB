package com.jyotirmoyb.loan.domain

import java.math.BigDecimal
import java.util.Locale

/**
 * Fixed-rate loan domain helper extracted from [LoanCalculationEngine] for modularity.
 */
internal object FixedRateLoanCalculator {

    private val MONTH_NAMES = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    fun validateFixedRateRequest(
        request: FixedRateLoanRequest,
        category: LoanCategory? = HomeLoanCatalogDefaults.homeLoanCategory
    ): List<String> {
        val errors = mutableListOf<String>()
        if (request.principal <= 0.0) errors.add("Loan principal must be greater than 0.")
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
                    "LTV (${String.format(
                        Locale.US,
                        "%.1f",
                        ltv
                    )}%) exceeds ${String.format(Locale.US, "%.0f", category.ltvMaxPercent)}% cap for ${category.name}."
                )
            }
        }
        return errors
    }

    fun calculateFixedRateLoan(
        request: FixedRateLoanRequest,
        category: LoanCategory? = HomeLoanCatalogDefaults.homeLoanCategory
    ): FixedRateLoanResult {
        val ltvCap = category?.ltvMaxPercent ?: 80.0
        val ltvPercent = if (request.propertyValue >
            0.0
        ) {
            LoanCalculationEngine.roundPaisa((request.principal / request.propertyValue) * 100.0)
        } else {
            0.0
        }
        val downPayment = if (request.propertyValue >
            request.principal
        ) {
            LoanCalculationEngine.roundPaisa(request.propertyValue - request.principal)
        } else {
            0.0
        }
        val isLtvExceeded = (category?.secured == true) && request.propertyValue > 0.0 && ltvPercent > ltvCap + 0.01

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

        val monthlyEmiBd = MoneyMath.standardEmiBd(
            MoneyMath.bd(request.principal),
            MoneyMath.bd(request.annualInterestRatePercent),
            request.tenureMonths
        )
        val rateBd = MoneyMath.bd(request.annualInterestRatePercent)
        val schedule = ArrayList<AmortizationScheduleItem>(request.tenureMonths)
        var remainingBd = MoneyMath.roundPaiseBd(MoneyMath.bd(request.principal))
        var totalInterestBd = MoneyMath.ZERO
        var totalPrincipalBd = MoneyMath.ZERO
        var totalPaymentBd = MoneyMath.ZERO
        val startMonthZeroBased = (request.startMonth - 1).coerceIn(0, 11)

        for (monthNumber in 1..request.tenureMonths) {
            val openingBd = remainingBd
            val interestBd = MoneyMath.monthlyInterest(openingBd, rateBd)
            val isFinal = monthNumber == request.tenureMonths
            val principalBd: BigDecimal
            val emiBd: BigDecimal
            if (isFinal) {
                principalBd = openingBd
                emiBd = MoneyMath.roundPaiseBd(principalBd.add(interestBd))
                remainingBd = MoneyMath.ZERO
            } else {
                principalBd = MoneyMath.roundPaiseBd(monthlyEmiBd.subtract(interestBd)).max(MoneyMath.ZERO).min(openingBd)
                emiBd = MoneyMath.roundPaiseBd(principalBd.add(interestBd))
                remainingBd = MoneyMath.roundPaiseBd(openingBd.subtract(principalBd)).max(MoneyMath.ZERO)
            }
            totalPrincipalBd = MoneyMath.roundPaiseBd(totalPrincipalBd.add(principalBd))
            totalInterestBd = MoneyMath.roundPaiseBd(totalInterestBd.add(interestBd))
            totalPaymentBd = MoneyMath.roundPaiseBd(totalPaymentBd.add(emiBd))

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
                    openingBalance = openingBd.toDouble(),
                    emiAmount = emiBd.toDouble(),
                    principalComponent = principalBd.toDouble(),
                    interestComponent = interestBd.toDouble(),
                    closingBalance = remainingBd.toDouble(),
                    appliedAnnualRatePercent = request.annualInterestRatePercent
                )
            )
        }

        val yearlySummaries = schedule.groupBy { it.yearIndex }.entries.sortedBy { it.key }.map { (yrIdx, rows) ->
            val firstShort = rows.first().monthLabel.substringBefore(" ")
            val lastShort = rows.last().monthLabel.substringBefore(" ")
            YearlyAmortizationSummary(
                yearIndex = yrIdx,
                label = "YEAR $yrIdx · $firstShort – $lastShort",
                totalPrincipalPaid = LoanCalculationEngine.roundPaisa(rows.sumOf { it.principalComponent }),
                totalInterestPaid = LoanCalculationEngine.roundPaisa(rows.sumOf { it.interestComponent }),
                totalPayment = LoanCalculationEngine.roundPaisa(rows.sumOf { it.emiAmount }),
                endingBalance = rows.last().closingBalance,
                months = rows
            )
        }

        val procFee = MoneyMath.percentageOf(MoneyMath.bd(request.principal), MoneyMath.bd(request.processingFeePercent)).toDouble()
        return FixedRateLoanResult(
            isValid = true,
            monthlyEmi = monthlyEmiBd.toDouble(),
            totalPrincipal = totalPrincipalBd.toDouble(),
            totalInterest = totalInterestBd.toDouble(),
            totalPayment = totalPaymentBd.toDouble(),
            processingFeeAmount = procFee,
            effectiveTotalCost = LoanCalculationEngine.roundPaisa(totalPaymentBd.toDouble() + procFee),
            ltvPercent = ltvPercent,
            ltvCapPercent = ltvCap,
            isLtvExceeded = isLtvExceeded,
            downPayment = downPayment,
            schedule = schedule,
            yearlySummaries = yearlySummaries
        )
    }
}
