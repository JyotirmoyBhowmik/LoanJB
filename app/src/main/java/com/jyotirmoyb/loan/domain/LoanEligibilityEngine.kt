package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import java.math.BigDecimal
import java.math.RoundingMode

enum class CibilBand(val id: String, val displayLabel: String, val rateDeltaPercent: Double, val foirFactor: Double) {
    EXCELLENT_750_PLUS("750_plus", "750+ (Excellent)", 0.0, 1.00),
    GOOD_700_749("700_749", "700–749 (Good)", 0.35, 0.95),
    FAIR_650_699("650_699", "650–699 (Fair)", 1.00, 0.85),
    BELOW_650("below_650", "< 650 (Needs Work)", 2.25, 0.70)
}

enum class AffordabilityZone {
    COMFORTABLE,
    STRETCH,
    RISKY
}

data class FoirConfig(
    val lowIncomeFoirPercent: Double,
    val midIncomeFoirPercent: Double,
    val highIncomeFoirPercent: Double,
    val lowIncomeThreshold: Double,
    val highIncomeThreshold: Double
) {
    fun defaultFoirForIncome(monthlyIncome: Double): Double = when {
        monthlyIncome < lowIncomeThreshold -> lowIncomeFoirPercent
        monthlyIncome >= highIncomeThreshold -> highIncomeFoirPercent
        else -> midIncomeFoirPercent
    }

    fun sourceKeyForIncome(monthlyIncome: Double): String = when {
        monthlyIncome < lowIncomeThreshold -> ConfigKeys.FOIR_LOW_PERCENT
        monthlyIncome >= highIncomeThreshold -> ConfigKeys.FOIR_HIGH_PERCENT
        else -> ConfigKeys.FOIR_MID_PERCENT
    }

    companion object {
        fun fromResolvedConfig(resolved: Map<String, ResolvedConfigItem>): FoirConfig = FoirConfig(
            lowIncomeFoirPercent = DataSourceResolver.resolveDouble(resolved, ConfigKeys.FOIR_LOW_PERCENT, 40.0),
            midIncomeFoirPercent = DataSourceResolver.resolveDouble(resolved, ConfigKeys.FOIR_MID_PERCENT, 50.0),
            highIncomeFoirPercent = DataSourceResolver.resolveDouble(resolved, ConfigKeys.FOIR_HIGH_PERCENT, 55.0),
            lowIncomeThreshold = DataSourceResolver.resolveDouble(resolved, ConfigKeys.FOIR_LOW_THRESHOLD, 50000.0),
            highIncomeThreshold = DataSourceResolver.resolveDouble(resolved, ConfigKeys.FOIR_HIGH_THRESHOLD, 150000.0)
        )
    }
}

data class EligibilityForwardInput(
    val monthlyIncome: Double = 120000.0,
    val existingEmis: Double = 15000.0,
    val ageYears: Int = 32,
    val retirementAgeYears: Int = 60,
    val baseAnnualRatePercent: Double = 8.5,
    val desiredTenureYears: Int = 20,
    val customFoirPercent: Double? = null,
    val cibilBand: CibilBand = CibilBand.EXCELLENT_750_PLUS
)

data class EligibilityReverseInput(
    val affordableMonthlyEmi: Double = 45000.0,
    val annualRatePercent: Double = 8.5,
    val tenureMonths: Int = 240,
    val monthlyIncome: Double = 120000.0,
    val existingEmis: Double = 15000.0,
    val cibilBand: CibilBand = CibilBand.EXCELLENT_750_PLUS
)

data class EligibilityResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val isReverseMode: Boolean = false,
    val maxEmi: Double = 0.0,
    val maxLoanAmount: Double = 0.0,
    val effectiveAnnualRatePercent: Double = 0.0,
    val appliedTenureMonths: Int = 0,
    val appliedFoirPercent: Double = 0.0,
    val totalEmiToIncomePercent: Double = 0.0,
    val remainingDisposableIncome: Double = 0.0,
    val zone: AffordabilityZone = AffordabilityZone.COMFORTABLE,
    val zoneTitle: String = "",
    val zoneSummary: String = ""
)

/**
 * Pure domain engine for Indian Loan Eligibility & Affordability (Forward & Reverse modes).
 * Uses exact BigDecimal inverse-EMI math and data-driven FOIR slabs by income band.
 */
object LoanEligibilityEngine {

