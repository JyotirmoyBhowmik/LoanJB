package com.jyotirmoyb.loan.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

data class DepositComparisonInput(
    val monthlyContribution: Double = 10000.0, // Equivalent annual = 12 * monthly, or lump-sum = total invested over horizon
    val horizonYears: Int = 15,
    val taxSlabPercent: Double = 30.0,
    val cessPercent: Double = 4.0,
    val fdAnnualRatePercent: Double = 7.0,
    val rdAnnualRatePercent: Double = 6.75,
    val ppfAnnualRatePercent: Double = 7.1,
    val ppfYearlyCap: Double = 150000.0,
    val debtFundExpectedReturnPercent: Double = 7.25
)

data class DepositInstrumentResult(
    val id: String,
    val name: String,
    val rateLabel: String,
    val taxRuleSummary: String,
    val totalInvested: Double,
    val preTaxMaturity: Double,
    val estimatedTax: Double,
    val postTaxMaturity: Double,
    val postTaxGain: Double,
    val postTaxCagrPercent: Double,
    val isWinner: Boolean = false
)

data class DepositComparisonReport(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val instruments: List<DepositInstrumentResult> = emptyList(),
    val winnerName: String = "",
    val taxAdvantageVsFd: Double = 0.0
)

/**
 * Pure domain engine comparing FD vs RD vs PPF vs Debt Mutual Fund on an apples-to-apples
 * recurring investment basis for the user's income tax slab (including cess).
 *
 * Key India tax realism:
 * - FD & RD: interest is taxed every financial year at the user's slab (+ cess) -> annual tax drag.
 * - PPF: EEE (Exempt-Exempt-Exempt), 0% tax on interest and maturity (respects yearly cap).
 * - Debt Mutual Fund (post-Apr 2023): gains taxed at slab (+ cess) ONLY on redemption -> tax-deferred compounding!
 */
object DepositComparisonEngine {

    fun compare(input: DepositComparisonInput): DepositComparisonReport {
        val errors = mutableListOf<String>()
        if (input.monthlyContribution <= 0.0) errors.add("Monthly investment must be greater than 0.")
        if (input.horizonYears < 1 || input.horizonYears > 30) errors.add("Horizon must be between 1 and 30 years.")
        if (errors.isNotEmpty()) {
            return DepositComparisonReport(isValid = false, validationErrors = errors)
        }

        val effectiveTaxRate = (input.taxSlabPercent.coerceIn(0.0, 45.0) * (1.0 + input.cessPercent / 100.0)) / 100.0
        val annualInvest = input.monthlyContribution * 12.0
        val totalInvested = MoneyMath.roundPaise(annualInvest * input.horizonYears)
        val totalMonths = input.horizonYears * 12

        // 1. Bank FD (Annual ladder / yearly reinvested FD with quarterly compounding and annual tax drag)
        val fdEar = (1.0 + input.fdAnnualRatePercent / 400.0).pow(4) - 1.0
        val fdPreTax = compoundAnnualContributions(annualInvest, fdEar, input.horizonYears, annualTaxRate = 0.0)
        val fdPostTax = compoundAnnualContributions(annualInvest, fdEar, input.horizonYears, annualTaxRate = effectiveTaxRate)
        val fdTax = MoneyMath.roundPaise((fdPreTax - fdPostTax).coerceAtLeast(0.0))

        // 2. Bank RD (Monthly instalments, quarterly compounding, annual tax drag on yearly interest)
        val rdRes = RecurringDepositEngine.calculate(
            RecurringDepositInput(
                monthlyDeposit = input.monthlyContribution,
                annualRatePercent = input.rdAnnualRatePercent,
                tenureMonths = totalMonths,
                taxSlabPercent = input.taxSlabPercent
            ),
            TdsConfig(50000.0, 100000.0, 10.0, 20.0, input.cessPercent)
        )
        val rdPreTax = rdRes.maturityAmount
        val rdPostTax = rdRes.postTaxMaturityAmount
        val rdTax = rdRes.totalSlabTax

        // 3. PPF (Annual compounding, EEE tax-exempt; if annualInvest > cap, excess earns 0 or capped cleanly)
        val ppfAnnualCapped = minOf(annualInvest, input.ppfYearlyCap)
        val ppfTotalInvested = MoneyMath.roundPaise(ppfAnnualCapped * input.horizonYears)
        val ppfPreTax = compoundAnnualContributions(ppfAnnualCapped, input.ppfAnnualRatePercent / 100.0, input.horizonYears, annualTaxRate = 0.0)
        val ppfPostTax = ppfPreTax // EEE

        // 4. Debt Mutual Fund (Monthly SIP compounding, taxed at slab ONLY at final redemption)
        val debtPreTax = compoundMonthlySip(input.monthlyContribution, input.debtFundExpectedReturnPercent, totalMonths)
        val debtGain = (debtPreTax - totalInvested).coerceAtLeast(0.0)
        val debtTax = MoneyMath.roundPaise(debtGain * effectiveTaxRate)
        val debtPostTax = MoneyMath.roundPaise(debtPreTax - debtTax)

        val rawList = listOf(
            DepositInstrumentResult(
                id = "fd",
                name = "Fixed Deposit (FD)",
                rateLabel = "${CurrencyFormatter.formatPercent(input.fdAnnualRatePercent)} (Qtrly)",
                taxRuleSummary = "Interest taxed every year at slab",
                totalInvested = totalInvested,
                preTaxMaturity = fdPreTax,
                estimatedTax = fdTax,
                postTaxMaturity = fdPostTax,
                postTaxGain = MoneyMath.roundPaise(fdPostTax - totalInvested),
                postTaxCagrPercent = effectiveSipAnnualizedReturn(totalInvested, fdPostTax, input.horizonYears)
            ),
            DepositInstrumentResult(
                id = "rd",
                name = "Recurring Deposit (RD)",
                rateLabel = "${CurrencyFormatter.formatPercent(input.rdAnnualRatePercent)} (Qtrly)",
                taxRuleSummary = "Interest taxed at slab rate",
                totalInvested = totalInvested,
                preTaxMaturity = rdPreTax,
                estimatedTax = rdTax,
                postTaxMaturity = rdPostTax,
                postTaxGain = MoneyMath.roundPaise(rdPostTax - totalInvested),
                postTaxCagrPercent = effectiveSipAnnualizedReturn(totalInvested, rdPostTax, input.horizonYears)
            ),
            DepositInstrumentResult(
                id = "ppf",
                name = "PPF (Small Savings)",
                rateLabel = "${CurrencyFormatter.formatPercent(input.ppfAnnualRatePercent)} (EEE)",
                taxRuleSummary = if (annualInvest > input.ppfYearlyCap) {
                    "Tax-free EEE (capped at ${CurrencyFormatter.formatCompact(input.ppfYearlyCap, CurrencyCode.INR)}/yr)"
                } else {
                    "100% tax-free (EEE)"
                },
                totalInvested = ppfTotalInvested,
                preTaxMaturity = ppfPreTax,
                estimatedTax = 0.0,
                postTaxMaturity = ppfPostTax,
                postTaxGain = MoneyMath.roundPaise(ppfPostTax - ppfTotalInvested),
                postTaxCagrPercent = effectiveSipAnnualizedReturn(ppfTotalInvested, ppfPostTax, input.horizonYears)
            ),
            DepositInstrumentResult(
                id = "debt_fund",
                name = "Debt Mutual Fund",
                rateLabel = "${CurrencyFormatter.formatPercent(input.debtFundExpectedReturnPercent)} (Est.)",
                taxRuleSummary = "Tax deferred until redemption (slab)",
                totalInvested = totalInvested,
                preTaxMaturity = debtPreTax,
                estimatedTax = debtTax,
                postTaxMaturity = debtPostTax,
                postTaxGain = MoneyMath.roundPaise(debtPostTax - totalInvested),
                postTaxCagrPercent = effectiveSipAnnualizedReturn(totalInvested, debtPostTax, input.horizonYears)
            )
        )

        // Rank winner by highest Post-Tax Yield % (fair even if PPF hits ₹1.5L cap)
        val winner = rawList.maxByOrNull { it.postTaxCagrPercent }
        val ranked = rawList.map { it.copy(isWinner = it.id == winner?.id) }
        val fdItem = ranked.first { it.id == "fd" }
        val advantageVsFd = winner?.let { MoneyMath.roundPaise((it.postTaxGain - fdItem.postTaxGain).coerceAtLeast(0.0)) } ?: 0.0

        return DepositComparisonReport(
            isValid = true,
            instruments = ranked,
            winnerName = winner?.name.orEmpty(),
            taxAdvantageVsFd = advantageVsFd
        )
    }

