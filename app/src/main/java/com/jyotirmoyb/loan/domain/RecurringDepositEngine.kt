package com.jyotirmoyb.loan.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

data class RecurringDepositInput(
    val monthlyDeposit: Double = 10000.0,
    val annualRatePercent: Double = 6.75,
    val isSeniorCitizen: Boolean = false,
    val seniorBonusPercent: Double = 0.5,
    val tenureMonths: Int = 60,
    val hasPan: Boolean = true,
    val taxSlabPercent: Double = 30.0,
    val startYear: Int = 2026,
    val startMonth: Int = 11,
    val startDay: Int = 25
)

data class RecurringDepositResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val effectiveNominalRatePercent: Double = 0.0,
    val totalDeposited: Double = 0.0,
    val totalInterest: Double = 0.0,
    val maturityAmount: Double = 0.0,
    val totalEstimatedTds: Double = 0.0,
    val totalSlabTax: Double = 0.0,
    val postTaxInterest: Double = 0.0,
    val postTaxMaturityAmount: Double = 0.0,
    val effectiveYieldPercent: Double = 0.0,
    val maturityDateLabel: String = "",
    val yearlyRows: List<DepositYearRow> = emptyList()
)

/**
 * Pure domain engine for Indian Bank Recurring Deposits (monthly instalment with quarterly compounding).
 * Follows the standard Indian Banks' Association (IBA) formula:
 *   M = sum_{k=1..N} P * (1 + r/4)^((N - k + 1) / 3)
 */
object RecurringDepositEngine {

