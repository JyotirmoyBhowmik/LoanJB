package com.jyotirmoyb.loan.domain

import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.pow

// =============================================================================
// 1. SWP (SYSTEMATIC WITHDRAWAL PLAN) ENGINE
// =============================================================================

data class SwpInput(
    val initialCorpus: Double = 5000000.0, // ₹50 Lakh
    val monthlyWithdrawal: Double = 40000.0,
    val expectedReturnPercent: Double = 8.0,
    val tenureYears: Int = 20,
    val annualWithdrawalStepUpPercent: Double = 0.0,
    val startYear: Int = 2026,
    val startMonth: Int = 11
)

data class SwpYearRow(
    val yearIndex: Int,
    val fyLabel: String,
    val openingBalance: Double,
    val withdrawnInYear: Double,
    val returnsEarned: Double,
    val closingBalance: Double
)

data class SwpResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val totalWithdrawn: Double = 0.0,
    val finalBalance: Double = 0.0,
    val monthsLasted: Int = 0,
    val plannedMonths: Int = 0,
    val corpusDepleted: Boolean = false,
    val longevityLabel: String = "",
    val sustainableMonthlyWithdrawal: Double = 0.0, // Flat monthly payout lasting full tenureYears
    val perpetuityMonthlyWithdrawal: Double = 0.0, // Monthly payout preserving initial corpus
    val yearlyRows: List<SwpYearRow> = emptyList()
)

object SwpEngine {

    fun calculate(input: SwpInput): SwpResult {
        val errors = mutableListOf<String>()
        if (input.initialCorpus <= 0.0) errors.add("Initial corpus must be greater than 0.")
        if (input.monthlyWithdrawal <= 0.0) errors.add("Monthly withdrawal must be greater than 0.")
        if (input.expectedReturnPercent < 0.0 || input.expectedReturnPercent > 40.0) {
            errors.add("Expected return must be between 0% and 40%.")
        }
        if (input.tenureYears !in 1..50) errors.add("Horizon must be between 1 and 50 years.")
        if (errors.isNotEmpty()) return SwpResult(isValid = false, validationErrors = errors)

        val rMonthly = input.expectedReturnPercent / 1200.0
        val rMonthlyBd = MoneyMath.bd(input.expectedReturnPercent)
            .divide(MoneyMath.TWELVE_HUNDRED, MoneyMath.MC)
        val stepUpFactor = 1.0 + input.annualWithdrawalStepUpPercent.coerceIn(0.0, 25.0) / 100.0

        val plannedMonths = input.tenureYears * 12
        var balanceBd = MoneyMath.bd(input.initialCorpus)
        var totalWithdrawnBd = MoneyMath.ZERO
        var currentWithdrawal = input.monthlyWithdrawal
        var monthsLasted = 0
        var depleted = false
        val rows = mutableListOf<SwpYearRow>()

        for (yr in 1..input.tenureYears) {
            if (yr > 1 && input.annualWithdrawalStepUpPercent > 0.0) {
                currentWithdrawal = MoneyMath.roundPaise(currentWithdrawal * stepUpFactor)
            }
            val openingBd = balanceBd
            var yearWithdrawnBd = MoneyMath.ZERO
            var yearReturnsBd = MoneyMath.ZERO
            val withdrawalBd = MoneyMath.bd(currentWithdrawal)

            for (m in 1..12) {
                if (balanceBd <= BigDecimal.ZERO) {
                    depleted = true
                    break
                }
                val gainBd = MoneyMath.roundPaiseBd(balanceBd.multiply(rMonthlyBd, MoneyMath.MC))
                val availableBd = balanceBd.add(gainBd)
                yearReturnsBd = yearReturnsBd.add(gainBd)

                if (availableBd >= withdrawalBd) {
                    balanceBd = MoneyMath.roundPaiseBd(availableBd.subtract(withdrawalBd))
                    yearWithdrawnBd = yearWithdrawnBd.add(withdrawalBd)
                    totalWithdrawnBd = totalWithdrawnBd.add(withdrawalBd)
                    monthsLasted++
                    if (balanceBd <= BigDecimal.ZERO) {
                        balanceBd = MoneyMath.ZERO
                        depleted = true
                        break
                    }
                } else {
                    yearWithdrawnBd = yearWithdrawnBd.add(availableBd)
                    totalWithdrawnBd = totalWithdrawnBd.add(availableBd)
                    balanceBd = MoneyMath.ZERO
                    monthsLasted++
                    depleted = true
                    break
                }
            }

            rows.add(
                SwpYearRow(
                    yearIndex = yr,
                    fyLabel = MoneyMath.financialYearLabel(input.startYear + yr - 1, input.startMonth),
                    openingBalance = openingBd.toDouble(),
                    withdrawnInYear = MoneyMath.roundPaiseBd(yearWithdrawnBd).toDouble(),
                    returnsEarned = MoneyMath.roundPaiseBd(yearReturnsBd).toDouble(),
                    closingBalance = balanceBd.toDouble()
                )
            )
            if (depleted) break
        }

        // Exact sustainable flat monthly withdrawal over tenureYears (annuity PMT formula)
        val sustainable = if (rMonthly > 0.0) {
            val pow = (1.0 + rMonthly).pow(plannedMonths)
            MoneyMath.roundPaise(input.initialCorpus * (rMonthly * pow) / (pow - 1.0))
        } else {
            MoneyMath.roundPaise(input.initialCorpus / plannedMonths.toDouble())
        }

        // Perpetuity withdrawal keeping corpus intact forever
        val perpetuity = MoneyMath.roundPaise(input.initialCorpus * rMonthly)

        val yrs = monthsLasted / 12
        val mos = monthsLasted % 12
        val longevityLabel = if (depleted && monthsLasted < plannedMonths) {
            "Corpus lasts ${yrs}y ${mos}m ($monthsLasted mos)"
        } else {
            "Survives full ${input.tenureYears} yrs"
        }

        return SwpResult(
            isValid = true,
            totalWithdrawn = MoneyMath.roundPaiseBd(totalWithdrawnBd).toDouble(),
            finalBalance = balanceBd.toDouble(),
            monthsLasted = monthsLasted,
            plannedMonths = plannedMonths,
            corpusDepleted = depleted && monthsLasted < plannedMonths,
            longevityLabel = longevityLabel,
            sustainableMonthlyWithdrawal = sustainable,
            perpetuityMonthlyWithdrawal = perpetuity,
            yearlyRows = rows
        )
    }
}

