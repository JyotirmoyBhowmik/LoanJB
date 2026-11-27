package com.jyotirmoyb.loan.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object CurrencyFormatter {

    fun format(amount: Double, currency: CurrencyCode = CurrencyCode.INR, showDecimals: Boolean = false): String =
        format(MoneyMath.bd(amount), currency, showDecimals)

    fun format(amount: BigDecimal, currency: CurrencyCode = CurrencyCode.INR, showDecimals: Boolean = false): String {
        val rounded = amount.setScale(2, RoundingMode.HALF_UP)
        val negative = rounded < BigDecimal.ZERO
        val absBd = rounded.abs()
        val paise = absBd.movePointRight(2).longValueExact()
        val whole = paise / 100L
        val frac = (paise % 100L).toInt()

        val displayWhole = if (showDecimals) whole else absBd.setScale(0, RoundingMode.HALF_UP).longValueExact()
        val grouped = when (currency) {
            CurrencyCode.INR, CurrencyCode.NPR -> formatIndianGrouping(displayWhole)
            CurrencyCode.USD -> formatWesternGrouping(displayWhole)
        }
        val sign = if (negative) "-" else ""
        return if (showDecimals) {
            "$sign${currency.symbol}$grouped.${frac.toString().padStart(2, '0')}"
        } else {
            "$sign${currency.symbol}$grouped"
        }
    }

    /**
     * Short human-friendly Indian/International labels, e.g. "28.0 Lakh", "1.2 Cr", "45.0 K".
     */
    fun formatShortLabel(amount: Double, currency: CurrencyCode = CurrencyCode.INR): String {
        val absVal = abs(amount)
        val sign = if (amount < -0.004) "-" else ""
        return when (currency) {
            CurrencyCode.INR, CurrencyCode.NPR -> when {
                absVal >= 1_00_00_000.0 -> {
                    val cr = absVal / 1_00_00_000.0
                    val formatted = if (abs(cr * 10 - (cr * 10).roundToLong()) < 0.05) {
                        String.format(Locale.US, "%.1f", cr)
                    } else {
                        String.format(Locale.US, "%.2f", cr)
                    }
                    "$sign$formatted Cr"
                }
                absVal >= 1_00_000.0 -> "${sign}${String.format(Locale.US, "%.1f", absVal / 1_00_000.0)} Lakh"
                absVal >= 1_000.0 -> "${sign}${String.format(Locale.US, "%.1f", absVal / 1_000.0)} K"
                else -> "${sign}${currency.symbol}${absVal.roundToLong()}"
            }
            CurrencyCode.USD -> when {
                absVal >= 1_000_000.0 -> "$sign$${String.format(Locale.US, "%.2f", absVal / 1_000_000.0)}M"
                absVal >= 1_000.0 -> "$sign$${String.format(Locale.US, "%.1f", absVal / 1_000.0)}K"
                else -> "$sign$${absVal.roundToLong()}"
            }
        }
    }

    fun formatCompact(amount: Double, currency: CurrencyCode = CurrencyCode.INR): String = formatShortLabel(amount, currency)

    fun formatInputGrouping(whole: Long, currency: CurrencyCode = CurrencyCode.INR): String = when (currency) {
        CurrencyCode.INR, CurrencyCode.NPR -> formatIndianGrouping(whole)
        CurrencyCode.USD -> formatWesternGrouping(whole)
    }

    fun formatIndianGrouping(value: Long): String {
        val digits = abs(value).toString()
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val sb = StringBuilder()
        var i = rest.length
        while (i > 0) {
            val start = maxOf(0, i - 2)
            if (sb.isNotEmpty()) sb.insert(0, ',')
            sb.insert(0, rest.substring(start, i))
            i = start
        }
        sb.append(',').append(lastThree)
        return sb.toString()
    }

    fun formatWesternGrouping(value: Long): String {
        val digits = abs(value).toString()
        if (digits.length <= 3) return digits
        val sb = StringBuilder()
        var count = 0
        for (idx in digits.length - 1 downTo 0) {
            if (count > 0 && count % 3 == 0) sb.append(',')
            sb.append(digits[idx])
            count++
        }
        return sb.reverse().toString()
    }

    fun formatPercent(value: Double, decimals: Int = 2): String = "${String.format(Locale.US, "%.${decimals}f", value)}%"
}