    private fun compoundAnnualContributions(
        annualAmount: Double,
        annualRateFraction: Double,
        years: Int,
        annualTaxRate: Double
    ): Double {
        var bal = BigDecimal.ZERO
        val contrib = MoneyMath.bd(annualAmount)
        val netRate = MoneyMath.bd(annualRateFraction * (1.0 - annualTaxRate))
        repeat(years) {
            val base = bal.add(contrib)
            val interest = MoneyMath.roundPaiseBd(base.multiply(netRate, MoneyMath.MC))
            bal = MoneyMath.roundPaiseBd(base.add(interest))
        }
        return bal.toDouble()
    }

    private fun compoundMonthlySip(
        monthlyAmount: Double,
        annualReturnPercent: Double,
        totalMonths: Int
    ): Double {
        val monthlyRate = (1.0 + annualReturnPercent / 100.0).pow(1.0 / 12.0) - 1.0
        var bal = BigDecimal.ZERO
        val contrib = MoneyMath.bd(monthlyAmount)
        val rBd = MoneyMath.bd(monthlyRate)
        repeat(totalMonths) {
            val base = bal.add(contrib)
            val growth = base.multiply(rBd, MoneyMath.MC)
            bal = base.add(growth)
        }
        return MoneyMath.roundPaiseBd(bal).toDouble()
    }

    private fun effectiveSipAnnualizedReturn(
        totalInvested: Double,
        finalValue: Double,
        years: Int
    ): Double {
        if (totalInvested <= 0.0 || finalValue <= totalInvested || years <= 0) return 0.0
        // Average capital duration for regular contributions is ~(years + 1) / 2
        val avgDurationYears = (years + 1.0) / 2.0
        val ratio = finalValue / totalInvested
        val annualized = (ratio.pow(1.0 / avgDurationYears) - 1.0) * 100.0
        return MoneyMath.roundPaise(annualized)
    }
}
