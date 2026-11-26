package com.jyotirmoyb.loan.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

data class MfYearRow(
    val yearIndex: Int,
    val fyLabel: String,
    val annualContribution: Double,
    val totalInvestedToDate: Double,
    val estimatedReturnsToDate: Double,
    val closingValue: Double,
    val inflationAdjustedValue: Double
)

data class SipInput(
    val monthlySip: Double = 10000.0,
    val expectedReturnPercent: Double = 12.0,
    val tenureYears: Int = 10,
    val annualStepUpPercent: Double = 0.0,
    val inflationPercent: Double = 6.0,
    val startYear: Int = 2026,
    val startMonth: Int = 11
)

data class SipResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val totalInvested: Double = 0.0,
    val estimatedReturns: Double = 0.0,
    val maturityAmount: Double = 0.0,
    val inflationAdjustedValue: Double = 0.0,
    val realGain: Double = 0.0,
    val finalMonthlySip: Double = 0.0,
    val yearlyRows: List<MfYearRow> = emptyList()
)

data class LumpsumInput(
    val principal: Double = 100000.0,
    val expectedReturnPercent: Double = 12.0,
    val tenureYears: Int = 10,
    val inflationPercent: Double = 6.0,
    val startYear: Int = 2026,
    val startMonth: Int = 11
)

data class LumpsumResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val investedAmount: Double = 0.0,
    val estimatedReturns: Double = 0.0,
    val maturityAmount: Double = 0.0,
    val inflationAdjustedValue: Double = 0.0,
    val yearlyRows: List<MfYearRow> = emptyList()
)

data class GoalSipInput(
    val targetCorpusToday: Double = 10000000.0, // ₹1 Cr
    val years: Int = 15,
    val expectedReturnPercent: Double = 12.0,
    val inflationPercent: Double = 6.0,
    val adjustTargetForInflation: Boolean = true,
    val annualStepUpPercent: Double = 0.0,
    val startYear: Int = 2026,
    val startMonth: Int = 11
)

data class GoalSipResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val nominalTargetCorpus: Double = 0.0,
    val requiredMonthlySip: Double = 0.0,
    val totalInvested: Double = 0.0,
    val estimatedReturns: Double = 0.0,
    val projectedCorpus: Double = 0.0,
    val yearlyRows: List<MfYearRow> = emptyList()
)

/**
 * Pure BigDecimal domain engines for Mutual Fund SIP (with optional annual Step-Up & inflation),
 * Lumpsum compounding, and Goal-based reverse SIP planning.
 */
object SipLumpsumEngine {

    fun calculateSip(input: SipInput): SipResult {
        val errors = mutableListOf<String>()
        if (input.monthlySip <= 0.0) errors.add("Monthly SIP must be greater than 0.")
        if (input.expectedReturnPercent < 0.0 || input.expectedReturnPercent > 50.0) {
            errors.add("Expected return must be between 0% and 50%.")
        }
        if (input.tenureYears !in 1..50) errors.add("Tenure must be between 1 and 50 years.")
        if (input.annualStepUpPercent < 0.0 || input.annualStepUpPercent > 50.0) {
            errors.add("Annual step-up must be between 0% and 50%.")
        }
        if (input.inflationPercent < 0.0 || input.inflationPercent > 25.0) {
            errors.add("Inflation must be between 0% and 25%.")
        }
        if (errors.isNotEmpty()) return SipResult(isValid = false, validationErrors = errors)

        val monthlyRateBd = MoneyMath.bd(input.expectedReturnPercent)
            .divide(MoneyMath.TWELVE_HUNDRED, MoneyMath.MC)
        val stepUpFactorBd = MoneyMath.ONE.add(
            MoneyMath.bd(input.annualStepUpPercent).divide(MoneyMath.HUNDRED, MoneyMath.MC),
            MoneyMath.MC
        )
        val inflationFactor = 1.0 + input.inflationPercent / 100.0

        var corpusBd = MoneyMath.ZERO
        var totalInvestedBd = MoneyMath.ZERO
        var currentMonthlySipBd = MoneyMath.bd(input.monthlySip)
        val rows = mutableListOf<MfYearRow>()

        for (yr in 1..input.tenureYears) {
            if (yr > 1 && input.annualStepUpPercent > 0.0) {
                currentMonthlySipBd = MoneyMath.roundPaiseBd(
                    currentMonthlySipBd.multiply(stepUpFactorBd, MoneyMath.MC)
                )
            }
            var yearContribBd = MoneyMath.ZERO
            repeat(12) {
                corpusBd = corpusBd.add(currentMonthlySipBd)
                yearContribBd = yearContribBd.add(currentMonthlySipBd)
                totalInvestedBd = totalInvestedBd.add(currentMonthlySipBd)
                if (monthlyRateBd > BigDecimal.ZERO) {
                    val interest = corpusBd.multiply(monthlyRateBd, MoneyMath.MC)
                    corpusBd = corpusBd.add(interest)
                }
            }
            val closingRounded = MoneyMath.roundPaiseBd(corpusBd)
            val investedRounded = MoneyMath.roundPaiseBd(totalInvestedBd)
            val returnsRounded = MoneyMath.roundPaiseBd(closingRounded.subtract(investedRounded).max(BigDecimal.ZERO))
            val realDivisor = MoneyMath.bd(inflationFactor.pow(yr))
            val realValue = closingRounded.divide(realDivisor, 2, RoundingMode.HALF_UP)

            rows.add(
                MfYearRow(
                    yearIndex = yr,
                    fyLabel = MoneyMath.financialYearLabel(input.startYear + yr - 1, input.startMonth),
                    annualContribution = MoneyMath.roundPaiseBd(yearContribBd).toDouble(),
                    totalInvestedToDate = investedRounded.toDouble(),
                    estimatedReturnsToDate = returnsRounded.toDouble(),
                    closingValue = closingRounded.toDouble(),
                    inflationAdjustedValue = realValue.toDouble()
                )
            )
        }

        val finalMaturity = rows.last().closingValue
        val finalInvested = rows.last().totalInvestedToDate
        val finalReturns = rows.last().estimatedReturnsToDate
        val finalReal = rows.last().inflationAdjustedValue

        return SipResult(
            isValid = true,
            totalInvested = finalInvested,
            estimatedReturns = finalReturns,
            maturityAmount = finalMaturity,
            inflationAdjustedValue = finalReal,
            realGain = MoneyMath.roundPaise(finalReal - finalInvested),
            finalMonthlySip = currentMonthlySipBd.toDouble(),
            yearlyRows = rows
        )
    }

