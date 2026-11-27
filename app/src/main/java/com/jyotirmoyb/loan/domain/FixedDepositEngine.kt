package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

enum class CompoundingFrequency(val periodsPerYear: Int, val label: String) {
    MONTHLY(12, "Monthly"),
    QUARTERLY(4, "Quarterly"),
    HALF_YEARLY(2, "Half-Yearly"),
    YEARLY(1, "Yearly")
}

enum class PayoutFrequency(val payoutsPerYear: Int, val label: String) {
    MONTHLY(12, "Monthly"),
    QUARTERLY(4, "Quarterly"),
    HALF_YEARLY(2, "Half-Yearly"),
    YEARLY(1, "Yearly")
}

enum class FdMode {
    CUMULATIVE,
    PAYOUT
}

data class TdsConfig(
    val generalThreshold: Double,
    val seniorThreshold: Double,
    val rateWithPanPercent: Double,
    val rateWithoutPanPercent: Double,
    val cessPercent: Double = 4.0
) {
    fun thresholdFor(isSeniorCitizen: Boolean): Double = if (isSeniorCitizen) seniorThreshold else generalThreshold

    fun tdsRateFor(hasPan: Boolean): Double = if (hasPan) rateWithPanPercent else rateWithoutPanPercent

    companion object {
        fun fromResolvedConfig(resolved: Map<String, ResolvedConfigItem>): TdsConfig = TdsConfig(
            generalThreshold = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TDS_FD_GENERAL_THRESHOLD, 50000.0),
            seniorThreshold = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TDS_FD_SENIOR_THRESHOLD, 100000.0),
            rateWithPanPercent = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TDS_RATE_WITH_PAN, 10.0),
            rateWithoutPanPercent = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TDS_RATE_WITHOUT_PAN, 20.0),
            cessPercent = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_CESS_PERCENT, 4.0)
        )
    }
}

data class FixedDepositInput(
    val principal: Double = 500000.0,
    val annualRatePercent: Double = 7.0,
    val isSeniorCitizen: Boolean = false,
    val seniorBonusPercent: Double = 0.5,
    val tenureMonths: Int = 60,
    val mode: FdMode = FdMode.CUMULATIVE,
    val compounding: CompoundingFrequency = CompoundingFrequency.QUARTERLY,
    val payoutFrequency: PayoutFrequency = PayoutFrequency.QUARTERLY,
    val hasPan: Boolean = true,
    val taxSlabPercent: Double = 30.0,
    val startYear: Int = 2026,
    val startMonth: Int = 11,
    val startDay: Int = 25
)

data class DepositYearRow(
    val yearIndex: Int,
    val fyLabel: String,
    val openingBalance: Double,
    val depositAdded: Double,
    val interestEarned: Double,
    val estimatedTds: Double,
    val closingBalance: Double
)

data class FixedDepositResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val effectiveNominalRatePercent: Double = 0.0,
    val principal: Double = 0.0,
    val maturityAmount: Double = 0.0,
    val totalInterest: Double = 0.0,
    val periodicPayoutAmount: Double = 0.0,
    val totalPayoutCount: Int = 0,
    val totalPayoutReceived: Double = 0.0,
    val appliedTdsThreshold: Double = 0.0,
    val appliedTdsRatePercent: Double = 0.0,
    val totalEstimatedTds: Double = 0.0,
    val totalSlabTax: Double = 0.0,
    val postTaxInterest: Double = 0.0,
    val postTaxMaturityAmount: Double = 0.0,
    val effectivePreTaxYieldPercent: Double = 0.0,
    val effectivePostTaxYieldPercent: Double = 0.0,
    val maturityDateLabel: String = "",
    val yearlyRows: List<DepositYearRow> = emptyList()
)

/**
 * Pure domain engine for Indian Fixed Deposits (Cumulative & Interest Payout)
 * using BigDecimal money math, Sec 194A / 206AA TDS rules from config, and effective yields.
 */
object FixedDepositEngine {