// =============================================================================
// 2. XIRR ENGINE (IRREGULAR DATED CASH FLOWS — NEWTON-RAPHSON + BISECTION)
// =============================================================================

data class CashFlowEntry(
    val id: Long = 0L,
    val dateIso: String, // YYYY-MM-DD
    val amount: Double, // Negative = Investment (Outflow), Positive = Redemption/Current Value (Inflow)
    val note: String = ""
)

data class XirrResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val xirrPercent: Double = 0.0,
    val totalInvested: Double = 0.0,
    val totalReturned: Double = 0.0,
    val netProfit: Double = 0.0,
    val absoluteReturnPercent: Double = 0.0,
    val holdingDays: Long = 0L
)

object XirrEngine {

    private const val MILLIS_PER_DAY = 86_400_000L

    fun calculate(flows: List<CashFlowEntry>): XirrResult {
        if (flows.size < 2) {
            return XirrResult(false, "Add at least 2 dated cash flows (one investment and one redemption/current value).")
        }
        val parsed = flows.mapNotNull { entry ->
            val day = parseIsoToEpochDay(entry.dateIso) ?: return@mapNotNull null
            day to entry.amount
        }.sortedBy { it.first }

        if (parsed.size != flows.size) {
            return XirrResult(false, "One or more dates are invalid. Use YYYY-MM-DD format.")
        }

        val hasNegative = parsed.any { it.second < 0.0 }
        val hasPositive = parsed.any { it.second > 0.0 }
        if (!hasNegative || !hasPositive) {
            return XirrResult(
                false,
                "Cash flows must include at least one negative outflow (investment) and one positive inflow (redemption/current value)."
            )
        }

        val firstDay = parsed.first().first
        val lastDay = parsed.last().first
        val holdingDays = (lastDay - firstDay).coerceAtLeast(1L)

        val totalInvested = MoneyMath.roundPaise(parsed.filter { it.second < 0 }.sumOf { -it.second })
        val totalReturned = MoneyMath.roundPaise(parsed.filter { it.second > 0 }.sumOf { it.second })
        val netProfit = MoneyMath.roundPaise(totalReturned - totalInvested)
        val absReturn = if (totalInvested > 0.0) MoneyMath.roundPaise((netProfit / totalInvested) * 100.0) else 0.0

        fun npv(rate: Double): Double {
            if (rate <= -0.999999) return Double.NaN
            var sum = 0.0
            for ((day, amt) in parsed) {
                val years = (day - firstDay) / 365.0
                sum += amt / (1.0 + rate).pow(years)
            }
            return sum
        }

        fun npvDerivative(rate: Double): Double {
            if (rate <= -0.999999) return Double.NaN
            var sum = 0.0
            for ((day, amt) in parsed) {
                val years = (day - firstDay) / 365.0
                if (years > 0.0) {
                    sum -= (years * amt) / (1.0 + rate).pow(years + 1.0)
                }
            }
            return sum
        }

        // Try Newton-Raphson from multiple sensible seeds
        val seeds = listOf(0.12, 0.05, 0.25, -0.10, 0.50)
        for (seed in seeds) {
            var r = seed
            var converged = false
            for (iter in 0 until 80) {
                val f = npv(r)
                val df = npvDerivative(r)
                if (f.isNaN() || df.isNaN() || abs(df) < 1e-12) break
                val next = r - f / df
                if (next <= -0.999 || next > 100.0 || next.isNaN()) break
                if (abs(next - r) < 1e-9 && abs(npv(next)) < 1e-4) {
                    r = next
                    converged = true
                    break
                }
                r = next
            }
            if (converged) {
                return XirrResult(
                    isValid = true,
                    xirrPercent = MoneyMath.roundPaise(r * 100.0),
                    totalInvested = totalInvested,
                    totalReturned = totalReturned,
                    netProfit = netProfit,
                    absoluteReturnPercent = absReturn,
                    holdingDays = holdingDays
                )
            }
        }

        // Bisection fallback across [-0.95, 10.0]
        var low = -0.95
        var high = 10.0
        var fLow = npv(low)
        val fHigh = npv(high)
        if (!fLow.isNaN() && !fHigh.isNaN() && fLow * fHigh <= 0.0) {
            repeat(100) {
                val mid = (low + high) / 2.0
                val fMid = npv(mid)
                if (abs(fMid) < 1e-6) {
                    low = mid
                    high = mid
                    return@repeat
                }
                if (fLow * fMid <= 0.0) {
                    high = mid
                } else {
                    low = mid
                    fLow = fMid
                }
            }
            val solved = (low + high) / 2.0
            return XirrResult(
                isValid = true,
                xirrPercent = MoneyMath.roundPaise(solved * 100.0),
                totalInvested = totalInvested,
                totalReturned = totalReturned,
                netProfit = netProfit,
                absoluteReturnPercent = absReturn,
                holdingDays = holdingDays
            )
        }

        return XirrResult(false, "Could not converge XIRR for the given cash flows. Check signs and dates.")
    }