    fun calculateLumpsum(input: LumpsumInput): LumpsumResult {
        val errors = mutableListOf<String>()
        if (input.principal <= 0.0) errors.add("Investment amount must be greater than 0.")
        if (input.expectedReturnPercent < 0.0 || input.expectedReturnPercent > 50.0) {
            errors.add("Expected return must be between 0% and 50%.")
        }
        if (input.tenureYears !in 1..50) errors.add("Tenure must be between 1 and 50 years.")
        if (errors.isNotEmpty()) return LumpsumResult(isValid = false, validationErrors = errors)

        val principalBd = MoneyMath.bd(input.principal)
        val annualGrowthBd = MoneyMath.ONE.add(
            MoneyMath.bd(input.expectedReturnPercent).divide(MoneyMath.HUNDRED, MoneyMath.MC),
            MoneyMath.MC
        )
        val inflationFactor = 1.0 + input.inflationPercent / 100.0

        val rows = mutableListOf<MfYearRow>()
        for (yr in 1..input.tenureYears) {
            val factor = annualGrowthBd.pow(yr, MoneyMath.MC)
            val closing = MoneyMath.roundPaiseBd(principalBd.multiply(factor, MoneyMath.MC))
            val returns = MoneyMath.roundPaiseBd(closing.subtract(principalBd).max(BigDecimal.ZERO))
            val realValue = closing.divide(MoneyMath.bd(inflationFactor.pow(yr)), 2, RoundingMode.HALF_UP)

            rows.add(
                MfYearRow(
                    yearIndex = yr,
                    fyLabel = MoneyMath.financialYearLabel(input.startYear + yr - 1, input.startMonth),
                    annualContribution = if (yr == 1) principalBd.toDouble() else 0.0,
                    totalInvestedToDate = principalBd.toDouble(),
                    estimatedReturnsToDate = returns.toDouble(),
                    closingValue = closing.toDouble(),
                    inflationAdjustedValue = realValue.toDouble()
                )
            )
        }

        val last = rows.last()
        return LumpsumResult(
            isValid = true,
            investedAmount = principalBd.toDouble(),
            estimatedReturns = last.estimatedReturnsToDate,
            maturityAmount = last.closingValue,
            inflationAdjustedValue = last.inflationAdjustedValue,
            yearlyRows = rows
        )
    }

    fun calculateGoalSip(input: GoalSipInput): GoalSipResult {
        val errors = mutableListOf<String>()
        if (input.targetCorpusToday <= 0.0) errors.add("Target corpus must be greater than 0.")
        if (input.years !in 1..50) errors.add("Time horizon must be between 1 and 50 years.")
        if (input.expectedReturnPercent < 0.0 || input.expectedReturnPercent > 50.0) {
            errors.add("Expected return must be between 0% and 50%.")
        }
        if (errors.isNotEmpty()) return GoalSipResult(isValid = false, validationErrors = errors)

        val nominalTarget = if (input.adjustTargetForInflation && input.inflationPercent > 0.0) {
            MoneyMath.roundPaise(input.targetCorpusToday * (1.0 + input.inflationPercent / 100.0).pow(input.years))
        } else {
            MoneyMath.roundPaise(input.targetCorpusToday)
        }

        // Since SIP maturity is strictly linear in initial monthlySip, evaluate unit SIP = ₹1,000
        val unitSip = 1000.0
        val unitResult = calculateSip(
            SipInput(
                monthlySip = unitSip,
                expectedReturnPercent = input.expectedReturnPercent,
                tenureYears = input.years,
                annualStepUpPercent = input.annualStepUpPercent,
                inflationPercent = input.inflationPercent,
                startYear = input.startYear,
                startMonth = input.startMonth
            )
        )
        val requiredSipRaw = if (unitResult.maturityAmount > 0.0) {
            (nominalTarget / unitResult.maturityAmount) * unitSip
        } else {
            nominalTarget / (input.years * 12.0)
        }
        val requiredSip = MoneyMath.roundPaise(requiredSipRaw)

        val projected = calculateSip(
            SipInput(
                monthlySip = requiredSip,
                expectedReturnPercent = input.expectedReturnPercent,
                tenureYears = input.years,
                annualStepUpPercent = input.annualStepUpPercent,
                inflationPercent = input.inflationPercent,
                startYear = input.startYear,
                startMonth = input.startMonth
            )
        )

        return GoalSipResult(
            isValid = true,
            nominalTargetCorpus = nominalTarget,
            requiredMonthlySip = requiredSip,
            totalInvested = projected.totalInvested,
            estimatedReturns = projected.estimatedReturns,
            projectedCorpus = projected.maturityAmount,
            yearlyRows = projected.yearlyRows
        )
    }
}
