package com.jyotirmoyb.loan.data.market

import com.jyotirmoyb.loan.data.BankFdRateEntity
import com.jyotirmoyb.loan.data.MarketDao
import com.jyotirmoyb.loan.data.MarketHistoryEntity
import com.jyotirmoyb.loan.data.MarketQuoteCacheEntity
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.ConfigValueSource
import com.jyotirmoyb.loan.data.remote.RatesRepository
import com.jyotirmoyb.loan.domain.BankFdRateItem
import com.jyotirmoyb.loan.domain.MarketHistoryPoint
import com.jyotirmoyb.loan.domain.MarketMetricCardData
import com.jyotirmoyb.loan.domain.MarketMetricSection
import com.jyotirmoyb.loan.domain.MarketQuoteSample
import com.jyotirmoyb.loan.domain.MarketSourceType
import com.jyotirmoyb.loan.domain.MarketStalenessEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class MarketMetricDescriptor(
    val key: String,
    val title: String,
    val subtitle: String,
    val unit: String,
    val section: MarketMetricSection
)

data class MarketDashboardSnapshot(
    val cards: List<MarketMetricCardData> = emptyList(),
    val bankFdRates: List<BankFdRateItem> = emptyList(),
    val historyByMetric: Map<String, List<MarketHistoryPoint>> = emptyMap(),
    val staleLimitHours: Int = 24,
    val isRefreshing: Boolean = false,
    val lastRefreshMessage: String = "Using cached / built-in market rates",
    val configuredApiProvidersCount: Int = 0
)