    /**
     * Parses pasted bulk lines such as:
     *   2024-01-10, -10000
     *   10-Feb-2024 -10000
     *   10/01/2025, 22500
     */
    fun parseBulkCashFlows(rawText: String): List<CashFlowEntry> {
        val result = mutableListOf<CashFlowEntry>()
        var nextId = 1L
        rawText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val tokens = line.split(',', ';', '\t', ' ').filter { it.isNotBlank() }
            if (tokens.size < 2) return@forEach
            val isoDate = normalizeDateToIso(tokens[0]) ?: return@forEach
            val amtToken = tokens[1].replace("₹", "").replace(",", "")
            val amount = amtToken.toDoubleOrNull() ?: return@forEach
            val note = if (tokens.size > 2) tokens.drop(2).joinToString(" ") else ""
            result.add(CashFlowEntry(id = nextId++, dateIso = isoDate, amount = amount, note = note))
        }
        return result
    }

    fun normalizeDateToIso(raw: String): String? {
        val trimmed = raw.trim()
        val formats = listOf("yyyy-MM-dd", "dd-MMM-yyyy", "dd/MM/yyyy", "dd-MM-yyyy")
        val outFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        for (pattern in formats) {
            val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed = runCatching { sdf.parse(trimmed) }.getOrNull()
            if (parsed != null) return outFmt.format(parsed)
        }
        return null
    }

    fun parseIsoToEpochDay(iso: String): Long? {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = runCatching { sdf.parse(iso.trim()) }.getOrNull() ?: return null
        return date.time / MILLIS_PER_DAY
    }
}

