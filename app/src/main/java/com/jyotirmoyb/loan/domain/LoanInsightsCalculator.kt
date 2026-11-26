package com.jyotirmoyb.loan.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pure Kotlin domain calculators for loan comparison insights, effective annual rate (IRR),
 * rate sensitivity tables, balance transfer, top-up, and Indian tax benefit estimation.
 */
object LoanInsightsCalculator {

    fun buildSensitivityTable(
        principal: Double,
        baseRate: Double,
        tenureMonths: Int
    ): List<RateSensitivityRow> {
        val pBd = MoneyMath.bd(principal)
        val baseEmi = MoneyMath.standardEmiBd(pBd, MoneyMath.bd(baseRate), tenureMonths)
        val baseTotalInt = MoneyMath.roundPaiseBd(
            baseEmi.multiply(BigDecimal(tenureMonths)).subtract(pBd)
        ).max(MoneyMath.ZERO)

        val deltas = listOf(-2.0, -1.0, 0.0, 1.0, 2.0)
        return deltas.map { delta ->
            val rate = (baseRate + delta).coerceIn(0.1, 40.0)
            val emi = MoneyMath.standardEmiBd(pBd, MoneyMath.bd(rate), tenureMonths)
            val totalInt = MoneyMath.roundPaiseBd(
                emi.multiply(BigDecimal(tenureMonths)).subtract(pBd)
            ).max(MoneyMath.ZERO)
            RateSensitivityRow(
                deltaPercent = delta,
                effectiveRate = MoneyMath.roundPaise(rate),
                monthlyEmi = emi.toDouble(),
                totalInterest = totalInt.toDouble(),
                interestDiff = MoneyMath.roundPaiseBd(totalInt.subtract(baseTotalInt)).toDouble()
            )
        }
    }

    /**
     * Computes Flat Rate vs Reducing Balance Rate comparison + true Effective Annual Rate (IRR) of Flat loan.
     */
    fun calculateFlatVsReducing(
        principal: Double,
        quotedAnnualRate: Double,
        tenureMonths: Int
    ): FlatVsReducingComparison {
        val pBd = MoneyMath.bd(principal)
        val rateBd = MoneyMath.bd(quotedAnnualRate)
        val yearsBd = BigDecimal(tenureMonths).divide(BigDecimal("12"), MoneyMath.MC)
        val flatTotalInterestBd = MoneyMath.roundPaiseBd(
            pBd.multiply(rateBd, MoneyMath.MC).divide(MoneyMath.HUNDRED, MoneyMath.MC).multiply(yearsBd, MoneyMath.MC)
        )
        val flatMonthlyEmiBd = if (tenureMonths > 0) {
            pBd.add(flatTotalInterestBd).divide(BigDecimal(tenureMonths), 2, RoundingMode.HALF_UP)
        } else MoneyMath.ZERO

        val reducingMonthlyEmiBd = MoneyMath.standardEmiBd(pBd, rateBd, tenureMonths)
        val reducingTotalInterestBd = MoneyMath.roundPaiseBd(
            reducingMonthlyEmiBd.multiply(BigDecimal(tenureMonths)).subtract(pBd)
        ).max(MoneyMath.ZERO)
        val extraCostBd = MoneyMath.roundPaiseBd(
            flatTotalInterestBd.subtract(reducingTotalInterestBd)
        ).max(MoneyMath.ZERO)

        val earPercent = solveEffectiveAnnualRate(principal, flatMonthlyEmiBd.toDouble(), tenureMonths)

        return FlatVsReducingComparison(
            quotedRate = quotedAnnualRate,
            flatMonthlyEmi = flatMonthlyEmiBd.toDouble(),
            reducingMonthlyEmi = reducingMonthlyEmiBd.toDouble(),
            flatTotalInterest = flatTotalInterestBd.toDouble(),
            reducingTotalInterest = reducingTotalInterestBd.toDouble(),
            extraInterestCost = extraCostBd.toDouble(),
            effectiveAnnualRatePercent = MoneyMath.roundPaise(earPercent)
        )
    }

    fun solveEffectiveAnnualRate(principal: Double, monthlyPayment: Double, months: Int): Double {
        if (principal <= 0.0 || months <= 0 || monthlyPayment * months <= principal) return 0.0
        var low = 0.0
        var high = 1.0
        repeat(60) {
            val mid = (low + high) / 2.0
            val pv = if (mid < 1e-9) {
                monthlyPayment * months
            } else {
                monthlyPayment * (1.0 - (1.0 + mid).pow(-months)) / mid
            }
            if (pv > principal) low = mid else high = mid
        }
        val monthlyIrr = (low + high) / 2.0
        return monthlyIrr * 12.0 * 100.0
    }

