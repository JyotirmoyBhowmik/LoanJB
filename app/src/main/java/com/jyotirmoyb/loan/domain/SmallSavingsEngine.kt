package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

enum class SmallSavingsSchemeType(
    val id: String,
    val title: String,
    val shortTitle: String,
    val taxBadge: String,
    val summary: String
) {
    PPF("ppf", "Public Provident Fund (PPF)", "PPF", "EEE Tax-Free", "15-yr lock-in with 5-yr extension blocks; ₹1.5L yearly cap."),
    SSY("ssy", "Sukanya Samriddhi (SSY)", "SSY", "EEE Tax-Free", "Deposit for 15 yrs; compounds tax-free until maturity at 21 yrs."),
    NSC("nsc", "National Savings Cert (NSC)", "NSC", "80C Eligible", "5-yr lump-sum compounded annually; interest reinvested."),
    SCSS("scss", "Senior Citizens Savings (SCSS)", "SCSS", "Quarterly Payout", "5-yr senior scheme paying interest every quarter; ₹30L cap."),
    PO_MIS("po_mis", "Post Office MIS", "PO MIS", "Monthly Payout", "5-yr steady monthly income; Single ₹9L / Joint ₹15L cap."),
    PO_TD("po_td", "Post Office Time Deposit", "PO TD", "5Y 80C Eligible", "1Y, 2Y, 3Y or 5Y deposit; quarterly compounding paid yearly."),
    PO_RD("po_rd", "Post Office RD (5-Yr)", "PO RD", "Taxable", "Monthly Post Office deposit with quarterly compounding."),
    KVP("kvp", "Kisan Vikas Patra (KVP)", "KVP", "Doubles Money", "Government certificate that doubles lump-sum in notified months.")
}