class MarketRepository(
    private val marketDao: MarketDao,
    private val ratesRepository: RatesRepository,
    private val prefsRepo: UserPreferencesRepository,
    private val apiKeyStore: SecureApiKeyStore,
    private val liveApiProviders: List<MarketDataProvider> = listOf(
        ExchangeRateApiProvider(apiKeyStore),
        MetalsApiProvider(apiKeyStore),
        EquityIndexApiProvider(apiKeyStore)
    )
) {
    private val primaryProvider = RemoteConfigMarketProvider(ratesRepository)
    private val _isRefreshing = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow("Loaded from Remote Config & local cache")

    val dashboardFlow: Flow<MarketDashboardSnapshot> = combine(
        ratesRepository.resolvedMapFlow,
        ratesRepository.marketPreviousValuesFlow,
        ratesRepository.bankFdSeedFlow,
        marketDao.observeAllQuotes(),
        marketDao.observeAllHistory(),
        marketDao.observeBankFdRates(),
        prefsRepo.preferencesFlow,
        apiKeyStore.statusFlow,
        _isRefreshing,
        _statusMessage
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val resolvedMap = args[0] as Map<String, com.jyotirmoyb.loan.data.remote.ResolvedConfigItem>
        @Suppress("UNCHECKED_CAST")
        val prevMap = args[1] as Map<String, Double>
        @Suppress("UNCHECKED_CAST")
        val bankSeeds = args[2] as List<com.jyotirmoyb.loan.data.remote.BankFdRateSeed>
        @Suppress("UNCHECKED_CAST")
        val cachedQuotes = args[3] as List<MarketQuoteCacheEntity>
        @Suppress("UNCHECKED_CAST")
        val historyEntities = args[4] as List<MarketHistoryEntity>
        @Suppress("UNCHECKED_CAST")
        val customBankFds = args[5] as List<BankFdRateEntity>
        val prefs = args[6] as com.jyotirmoyb.loan.data.UserPreferences
        val apiStatus = args[7] as ApiKeyStatusSnapshot
        val refreshing = args[8] as Boolean
        val statusMsg = args[9] as String

        val nowMillis = System.currentTimeMillis()
        val cachedByKey = cachedQuotes.associateBy { it.metricKey }
        val historyByMetric = historyEntities.groupBy { it.metricKey }.mapValues { (_, list) ->
            list.map {
                MarketHistoryPoint(
                    recordedAtMillis = it.recordedAtMillis,
                    value = it.value,
                    sourceType = MarketSourceType.fromNameOrDefault(it.sourceType)
                )
            }
        }

        val cards = METRIC_CATALOG.map { desc ->
            val configItem = resolvedMap[desc.key]
            val cached = cachedByKey[desc.key]

            val manualSample: MarketQuoteSample? = when {
                cached != null && cached.sourceType == MarketSourceType.MANUAL.name -> MarketQuoteSample(
                    metricKey = desc.key,
                    value = cached.value,
                    previousValue = cached.previousValue ?: prevMap[desc.key],
                    unit = cached.unit,
                    sourceType = MarketSourceType.MANUAL,
                    sourceName = cached.sourceName,
                    asOfMillis = cached.asOfMillis
                )
                configItem != null && configItem.source == ConfigValueSource.MANUAL && configItem.numericValue != null -> MarketQuoteSample(
                    metricKey = desc.key,
                    value = configItem.numericValue,
                    previousValue = prevMap[desc.key],
                    unit = configItem.unit,
                    sourceType = MarketSourceType.MANUAL,
                    sourceName = "Manual Override",
                    asOfMillis = MarketStalenessEvaluator.parseIsoDateToMillis(configItem.effectiveFrom)
                        .takeIf { it > 0L } ?: nowMillis
                )
                else -> null
            }

            val liveApiSample = cached?.takeIf { it.sourceType == MarketSourceType.LIVE_API.name }?.let {
                MarketQuoteSample(
                    metricKey = desc.key,
                    value = it.value,
                    previousValue = it.previousValue ?: prevMap[desc.key],
                    unit = it.unit,
                    sourceType = MarketSourceType.LIVE_API,
                    sourceName = it.sourceName,
                    asOfMillis = it.asOfMillis
                )
            }

            val onlineConfigSample = configItem?.takeIf {
                it.source == ConfigValueSource.ONLINE && it.numericValue != null
            }?.let {
                MarketQuoteSample(
                    metricKey = desc.key,
                    value = it.numericValue!!,
                    previousValue = prevMap[desc.key],
                    unit = it.unit,
                    sourceType = MarketSourceType.ONLINE,
                    sourceName = it.sourceName,
                    asOfMillis = MarketStalenessEvaluator.parseIsoDateToMillis(it.effectiveFrom)
                        .takeIf { ts -> ts > 0L } ?: nowMillis
                )
            }

            val cachedFallbackSample = cached?.let {
                MarketQuoteSample(
                    metricKey = desc.key,
                    value = it.value,
                    previousValue = it.previousValue ?: prevMap[desc.key],
                    unit = it.unit,
                    sourceType = MarketSourceType.fromNameOrDefault(it.sourceType),
                    sourceName = it.sourceName,
                    asOfMillis = it.asOfMillis
                )
            }

            val builtInSample = configItem?.takeIf {
                it.source == ConfigValueSource.BUILT_IN && it.numericValue != null
            }?.let {
                MarketQuoteSample(
                    metricKey = desc.key,
                    value = it.numericValue!!,
                    previousValue = prevMap[desc.key],
                    unit = it.unit,
                    sourceType = MarketSourceType.BUILT_IN,
                    sourceName = it.sourceName,
                    asOfMillis = MarketStalenessEvaluator.parseIsoDateToMillis(it.effectiveFrom)
                )
            }

            val chosen = MarketStalenessEvaluator.selectBestQuote(
                manualQuote = manualSample,
                liveApiQuote = liveApiSample,
                remoteOnlineQuote = onlineConfigSample,
                cachedFallbackQuote = cachedFallbackSample,
                builtInSeedQuote = builtInSample
            )

            val metricHistory = historyByMetric[desc.key].orEmpty()
            val priorFromHistory = metricHistory.dropLast(1).lastOrNull()?.value
            val delta = MarketStalenessEvaluator.computeDelta(
                currentValue = chosen?.value,
                previousValue = chosen?.previousValue ?: priorFromHistory
            )
            val asOf = chosen?.asOfMillis ?: 0L
            val stale = MarketStalenessEvaluator.isStale(asOf, nowMillis, prefs.marketStaleHours)

            MarketMetricCardData(
                key = desc.key,
                title = desc.title,
                subtitle = desc.subtitle,
                section = desc.section,
                value = chosen?.value,
                unit = chosen?.unit ?: desc.unit,
                deltaAbs = delta?.absoluteChange,
                deltaPercent = delta?.percentChange,
                sourceType = chosen?.sourceType ?: MarketSourceType.BUILT_IN,
                sourceName = chosen?.sourceName ?: "No data",
                asOfMillis = asOf,
                isStale = stale,
                hasManualOverride = manualSample != null
            )
        }

        // Merge editable bank FD rates: Room edits override/extend seed rows
        val customMap = customBankFds.associateBy { it.bankId }
        val mergedBanks = LinkedHashMap<String, BankFdRateItem>()
        for (seed in bankSeeds) {
            val custom = customMap[seed.bankId]
            mergedBanks[seed.bankId] = if (custom != null) {
                BankFdRateItem(
                    bankId = custom.bankId,
                    bankName = custom.bankName,
                    generalRate = custom.generalRate,
                    seniorRate = custom.seniorRate,
                    tenureLabel = custom.tenureLabel,
                    isManualOverride = custom.isManualOverride,
                    updatedOnDate = custom.updatedOnDate
                )
            } else {
                BankFdRateItem(
                    bankId = seed.bankId,
                    bankName = seed.bankName,
                    generalRate = seed.generalRate,
                    seniorRate = seed.seniorRate,
                    tenureLabel = seed.tenureLabel,
                    isManualOverride = false
                )
            }
        }
        for (custom in customBankFds) {
            if (!mergedBanks.containsKey(custom.bankId)) {
                mergedBanks[custom.bankId] = BankFdRateItem(
                    bankId = custom.bankId,
                    bankName = custom.bankName,
                    generalRate = custom.generalRate,
                    seniorRate = custom.seniorRate,
                    tenureLabel = custom.tenureLabel,
                    isManualOverride = true,
                    updatedOnDate = custom.updatedOnDate
                )
            }
        }

        MarketDashboardSnapshot(
            cards = cards,
            bankFdRates = mergedBanks.values.sortedByDescending { it.generalRate },
            historyByMetric = historyByMetric,
            staleLimitHours = prefs.marketStaleHours,
            isRefreshing = refreshing,
            lastRefreshMessage = statusMsg,
            configuredApiProvidersCount = apiStatus.configuredCount
        )
    }

    /**
     * Ensures initial baseline history points exist in Room from cached/seed data so charts
     * reflect real recorded values (never synthetic random walks).
     */
    suspend fun ensureBaselineCached() = withContext(Dispatchers.IO) {
        val primaryQuotes = primaryProvider.fetchQuotes().getOrNull().orEmpty()
        for ((key, sample) in primaryQuotes) {
            val existingCount = marketDao.countHistory(key)
            if (existingCount == 0) {
                if (sample.previousValue != null && sample.previousValue != sample.value) {
                    marketDao.insertHistoryPoint(
                        MarketHistoryEntity(
                            metricKey = key,
                            value = sample.previousValue,
                            recordedAtMillis = (sample.asOfMillis - 86_400_000L).coerceAtLeast(1L),
                            sourceType = sample.sourceType.name
                        )
                    )
                }
                marketDao.insertHistoryPoint(
                    MarketHistoryEntity(
                        metricKey = key,
                        value = sample.value,
                        recordedAtMillis = sample.asOfMillis,
                        sourceType = sample.sourceType.name
                    )
                )
            }
        }
    }

    /**
     * Refreshes primary RemoteConfig (`rates.json`) AND any optional Live API providers whose
     * API keys are configured in EncryptedSharedPreferences.
     * On failure, preserves the last cached values with their original timestamps gracefully.
     */
    suspend fun refreshAll(): String = withContext(Dispatchers.IO) {
        _isRefreshing.value = true
        val now = System.currentTimeMillis()
        var liveUpdatedCount = 0
        var liveFailedCount = 0
        val notes = mutableListOf<String>()

        try {
            val useOnline = prefsRepo.preferencesFlow.first().useOnlineData
            val remoteSummary = ratesRepository.refreshNow()
            if (remoteSummary.updatedFiles > 0) {
                notes.add("Remote config updated")
            }

            // Record any updated RemoteConfig values into history
            val primaryQuotes = primaryProvider.fetchQuotes().getOrNull().orEmpty()
            for ((key, sample) in primaryQuotes) {
                val existing = marketDao.getQuote(key)
                if (existing == null || existing.sourceType != MarketSourceType.MANUAL.name) {
                    // Only overwrite cache with RemoteConfig if not currently held by fresher LIVE_API
                    if (existing == null || existing.sourceType != MarketSourceType.LIVE_API.name) {
                        persistSampleWithHistory(sample, existing?.value, now)
                    }
                }
            }

            // Invoke optional public API providers only when online data is enabled
            if (useOnline) {
                for (provider in liveApiProviders) {
                    val fetchRes = provider.fetchQuotes()
                    fetchRes.fold(
                        onSuccess = { quotes ->
                            for ((key, sample) in quotes) {
                                val existing = marketDao.getQuote(key)
                                if (existing?.sourceType == MarketSourceType.MANUAL.name) continue
                                persistSampleWithHistory(sample, existing?.value, now)
                                liveUpdatedCount++
                            }
                        },
                        onFailure = {
                            liveFailedCount++
                        }
                    )
                }
            }

            val finalMsg = when {
                !useOnline -> "Offline mode — showing cached / built-in market values"
                liveUpdatedCount > 0 && liveFailedCount == 0 ->
                    "Updated $liveUpdatedCount live quote(s) & synced Remote Config"
                liveFailedCount > 0 ->
                    "Live API unavailable ($liveFailedCount failed) — showing last cached values"
                remoteSummary.success ->
                    "Market synced (${remoteSummary.message})"
                else ->
                    "Offline or sync failed — showing last cached values"
            }
            _statusMessage.value = finalMsg
            finalMsg
        } finally {
            _isRefreshing.value = false
        }
    }

    suspend fun saveManualMetric(metricKey: String, newValue: Double): Result<Unit> = withContext(Dispatchers.IO) {
        if (!newValue.isFinite() || newValue < 0.0) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid non-negative number."))
        }
        val desc = METRIC_CATALOG.firstOrNull { it.key == metricKey }
            ?: return@withContext Result.failure(IllegalArgumentException("Unknown metric key."))
        val now = System.currentTimeMillis()
        val prev = marketDao.getQuote(metricKey)?.value
        val sample = MarketQuoteSample(
            metricKey = metricKey,
            value = newValue,
            previousValue = prev,
            unit = desc.unit,
            sourceType = MarketSourceType.MANUAL,
            sourceName = "Manual Entry",
            asOfMillis = now
        )
        persistSampleWithHistory(sample, prev, now)
        ratesRepository.setManualOverrideByKey(metricKey, newValue)
        _statusMessage.value = "Saved manual value for ${desc.title}"
        Result.success(Unit)
    }

    suspend fun resetManualMetric(metricKey: String) = withContext(Dispatchers.IO) {
        marketDao.deleteManualQuote(metricKey)
        ratesRepository.resetToDefault(metricKey)
        _statusMessage.value = "Reset metric to default provider"
    }

    suspend fun saveBankFdRate(
        bankId: String?,
        bankName: String,
        generalRate: Double,
        seniorRate: Double,
        tenureLabel: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanName = bankName.trim()
        if (cleanName.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Bank name cannot be empty."))
        if (!generalRate.isFinite() || generalRate !in 0.0..25.0 || !seniorRate.isFinite() || seniorRate !in 0.0..25.0) {
            return@withContext Result.failure(IllegalArgumentException("Rates must be between 0% and 25%."))
        }
        val id = bankId?.takeIf { it.isNotBlank() } ?: "custom_${UUID.randomUUID().toString().take(8)}"
        val today = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date())
        marketDao.upsertBankFdRate(
            BankFdRateEntity(
                bankId = id,
                bankName = cleanName,
                generalRate = generalRate,
                seniorRate = seniorRate,
                tenureLabel = tenureLabel.trim().ifEmpty { "1–3 yrs" },
                isManualOverride = true,
                updatedOnDate = today
            )
        )
        Result.success(Unit)
    }

    suspend fun deleteBankFdRate(bankId: String) = withContext(Dispatchers.IO) {
        marketDao.deleteBankFdRate(bankId)
    }

    suspend fun resetAllBankFdRates() = withContext(Dispatchers.IO) {
        marketDao.clearAllBankFdRates()
    }

    private suspend fun persistSampleWithHistory(
        sample: MarketQuoteSample,
        previousCachedValue: Double?,
        nowMillis: Long
    ) {
        marketDao.upsertQuote(
            MarketQuoteCacheEntity(
                metricKey = sample.metricKey,
                value = sample.value,
                previousValue = sample.previousValue ?: previousCachedValue,
                unit = sample.unit,
                sourceType = sample.sourceType.name,
                sourceName = sample.sourceName,
                asOfMillis = sample.asOfMillis,
                updatedAtMillis = nowMillis
            )
        )
        val latest = marketDao.getLatestHistoryPoint(sample.metricKey)
        val valueChanged = latest == null || kotlin.math.abs(latest.value - sample.value) > 1e-6
        val olderThanHour = latest == null || (sample.asOfMillis - latest.recordedAtMillis) >= MarketStalenessEvaluator.MILLIS_PER_HOUR
        if (valueChanged || olderThanHour) {
            marketDao.insertHistoryPoint(
                MarketHistoryEntity(
                    metricKey = sample.metricKey,
                    value = sample.value,
                    recordedAtMillis = sample.asOfMillis.coerceAtLeast(1L),
                    sourceType = sample.sourceType.name
                )
            )
        }
    }

    companion object {
        val METRIC_CATALOG: List<MarketMetricDescriptor> = listOf(
            // 1. RBI Policy & Key Benchmarks
            MarketMetricDescriptor(ConfigKeys.RBI_REPO_RATE, "RBI Policy Repo Rate", "EBLR benchmark for floating loans", "% p.a.", MarketMetricSection.RBI_BENCHMARKS),
            MarketMetricDescriptor(ConfigKeys.MARKET_RBI_SDF_RATE, "Standing Deposit Facility", "RBI policy floor rate (SDF)", "% p.a.", MarketMetricSection.RBI_BENCHMARKS),
            MarketMetricDescriptor(ConfigKeys.MARKET_RBI_MSF_RATE, "MSF / Bank Rate", "Marginal Standing Facility ceiling", "% p.a.", MarketMetricSection.RBI_BENCHMARKS),
            MarketMetricDescriptor(ConfigKeys.MARKET_RBI_CRR, "Cash Reserve Ratio (CRR)", "Mandatory bank reserve ratio", "%", MarketMetricSection.RBI_BENCHMARKS),
            // 2. Bullion
            MarketMetricDescriptor(ConfigKeys.MARKET_GOLD_24K_10G, "Gold 24K (999)", "Per 10 grams (India reference)", "INR/10g", MarketMetricSection.BULLION),
            MarketMetricDescriptor(ConfigKeys.MARKET_GOLD_22K_10G, "Gold 22K (916)", "Per 10 grams (jewellery / loan)", "INR/10g", MarketMetricSection.BULLION),
            MarketMetricDescriptor(ConfigKeys.MARKET_SILVER_1KG, "Silver (999)", "Per 1 kilogram (India reference)", "INR/kg", MarketMetricSection.BULLION),
            // 3. FX & Equity Indices
            MarketMetricDescriptor(ConfigKeys.MARKET_USD_INR, "USD / INR", "US Dollar to Indian Rupee", "INR/USD", MarketMetricSection.FX_INDICES),
            MarketMetricDescriptor(ConfigKeys.MARKET_SENSEX, "BSE Sensex", "30-stock flagship benchmark", "pts", MarketMetricSection.FX_INDICES),
            MarketMetricDescriptor(ConfigKeys.MARKET_NIFTY_50, "NSE Nifty 50", "50-stock broad market index", "pts", MarketMetricSection.FX_INDICES),
            // 4. Sovereign Bond Yields
            MarketMetricDescriptor(ConfigKeys.MARKET_GSEC_10Y_YIELD, "India 10Y G-Sec Yield", "Sovereign benchmark (sets small-savings rates)", "% p.a.", MarketMetricSection.BONDS),
            // 5. Current Small-Savings Rates (from schemes.json)
            MarketMetricDescriptor(ConfigKeys.SCHEME_PPF_RATE, "PPF", "15-yr EEE tax-free compounding", "% p.a.", MarketMetricSection.SMALL_SAVINGS),
            MarketMetricDescriptor(ConfigKeys.SCHEME_SSY_RATE, "Sukanya Samriddhi (SSY)", "Girl child EEE sovereign scheme", "% p.a.", MarketMetricSection.SMALL_SAVINGS),
            MarketMetricDescriptor(ConfigKeys.SCHEME_SCSS_RATE, "Senior Citizens' Savings", "Quarterly payout, 5-yr tenure", "% p.a.", MarketMetricSection.SMALL_SAVINGS),
            MarketMetricDescriptor(ConfigKeys.SCHEME_NSC_RATE, "NSC (VIII Issue)", "5-yr compounded annually", "% p.a.", MarketMetricSection.SMALL_SAVINGS),
            MarketMetricDescriptor(ConfigKeys.SCHEME_POMIS_RATE, "Post Office MIS", "Monthly sovereign income", "% p.a.", MarketMetricSection.SMALL_SAVINGS),
            MarketMetricDescriptor(ConfigKeys.SCHEME_POTD_5Y_RATE, "Post Office 5Y TD", "Eligible for Sec 80C (Old Regime)", "% p.a.", MarketMetricSection.SMALL_SAVINGS),
            MarketMetricDescriptor(ConfigKeys.SCHEME_KVP_RATE, "Kisan Vikas Patra (KVP)", "Guaranteed money doubling", "% p.a.", MarketMetricSection.SMALL_SAVINGS)
        )
    }
}
