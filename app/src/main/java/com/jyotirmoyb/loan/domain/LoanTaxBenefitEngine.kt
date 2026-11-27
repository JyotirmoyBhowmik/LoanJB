package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import java.math.BigDecimal
import java.math.RoundingMode

enum class PropertyOccupancy {
    SELF_OCCUPIED,
    LET_OUT
}

/**
 * All Indian Income Tax slabs, caps, standard deductions, rebates, cess, and regime applicability
 * flags read strictly from taxslabs.json via DataSourceResolver (zero hardcoded slabs in logic).
 */
data class TaxSlabsConfig(
    val sec24bSelfOccupiedCap: Double,
    val sec24bLetOutLossSetOffCap: Double,
    val sec80cCap: Double,
    val sec80eMaxYears: Int,
    val oldStdDeduction: Double,
    val newStdDeduction: Double,
    val oldRebateLimit: Double,
    val newRebateLimit: Double,
    val cessPercent: Double,
    val oldSlab1Limit: Double, // 0% up to 2.5L
    val oldSlab2Limit: Double, // 5% up to 5L
    val oldSlab3Limit: Double, // 20% up to 10L, 30% above
    val newSlab1Limit: Double, // 0% up to 4L
    val newSlab2Limit: Double, // 5% up to 8L
    val newSlab3Limit: Double, // 10% up to 12L
    val newSlab4Limit: Double, // 15% up to 16L
    val newSlab5Limit: Double, // 20% up to 20L
    val newSlab6Limit: Double, // 25% up to 24L, 30% above
    val newRegimeSec24bSelfOccupiedAllowed: Boolean,
    val newRegimeSec80cAllowed: Boolean,
    val newRegimeSec80eAllowed: Boolean
) {
    companion object {
        fun fromResolvedConfig(resolved: Map<String, ResolvedConfigItem>): TaxSlabsConfig = TaxSlabsConfig(
            sec24bSelfOccupiedCap = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SEC_24B_CAP, 200000.0),
            sec24bLetOutLossSetOffCap = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SEC_24B_LETOUT_SETOFF_CAP, 200000.0),
            sec80cCap = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SEC_80C_CAP, 150000.0),
            sec80eMaxYears = DataSourceResolver.resolveInt(resolved, ConfigKeys.SEC_80E_MAX_YEARS, 8),
            oldStdDeduction = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_OLD_STD_DEDUCTION, 50000.0),
            newStdDeduction = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_NEW_STD_DEDUCTION, 75000.0),
            oldRebateLimit = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_OLD_REBATE_LIMIT, 500000.0),
            newRebateLimit = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_NEW_REBATE_LIMIT, 1200000.0),
            cessPercent = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_CESS_PERCENT, 4.0),
            oldSlab1Limit = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_OLD_SLAB1, 250000.0),
            oldSlab2Limit = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_OLD_SLAB2, 500000.0),
            oldSlab3Limit = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_OLD_SLAB3, 1000000.0),
            newSlab1Limit = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_NEW_SLAB1, 400000.0),
            newSlab2Limit = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_NEW_SLAB2, 800000.0),
            newSlab3Limit = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_NEW_SLAB3, 1200000.0),
            newSlab4Limit = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_NEW_SLAB4, 1600000.0),
            newSlab5Limit = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_NEW_SLAB5, 2000000.0),
            newSlab6Limit = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_NEW_SLAB6, 2400000.0),
            newRegimeSec24bSelfOccupiedAllowed = DataSourceResolver.resolveDouble(
                resolved,
                ConfigKeys.TAX_NEW_SEC24B_SO_ALLOWED,
                0.0
            ) > 0.5,
            newRegimeSec80cAllowed = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_NEW_SEC80C_ALLOWED, 0.0) > 0.5,
            newRegimeSec80eAllowed = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_NEW_SEC80E_ALLOWED, 0.0) > 0.5
        )
    }
}

