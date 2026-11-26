package com.jyotirmoyb.loan.data.market

import com.jyotirmoyb.loan.data.MarketDao
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.ConfigValueSource
import com.jyotirmoyb.loan.data.remote.RatesRepository
import com.jyotirmoyb.loan.domain.MarketQuoteSample
import com.jyotirmoyb.loan.domain.MarketSourceType
import com.jyotirmoyb.loan.domain.MarketStalenessEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

/**
 * Pluggable data provider contract for Market metrics.
 * Implementations cover:
 *  (a) RemoteConfigMarketProvider (primary source: user-controlled rates.json / schemes.json)
 *  (b) Optional public API providers (ExchangeRateApiProvider, MetalsApiProvider, EquityIndexApiProvider)
 *  (c) ManualEntryMarketProvider (user's offline manual entries)
 */
interface MarketDataProvider {
    val providerId: String
    val displayName: String
    val sourceType: MarketSourceType
    val supportedKeys: Set<String>

    suspend fun fetchQuotes(): Result<Map<String, MarketQuoteSample>>
}

/**
 * (a) Primary provider backed by RemoteConfig (`rates.json` & `schemes.json`).
 */
class RemoteConfigMarketProvider(
    private val ratesRepository: RatesRepository
) : MarketDataProvider {

    override val providerId: String = "remote_config_primary"
    override val displayName: String = "LoanJB Remote Config (rates.json)"
    override val sourceType: MarketSourceType = MarketSourceType.ONLINE
    override val supportedKeys: Set<String> = PRIMARY_MARKET_KEYS

    override suspend fun fetchQuotes(): Result<Map<String, MarketQuoteSample>> = runCatching {
        val resolvedMap = ratesRepository.resolvedMapFlow.first()
        val prevMap = ratesRepository.marketPreviousValuesFlow.first()
        val result = LinkedHashMap<String, MarketQuoteSample>()

        for (key in supportedKeys) {
            val item = resolvedMap[key] ?: continue
            val numeric = item.numericValue ?: continue
            if (item.source == ConfigValueSource.MANUAL) continue // Manual provider handles overrides
            val srcType = if (item.source == ConfigValueSource.ONLINE) {
                MarketSourceType.ONLINE
            } else {
                MarketSourceType.BUILT_IN
            }
            val parsedMillis = MarketStalenessEvaluator.parseIsoDateToMillis(item.effectiveFrom)
            result[key] = MarketQuoteSample(
                metricKey = key,
                value = numeric,
                previousValue = prevMap[key],
                unit = item.unit,
                sourceType = srcType,
                sourceName = item.sourceName,
                asOfMillis = if (parsedMillis > 0L) parsedMillis else System.currentTimeMillis()
            )
        }
        result
    }

    companion object {
        val PRIMARY_MARKET_KEYS: Set<String> = setOf(
            ConfigKeys.RBI_REPO_RATE,
            ConfigKeys.MARKET_RBI_SDF_RATE,
            ConfigKeys.MARKET_RBI_MSF_RATE,
            ConfigKeys.MARKET_RBI_CRR,
            ConfigKeys.MARKET_GSEC_10Y_YIELD,
            ConfigKeys.MARKET_GOLD_24K_10G,
            ConfigKeys.MARKET_GOLD_22K_10G,
            ConfigKeys.MARKET_SILVER_1KG,
            ConfigKeys.MARKET_USD_INR,
            ConfigKeys.MARKET_SENSEX,
            ConfigKeys.MARKET_NIFTY_50,
            ConfigKeys.SCHEME_PPF_RATE,
            ConfigKeys.SCHEME_SSY_RATE,
            ConfigKeys.SCHEME_NSC_RATE,
            ConfigKeys.SCHEME_SCSS_RATE,
            ConfigKeys.SCHEME_POMIS_RATE,
            ConfigKeys.SCHEME_POTD_5Y_RATE,
            ConfigKeys.SCHEME_KVP_RATE
        )
    }
}

/**
 * (b-1) Optional pluggable public API provider for USD–INR using user's own ExchangeRate-API key
 * stored in EncryptedSharedPreferences. Skipped cleanly if no key is configured.
 */