    /**
     * Exact BigDecimal inverse EMI formula:
     *   P = EMI * ((1 + r)^n - 1) / (r * (1 + r)^n)
     */
    fun maxPrincipalForEmiBd(monthlyEmi: BigDecimal, annualRatePercent: BigDecimal, tenureMonths: Int): BigDecimal {
        if (monthlyEmi <= BigDecimal.ZERO || tenureMonths <= 0) return MoneyMath.ZERO
        if (annualRatePercent <= BigDecimal.ZERO) {
            return MoneyMath.roundPaiseBd(monthlyEmi.multiply(BigDecimal(tenureMonths), MoneyMath.MC))
        }
        val r = annualRatePercent.divide(MoneyMath.TWELVE_HUNDRED, MoneyMath.MC)
        val onePlusR = MoneyMath.ONE.add(r, MoneyMath.MC)
        val factor = onePlusR.pow(tenureMonths, MoneyMath.MC)
        val numerator = monthlyEmi.multiply(factor.subtract(MoneyMath.ONE, MoneyMath.MC), MoneyMath.MC)
        val denominator = r.multiply(factor, MoneyMath.MC)
        if (denominator <= BigDecimal.ZERO) return MoneyMath.ZERO
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP)
    }

    fun calculateForward(input: EligibilityForwardInput, foirConfig: FoirConfig): EligibilityResult {
        val errors = mutableListOf<String>()
        if (input.monthlyIncome <= 0.0) errors.add("Monthly income must be greater than 0.")
        if (input.existingEmis < 0.0) errors.add("Existing EMIs cannot be negative.")
        if (input.ageYears < 18 || input.ageYears > 75) errors.add("Age must be between 18 and 75.")
        if (input.retirementAgeYears <= input.ageYears) errors.add("Retirement age must be greater than current age.")
        if (input.baseAnnualRatePercent <= 0.0 || input.baseAnnualRatePercent > 40.0) {
            errors.add("Interest rate must be between 0.1% and 40%.")
        }
        if (input.desiredTenureYears <= 0 || input.desiredTenureYears > 35) {
            errors.add("Desired tenure must be between 1 and 35 years.")
        }
        if (errors.isNotEmpty()) {
            return EligibilityResult(isValid = false, validationErrors = errors, isReverseMode = false)
        }

        val maxYearsByAge = (input.retirementAgeYears - input.ageYears).coerceIn(1, 30)
        val appliedYears = minOf(input.desiredTenureYears, maxYearsByAge)
        val appliedMonths = appliedYears * 12

        val baseFoir = (input.customFoirPercent ?: foirConfig.defaultFoirForIncome(input.monthlyIncome))
            .coerceIn(10.0, 80.0)
        val effectiveFoir = MoneyMath.roundPaise(baseFoir * input.cibilBand.foirFactor)
        val effectiveRate = MoneyMath.roundPaise(input.baseAnnualRatePercent + input.cibilBand.rateDeltaPercent)

        val incomeBd = MoneyMath.bd(input.monthlyIncome)
        val existingBd = MoneyMath.bd(input.existingEmis)
        val maxTotalObligationBd = MoneyMath.percentageOf(incomeBd, MoneyMath.bd(effectiveFoir))
        val availableEmiBd = maxTotalObligationBd.subtract(existingBd).max(BigDecimal.ZERO)
        val maxLoanBd = maxPrincipalForEmiBd(availableEmiBd, MoneyMath.bd(effectiveRate), appliedMonths)

        val totalObligationAfterBd = existingBd.add(availableEmiBd)
        val emiToIncomePct = if (incomeBd > BigDecimal.ZERO) {
            totalObligationAfterBd.multiply(MoneyMath.HUNDRED, MoneyMath.MC)
                .divide(incomeBd, 1, RoundingMode.HALF_UP)
                .toDouble()
        } else {
            0.0
        }
        val disposableBd = incomeBd.subtract(totalObligationAfterBd).max(BigDecimal.ZERO)

        val (zone, zoneTitle, zoneSummary) = classifyZone(
            emiToIncomePct = emiToIncomePct,
            availableEmi = availableEmiBd.toDouble(),
            cibilBand = input.cibilBand
        )

        return EligibilityResult(
            isValid = true,
            isReverseMode = false,
            maxEmi = MoneyMath.roundPaiseBd(availableEmiBd).toDouble(),
            maxLoanAmount = MoneyMath.roundPaiseBd(maxLoanBd).toDouble(),
            effectiveAnnualRatePercent = effectiveRate,
            appliedTenureMonths = appliedMonths,
            appliedFoirPercent = effectiveFoir,
            totalEmiToIncomePercent = emiToIncomePct,
            remainingDisposableIncome = MoneyMath.roundPaiseBd(disposableBd).toDouble(),
            zone = zone,
            zoneTitle = zoneTitle,
            zoneSummary = zoneSummary
        )
    }

    fun calculateReverse(input: EligibilityReverseInput): EligibilityResult {
        val errors = mutableListOf<String>()
        if (input.affordableMonthlyEmi <= 0.0) errors.add("Monthly EMI budget must be greater than 0.")
        if (input.annualRatePercent <= 0.0 || input.annualRatePercent > 40.0) {
            errors.add("Interest rate must be between 0.1% and 40%.")
        }
        if (input.tenureMonths <= 0 || input.tenureMonths > 420) {
            errors.add("Tenure must be between 1 and 420 months.")
        }
        if (errors.isNotEmpty()) {
            return EligibilityResult(isValid = false, validationErrors = errors, isReverseMode = true)
        }

        val effectiveRate = MoneyMath.roundPaise(input.annualRatePercent + input.cibilBand.rateDeltaPercent)
        val emiBd = MoneyMath.bd(input.affordableMonthlyEmi)
        val maxLoanBd = maxPrincipalForEmiBd(emiBd, MoneyMath.bd(effectiveRate), input.tenureMonths)

        val incomeBd = MoneyMath.bd(input.monthlyIncome.coerceAtLeast(0.0))
        val existingBd = MoneyMath.bd(input.existingEmis.coerceAtLeast(0.0))
        val totalObligationBd = existingBd.add(emiBd)
        val emiToIncomePct = if (incomeBd > BigDecimal.ZERO) {
            totalObligationBd.multiply(MoneyMath.HUNDRED, MoneyMath.MC)
                .divide(incomeBd, 1, RoundingMode.HALF_UP)
                .toDouble()
        } else {
            0.0
        }
        val disposableBd = if (incomeBd > BigDecimal.ZERO) {
            incomeBd.subtract(totalObligationBd).max(BigDecimal.ZERO)
        } else {
            BigDecimal.ZERO
        }

        val (zone, zoneTitle, zoneSummary) = classifyZone(
            emiToIncomePct = emiToIncomePct,
            availableEmi = emiBd.toDouble(),
            cibilBand = input.cibilBand,
            hasIncomeContext = input.monthlyIncome > 0.0
        )

        return EligibilityResult(
            isValid = true,
            isReverseMode = true,
            maxEmi = MoneyMath.roundPaiseBd(emiBd).toDouble(),
            maxLoanAmount = MoneyMath.roundPaiseBd(maxLoanBd).toDouble(),
            effectiveAnnualRatePercent = effectiveRate,
            appliedTenureMonths = input.tenureMonths,
            appliedFoirPercent = emiToIncomePct,
            totalEmiToIncomePercent = emiToIncomePct,
            remainingDisposableIncome = MoneyMath.roundPaiseBd(disposableBd).toDouble(),
            zone = zone,
            zoneTitle = zoneTitle,
            zoneSummary = zoneSummary
        )
    }

    private fun classifyZone(
        emiToIncomePct: Double,
        availableEmi: Double,
        cibilBand: CibilBand,
        hasIncomeContext: Boolean = true
    ): Triple<AffordabilityZone, String, String> {
        if (availableEmi <= 0.0) {
            return Triple(
                AffordabilityZone.RISKY,
                "Risky",
                "Existing EMIs already exceed your FOIR threshold. Reduce current debt before borrowing."
            )
        }
        if (cibilBand == CibilBand.BELOW_650) {
            return Triple(
                AffordabilityZone.RISKY,
                "Risky",
                "CIBIL score below 650 attracts higher rates and stricter lender approval. Improve score first."
            )
        }
        if (!hasIncomeContext) {
            return Triple(
                AffordabilityZone.COMFORTABLE,
                "Comfortable",
                "Enter your monthly income to measure your EMI-to-income comfort level."
            )
        }
        return when {
            emiToIncomePct <= 40.0 -> Triple(
                AffordabilityZone.COMFORTABLE,
                "Comfortable",
                "Total EMIs stay within 40% of income — healthy monthly cushion for savings and emergencies."
            )
            emiToIncomePct <= 52.0 -> Triple(
                AffordabilityZone.STRETCH,
                "Stretch",
                "Total EMIs sit between 40% and 52% of income — manageable, but keep a 6-month emergency fund."
            )
            else -> Triple(
                AffordabilityZone.RISKY,
                "Risky",
                "Total EMIs exceed 52% of income — cash flow is tight and vulnerable to floating rate hikes."
            )
        }
    }
}