data class LoanTaxBenefitInput(
    val grossAnnualIncome: Double = 1800000.0,
    val isSalaried: Boolean = true,
    val propertyOccupancy: PropertyOccupancy = PropertyOccupancy.SELF_OCCUPIED,
    val homeLoanInterestPaid: Double = 240000.0,
    val annualRentalIncome: Double = 0.0,
    val homeLoanPrincipalPaid: Double = 120000.0,
    val other80cInvestments: Double = 50000.0,
    val educationLoanInterestPaid: Double = 0.0
)

data class RegimeTaxBreakdown(
    val regimeName: String,
    val taxableIncomeWithoutLoans: Double,
    val taxableIncomeWithLoans: Double,
    val taxWithoutLoanDeductions: Double,
    val taxWithLoanDeductions: Double,
    val totalTaxSavedByLoans: Double,
    val sec24bDeductionUsed: Double,
    val sec80cLoanDeductionUsed: Double,
    val sec80eDeductionUsed: Double,
    val notes: String
)

data class LoanTaxBenefitResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val oldRegime: RegimeTaxBreakdown,
    val newRegime: RegimeTaxBreakdown,
    val recommendedRegime: String,
    val lowerTaxSavingsDifference: Double,
    val recommendationSummary: String
)

/**
 * Pure domain engine computing Indian Loan Tax Benefits (Sec 24(b), Sec 80C, Sec 80E)
 * comparing Old Regime vs New Regime with all caps, slabs, rebates, and cess driven by taxslabs.json.
 */
object LoanTaxBenefitEngine {