// =============================================================================
// 3. MUTUAL FUND CAPITAL GAINS TAX ENGINE (DATA-DRIVEN FROM taxslabs.json)
// =============================================================================

enum class MfFundTaxCategory(val label: String, val subtitle: String) {
    EQUITY("Equity Fund (≥65% Equity)", "Sec 111A STCG / Sec 112A LTCG"),
    DEBT("Debt Fund (≤35% Equity)", "Sec 50AA — Always taxed at slab rate"),
    HYBRID_GOLD_INTL("Hybrid / Gold / International", "35–65% Equity, Gold ETF/FoF, Global")
}

data class MfTaxConfig(
    val equityLtcgHoldingMonths: Int,
    val equityLtcgExemption: Double,
    val equityLtcgRatePercent: Double,
    val equityStcgRatePercent: Double,
    val hybridLtcgHoldingMonths: Int,
    val hybridLtcgRatePercent: Double,
    val cessPercent: Double
) {
    companion object {
        fun fromResolvedConfig(resolved: Map<String, ResolvedConfigItem>): MfTaxConfig = MfTaxConfig(
            equityLtcgHoldingMonths = DataSourceResolver.resolveInt(resolved, ConfigKeys.TAX_MF_EQUITY_LTCG_MONTHS, 12),
            equityLtcgExemption = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_MF_EQUITY_LTCG_EXEMPTION, 125000.0),
            equityLtcgRatePercent = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_MF_EQUITY_LTCG_RATE, 12.5),
            equityStcgRatePercent = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_MF_EQUITY_STCG_RATE, 20.0),
            hybridLtcgHoldingMonths = DataSourceResolver.resolveInt(resolved, ConfigKeys.TAX_MF_HYBRID_LTCG_MONTHS, 24),
            hybridLtcgRatePercent = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_MF_HYBRID_LTCG_RATE, 12.5),
            cessPercent = DataSourceResolver.resolveDouble(resolved, ConfigKeys.TAX_CESS_PERCENT, 4.0)
        )
    }
}

data class MfTaxInput(
    val category: MfFundTaxCategory = MfFundTaxCategory.EQUITY,
    val investedAmount: Double = 500000.0,
    val redemptionValue: Double = 900000.0,
    val holdingMonths: Int = 24,
    val userTaxSlabPercent: Double = 30.0
)

data class MfTaxResult(
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val totalGain: Double = 0.0,
    val isLongTerm: Boolean = false,
    val exemptionApplied: Double = 0.0,
    val taxableGain: Double = 0.0,
    val appliedBaseRatePercent: Double = 0.0,
    val baseTax: Double = 0.0,
    val cessAmount: Double = 0.0,
    val totalTax: Double = 0.0,
    val effectiveTaxRateOnGainPercent: Double = 0.0,
    val netPostTaxProceeds: Double = 0.0,
    val appliedRuleLabel: String = ""
)

object MfTaxGainsEngine {

