package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.remote.ApyContributionRow
import com.jyotirmoyb.loan.data.remote.PmsymContributionRow
import com.jyotirmoyb.loan.data.remote.SeniorSchemeStatusRule
import java.math.BigDecimal

// =============================================================================
// 3. EMPLOYEES' PROVIDENT FUND (EPF + EPS) ENGINE
// =============================================================================

data class EpfInput(
    val currentAge: Int = 28,
    val retirementAge: Int = 58,
    val monthlyBasicPlusDa: Double = 50000.0,
    val existingEpfBalance: Double = 200000.0,
    val annualSalaryGrowthPercent: Double = 6.0,
    val vpfExtraMonthly: Double = 0.0,
    val applyWageCeiling: Boolean = false,
    val epfInterestRatePercent: Double = 8.25
)

data class EpfConfigRules(
    val employeePercent: Double = 12.0,
    val employerEpfPercent: Double = 3.67,
    val employerEpsPercent: Double = 8.33,
    val wageCeilingMonthly: Double = 15000.0,
    val epsMonthlyCap: Double = 1250.0
)

data class EpfYearlyRow(
    val age: Int,
    val monthlyBasicDa: Double,
    val employeeAnnual: Double,
    val employerEpfAnnual: Double,
    val epsAnnual: Double,
    val interestEarnedYear: Double,
    val closingEpfBalance: Double
)

data class EpfResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val yearsToRetirement: Int = 0,
    val employeeTotalContribution: Double = 0.0,
    val employerEpfTotalContribution: Double = 0.0,
    val epsTotalContribution: Double = 0.0,
    val totalInterestEarned: Double = 0.0,
    val projectedEpfCorpus: Double = 0.0,
    val firstMonthEmployeeEpf: Double = 0.0,
    val firstMonthEmployerEpf: Double = 0.0,
    val firstMonthEmployerEps: Double = 0.0,
    val epsNote: String = "",
    val yearlyRows: List<EpfYearlyRow> = emptyList()
)

object EpfEngine {

    private val TWELVE = BigDecimal("12")