data class SmallSavingsConfig(
    val quarterLabel: String,
    val ppfRate: Double,
    val ppfYearlyCap: Double,
    val ppfTenureYears: Int,
    val ppfExtensionBlockYears: Int,
    val ssyRate: Double,
    val ssyYearlyCap: Double,
    val ssyDepositYears: Int,
    val ssyMaturityYears: Int,
    val nscRate: Double,
    val nscTenureYears: Int,
    val scssRate: Double,
    val scssMaxCap: Double,
    val scssTenureYears: Int,
    val pomisRate: Double,
    val pomisSingleCap: Double,
    val pomisJointCap: Double,
    val pomisTenureYears: Int,
    val potd1yRate: Double,
    val potd2yRate: Double,
    val potd3yRate: Double,
    val potd5yRate: Double,
    val pordRate: Double,
    val pordTenureMonths: Int,
    val kvpRate: Double,
    val kvpDoublingMonths: Int
) {
    fun rateKeyFor(scheme: SmallSavingsSchemeType, potdYears: Int = 5): String = when (scheme) {
        SmallSavingsSchemeType.PPF -> ConfigKeys.SCHEME_PPF_RATE
        SmallSavingsSchemeType.SSY -> ConfigKeys.SCHEME_SSY_RATE
        SmallSavingsSchemeType.NSC -> ConfigKeys.SCHEME_NSC_RATE
        SmallSavingsSchemeType.SCSS -> ConfigKeys.SCHEME_SCSS_RATE
        SmallSavingsSchemeType.PO_MIS -> ConfigKeys.SCHEME_POMIS_RATE
        SmallSavingsSchemeType.PO_TD -> when (potdYears) {
            1 -> ConfigKeys.SCHEME_POTD_1Y_RATE
            2 -> ConfigKeys.SCHEME_POTD_2Y_RATE
            3 -> ConfigKeys.SCHEME_POTD_3Y_RATE
            else -> ConfigKeys.SCHEME_POTD_5Y_RATE
        }
        SmallSavingsSchemeType.PO_RD -> ConfigKeys.SCHEME_PORD_RATE
        SmallSavingsSchemeType.KVP -> ConfigKeys.SCHEME_KVP_RATE
    }

    fun rateFor(scheme: SmallSavingsSchemeType, potdYears: Int = 5): Double = when (scheme) {
        SmallSavingsSchemeType.PPF -> ppfRate
        SmallSavingsSchemeType.SSY -> ssyRate
        SmallSavingsSchemeType.NSC -> nscRate
        SmallSavingsSchemeType.SCSS -> scssRate
        SmallSavingsSchemeType.PO_MIS -> pomisRate
        SmallSavingsSchemeType.PO_TD -> when (potdYears) {
            1 -> potd1yRate
            2 -> potd2yRate
            3 -> potd3yRate
            else -> potd5yRate
        }
        SmallSavingsSchemeType.PO_RD -> pordRate
        SmallSavingsSchemeType.KVP -> kvpRate
    }

    companion object {
        fun fromResolvedConfig(resolved: Map<String, ResolvedConfigItem>): SmallSavingsConfig = SmallSavingsConfig(
            quarterLabel = DataSourceResolver.resolveText(resolved, ConfigKeys.SCHEME_QUARTER_LABEL, "Q3 FY 2026-27"),
            ppfRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_PPF_RATE, 7.1),
            ppfYearlyCap = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_PPF_YEARLY_CAP, 150000.0),
            ppfTenureYears = DataSourceResolver.resolveInt(resolved, ConfigKeys.SCHEME_PPF_TENURE_YEARS, 15),
            ppfExtensionBlockYears = DataSourceResolver.resolveInt(resolved, ConfigKeys.SCHEME_PPF_EXTENSION_YEARS, 5),
            ssyRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_SSY_RATE, 8.2),
            ssyYearlyCap = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_SSY_YEARLY_CAP, 150000.0),
            ssyDepositYears = DataSourceResolver.resolveInt(resolved, ConfigKeys.SCHEME_SSY_DEPOSIT_YEARS, 15),
            ssyMaturityYears = DataSourceResolver.resolveInt(resolved, ConfigKeys.SCHEME_SSY_MATURITY_YEARS, 21),
            nscRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_NSC_RATE, 7.7),
            nscTenureYears = DataSourceResolver.resolveInt(resolved, ConfigKeys.SCHEME_NSC_TENURE_YEARS, 5),
            scssRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_SCSS_RATE, 8.2),
            scssMaxCap = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_SCSS_MAX_CAP, 3000000.0),
            scssTenureYears = DataSourceResolver.resolveInt(resolved, ConfigKeys.SCHEME_SCSS_TENURE_YEARS, 5),
            pomisRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_POMIS_RATE, 7.4),
            pomisSingleCap = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_POMIS_SINGLE_CAP, 900000.0),
            pomisJointCap = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_POMIS_JOINT_CAP, 1500000.0),
            pomisTenureYears = DataSourceResolver.resolveInt(resolved, ConfigKeys.SCHEME_POMIS_TENURE_YEARS, 5),
            potd1yRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_POTD_1Y_RATE, 6.9),
            potd2yRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_POTD_2Y_RATE, 7.0),
            potd3yRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_POTD_3Y_RATE, 7.1),
            potd5yRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_POTD_5Y_RATE, 7.5),
            pordRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_PORD_RATE, 6.7),
            pordTenureMonths = DataSourceResolver.resolveInt(resolved, ConfigKeys.SCHEME_PORD_TENURE_MONTHS, 60),
            kvpRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SCHEME_KVP_RATE, 7.5),
            kvpDoublingMonths = DataSourceResolver.resolveInt(resolved, ConfigKeys.SCHEME_KVP_DOUBLING_MONTHS, 115)
        )
    }
}

data class SmallSavingsInput(
    val scheme: SmallSavingsSchemeType = SmallSavingsSchemeType.PPF,
    val contributionAmount: Double = 150000.0, // yearly for PPF/SSY, monthly for PO_RD, lump-sum for others
    val ppfExtensionBlocks: Int = 0, // 0..3 blocks of 5 years
    val ppfContributeDuringExtension: Boolean = true,
    val pomisJointAccount: Boolean = false,
    val potdTenureYears: Int = 5, // 1, 2, 3, 5
    val startYear: Int = 2026,
    val startMonth: Int = 11,
    val startDay: Int = 25
)