    fun calculate(input: LoanTaxBenefitInput, config: TaxSlabsConfig): LoanTaxBenefitResult {
        val errors = mutableListOf<String>()
        if (input.grossAnnualIncome < 0.0) errors.add("Gross annual income cannot be negative.")
        if (input.homeLoanInterestPaid < 0.0) errors.add("Home loan interest cannot be negative.")
        if (input.homeLoanPrincipalPaid < 0.0) errors.add("Home loan principal cannot be negative.")
        if (input.educationLoanInterestPaid < 0.0) errors.add("Education loan interest cannot be negative.")
        if (errors.isNotEmpty()) {
            val empty = RegimeTaxBreakdown("", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, "")
            return LoanTaxBenefitResult(
                isValid = false,
                validationErrors = errors,
                oldRegime = empty,
                newRegime = empty,
                recommendedRegime = "",
                lowerTaxSavingsDifference = 0.0,
                recommendationSummary = ""
            )
        }

        val oldBreakdown = computeOldRegime(input, config)
        val newBreakdown = computeNewRegime(input, config)

        val oldTaxBd = MoneyMath.bd(oldBreakdown.taxWithLoanDeductions)
        val newTaxBd = MoneyMath.bd(newBreakdown.taxWithLoanDeductions)
        val diffBd = MoneyMath.roundPaiseBd(oldTaxBd.subtract(newTaxBd).abs())

        val (recommended, summary) = when {
            oldTaxBd < newTaxBd ->
                "Old Regime" to
                    "Old Regime saves ${CurrencyFormatter.format(
                        diffBd.toDouble(),
                        CurrencyCode.INR
                    )} more total tax after loan deductions."
            newTaxBd < oldTaxBd ->
                "New Regime" to
                    "New Regime still leaves you ${CurrencyFormatter.format(
                        diffBd.toDouble(),
                        CurrencyCode.INR
                    )} ahead despite lower loan deductions."
            else ->
                "Either Regime" to
                    "Both Old and New Regimes result in the same estimated tax liability."
        }

        return LoanTaxBenefitResult(
            isValid = true,
            oldRegime = oldBreakdown,
            newRegime = newBreakdown,
            recommendedRegime = recommended,
            lowerTaxSavingsDifference = diffBd.toDouble(),
            recommendationSummary = summary
        )
    }

    private fun computeOldRegime(input: LoanTaxBenefitInput, config: TaxSlabsConfig): RegimeTaxBreakdown {
        val grossBd = MoneyMath.bd(input.grossAnnualIncome)
        val stdDedBd = if (input.isSalaried) MoneyMath.bd(config.oldStdDeduction) else BigDecimal.ZERO
        val other80cBd = MoneyMath.bd(input.other80cInvestments.coerceAtLeast(0.0))
            .min(MoneyMath.bd(config.sec80cCap))

        // Let-out net annual value (after standard 30% repair deduction under Sec 24(a))
        val netRentBd = if (input.propertyOccupancy == PropertyOccupancy.LET_OUT) {
            MoneyMath.bd(input.annualRentalIncome.coerceAtLeast(0.0))
                .multiply(BigDecimal("0.70"), MoneyMath.MC)
        } else {
            BigDecimal.ZERO
        }

        val baseTaxableBeforeLoans = grossBd
            .add(netRentBd)
            .subtract(stdDedBd)
            .subtract(other80cBd)
            .max(BigDecimal.ZERO)

        // Sec 24(b) deduction in Old Regime
        val homeIntBd = MoneyMath.bd(input.homeLoanInterestPaid.coerceAtLeast(0.0))
        val sec24bUsedBd = when (input.propertyOccupancy) {
            PropertyOccupancy.SELF_OCCUPIED -> homeIntBd.min(MoneyMath.bd(config.sec24bSelfOccupiedCap))
            PropertyOccupancy.LET_OUT -> {
                // Interest offsets full net rent, plus any loss up to sec24bLetOutLossSetOffCap against salary/other income
                val maxAllowedAgainstTotal = netRentBd.add(MoneyMath.bd(config.sec24bLetOutLossSetOffCap))
                homeIntBd.min(maxAllowedAgainstTotal)
            }
        }

        // Sec 80C remaining room for home loan principal
        val remaining80cRoomBd = MoneyMath.bd(config.sec80cCap).subtract(other80cBd).max(BigDecimal.ZERO)
        val sec80cLoanUsedBd = MoneyMath.bd(input.homeLoanPrincipalPaid.coerceAtLeast(0.0)).min(remaining80cRoomBd)

        // Sec 80E uncapped education loan interest
        val sec80eUsedBd = MoneyMath.bd(input.educationLoanInterestPaid.coerceAtLeast(0.0))

        val totalLoanDedBd = sec24bUsedBd.add(sec80cLoanUsedBd).add(sec80eUsedBd)
        val taxableWithLoans = baseTaxableBeforeLoans.subtract(totalLoanDedBd).max(BigDecimal.ZERO)

        val taxBefore = computeOldSlabTaxWithCess(baseTaxableBeforeLoans, config)
        val taxAfter = computeOldSlabTaxWithCess(taxableWithLoans, config)
        val taxSaved = MoneyMath.roundPaiseBd(taxBefore.subtract(taxAfter).max(BigDecimal.ZERO))

        val notes = if (input.propertyOccupancy == PropertyOccupancy.SELF_OCCUPIED) {
            "Allows Sec 24(b) up to cap, Sec 80C principal room, and full Sec 80E interest (up to ${config.sec80eMaxYears} yrs)."
        } else {
            "Let-out property offsets interest against net rent (70% of rent) plus house-property loss set-off cap."
        }

        return RegimeTaxBreakdown(
            regimeName = "Old Regime",
            taxableIncomeWithoutLoans = MoneyMath.roundPaiseBd(baseTaxableBeforeLoans).toDouble(),
            taxableIncomeWithLoans = MoneyMath.roundPaiseBd(taxableWithLoans).toDouble(),
            taxWithoutLoanDeductions = taxBefore.toDouble(),
            taxWithLoanDeductions = taxAfter.toDouble(),
            totalTaxSavedByLoans = taxSaved.toDouble(),
            sec24bDeductionUsed = MoneyMath.roundPaiseBd(sec24bUsedBd).toDouble(),
            sec80cLoanDeductionUsed = MoneyMath.roundPaiseBd(sec80cLoanUsedBd).toDouble(),
            sec80eDeductionUsed = MoneyMath.roundPaiseBd(sec80eUsedBd).toDouble(),
            notes = notes
        )
    }

    private fun computeNewRegime(input: LoanTaxBenefitInput, config: TaxSlabsConfig): RegimeTaxBreakdown {
        val grossBd = MoneyMath.bd(input.grossAnnualIncome)
        val stdDedBd = if (input.isSalaried) MoneyMath.bd(config.newStdDeduction) else BigDecimal.ZERO
        val netRentBd = if (input.propertyOccupancy == PropertyOccupancy.LET_OUT) {
            MoneyMath.bd(input.annualRentalIncome.coerceAtLeast(0.0))
                .multiply(BigDecimal("0.70"), MoneyMath.MC)
        } else {
            BigDecimal.ZERO
        }

        val baseTaxableBeforeLoans = grossBd.add(netRentBd).subtract(stdDedBd).max(BigDecimal.ZERO)

        val homeIntBd = MoneyMath.bd(input.homeLoanInterestPaid.coerceAtLeast(0.0))
        val sec24bUsedBd = when (input.propertyOccupancy) {
            PropertyOccupancy.SELF_OCCUPIED -> if (config.newRegimeSec24bSelfOccupiedAllowed) {
                homeIntBd.min(MoneyMath.bd(config.sec24bSelfOccupiedCap))
            } else {
                BigDecimal.ZERO
            }
            PropertyOccupancy.LET_OUT -> {
                // Under New Regime, interest on let-out property can offset net rental income up to zero (no loss set-off against salary)
                homeIntBd.min(netRentBd)
            }
        }

        val sec80cLoanUsedBd = if (config.newRegimeSec80cAllowed) {
            MoneyMath.bd(input.homeLoanPrincipalPaid.coerceAtLeast(0.0)).min(MoneyMath.bd(config.sec80cCap))
        } else {
            BigDecimal.ZERO
        }

        val sec80eUsedBd = if (config.newRegimeSec80eAllowed) {
            MoneyMath.bd(input.educationLoanInterestPaid.coerceAtLeast(0.0))
        } else {
            BigDecimal.ZERO
        }

        val totalLoanDedBd = sec24bUsedBd.add(sec80cLoanUsedBd).add(sec80eUsedBd)
        val taxableWithLoans = baseTaxableBeforeLoans.subtract(totalLoanDedBd).max(BigDecimal.ZERO)

        val taxBefore = computeNewSlabTaxWithCess(baseTaxableBeforeLoans, config)
        val taxAfter = computeNewSlabTaxWithCess(taxableWithLoans, config)
        val taxSaved = MoneyMath.roundPaiseBd(taxBefore.subtract(taxAfter).max(BigDecimal.ZERO))

        val notes = if (input.propertyOccupancy == PropertyOccupancy.SELF_OCCUPIED) {
            "New Regime offers wider slabs & higher 87A rebate, but self-occupied Sec 24(b), 80C & 80E loan deductions are not permitted."
        } else {
            "New Regime allows let-out home loan interest only up to taxable net rental income (no house-property loss set-off against salary)."
        }

        return RegimeTaxBreakdown(
            regimeName = "New Regime",
            taxableIncomeWithoutLoans = MoneyMath.roundPaiseBd(baseTaxableBeforeLoans).toDouble(),
            taxableIncomeWithLoans = MoneyMath.roundPaiseBd(taxableWithLoans).toDouble(),
            taxWithoutLoanDeductions = taxBefore.toDouble(),
            taxWithLoanDeductions = taxAfter.toDouble(),
            totalTaxSavedByLoans = taxSaved.toDouble(),
            sec24bDeductionUsed = MoneyMath.roundPaiseBd(sec24bUsedBd).toDouble(),
            sec80cLoanDeductionUsed = MoneyMath.roundPaiseBd(sec80cLoanUsedBd).toDouble(),
            sec80eDeductionUsed = MoneyMath.roundPaiseBd(sec80eUsedBd).toDouble(),
            notes = notes
        )
    }

    private fun computeOldSlabTaxWithCess(taxableIncome: BigDecimal, config: TaxSlabsConfig): BigDecimal {
        if (taxableIncome <= MoneyMath.bd(config.oldRebateLimit)) return MoneyMath.ZERO
        val slabs = listOf(
            MoneyMath.bd(config.oldSlab1Limit) to BigDecimal.ZERO,
            MoneyMath.bd(config.oldSlab2Limit) to BigDecimal("5"),
            MoneyMath.bd(config.oldSlab3Limit) to BigDecimal("20")
        )
        val baseTax = progressiveTax(taxableIncome, slabs, topRatePercent = BigDecimal("30"))
        val cess = MoneyMath.percentageOf(baseTax, MoneyMath.bd(config.cessPercent))
        return MoneyMath.roundPaiseBd(baseTax.add(cess))
    }

    private fun computeNewSlabTaxWithCess(taxableIncome: BigDecimal, config: TaxSlabsConfig): BigDecimal {
        if (taxableIncome <= MoneyMath.bd(config.newRebateLimit)) return MoneyMath.ZERO
        val slabs = listOf(
            MoneyMath.bd(config.newSlab1Limit) to BigDecimal.ZERO,
            MoneyMath.bd(config.newSlab2Limit) to BigDecimal("5"),
            MoneyMath.bd(config.newSlab3Limit) to BigDecimal("10"),
            MoneyMath.bd(config.newSlab4Limit) to BigDecimal("15"),
            MoneyMath.bd(config.newSlab5Limit) to BigDecimal("20"),
            MoneyMath.bd(config.newSlab6Limit) to BigDecimal("25")
        )
        val baseTax = progressiveTax(taxableIncome, slabs, topRatePercent = BigDecimal("30"))
        // Marginal relief near Section 87A rebate threshold
        val excessOverRebate = taxableIncome.subtract(MoneyMath.bd(config.newRebateLimit))
        val effectiveBaseTax = if (excessOverRebate > BigDecimal.ZERO && baseTax > excessOverRebate) {
            excessOverRebate
        } else {
            baseTax
        }
        val cess = MoneyMath.percentageOf(effectiveBaseTax, MoneyMath.bd(config.cessPercent))
        return MoneyMath.roundPaiseBd(effectiveBaseTax.add(cess))
    }

    private fun progressiveTax(income: BigDecimal, slabs: List<Pair<BigDecimal, BigDecimal>>, topRatePercent: BigDecimal): BigDecimal {
        var prevLimit = BigDecimal.ZERO
        var tax = BigDecimal.ZERO
        for ((limit, ratePct) in slabs) {
            if (income <= prevLimit) break
            val slice = income.min(limit).subtract(prevLimit).max(BigDecimal.ZERO)
            if (ratePct > BigDecimal.ZERO && slice > BigDecimal.ZERO) {
                tax = tax.add(
                    slice.multiply(ratePct, MoneyMath.MC).divide(MoneyMath.HUNDRED, 2, RoundingMode.HALF_UP)
                )
            }
            prevLimit = limit
        }
        if (income > prevLimit) {
            val topSlice = income.subtract(prevLimit)
            tax = tax.add(
                topSlice.multiply(topRatePercent, MoneyMath.MC).divide(MoneyMath.HUNDRED, 2, RoundingMode.HALF_UP)
            )
        }
        return MoneyMath.roundPaiseBd(tax)
    }
}