    fun calculate(
        input: RecurringDepositInput,
        tdsConfig: TdsConfig
    ): RecurringDepositResult {
        val errors = mutableListOf<String>()
        if (input.monthlyDeposit <= 0.0) errors.add("Monthly instalment must be greater than 0.")
        if (input.annualRatePercent <= 0.0 || input.annualRatePercent > 30.0) {
            errors.add("Interest rate must be between 0.1% and 30%.")
        }
        if (input.tenureMonths < 6 || input.tenureMonths > 240) {
            errors.add("RD tenure must be between 6 and 240 months.")
        }
        if (errors.isNotEmpty()) {
            return RecurringDepositResult(isValid = false, validationErrors = errors)
        }

        val effectiveRate = MoneyMath.roundPaise(
            input.annualRatePercent + if (input.isSeniorCitizen) input.seniorBonusPercent else 0.0
        )
        val monthlyBd = MoneyMath.bd(input.monthlyDeposit)
        val quarterlyFactorBase = 1.0 + (effectiveRate / 400.0)

        fun maturityAfterMonths(totalMonths: Int): BigDecimal {
            var sum = BigDecimal.ZERO
            for (k in 1..totalMonths) {
                val monthsInvested = totalMonths - k + 1
                val quarters = monthsInvested / 3.0
                val growth = quarterlyFactorBase.pow(quarters)
                sum = sum.add(monthlyBd.multiply(MoneyMath.bd(growth), MoneyMath.MC))
            }
            return MoneyMath.roundPaiseBd(sum)
        }

        val maturityBd = maturityAfterMonths(input.tenureMonths)
        val totalDepositedBd = MoneyMath.roundPaiseBd(monthlyBd.multiply(BigDecimal(input.tenureMonths), MoneyMath.MC))
        val totalInterestBd = MoneyMath.roundPaiseBd(maturityBd.subtract(totalDepositedBd).max(BigDecimal.ZERO))

        val tdsThresholdBd = MoneyMath.bd(tdsConfig.thresholdFor(input.isSeniorCitizen))
        val tdsRateBd = MoneyMath.bd(tdsConfig.tdsRateFor(input.hasPan))

        val fullYears = input.tenureMonths / 12
        val remMonths = input.tenureMonths % 12
        val totalSlices = fullYears + if (remMonths > 0) 1 else 0

        val yearlyRows = mutableListOf<DepositYearRow>()
        var prevEndBalanceBd = MoneyMath.ZERO
        var totalTdsBd = MoneyMath.ZERO

        for (yr in 1..totalSlices) {
            val cumulativeMonths = minOf(yr * 12, input.tenureMonths)
            val sliceMonths = if (yr <= fullYears) 12 else remMonths
            val depositAddedBd = MoneyMath.roundPaiseBd(monthlyBd.multiply(BigDecimal(sliceMonths), MoneyMath.MC))
            val closingBd = maturityAfterMonths(cumulativeMonths)
            val yrInterestBd = MoneyMath.roundPaiseBd(
                closingBd.subtract(prevEndBalanceBd).subtract(depositAddedBd).max(BigDecimal.ZERO)
            )
            val yrTdsBd = if (yrInterestBd > tdsThresholdBd) {
                MoneyMath.percentageOf(yrInterestBd, tdsRateBd)
            } else {
                MoneyMath.ZERO
            }
            totalTdsBd = totalTdsBd.add(yrTdsBd)

            val (sliceY, sliceM) = PartPaymentPlannerEngine.addMonthsToYearMonth(
                input.startYear,
                input.startMonth,
                (yr - 1) * 12
            )
            yearlyRows.add(
                DepositYearRow(
                    yearIndex = yr,
                    fyLabel = MoneyMath.financialYearLabel(sliceY, sliceM),
                    openingBalance = prevEndBalanceBd.toDouble(),
                    depositAdded = depositAddedBd.toDouble(),
                    interestEarned = yrInterestBd.toDouble(),
                    estimatedTds = yrTdsBd.toDouble(),
                    closingBalance = closingBd.toDouble()
                )
            )
            prevEndBalanceBd = closingBd
        }

        val effectiveTaxRate = input.taxSlabPercent.coerceIn(0.0, 45.0) * (1.0 + tdsConfig.cessPercent / 100.0)
        val totalSlabTaxBd = MoneyMath.percentageOf(totalInterestBd, MoneyMath.bd(effectiveTaxRate))
        val postTaxInterestBd = MoneyMath.roundPaiseBd(totalInterestBd.subtract(totalSlabTaxBd).max(BigDecimal.ZERO))
        val postTaxMaturityBd = MoneyMath.roundPaiseBd(totalDepositedBd.add(postTaxInterestBd))

        val effectiveYield = FixedDepositEngine.annualizedYieldPercent(
            principal = totalDepositedBd.toDouble() / 2.0, // approximate average capital yield -> keep nominal compounded EAR
            finalAmount = totalDepositedBd.toDouble() / 2.0,
            years = 1.0
        ).let {
            // Standard Effective Annual Rate for quarterly compounding: (1 + r/4)^4 - 1
            MoneyMath.roundPaise((quarterlyFactorBase.pow(4) - 1.0) * 100.0)
        }

        val (matYear, matMonth) = PartPaymentPlannerEngine.addMonthsToYearMonth(
            input.startYear,
            input.startMonth,
            input.tenureMonths
        )

        return RecurringDepositResult(
            isValid = true,
            effectiveNominalRatePercent = effectiveRate,
            totalDeposited = totalDepositedBd.toDouble(),
            totalInterest = totalInterestBd.toDouble(),
            maturityAmount = maturityBd.toDouble(),
            totalEstimatedTds = MoneyMath.roundPaiseBd(totalTdsBd).toDouble(),
            totalSlabTax = totalSlabTaxBd.toDouble(),
            postTaxInterest = postTaxInterestBd.toDouble(),
            postTaxMaturityAmount = postTaxMaturityBd.toDouble(),
            effectiveYieldPercent = effectiveYield,
            maturityDateLabel = MoneyMath.formatDateDdMmmYyyy(input.startDay, matMonth, matYear),
            yearlyRows = yearlyRows
        )
    }
}