    fun calculate(input: MfTaxInput, config: MfTaxConfig): MfTaxResult {
        val errors = mutableListOf<String>()
        if (input.investedAmount <= 0.0) errors.add("Invested amount must be greater than 0.")
        if (input.redemptionValue < 0.0) errors.add("Redemption value cannot be negative.")
        if (input.holdingMonths < 1) errors.add("Holding period must be at least 1 month.")
        if (errors.isNotEmpty()) return MfTaxResult(isValid = false, validationErrors = errors)

        val gainBd = MoneyMath.roundPaiseBd(MoneyMath.bd(input.redemptionValue).subtract(MoneyMath.bd(input.investedAmount)))
        if (gainBd <= BigDecimal.ZERO) {
            return MfTaxResult(
                isValid = true,
                totalGain = gainBd.toDouble(),
                isLongTerm = when (input.category) {
                    MfFundTaxCategory.EQUITY -> input.holdingMonths >= config.equityLtcgHoldingMonths
                    MfFundTaxCategory.HYBRID_GOLD_INTL -> input.holdingMonths >= config.hybridLtcgHoldingMonths
                    MfFundTaxCategory.DEBT -> false
                },
                netPostTaxProceeds = MoneyMath.roundPaise(input.redemptionValue),
                appliedRuleLabel = "No capital gain tax (Capital loss / break-even)"
            )
        }

        val isLongTerm: Boolean
        val exemptionBd: BigDecimal
        val baseRatePercent: Double
        val ruleLabel: String

        when (input.category) {
            MfFundTaxCategory.EQUITY -> {
                isLongTerm = input.holdingMonths >= config.equityLtcgHoldingMonths
                if (isLongTerm) {
                    exemptionBd = MoneyMath.bd(config.equityLtcgExemption).min(gainBd)
                    baseRatePercent = config.equityLtcgRatePercent
                    val exemptionLabel = CurrencyFormatter.formatCompact(config.equityLtcgExemption, CurrencyCode.INR)
                    ruleLabel = "Equity LTCG (≥${config.equityLtcgHoldingMonths}m) — " +
                        "Sec 112A @ ${config.equityLtcgRatePercent}% above $exemptionLabel"
                } else {
                    exemptionBd = MoneyMath.ZERO
                    baseRatePercent = config.equityStcgRatePercent
                    ruleLabel = "Equity STCG (<${config.equityLtcgHoldingMonths}m) — Sec 111A @ ${config.equityStcgRatePercent}%"
                }
            }

            MfFundTaxCategory.DEBT -> {
                isLongTerm = false
                exemptionBd = MoneyMath.ZERO
                baseRatePercent = input.userTaxSlabPercent.coerceIn(0.0, 45.0)
                ruleLabel = "Debt MF (Sec 50AA) — Taxed at your ${baseRatePercent.toInt()}% income slab"
            }

            MfFundTaxCategory.HYBRID_GOLD_INTL -> {
                isLongTerm = input.holdingMonths >= config.hybridLtcgHoldingMonths
                exemptionBd = MoneyMath.ZERO
                if (isLongTerm) {
                    baseRatePercent = config.hybridLtcgRatePercent
                    ruleLabel = "Hybrid/Gold LTCG (≥${config.hybridLtcgHoldingMonths}m) — Sec 112 @ ${config.hybridLtcgRatePercent}%"
                } else {
                    baseRatePercent = input.userTaxSlabPercent.coerceIn(0.0, 45.0)
                    ruleLabel = "Hybrid/Gold STCG (<${config.hybridLtcgHoldingMonths}m) — Taxed at ${baseRatePercent.toInt()}% slab"
                }
            }
        }

        val taxableGainBd = MoneyMath.roundPaiseBd(gainBd.subtract(exemptionBd).max(BigDecimal.ZERO))
        val baseTaxBd = MoneyMath.percentageOf(taxableGainBd, MoneyMath.bd(baseRatePercent))
        val cessBd = MoneyMath.percentageOf(baseTaxBd, MoneyMath.bd(config.cessPercent))
        val totalTaxBd = MoneyMath.roundPaiseBd(baseTaxBd.add(cessBd))
        val netProceedsBd = MoneyMath.roundPaiseBd(MoneyMath.bd(input.redemptionValue).subtract(totalTaxBd))
        val effectiveRate = if (gainBd > BigDecimal.ZERO) {
            totalTaxBd.multiply(MoneyMath.HUNDRED, MoneyMath.MC)
                .divide(gainBd, 2, RoundingMode.HALF_UP)
                .toDouble()
        } else {
            0.0
        }

        return MfTaxResult(
            isValid = true,
            totalGain = gainBd.toDouble(),
            isLongTerm = isLongTerm,
            exemptionApplied = MoneyMath.roundPaiseBd(exemptionBd).toDouble(),
            taxableGain = taxableGainBd.toDouble(),
            appliedBaseRatePercent = baseRatePercent,
            baseTax = baseTaxBd.toDouble(),
            cessAmount = cessBd.toDouble(),
            totalTax = totalTaxBd.toDouble(),
            effectiveTaxRateOnGainPercent = effectiveRate,
            netPostTaxProceeds = netProceedsBd.toDouble(),
            appliedRuleLabel = ruleLabel
        )
    }
}