data class SmallSavingsResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val scheme: SmallSavingsSchemeType = SmallSavingsSchemeType.PPF,
    val appliedRatePercent: Double = 0.0,
    val quarterLabel: String = "",
    val tenureMonths: Int = 0,
    val tenureDisplay: String = "",
    val totalInvested: Double = 0.0,
    val totalInterest: Double = 0.0,
    val maturityAmount: Double = 0.0,
    val periodicPayoutLabel: String? = null,
    val periodicPayoutAmount: Double = 0.0,
    val capWarning: String? = null,
    val maturityDateLabel: String = "",
    val yearlyRows: List<DepositYearRow> = emptyList()
)

/**
 * Pure domain engine for all 8 Indian Government Small Savings Schemes
 * reading rates, caps, tenures, and notification quarter strictly from schemes.json.
 */
object SmallSavingsEngine {

    fun calculate(
        input: SmallSavingsInput,
        config: SmallSavingsConfig
    ): SmallSavingsResult {
        if (input.contributionAmount <= 0.0) {
            return SmallSavingsResult(
                isValid = false,
                validationErrors = listOf("Deposit amount must be greater than 0."),
                scheme = input.scheme
            )
        }

        val rate = config.rateFor(input.scheme, input.potdTenureYears)
        val rateBd = MoneyMath.bd(rate)

        return when (input.scheme) {
            SmallSavingsSchemeType.PPF -> calculatePpf(input, config, rate, rateBd)
            SmallSavingsSchemeType.SSY -> calculateSsy(input, config, rate, rateBd)
            SmallSavingsSchemeType.NSC -> calculateNsc(input, config, rate, rateBd)
            SmallSavingsSchemeType.SCSS -> calculateScss(input, config, rate, rateBd)
            SmallSavingsSchemeType.PO_MIS -> calculatePoMis(input, config, rate, rateBd)
            SmallSavingsSchemeType.PO_TD -> calculatePoTd(input, config, rate, rateBd)
            SmallSavingsSchemeType.PO_RD -> calculatePoRd(input, config, rate)
            SmallSavingsSchemeType.KVP -> calculateKvp(input, config, rate)
        }
    }

    private fun calculatePpf(
        input: SmallSavingsInput,
        config: SmallSavingsConfig,
        rate: Double,
        rateBd: BigDecimal
    ): SmallSavingsResult {
        val extBlocks = input.ppfExtensionBlocks.coerceIn(0, 3)
        val totalYears = config.ppfTenureYears + extBlocks * config.ppfExtensionBlockYears
        val capExceeded = input.contributionAmount > config.ppfYearlyCap
        val effectiveAnnual = minOf(input.contributionAmount, config.ppfYearlyCap)
        val annualBd = MoneyMath.bd(effectiveAnnual)

        var balanceBd = MoneyMath.ZERO
        var totalInvestedBd = MoneyMath.ZERO
        var totalInterestBd = MoneyMath.ZERO
        val rows = mutableListOf<DepositYearRow>()

        for (yr in 1..totalYears) {
            val openingBd = balanceBd
            val contribBd = if (yr <= config.ppfTenureYears || input.ppfContributeDuringExtension) {
                annualBd
            } else {
                MoneyMath.ZERO
            }
            val interestBase = openingBd.add(contribBd)
            val interestBd = MoneyMath.percentageOf(interestBase, rateBd)
            val closingBd = MoneyMath.roundPaiseBd(interestBase.add(interestBd))

            totalInvestedBd = totalInvestedBd.add(contribBd)
            totalInterestBd = totalInterestBd.add(interestBd)
            balanceBd = closingBd

            rows.add(
                DepositYearRow(
                    yearIndex = yr,
                    fyLabel = MoneyMath.financialYearLabel(input.startYear + yr - 1, input.startMonth),
                    openingBalance = openingBd.toDouble(),
                    depositAdded = contribBd.toDouble(),
                    interestEarned = interestBd.toDouble(),
                    estimatedTds = 0.0,
                    closingBalance = closingBd.toDouble()
                )
            )
        }

        val matDate = maturityDate(input, totalYears * 12)
        return SmallSavingsResult(
            isValid = true,
            scheme = SmallSavingsSchemeType.PPF,
            appliedRatePercent = rate,
            quarterLabel = config.quarterLabel,
            tenureMonths = totalYears * 12,
            tenureDisplay = "$totalYears years" + if (extBlocks > 0) " (${config.ppfTenureYears} + ${extBlocks * config.ppfExtensionBlockYears} ext)" else "",
            totalInvested = MoneyMath.roundPaiseBd(totalInvestedBd).toDouble(),
            totalInterest = MoneyMath.roundPaiseBd(totalInterestBd).toDouble(),
            maturityAmount = balanceBd.toDouble(),
            capWarning = if (capExceeded) "Capped at PPF yearly maximum of ${CurrencyFormatter.format(config.ppfYearlyCap, CurrencyCode.INR)}/yr." else null,
            maturityDateLabel = matDate,
            yearlyRows = rows
        )
    }