    fun calculate(input: FixedDepositInput, tdsConfig: TdsConfig): FixedDepositResult {
        val errors = mutableListOf<String>()
        if (input.principal <= 0.0) errors.add("Deposit amount must be greater than 0.")
        if (input.annualRatePercent <= 0.0 || input.annualRatePercent > 30.0) {
            errors.add("Interest rate must be between 0.1% and 30%.")
        }
        if (input.tenureMonths <= 0 || input.tenureMonths > 360) {
            errors.add("Tenure must be between 1 and 360 months.")
        }
        if (errors.isNotEmpty()) {
            return FixedDepositResult(isValid = false, validationErrors = errors)
        }

        val effectiveRate = MoneyMath.roundPaise(
            input.annualRatePercent + if (input.isSeniorCitizen) input.seniorBonusPercent else 0.0
        )
        val principalBd = MoneyMath.bd(input.principal)
        val rateBd = MoneyMath.bd(effectiveRate)
        val yearsDouble = input.tenureMonths / 12.0

        val tdsThreshold = tdsConfig.thresholdFor(input.isSeniorCitizen)
        val tdsRatePercent = tdsConfig.tdsRateFor(input.hasPan)
        val tdsThresholdBd = MoneyMath.bd(tdsThreshold)
        val tdsRateBd = MoneyMath.bd(tdsRatePercent)

        val (matYear, matMonth) = PartPaymentPlannerEngine.addMonthsToYearMonth(
            input.startYear,
            input.startMonth,
            input.tenureMonths
        )
        val maturityDateLabel = MoneyMath.formatDateDdMmmYyyy(input.startDay, matMonth, matYear)

        val yearlyRows = mutableListOf<DepositYearRow>()
        var totalInterestBd = MoneyMath.ZERO
        var totalTdsBd = MoneyMath.ZERO
        var maturityBd = principalBd
        var periodicPayoutBd = MoneyMath.ZERO
        var payoutCount = 0

        if (input.mode == FdMode.CUMULATIVE) {
            val n = input.compounding.periodsPerYear
            val nBd = BigDecimal(n)
            val ratePerPeriod = rateBd.divide(MoneyMath.HUNDRED.multiply(nBd, MoneyMath.MC), MoneyMath.MC)

            val fullYears = input.tenureMonths / 12
            val remMonths = input.tenureMonths % 12
            val totalSlices = fullYears + if (remMonths > 0) 1 else 0

            var balanceBd = principalBd
            for (yr in 1..totalSlices) {
                val sliceMonths = if (yr <= fullYears) 12 else remMonths
                val openingBd = balanceBd
                val exactPeriods = (sliceMonths.toDouble() / 12.0) * n
                val wholePeriods = exactPeriods.toInt()
                val fracPeriod = exactPeriods - wholePeriods

                var factor = MoneyMath.ONE.add(ratePerPeriod, MoneyMath.MC).pow(wholePeriods, MoneyMath.MC)
                if (fracPeriod > 1e-9) {
                    val fracFactor = MoneyMath.ONE.add(
                        ratePerPeriod.multiply(MoneyMath.bd(fracPeriod), MoneyMath.MC),
                        MoneyMath.MC
                    )
                    factor = factor.multiply(fracFactor, MoneyMath.MC)
                }

                val closingBd = MoneyMath.roundPaiseBd(openingBd.multiply(factor, MoneyMath.MC))
                val yrInterestBd = MoneyMath.roundPaiseBd(closingBd.subtract(openingBd).max(BigDecimal.ZERO))

                // Sec 194A TDS: if interest in FY exceeds threshold, TDS applies on full FY interest
                val yrTdsBd = if (yrInterestBd > tdsThresholdBd) {
                    MoneyMath.percentageOf(yrInterestBd, tdsRateBd)
                } else {
                    MoneyMath.ZERO
                }

                totalInterestBd = totalInterestBd.add(yrInterestBd)
                totalTdsBd = totalTdsBd.add(yrTdsBd)
                balanceBd = closingBd

                val (sliceY, sliceM) = PartPaymentPlannerEngine.addMonthsToYearMonth(
                    input.startYear,
                    input.startMonth,
                    (yr - 1) * 12
                )
                yearlyRows.add(
                    DepositYearRow(
                        yearIndex = yr,
                        fyLabel = MoneyMath.financialYearLabel(sliceY, sliceM),
                        openingBalance = openingBd.toDouble(),
                        depositAdded = if (yr == 1) principalBd.toDouble() else 0.0,
                        interestEarned = yrInterestBd.toDouble(),
                        estimatedTds = yrTdsBd.toDouble(),
                        closingBalance = closingBd.toDouble()
                    )
                )
            }
            maturityBd = balanceBd
        } else {
            // PAYOUT MODE: principal stays constant and interest is paid out periodically
            val payoutsPerYear = input.payoutFrequency.payoutsPerYear
            val monthsPerPayout = 12 / payoutsPerYear
            payoutCount = (input.tenureMonths / monthsPerPayout).coerceAtLeast(1)

            periodicPayoutBd = principalBd
                .multiply(rateBd, MoneyMath.MC)
                .divide(MoneyMath.HUNDRED.multiply(BigDecimal(payoutsPerYear), MoneyMath.MC), 2, RoundingMode.HALF_UP)

            val fullYears = input.tenureMonths / 12
            val remMonths = input.tenureMonths % 12
            val totalSlices = fullYears + if (remMonths > 0) 1 else 0

            for (yr in 1..totalSlices) {
                val sliceMonths = if (yr <= fullYears) 12 else remMonths
                val yrInterestBd = principalBd
                    .multiply(rateBd, MoneyMath.MC)
                    .multiply(BigDecimal(sliceMonths), MoneyMath.MC)
                    .divide(MoneyMath.TWELVE_HUNDRED, 2, RoundingMode.HALF_UP)

                val yrTdsBd = if (yrInterestBd > tdsThresholdBd) {
                    MoneyMath.percentageOf(yrInterestBd, tdsRateBd)
                } else {
                    MoneyMath.ZERO
                }

                totalInterestBd = totalInterestBd.add(yrInterestBd)
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
                        openingBalance = principalBd.toDouble(),
                        depositAdded = if (yr == 1) principalBd.toDouble() else 0.0,
                        interestEarned = yrInterestBd.toDouble(),
                        estimatedTds = yrTdsBd.toDouble(),
                        closingBalance = principalBd.toDouble()
                    )
                )
            }
            maturityBd = principalBd // Principal returned on maturity; interest paid out along the way
        }

        val totalInterestRounded = MoneyMath.roundPaiseBd(totalInterestBd)
        val totalTdsRounded = MoneyMath.roundPaiseBd(totalTdsBd)

        // Effective slab tax (slab * (1 + cess%))
        val effectiveTaxRate = input.taxSlabPercent.coerceIn(0.0, 45.0) * (1.0 + tdsConfig.cessPercent / 100.0)
        val totalSlabTaxBd = MoneyMath.percentageOf(totalInterestRounded, MoneyMath.bd(effectiveTaxRate))
        val postTaxInterestBd = MoneyMath.roundPaiseBd(totalInterestRounded.subtract(totalSlabTaxBd).max(BigDecimal.ZERO))
        val postTaxMaturityBd = MoneyMath.roundPaiseBd(principalBd.add(postTaxInterestBd))

        val totalPreTaxValue = principalBd.add(totalInterestRounded).toDouble()
        val preTaxYield = annualizedYieldPercent(input.principal, totalPreTaxValue, yearsDouble)
        val postTaxYield = annualizedYieldPercent(input.principal, postTaxMaturityBd.toDouble(), yearsDouble)

        return FixedDepositResult(
            isValid = true,
            effectiveNominalRatePercent = effectiveRate,
            principal = principalBd.toDouble(),
            maturityAmount = maturityBd.toDouble(),
            totalInterest = totalInterestRounded.toDouble(),
            periodicPayoutAmount = periodicPayoutBd.toDouble(),
            totalPayoutCount = payoutCount,
            totalPayoutReceived = if (input.mode == FdMode.PAYOUT) totalInterestRounded.toDouble() else 0.0,
            appliedTdsThreshold = tdsThreshold,
            appliedTdsRatePercent = tdsRatePercent,
            totalEstimatedTds = totalTdsRounded.toDouble(),
            totalSlabTax = totalSlabTaxBd.toDouble(),
            postTaxInterest = postTaxInterestBd.toDouble(),
            postTaxMaturityAmount = postTaxMaturityBd.toDouble(),
            effectivePreTaxYieldPercent = preTaxYield,
            effectivePostTaxYieldPercent = postTaxYield,
            maturityDateLabel = maturityDateLabel,
            yearlyRows = yearlyRows
        )
    }

    fun annualizedYieldPercent(principal: Double, finalAmount: Double, years: Double): Double {
        if (principal <= 0.0 || finalAmount <= principal || years <= 0.0) return 0.0
        val ratio = finalAmount / principal
        val cagr = (ratio.pow(1.0 / years) - 1.0) * 100.0
        return MoneyMath.roundPaise(cagr)
    }
}
