package com.jyotirmoyb.loan.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MarketSourceType(val chipLabel: String) {
    MANUAL("Manual"),
    LIVE_API("Live API"),
    ONLINE("Online"),
    BUILT_IN("Built-in");

    companion object {
        fun fromNameOrDefault(raw: String?): MarketSourceType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BUILT_IN
    }
}

enum class MarketMetricSection(val title: String) {
    RBI_BENCHMARKS("RBI Policy & Benchmark Rates"),
    BULLION("Gold & Silver (India Bullion)"),
    FX_INDICES("USD–INR & Equity Indices"),
    BONDS("Sovereign Bond Yields"),
    SMALL_SAVINGS("Current Small-Savings Rates")
}

data class MarketQuoteSample(
    val metricKey: String,
    val value: Double,
    val previousValue: Double? = null,
    val unit: String,
    val sourceType: MarketSourceType,
    val sourceName: String,
    val asOfMillis: Long
)

data class MarketHistoryPoint(
    val recordedAtMillis: Long,
    val value: Double,
    val sourceType: MarketSourceType
)

data class MarketMetricCardData(
    val key: String,
    val title: String,
    val subtitle: String,
    val section: MarketMetricSection,
    val value: Double?,
    val unit: String,
    val deltaAbs: Double?,
    val deltaPercent: Double?,
    val sourceType: MarketSourceType,
    val sourceName: String,
    val asOfMillis: Long,
    val isStale: Boolean,
    val canEditManually: Boolean = true,
    val hasManualOverride: Boolean = false
) {
    val isAvailable: Boolean get() = value != null
}

data class BankFdRateItem(
    val bankId: String,
    val bankName: String,
    val generalRate: Double,
    val seniorRate: Double,
    val tenureLabel: String,
    val isManualOverride: Boolean = false,
    val updatedOnDate: String = ""
)

data class MarketDelta(
    val absoluteChange: Double,
    val percentChange: Double
)

/**
 * Pure domain logic for Market staleness checks, delta calculation, provider precedence,
 * and Indian-formatted metric display.
 */
object MarketStalenessEvaluator {

    const val MILLIS_PER_HOUR = 3_600_000L

    fun isStale(asOfMillis: Long, nowMillis: Long, maxAgeHours: Int): Boolean {
        if (asOfMillis <= 0L) return true
        val safeHours = maxAgeHours.coerceAtLeast(1)
        val ageMillis = (nowMillis - asOfMillis).coerceAtLeast(0L)
        return ageMillis > safeHours * MILLIS_PER_HOUR
    }

    fun computeDelta(currentValue: Double?, previousValue: Double?): MarketDelta? {
        if (currentValue == null || previousValue == null) return null
        if (!currentValue.isFinite() || !previousValue.isFinite()) return null
        val curBd = BigDecimal.valueOf(currentValue)
        val prevBd = BigDecimal.valueOf(previousValue)
        val diffBd = curBd.subtract(prevBd).setScale(4, RoundingMode.HALF_UP)
        val pctBd = if (prevBd.compareTo(BigDecimal.ZERO) != 0) {
            diffBd.multiply(BigDecimal("100"))
                .divide(prevBd.abs(), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
        return MarketDelta(
            absoluteChange = diffBd.toDouble(),
            percentChange = pctBd.toDouble()
        )
    }

    /**
     * Resolves a single market metric across layered sources without ever inventing a value:
     * Priority:
     *   1. Manual user entry (highest priority)
     *   2. Fresh Live API quote (if available & enabled)
     *   3. RemoteConfig Online value (user-controlled rates.json/schemes.json)
     *   4. Last cached Room quote (graceful offline/failure fallback with original timestamp)
     *   5. Bundled seed value (built-in)
     * Returns null if no source holds a valid quote.
     */
    fun selectBestQuote(
        manualQuote: MarketQuoteSample?,
        liveApiQuote: MarketQuoteSample?,
        remoteOnlineQuote: MarketQuoteSample?,
        cachedFallbackQuote: MarketQuoteSample?,
        builtInSeedQuote: MarketQuoteSample?
    ): MarketQuoteSample? {
        if (manualQuote != null && manualQuote.value.isFinite()) return manualQuote
        if (liveApiQuote != null && liveApiQuote.value.isFinite()) return liveApiQuote
        if (remoteOnlineQuote != null && remoteOnlineQuote.value.isFinite()) return remoteOnlineQuote
        if (cachedFallbackQuote != null && cachedFallbackQuote.value.isFinite()) return cachedFallbackQuote
        if (builtInSeedQuote != null && builtInSeedQuote.value.isFinite()) return builtInSeedQuote
        return null
    }

    fun formatMetricValue(value: Double?, unit: String): String {
        if (value == null) return "Unavailable"
        return when {
            unit.contains("%") -> "${formatDecimal(value, 2)}%"
            unit.equals("INR/10g", ignoreCase = true) || unit.equals("INR/kg", ignoreCase = true) ->
                CurrencyFormatter.format(value, CurrencyCode.INR)
            unit.equals("INR/USD", ignoreCase = true) -> "₹${formatDecimal(value, 2)}"
            unit.equals("pts", ignoreCase = true) -> formatIndianNumber(value, 0)
            else -> "${formatDecimal(value, 2)} $unit"
        }
    }

    fun formatTimestamp(asOfMillis: Long): String {
        if (asOfMillis <= 0L) return "Unknown date"
        val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)
        return fmt.format(Date(asOfMillis))
    }

    fun parseIsoDateToMillis(isoDate: String): Long {
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)?.time ?: 0L
        }.getOrDefault(0L)
    }

    private fun formatDecimal(value: Double, scale: Int): String =
        BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString()

    private fun formatIndianNumber(value: Double, decimals: Int): String {
        val formatted = CurrencyFormatter.format(value, CurrencyCode.INR)
        val stripped = formatted.removePrefix("₹").trim()
        return if (decimals == 0) stripped.substringBefore(".") else stripped
    }
}