    fun calculate(input: EpfInput, rules: EpfConfigRules = EpfConfigRules()): EpfResult {
        val errors = mutableListOf<String>()
        if (input.currentAge !in 18..57) errors.add("Current age must be between 18 and 57.")
        if (input.retirementAge <= input.currentAge || input.retirementAge > 65) {
            errors.add("Retirement age must be greater than current age (up to 65).")
        }
        if (input.monthlyBasicPlusDa <= 0.0) errors.add("Monthly Basic + DA must be greater than 0.")
        if (input.existingEpfBalance < 0.0) errors.add("Existing EPF balance cannot be negative.")
        if (input.vpfExtraMonthly < 0.0) errors.add("VPF contribution cannot be negative.")
        if (input.epfInterestRatePercent < 0.0 || input.epfInterestRatePercent > 20.0) {
            errors.add("EPF interest rate must be between 0% and 20%.")
        }
        if (errors.isNotEmpty()) return EpfResult(isValid = false, validationErrors = errors)

        val years = input.retirementAge - input.currentAge
        val totalEmployerPct = rules.employerEpfPercent + rules.employerEpsPercent
        val growthFactor = MoneyMath.ONE.add(MoneyMath.bd(input.annualSalaryGrowthPercent.coerceIn(0.0, 25.0) / 100.0))
        val monthlyRateBd = MoneyMath.bd(input.epfInterestRatePercent).divide(BigDecimal("1200"), MoneyMath.MC)

        var basicDaBd = MoneyMath.bd(input.monthlyBasicPlusDa)
        var balanceBd = MoneyMath.bd(input.existingEpfBalance)
        var totalEmployeeBd = BigDecimal.ZERO
        var totalEmployerEpfBd = BigDecimal.ZERO
        var totalEpsBd = BigDecimal.ZERO
        var totalInterestBd = BigDecimal.ZERO

        var firstEmpEpf = 0.0
        var firstEmprEpf = 0.0
        var firstEmprEps = 0.0
        val rows = mutableListOf<EpfYearlyRow>()

        for (y in 1..years) {
            val wageCeilingBd = MoneyMath.bd(rules.wageCeilingMonthly)
            val pfWageBase = if (input.applyWageCeiling) basicDaBd.min(wageCeilingBd) else basicDaBd
            val epsWageBase = basicDaBd.min(wageCeilingBd)

            val empMonthlyBd = MoneyMath.roundPaiseBd(
                MoneyMath.percentageOf(pfWageBase, MoneyMath.bd(rules.employeePercent))
                    .add(MoneyMath.bd(input.vpfExtraMonthly))
            )
            val epsMonthlyBd = MoneyMath.roundPaiseBd(
                MoneyMath.percentageOf(epsWageBase, MoneyMath.bd(rules.employerEpsPercent))
                    .min(MoneyMath.bd(rules.epsMonthlyCap))
            )
            val totalEmployerMonthlyBd = MoneyMath.roundPaiseBd(
                MoneyMath.percentageOf(pfWageBase, MoneyMath.bd(totalEmployerPct))
            )
            val employerEpfMonthlyBd = MoneyMath.roundPaiseBd(
                totalEmployerMonthlyBd.subtract(epsMonthlyBd).max(BigDecimal.ZERO)
            )

            if (y == 1) {
                firstEmpEpf = empMonthlyBd.toDouble()
                firstEmprEpf = employerEpfMonthlyBd.toDouble()
                firstEmprEps = epsMonthlyBd.toDouble()
            }

            // EPFO calculates interest monthly on running balance and credits annually at FY-end
            var accruedYearInterest = BigDecimal.ZERO
            var runningForYear = balanceBd
            for (m in 1..12) {
                runningForYear = runningForYear.add(empMonthlyBd).add(employerEpfMonthlyBd)
                accruedYearInterest = accruedYearInterest.add(runningForYear.multiply(monthlyRateBd, MoneyMath.MC))
            }
            val yearIntRounded = MoneyMath.roundPaiseBd(accruedYearInterest)
            balanceBd = MoneyMath.roundPaiseBd(runningForYear.add(yearIntRounded))

            val empYear = MoneyMath.roundPaiseBd(empMonthlyBd.multiply(TWELVE))
            val emprEpfYear = MoneyMath.roundPaiseBd(employerEpfMonthlyBd.multiply(TWELVE))
            val epsYear = MoneyMath.roundPaiseBd(epsMonthlyBd.multiply(TWELVE))

            totalEmployeeBd = totalEmployeeBd.add(empYear)
            totalEmployerEpfBd = totalEmployerEpfBd.add(emprEpfYear)
            totalEpsBd = totalEpsBd.add(epsYear)
            totalInterestBd = totalInterestBd.add(yearIntRounded)

            rows.add(
                EpfYearlyRow(
                    age = input.currentAge + y,
                    monthlyBasicDa = MoneyMath.roundPaiseBd(basicDaBd).toDouble(),
                    employeeAnnual = empYear.toDouble(),
                    employerEpfAnnual = emprEpfYear.toDouble(),
                    epsAnnual = epsYear.toDouble(),
                    interestEarnedYear = yearIntRounded.toDouble(),
                    closingEpfBalance = balanceBd.toDouble()
                )
            )

            basicDaBd = basicDaBd.multiply(growthFactor, MoneyMath.MC)
        }

        val ceilingText = CurrencyFormatter.format(rules.wageCeilingMonthly, CurrencyCode.INR)
        val epsCapText = CurrencyFormatter.format(rules.epsMonthlyCap, CurrencyCode.INR)
        val epsNote = "EPS-95 Note: ${rules.employerEpsPercent}% of Basic+DA (capped at $epsCapText/mo on the $ceilingText " +
            "statutory wage ceiling) flows into the Employees' Pension Scheme (EPS). EPS earns no interest and is excluded " +
            "from the EPF corpus; instead it provides a lifelong monthly pension after 10+ years of eligible service (age 58). " +
            "The remaining employer share flows into your EPF account."

        return EpfResult(
            isValid = true,
            yearsToRetirement = years,
            employeeTotalContribution = MoneyMath.roundPaiseBd(totalEmployeeBd).toDouble(),
            employerEpfTotalContribution = MoneyMath.roundPaiseBd(totalEmployerEpfBd).toDouble(),
            epsTotalContribution = MoneyMath.roundPaiseBd(totalEpsBd).toDouble(),
            totalInterestEarned = MoneyMath.roundPaiseBd(totalInterestBd).toDouble(),
            projectedEpfCorpus = balanceBd.toDouble(),
            firstMonthEmployeeEpf = firstEmpEpf,
            firstMonthEmployerEpf = firstEmprEpf,
            firstMonthEmployerEps = firstEmprEps,
            epsNote = epsNote,
            yearlyRows = rows
        )
    }
}

