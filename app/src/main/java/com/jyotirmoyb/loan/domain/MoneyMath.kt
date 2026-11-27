package com.jyotirmoyb.loan.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Pure Kotlin exact monetary mathematics using BigDecimal and integer paise (1 INR = 100 paise).
 * Eliminates IEEE-754 floating-point drift across multi-decade amortization schedules.
 */
object MoneyMath {

    val MC: MathContext = MathContext(16, RoundingMode.HALF_UP)
    val ZERO: BigDecimal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
    val ONE: BigDecimal = BigDecimal.ONE
    val HUNDRED: BigDecimal = BigDecimal("100")
    val TWELVE_HUNDRED: BigDecimal = BigDecimal("1200")
    val THREE_SIXTY_FIVE: BigDecimal = BigDecimal("365")

    private val SHORT_MONTHS = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    fun bd(value: Double): BigDecimal {
        if (value.isNaN() || value.isInfinite()) return ZERO
        return BigDecimal.valueOf(value)
    }

    fun roundPaiseBd(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    fun roundPaise(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return roundPaiseBd(BigDecimal.valueOf(value)).toDouble()
    }

    fun toPaise(amount: BigDecimal): Long = roundPaiseBd(amount).movePointRight(2).longValueExact()

    fun toPaise(amount: Double): Long = toPaise(bd(amount))

    fun fromPaise(paise: Long): BigDecimal = BigDecimal.valueOf(paise, 2)

    /**
     * Exact reducing-balance EMI formula computed with BigDecimal:
     *   EMI = P * r * (1 + r)^n / ((1 + r)^n - 1), where r = annualRatePercent / 1200.
     */
    fun standardEmiBd(principal: BigDecimal, annualRatePercent: BigDecimal, tenureMonths: Int): BigDecimal {
        if (principal <= BigDecimal.ZERO || tenureMonths <= 0) return ZERO
        if (annualRatePercent <= BigDecimal.ZERO) {
            return principal.divide(BigDecimal(tenureMonths), 2, RoundingMode.HALF_UP)
        }
        val r = annualRatePercent.divide(TWELVE_HUNDRED, MC)
        val onePlusR = ONE.add(r, MC)
        val factor = onePlusR.pow(tenureMonths, MC)
        val numerator = principal.multiply(r, MC).multiply(factor, MC)
        val denominator = factor.subtract(ONE, MC)
        if (denominator <= BigDecimal.ZERO) {
            return principal.divide(BigDecimal(tenureMonths), 2, RoundingMode.HALF_UP)
        }
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP)
    }

    fun percentageOf(base: BigDecimal, percent: BigDecimal): BigDecimal {
        if (base <= BigDecimal.ZERO || percent <= BigDecimal.ZERO) return ZERO
        return base.multiply(percent, MC).divide(HUNDRED, 2, RoundingMode.HALF_UP)
    }

    fun monthlyInterest(principal: BigDecimal, annualRatePercent: BigDecimal): BigDecimal {
        if (principal <= BigDecimal.ZERO || annualRatePercent <= BigDecimal.ZERO) return ZERO
        return principal.multiply(annualRatePercent, MC).divide(TWELVE_HUNDRED, 2, RoundingMode.HALF_UP)
    }

    fun dailyProRataInterest(principal: BigDecimal, annualRatePercent: BigDecimal, days: Int): BigDecimal {
        if (principal <= BigDecimal.ZERO || annualRatePercent <= BigDecimal.ZERO || days <= 0) return ZERO
        return principal
            .multiply(annualRatePercent, MC)
            .multiply(BigDecimal(days), MC)
            .divide(HUNDRED.multiply(THREE_SIXTY_FIVE, MC), 2, RoundingMode.HALF_UP)
    }

    /**
     * Formats an Indian date as `dd MMM yyyy`, e.g. `22 Oct 2026`.
     */
    fun formatDateDdMmmYyyy(day: Int, monthOneBased: Int, year: Int): String {
        val d = day.coerceIn(1, 31).toString().padStart(2, '0')
        val m = SHORT_MONTHS[(monthOneBased - 1).coerceIn(0, 11)]
        return "$d $m $year"
    }

    /**
     * Indian Financial Year runs April 1 to March 31.
     * E.g. Mar 2026 -> "FY 2025-26", Apr 2026 -> "FY 2026-27".
     */
    fun financialYearLabel(year: Int, monthOneBased: Int): String {
        val fyStart = if (monthOneBased >= 4) year else year - 1
        val fyEndShort = ((fyStart + 1) % 100).toString().padStart(2, '0')
        return "FY $fyStart-$fyEndShort"
    }
}