class ExchangeRateApiProvider(
    private val keyStore: SecureApiKeyStore,
    private val client: OkHttpClient = createSafeMarketHttpClient()
) : MarketDataProvider {

    override val providerId: String = "api_exchangerate_fx"
    override val displayName: String = "ExchangeRate-API (USD–INR)"
    override val sourceType: MarketSourceType = MarketSourceType.LIVE_API
    override val supportedKeys: Set<String> = setOf(ConfigKeys.MARKET_USD_INR)

    override suspend fun fetchQuotes(): Result<Map<String, MarketQuoteSample>> = withContext(Dispatchers.IO) {
        val apiKey = keyStore.getApiKey(MarketApiProviderSlot.FX_EXCHANGERATE)
            ?: return@withContext Result.success(emptyMap())

        if (!SAFE_KEY_REGEX.matches(apiKey)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid FX API key format."))
        }

        runCatching {
            val req = Request.Builder()
                .url("https://v6.exchangerate-api.com/v6/$apiKey/latest/USD")
                .header("Accept", "application/json")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("FX API HTTP ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                if (body.length > MAX_API_BODY_BYTES) throw IllegalStateException("FX API response too large")
                val json = JSONObject(body)
                val rates = json.optJSONObject("conversion_rates")
                    ?: throw IllegalStateException("Missing conversion_rates")
                val inr = rates.optDouble("INR", Double.NaN)
                if (!inr.isFinite() || inr <= 0.0) throw IllegalStateException("Invalid INR rate")
                val asOfSecs = json.optLong("time_last_update_unix", 0L)
                val asOfMillis = if (asOfSecs > 0L) asOfSecs * 1000L else System.currentTimeMillis()
                val rounded = BigDecimal.valueOf(inr).setScale(2, RoundingMode.HALF_UP).toDouble()

                mapOf(
                    ConfigKeys.MARKET_USD_INR to MarketQuoteSample(
                        metricKey = ConfigKeys.MARKET_USD_INR,
                        value = rounded,
                        previousValue = null,
                        unit = "INR/USD",
                        sourceType = MarketSourceType.LIVE_API,
                        sourceName = "ExchangeRate-API",
                        asOfMillis = asOfMillis
                    )
                )
            }
        }
    }
}

/**
 * (b-2) Optional pluggable public API provider for Gold (24K/22K per 10g) & Silver (per kg) in INR
 * using user's own MetalpriceAPI key stored in EncryptedSharedPreferences.
 */
class MetalsApiProvider(
    private val keyStore: SecureApiKeyStore,
    private val client: OkHttpClient = createSafeMarketHttpClient()
) : MarketDataProvider {

    override val providerId: String = "api_metalprice_bullion"
    override val displayName: String = "MetalpriceAPI (Gold & Silver)"
    override val sourceType: MarketSourceType = MarketSourceType.LIVE_API
    override val supportedKeys: Set<String> = setOf(
        ConfigKeys.MARKET_GOLD_24K_10G,
        ConfigKeys.MARKET_GOLD_22K_10G,
        ConfigKeys.MARKET_SILVER_1KG
    )

    override suspend fun fetchQuotes(): Result<Map<String, MarketQuoteSample>> = withContext(Dispatchers.IO) {
        val apiKey = keyStore.getApiKey(MarketApiProviderSlot.METALS_BULLION)
            ?: return@withContext Result.success(emptyMap())

        if (!SAFE_KEY_REGEX.matches(apiKey)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid Metals API key format."))
        }

        runCatching {
            val req = Request.Builder()
                .url("https://api.metalpriceapi.com/v1/latest?api_key=$apiKey&base=INR&currencies=XAU,XAG")
                .header("Accept", "application/json")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Metals API HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                if (body.length > MAX_API_BODY_BYTES) throw IllegalStateException("Metals API response too large")
                val json = JSONObject(body)
                if (!json.optBoolean("success", true)) {
                    throw IllegalStateException("Metals API returned error")
                }
                val rates = json.optJSONObject("rates") ?: throw IllegalStateException("Missing rates object")
                val inrPerOzGold = rates.optDouble("INRXAU", Double.NaN).takeIf { it.isFinite() && it > 0 }
                    ?: rates.optDouble("XAU", Double.NaN).takeIf { it.isFinite() && it > 0 }?.let { 1.0 / it }
                val inrPerOzSilver = rates.optDouble("INRXAG", Double.NaN).takeIf { it.isFinite() && it > 0 }
                    ?: rates.optDouble("XAG", Double.NaN).takeIf { it.isFinite() && it > 0 }?.let { 1.0 / it }

                val tsSecs = json.optLong("timestamp", 0L)
                val asOfMillis = if (tsSecs > 0L) tsSecs * 1000L else System.currentTimeMillis()
                val out = LinkedHashMap<String, MarketQuoteSample>()

                // 1 troy ounce = 31.1034768 grams
                val gramsPerTroyOz = BigDecimal("31.1034768")
                if (inrPerOzGold != null) {
                    val gold24k10g = BigDecimal.valueOf(inrPerOzGold)
                        .divide(gramsPerTroyOz, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal("10"))
                        .setScale(0, RoundingMode.HALF_UP)
                    val gold22k10g = gold24k10g
                        .multiply(BigDecimal("22"))
                        .divide(BigDecimal("24"), 0, RoundingMode.HALF_UP)
                    out[ConfigKeys.MARKET_GOLD_24K_10G] = MarketQuoteSample(
                        metricKey = ConfigKeys.MARKET_GOLD_24K_10G,
                        value = gold24k10g.toDouble(),
                        unit = "INR/10g",
                        sourceType = MarketSourceType.LIVE_API,
                        sourceName = "MetalpriceAPI",
                        asOfMillis = asOfMillis
                    )
                    out[ConfigKeys.MARKET_GOLD_22K_10G] = MarketQuoteSample(
                        metricKey = ConfigKeys.MARKET_GOLD_22K_10G,
                        value = gold22k10g.toDouble(),
                        unit = "INR/10g",
                        sourceType = MarketSourceType.LIVE_API,
                        sourceName = "MetalpriceAPI",
                        asOfMillis = asOfMillis
                    )
                }
                if (inrPerOzSilver != null) {
                    val silver1kg = BigDecimal.valueOf(inrPerOzSilver)
                        .divide(gramsPerTroyOz, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal("1000"))
                        .setScale(0, RoundingMode.HALF_UP)
                    out[ConfigKeys.MARKET_SILVER_1KG] = MarketQuoteSample(
                        metricKey = ConfigKeys.MARKET_SILVER_1KG,
                        value = silver1kg.toDouble(),
                        unit = "INR/kg",
                        sourceType = MarketSourceType.LIVE_API,
                        sourceName = "MetalpriceAPI",
                        asOfMillis = asOfMillis
                    )
                }
                out
            }
        }
    }
}