// =============================================================================
// 4. GOVERNMENT PENSION SCHEMES ENGINE (APY, PM-SYM, PMVVY & SENIOR SCHEMES)
// =============================================================================

data class ApyResult(
    val isValid: Boolean,
    val validationError: String? = null,
    val entryAge: Int = 25,
    val pensionAge: Int = 60,
    val contributionYears: Int = 35,
    val desiredMonthlyPension: Int = 5000,
    val monthlyContribution: Double = 0.0,
    val quarterlyContribution: Double = 0.0,
    val totalPersonalContribution: Double = 0.0,
    val indicativeNomineeCorpus: Double = 0.0
)

data class PmsymResult(
    val isValid: Boolean,
    val validationError: String? = null,
    val isIncomeEligible: Boolean = true,
    val entryAge: Int = 25,
    val contributionYears: Int = 35,
    val subscriberMonthlyContribution: Double = 0.0,
    val govtMatchingMonthlyContribution: Double = 0.0,
    val totalSubscriberContribution: Double = 0.0,
    val totalGovtContribution: Double = 0.0,
    val assuredMonthlyPension: Double = 3000.0,
    val incomeCeilingApplied: Double = 15000.0
)

data class SeniorSchemePayoutResult(
    val isValid: Boolean,
    val scheme: SeniorSchemeStatusRule,
    val investmentAmount: Double,
    val effectiveCappedAmount: Double,
    val exceedsCap: Boolean,
    val periodicPayout: Double,
    val payoutPeriodLabel: String,
    val annualInterest: Double,
    val totalInterestOverTenure: Double,
    val totalMaturityReturn: Double
)

object PensionSchemesEngine {

    private val TWELVE = BigDecimal("12")

    fun calculateApy(
        entryAge: Int,
        desiredMonthlyPension: Int,
        apyTable: List<ApyContributionRow>,
        corpusMultipliers: Map<Int, Double>
    ): ApyResult {
        if (entryAge !in 18..40) {
            return ApyResult(isValid = false, validationError = "APY entry age must be between 18 and 40 years.")
        }
        val row = apyTable.firstOrNull { it.entryAge == entryAge }
            ?: return ApyResult(isValid = false, validationError = "No APY contribution data available for age $entryAge.")

        val validSlab = listOf(1000, 2000, 3000, 4000, 5000).firstOrNull { it == desiredMonthlyPension } ?: 5000
        val monthlyBd = MoneyMath.bd(row.contributionForSlab(validSlab))
        val years = 60 - entryAge
        val totalContribBd = MoneyMath.roundPaiseBd(
            monthlyBd.multiply(BigDecimal.valueOf(years.toLong())).multiply(TWELVE)
        )
        val quarterlyBd = MoneyMath.roundPaiseBd(monthlyBd.multiply(BigDecimal("3")))
        val nomineeCorpus = corpusMultipliers[validSlab] ?: (validSlab * 170.0)

        return ApyResult(
            isValid = true,
            entryAge = entryAge,
            pensionAge = 60,
            contributionYears = years,
            desiredMonthlyPension = validSlab,
            monthlyContribution = monthlyBd.toDouble(),
            quarterlyContribution = quarterlyBd.toDouble(),
            totalPersonalContribution = totalContribBd.toDouble(),
            indicativeNomineeCorpus = nomineeCorpus
        )
    }