    private fun calculateSsy(
        input: SmallSavingsInput,
        config: SmallSavingsConfig,
        rate: Double,
        rateBd: BigDecimal
    ): SmallSavingsResult {
        val capExceeded = input.contributionAmount > config.ssyYearlyCap
        val effectiveAnnual = minOf(input.contributionAmount, config.ssyYearlyCap)
        val annualBd = MoneyMath.bd(effectiveAnnual)

        var balanceBd = MoneyMath.ZERO
        var totalInvestedBd = MoneyMath.ZERO
        var totalInterestBd = MoneyMath.ZERO
        val rows = mutableListOf<DepositYearRow>()

        for (yr in 1..config.ssyMaturityYears) {
            val openingBd = balanceBd
            val contribBd = if (yr <= config.ssyDepositYears) annualBd else MoneyMath.ZERO
            val interestBase = openingBd.add(contribBd)
            val interestBd = MoneyMath.percentageOf(interestBase, rateBd)
            val closingBd = MoneyMath.roundPaiseBd(interestBase.add(interestBd))

            totalInvestedBd = totalInvestedBd.add(contribBd)
            totalInterestBd = totalInterestBd.add(interestBd)
            balanceBd = closingBd

            rows.add(
                DepositYearRow(
                    yearIndex = yr,
                    fyLabel = MoneyMath.financialYearLabel(input.startYear + yr - 1, input.startMonth),
                    openingBalance = openingBd.toDouble(),
                    depositAdded = contribBd.toDouble(),
                    interestEarned = interestBd.toDouble(),
                    estimatedTds = 0.0,
                    closingBalance = closingBd.toDouble()
                )
            )
        }

        val matDate = maturityDate(input, config.ssyMaturityYears * 12)
        return SmallSavingsResult(
            isValid = true,
            scheme = SmallSavingsSchemeType.SSY,
            appliedRatePercent = rate,
            quarterLabel = config.quarterLabel,
            tenureMonths = config.ssyMaturityYears * 12,
            tenureDisplay = "${config.ssyMaturityYears} yrs (${config.ssyDepositYears} yrs deposit)",
            totalInvested = MoneyMath.roundPaiseBd(totalInvestedBd).toDouble(),
            totalInterest = MoneyMath.roundPaiseBd(totalInterestBd).toDouble(),
            maturityAmount = balanceBd.toDouble(),
            capWarning = if (capExceeded) "Capped at SSY yearly maximum of ${CurrencyFormatter.format(config.ssyYearlyCap, CurrencyCode.INR)}/yr." else null,
            maturityDateLabel = matDate,
            yearlyRows = rows
        )
    }

