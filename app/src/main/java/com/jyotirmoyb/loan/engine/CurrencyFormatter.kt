package com.jyotirmoyb.loan.engine

import kotlin.math.abs
import kotlin.math.roundToLong

object CurrencyFormatter {

    fun format(
        amount: Double,
        currency: CurrencyCode,
        showDecimals: Boolean = false
    ): String {
        val negative = amount < -0.004
        val absVal = abs(amount)
        val roundedCents = (absVal * 100.0).roundToLong()
        val whole = roundedCents / 100L
        val frac = (roundedCents % 100L).toInt()

        val displayWhole = if (showDecimals) whole else ((absVal).roundToLong())
        val grouped = when (currency) {
            CurrencyCode.INR -> formatIndianGrouping(displayWhole)
            CurrencyCode.USD -> formatWesternGrouping(displayWhole)
        }
        val sign = if (negative) "-" else ""
        return if (showDecimals) {
            "$sign${currency.symbol}$grouped.${frac.toString().padStart(2, '0')}"
        } else {
            "$sign${currency.symbol}$grouped"
        }
    }

    fun formatCompact(amount: Double, currency: CurrencyCode): String {
        val absVal = abs(amount)
        val sign = if (amount < -0.004) "-" else ""
        return when (currency) {
            CurrencyCode.INR -> when {
                absVal >= 1_00_00_000.0 -> "${sign}${String.format("%.2f", absVal / 1_00_00_000.0)} Cr"
                absVal >= 1_00_000.0 -> "${sign}${String.format("%.2f", absVal / 1_00_000.0)} Lakh"
                absVal >= 1_000.0 -> "${sign}${String.format("%.1f", absVal / 1_000.0)} K"
                else -> "${sign}${currency.symbol}${absVal.roundToLong()}"
            }
            CurrencyCode.USD -> when {
                absVal >= 1_000_000.0 -> "${sign}$${String.format("%.2f", absVal / 1_000_000.0)}M"
                absVal >= 1_000.0 -> "${sign}$${String.format("%.1f", absVal / 1_000.0)}K"
                else -> "${sign}$${absVal.roundToLong()}"
            }
        }
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

    fun formatPercent(value: Double, decimals: Int = 2): String {
        return "${String.format("%.${decimals}f", value)}%"
    }
}