    fun calculatePmsym(
        entryAge: Int,
        monthlyIncome: Double,
        pmsymTable: List<PmsymContributionRow>,
        assuredPension: Double = 3000.0,
        incomeCeiling: Double = 15000.0
    ): PmsymResult {
        if (entryAge !in 18..40) {
            return PmsymResult(isValid = false, validationError = "PM-SYM entry age must be between 18 and 40 years.")
        }
        val row = pmsymTable.firstOrNull { it.entryAge == entryAge }
            ?: return PmsymResult(isValid = false, validationError = "No PM-SYM contribution data available for age $entryAge.")

        val years = 60 - entryAge
        val subMonthlyBd = MoneyMath.bd(row.monthlyContribution)
        val totalSubBd = MoneyMath.roundPaiseBd(
            subMonthlyBd.multiply(BigDecimal.valueOf(years.toLong())).multiply(TWELVE)
        )

        return PmsymResult(
            isValid = true,
            isIncomeEligible = monthlyIncome <= incomeCeiling,
            entryAge = entryAge,
            contributionYears = years,
            subscriberMonthlyContribution = subMonthlyBd.toDouble(),
            govtMatchingMonthlyContribution = subMonthlyBd.toDouble(), // 1:1 matching by Govt of India
            totalSubscriberContribution = totalSubBd.toDouble(),
            totalGovtContribution = totalSubBd.toDouble(),
            assuredMonthlyPension = assuredPension,
            incomeCeilingApplied = incomeCeiling
        )
    }

    fun calculateSeniorSchemePayout(scheme: SeniorSchemeStatusRule, investmentAmount: Double): SeniorSchemePayoutResult {
        val safeAmt = investmentAmount.coerceAtLeast(0.0)
        val cap = scheme.maxInvestmentCap
        val exceedsCap = cap != null && safeAmt > cap
        val effectiveAmt = if (cap != null) safeAmt.coerceAtMost(cap) else safeAmt

        val principalBd = MoneyMath.bd(effectiveAmt)
        val annualIntBd = MoneyMath.percentageOf(principalBd, MoneyMath.bd(scheme.ratePercent))
        val (periodsPerYear, periodLabel) = when (scheme.payoutFrequency.uppercase()) {
            "MONTHLY" -> 12 to "Monthly Payout"
            "QUARTERLY" -> 4 to "Quarterly Payout"
            "HALF_YEARLY" -> 2 to "Half-Yearly Payout"
            else -> 1 to "Annual Payout"
        }
        val periodicBd = MoneyMath.roundPaiseBd(
            annualIntBd.divide(BigDecimal.valueOf(periodsPerYear.toLong()), MoneyMath.MC)
        )
        val totalIntBd = MoneyMath.roundPaiseBd(
            annualIntBd.multiply(BigDecimal.valueOf(scheme.tenureYears.toLong()))
        )
        val totalReturnBd = MoneyMath.roundPaiseBd(principalBd.add(totalIntBd))

        return SeniorSchemePayoutResult(
            isValid = safeAmt > 0.0,
            scheme = scheme,
            investmentAmount = MoneyMath.roundPaise(safeAmt),
            effectiveCappedAmount = MoneyMath.roundPaise(effectiveAmt),
            exceedsCap = exceedsCap,
            periodicPayout = periodicBd.toDouble(),
            payoutPeriodLabel = periodLabel,
            annualInterest = annualIntBd.toDouble(),
            totalInterestOverTenure = totalIntBd.toDouble(),
            totalMaturityReturn = totalReturnBd.toDouble()
        )
    }
}