    private fun calculateNsc(
        input: SmallSavingsInput,
        config: SmallSavingsConfig,
        rate: Double,
        rateBd: BigDecimal
    ): SmallSavingsResult {
        val principalBd = MoneyMath.bd(input.contributionAmount)
        var balanceBd = principalBd
        var totalInterestBd = MoneyMath.ZERO
        val rows = mutableListOf<DepositYearRow>()

        for (yr in 1..config.nscTenureYears) {
            val openingBd = balanceBd
            val interestBd = MoneyMath.percentageOf(openingBd, rateBd)
            val closingBd = MoneyMath.roundPaiseBd(openingBd.add(interestBd))
            totalInterestBd = totalInterestBd.add(interestBd)
            balanceBd = closingBd

            rows.add(
                DepositYearRow(
                    yearIndex = yr,
                    fyLabel = MoneyMath.financialYearLabel(input.startYear + yr - 1, input.startMonth),
                    openingBalance = openingBd.toDouble(),
                    depositAdded = if (yr == 1) principalBd.toDouble() else 0.0,
                    interestEarned = interestBd.toDouble(),
                    estimatedTds = 0.0,
                    closingBalance = closingBd.toDouble()
                )
            )
        }

        return SmallSavingsResult(
            isValid = true,
            scheme = SmallSavingsSchemeType.NSC,
            appliedRatePercent = rate,
            quarterLabel = config.quarterLabel,
            tenureMonths = config.nscTenureYears * 12,
            tenureDisplay = "${config.nscTenureYears} years (Annual compounding)",
            totalInvested = principalBd.toDouble(),
            totalInterest = MoneyMath.roundPaiseBd(totalInterestBd).toDouble(),
            maturityAmount = balanceBd.toDouble(),
            maturityDateLabel = maturityDate(input, config.nscTenureYears * 12),
            yearlyRows = rows
        )
    }

    private fun calculateScss(
        input: SmallSavingsInput,
        config: SmallSavingsConfig,
        rate: Double,
        rateBd: BigDecimal
    ): SmallSavingsResult {
        val capExceeded = input.contributionAmount > config.scssMaxCap
        val effectivePrincipal = minOf(input.contributionAmount, config.scssMaxCap)
        val principalBd = MoneyMath.bd(effectivePrincipal)

        val quarterlyPayoutBd = principalBd
            .multiply(rateBd, MoneyMath.MC)
            .divide(BigDecimal("400"), 2, RoundingMode.HALF_UP)
        val yearlyInterestBd = MoneyMath.roundPaiseBd(quarterlyPayoutBd.multiply(BigDecimal("4")))
        val totalInterestBd = MoneyMath.roundPaiseBd(yearlyInterestBd.multiply(BigDecimal(config.scssTenureYears)))

        val rows = (1..config.scssTenureYears).map { yr ->
            DepositYearRow(
                yearIndex = yr,
                fyLabel = MoneyMath.financialYearLabel(input.startYear + yr - 1, input.startMonth),
                openingBalance = principalBd.toDouble(),
                depositAdded = if (yr == 1) principalBd.toDouble() else 0.0,
                interestEarned = yearlyInterestBd.toDouble(),
                estimatedTds = 0.0,
                closingBalance = principalBd.toDouble()
            )
        }

        return SmallSavingsResult(
            isValid = true,
            scheme = SmallSavingsSchemeType.SCSS,
            appliedRatePercent = rate,
            quarterLabel = config.quarterLabel,
            tenureMonths = config.scssTenureYears * 12,
            tenureDisplay = "${config.scssTenureYears} years (Quarterly payout)",
            totalInvested = principalBd.toDouble(),
            totalInterest = totalInterestBd.toDouble(),
            maturityAmount = MoneyMath.roundPaiseBd(principalBd.add(totalInterestBd)).toDouble(),
            periodicPayoutLabel = "Quarterly Interest Payout",
            periodicPayoutAmount = quarterlyPayoutBd.toDouble(),
            capWarning = if (capExceeded) "Capped at SCSS maximum deposit of ${CurrencyFormatter.format(config.scssMaxCap, CurrencyCode.INR)}." else null,
            maturityDateLabel = maturityDate(input, config.scssTenureYears * 12),
            yearlyRows = rows
        )
    }