/**
 * (b-3) Optional pluggable public API provider for BSE Sensex & NSE Nifty 50 via Twelve Data.
 */
class EquityIndexApiProvider(
    private val keyStore: SecureApiKeyStore,
    private val client: OkHttpClient = createSafeMarketHttpClient()
) : MarketDataProvider {

    override val providerId: String = "api_twelvedata_indices"
    override val displayName: String = "Twelve Data (Sensex & Nifty)"
    override val sourceType: MarketSourceType = MarketSourceType.LIVE_API
    override val supportedKeys: Set<String> = setOf(ConfigKeys.MARKET_SENSEX, ConfigKeys.MARKET_NIFTY_50)

    override suspend fun fetchQuotes(): Result<Map<String, MarketQuoteSample>> = withContext(Dispatchers.IO) {
        val apiKey = keyStore.getApiKey(MarketApiProviderSlot.EQUITY_INDICES)
            ?: return@withContext Result.success(emptyMap())

        if (!SAFE_KEY_REGEX.matches(apiKey)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid Equity API key format."))
        }

        runCatching {
            val req = Request.Builder()
                .url("https://api.twelvedata.com/quote?symbol=SENSEX,NIFTY&apikey=$apiKey")
                .header("Accept", "application/json")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Equity API HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                if (body.length > MAX_API_BODY_BYTES) throw IllegalStateException("Equity API response too large")
                val json = JSONObject(body)
                val out = LinkedHashMap<String, MarketQuoteSample>()
                val now = System.currentTimeMillis()

                parseTwelveQuote(json.optJSONObject("SENSEX"), ConfigKeys.MARKET_SENSEX, now)?.let {
                    out[ConfigKeys.MARKET_SENSEX] = it
                }
                parseTwelveQuote(json.optJSONObject("NIFTY"), ConfigKeys.MARKET_NIFTY_50, now)?.let {
                    out[ConfigKeys.MARKET_NIFTY_50] = it
                }
                out
            }
        }
    }

    private fun parseTwelveQuote(obj: JSONObject?, metricKey: String, fallbackMillis: Long): MarketQuoteSample? {
        if (obj == null) return null
        val close = obj.optString("close").toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val prev = obj.optString("previous_close").toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        val ts = obj.optLong("timestamp", 0L)
        return MarketQuoteSample(
            metricKey = metricKey,
            value = BigDecimal.valueOf(close).setScale(2, RoundingMode.HALF_UP).toDouble(),
            previousValue = prev,
            unit = "pts",
            sourceType = MarketSourceType.LIVE_API,
            sourceName = "Twelve Data",
            asOfMillis = if (ts > 0L) ts * 1000L else fallbackMillis
        )
    }
}

/**
 * (c) Manual entry provider backed by user-entered Room overrides.
 */
class ManualEntryMarketProvider(
    private val marketDao: MarketDao
) : MarketDataProvider {

    override val providerId: String = "manual_entry"
    override val displayName: String = "Manual User Entry"
    override val sourceType: MarketSourceType = MarketSourceType.MANUAL
    override val supportedKeys: Set<String> = RemoteConfigMarketProvider.PRIMARY_MARKET_KEYS

    override suspend fun fetchQuotes(): Result<Map<String, MarketQuoteSample>> = runCatching {
        marketDao.getAllQuotesOnce()
            .filter { it.sourceType == MarketSourceType.MANUAL.name }
            .associate { entity ->
                entity.metricKey to MarketQuoteSample(
                    metricKey = entity.metricKey,
                    value = entity.value,
                    previousValue = entity.previousValue,
                    unit = entity.unit,
                    sourceType = MarketSourceType.MANUAL,
                    sourceName = entity.sourceName,
                    asOfMillis = entity.asOfMillis
                )
            }
    }
}

private const val MAX_API_BODY_BYTES = 128 * 1024
private val SAFE_KEY_REGEX = Regex("^[A-Za-z0-9._-]{4,128}$")

private fun createSafeMarketHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .followRedirects(false)
    .build()