    fun calculateBalanceTransfer(bt: BalanceTransferInput): BalanceTransferResult {
        val pBd = MoneyMath.bd(bt.currentOutstanding)
        val currentEmi = MoneyMath.standardEmiBd(pBd, MoneyMath.bd(bt.currentAnnualRate), bt.remainingMonths)
        val newEmi = MoneyMath.standardEmiBd(pBd, MoneyMath.bd(bt.newAnnualRate), bt.remainingMonths)
        val currentInt = MoneyMath.roundPaiseBd(
            currentEmi.multiply(BigDecimal(bt.remainingMonths)).subtract(pBd)
        ).max(MoneyMath.ZERO)
        val newInt = MoneyMath.roundPaiseBd(
            newEmi.multiply(BigDecimal(bt.remainingMonths)).subtract(pBd)
        ).max(MoneyMath.ZERO)
        val monthlySavings = MoneyMath.roundPaiseBd(currentEmi.subtract(newEmi))
        val feesBd = MoneyMath.roundPaiseBd(MoneyMath.bd(bt.transferFees))
        val netSavings = MoneyMath.roundPaiseBd(currentInt.subtract(newInt).subtract(feesBd))
        val breakEven = if (monthlySavings > BigDecimal.ZERO && feesBd > BigDecimal.ZERO) {
            ceil(feesBd.toDouble() / monthlySavings.toDouble()).toInt()
        } else if (monthlySavings > BigDecimal.ZERO) {
            1
        } else null

        return BalanceTransferResult(
            currentEmi = currentEmi.toDouble(),
            newEmi = newEmi.toDouble(),
            monthlySavings = monthlySavings.toDouble(),
            currentRemainingInterest = currentInt.toDouble(),
            newTotalInterest = newInt.toDouble(),
            transferFees = feesBd.toDouble(),
            netSavings = netSavings.toDouble(),
            breakEvenMonth = breakEven
        )
    }

    fun calculateTopUp(tu: TopUpInput): TopUpResult {
        val existPBd = MoneyMath.bd(tu.existingOutstanding)
        val topUpPBd = MoneyMath.bd(tu.topUpAmount)
        val existingEmi = MoneyMath.standardEmiBd(existPBd, MoneyMath.bd(tu.existingAnnualRate), tu.existingRemainingMonths)
        val topUpEmi = MoneyMath.standardEmiBd(topUpPBd, MoneyMath.bd(tu.topUpAnnualRate), tu.topUpTenureMonths)
        val blendedEmi = MoneyMath.roundPaiseBd(existingEmi.add(topUpEmi))
        val existingInt = MoneyMath.roundPaiseBd(
            existingEmi.multiply(BigDecimal(tu.existingRemainingMonths)).subtract(existPBd)
        ).max(MoneyMath.ZERO)
        val topUpInt = MoneyMath.roundPaiseBd(
            topUpEmi.multiply(BigDecimal(tu.topUpTenureMonths)).subtract(topUpPBd)
        ).max(MoneyMath.ZERO)

        return TopUpResult(
            existingEmi = existingEmi.toDouble(),
            topUpEmi = topUpEmi.toDouble(),
            blendedEmi = blendedEmi.toDouble(),
            combinedPrincipal = MoneyMath.roundPaiseBd(existPBd.add(topUpPBd)).toDouble(),
            combinedTotalInterest = MoneyMath.roundPaiseBd(existingInt.add(topUpInt)).toDouble()
        )
    }

    fun calculateTaxBenefit(
        input: CalculationInput,
        yearlySummaries: List<YearlySummary>
    ): TaxBenefitSummary? {
        if (yearlySummaries.isEmpty()) return null
        val bracket = (input.taxBracketPercent / 100.0).coerceIn(0.0, 0.45)

        return when (input.categoryId) {
            "home" -> {
                val cap24b = input.section24bCap.coerceAtLeast(0.0)
                val cap80c = input.section80cCap.coerceAtLeast(0.0)
                val firstYr = yearlySummaries.first()
                val yr1IntEligible = min(firstYr.interestPaid, cap24b)
                val yr1PrinEligible = min(firstYr.principalPaid, cap80c)
                val yr1Saved = MoneyMath.roundPaise((yr1IntEligible + yr1PrinEligible) * bracket)

                val totalSaved = MoneyMath.roundPaise(
                    yearlySummaries.sumOf { yr ->
                        (min(yr.interestPaid, cap24b) + min(yr.principalPaid, cap80c)) * bracket
                    }
                )
                TaxBenefitSummary(
                    sectionTitle = "Sec 24(b) & 80C (Old Tax Regime)",
                    firstYearInterestEligible = MoneyMath.roundPaise(yr1IntEligible),
                    firstYearPrincipalEligible = MoneyMath.roundPaise(yr1PrinEligible),
                    estimatedAnnualTaxSaved = yr1Saved,
                    estimatedTotalTaxSaved = totalSaved,
                    notes = "Old Regime self-occupied caps: Interest up to ${CurrencyFormatter.format(cap24b)} (24b) + Principal up to ${CurrencyFormatter.format(cap80c)} (80C). Not available under New Regime for self-occupied property."
                )
            }
            "education" -> {
                val maxYears = max(1, input.section80eMaxYears)
                val eligibleYears = yearlySummaries.take(maxYears)
                val yr1Int = eligibleYears.firstOrNull()?.interestPaid ?: 0.0
                val yr1Saved = MoneyMath.roundPaise(yr1Int * bracket)
                val totalSaved = MoneyMath.roundPaise(eligibleYears.sumOf { it.interestPaid * bracket })
                TaxBenefitSummary(
                    sectionTitle = "Sec 80E Interest Deduction (Old Regime)",
                    firstYearInterestEligible = MoneyMath.roundPaise(yr1Int),
                    firstYearPrincipalEligible = 0.0,
                    estimatedAnnualTaxSaved = yr1Saved,
                    estimatedTotalTaxSaved = totalSaved,
                    notes = "100% of education loan interest paid is deductible with no upper monetary cap for up to $maxYears consecutive assessment years under the Old Tax Regime."
                )
            }
            else -> null
        }
    }
}