    private fun calculatePoMis(
        input: SmallSavingsInput,
        config: SmallSavingsConfig,
        rate: Double,
        rateBd: BigDecimal
    ): SmallSavingsResult {
        val cap = if (input.pomisJointAccount) config.pomisJointCap else config.pomisSingleCap
        val capExceeded = input.contributionAmount > cap
        val effectivePrincipal = minOf(input.contributionAmount, cap)
        val principalBd = MoneyMath.bd(effectivePrincipal)

        val monthlyPayoutBd = principalBd
            .multiply(rateBd, MoneyMath.MC)
            .divide(MoneyMath.TWELVE_HUNDRED, 2, RoundingMode.HALF_UP)
        val yearlyInterestBd = MoneyMath.roundPaiseBd(monthlyPayoutBd.multiply(BigDecimal("12")))
        val totalInterestBd = MoneyMath.roundPaiseBd(yearlyInterestBd.multiply(BigDecimal(config.pomisTenureYears)))

        val rows = (1..config.pomisTenureYears).map { yr ->
            DepositYearRow(
                yearIndex = yr,
                fyLabel = MoneyMath.financialYearLabel(input.startYear + yr - 1, input.startMonth),
                openingBalance = principalBd.toDouble(),
                depositAdded = if (yr == 1) principalBd.toDouble() else 0.0,
                interestEarned = yearlyInterestBd.toDouble(),
                estimatedTds = 0.0,
                closingBalance = principalBd.toDouble()
            )
        }

        return SmallSavingsResult(
            isValid = true,
            scheme = SmallSavingsSchemeType.PO_MIS,
            appliedRatePercent = rate,
            quarterLabel = config.quarterLabel,
            tenureMonths = config.pomisTenureYears * 12,
            tenureDisplay = "${config.pomisTenureYears} years (Monthly income)",
            totalInvested = principalBd.toDouble(),
            totalInterest = totalInterestBd.toDouble(),
            maturityAmount = MoneyMath.roundPaiseBd(principalBd.add(totalInterestBd)).toDouble(),
            periodicPayoutLabel = "Monthly Income Payout",
            periodicPayoutAmount = monthlyPayoutBd.toDouble(),
            capWarning = if (capExceeded) {
                val acct = if (input.pomisJointAccount) "Joint" else "Single"
                "Capped at PO MIS $acct limit of ${CurrencyFormatter.format(cap, CurrencyCode.INR)}."
            } else null,
            maturityDateLabel = maturityDate(input, config.pomisTenureYears * 12),
            yearlyRows = rows
        )
    }

    private fun calculatePoTd(
        input: SmallSavingsInput,
        config: SmallSavingsConfig,
        rate: Double,
        rateBd: BigDecimal
    ): SmallSavingsResult {
        val years = input.potdTenureYears.let { if (it in listOf(1, 2, 3, 5)) it else 5 }
        val principalBd = MoneyMath.bd(input.contributionAmount)
        // Post Office TD compounds quarterly and pays interest annually
        val qRate = rateBd.divide(BigDecimal("400"), MoneyMath.MC)
        val annualFactor = MoneyMath.ONE.add(qRate, MoneyMath.MC).pow(4, MoneyMath.MC).subtract(MoneyMath.ONE, MoneyMath.MC)
        val yearlyInterestBd = MoneyMath.roundPaiseBd(principalBd.multiply(annualFactor, MoneyMath.MC))
        val totalInterestBd = MoneyMath.roundPaiseBd(yearlyInterestBd.multiply(BigDecimal(years)))

        val rows = (1..years).map { yr ->
            DepositYearRow(
                yearIndex = yr,
                fyLabel = MoneyMath.financialYearLabel(input.startYear + yr - 1, input.startMonth),
                openingBalance = principalBd.toDouble(),
                depositAdded = if (yr == 1) principalBd.toDouble() else 0.0,
                interestEarned = yearlyInterestBd.toDouble(),
                estimatedTds = 0.0,
                closingBalance = principalBd.toDouble()
            )
        }

        return SmallSavingsResult(
            isValid = true,
            scheme = SmallSavingsSchemeType.PO_TD,
            appliedRatePercent = rate,
            quarterLabel = config.quarterLabel,
            tenureMonths = years * 12,
            tenureDisplay = "$years-Year TD (Quarterly comp., paid yearly)",
            totalInvested = principalBd.toDouble(),
            totalInterest = totalInterestBd.toDouble(),
            maturityAmount = MoneyMath.roundPaiseBd(principalBd.add(totalInterestBd)).toDouble(),
            periodicPayoutLabel = "Annual Interest Credit",
            periodicPayoutAmount = yearlyInterestBd.toDouble(),
            maturityDateLabel = maturityDate(input, years * 12),
            yearlyRows = rows
        )
    }

    private fun calculatePoRd(
        input: SmallSavingsInput,
        config: SmallSavingsConfig,
        rate: Double
    ): SmallSavingsResult {
        val rdRes = RecurringDepositEngine.calculate(
            RecurringDepositInput(
                monthlyDeposit = input.contributionAmount,
                annualRatePercent = rate,
                isSeniorCitizen = false,
                seniorBonusPercent = 0.0,
                tenureMonths = config.pordTenureMonths,
                taxSlabPercent = 0.0,
                startYear = input.startYear,
                startMonth = input.startMonth,
                startDay = input.startDay
            ),
            TdsConfig(Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 0.0)
        )
        return SmallSavingsResult(
            isValid = rdRes.isValid,
            validationErrors = rdRes.validationErrors,
            scheme = SmallSavingsSchemeType.PO_RD,
            appliedRatePercent = rate,
            quarterLabel = config.quarterLabel,
            tenureMonths = config.pordTenureMonths,
            tenureDisplay = "${config.pordTenureMonths / 12} years (${config.pordTenureMonths} mos)",
            totalInvested = rdRes.totalDeposited,
            totalInterest = rdRes.totalInterest,
            maturityAmount = rdRes.maturityAmount,
            maturityDateLabel = rdRes.maturityDateLabel,
            yearlyRows = rdRes.yearlyRows
        )
    }

    private fun calculateKvp(
        input: SmallSavingsInput,
        config: SmallSavingsConfig,
        rate: Double
    ): SmallSavingsResult {
        val principalBd = MoneyMath.bd(input.contributionAmount)
        val months = config.kvpDoublingMonths.coerceAtLeast(12)
        val yearsExact = months / 12.0
        val factor = (1.0 + rate / 100.0).pow(yearsExact)
        val maturityBd = MoneyMath.roundPaiseBd(principalBd.multiply(MoneyMath.bd(factor), MoneyMath.MC))
        val totalInterestBd = MoneyMath.roundPaiseBd(maturityBd.subtract(principalBd).max(BigDecimal.ZERO))

        val totalWholeYears = (months + 11) / 12
        var prevBal = principalBd
        val rows = mutableListOf<DepositYearRow>()
        for (yr in 1..totalWholeYears) {
            val elapsedMonths = minOf(yr * 12, months)
            val yrBal = MoneyMath.roundPaiseBd(
                principalBd.multiply(MoneyMath.bd((1.0 + rate / 100.0).pow(elapsedMonths / 12.0)), MoneyMath.MC)
            )
            val yrInt = MoneyMath.roundPaiseBd(yrBal.subtract(prevBal).max(BigDecimal.ZERO))
            rows.add(
                DepositYearRow(
                    yearIndex = yr,
                    fyLabel = MoneyMath.financialYearLabel(input.startYear + yr - 1, input.startMonth),
                    openingBalance = prevBal.toDouble(),
                    depositAdded = if (yr == 1) principalBd.toDouble() else 0.0,
                    interestEarned = yrInt.toDouble(),
                    estimatedTds = 0.0,
                    closingBalance = yrBal.toDouble()
                )
            )
            prevBal = yrBal
        }

        return SmallSavingsResult(
            isValid = true,
            scheme = SmallSavingsSchemeType.KVP,
            appliedRatePercent = rate,
            quarterLabel = config.quarterLabel,
            tenureMonths = months,
            tenureDisplay = "$months months (${months / 12}y ${months % 12}m — Doubles)",
            totalInvested = principalBd.toDouble(),
            totalInterest = totalInterestBd.toDouble(),
            maturityAmount = maturityBd.toDouble(),
            maturityDateLabel = maturityDate(input, months),
            yearlyRows = rows
        )
    }

    private fun maturityDate(input: SmallSavingsInput, months: Int): String {
        val (y, m) = PartPaymentPlannerEngine.addMonthsToYearMonth(input.startYear, input.startMonth, months)
        return MoneyMath.formatDateDdMmmYyyy(input.startDay, m, y)
    }
}
