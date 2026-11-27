package com.jyotirmoyb.loan.data.market

import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.domain.MarketMetricSection

internal val DefaultMarketMetricCatalog: List<MarketMetricDescriptor> = listOf(
    // 1. RBI Policy & Key Benchmarks
    MarketMetricDescriptor(
        ConfigKeys.RBI_REPO_RATE,
        "RBI Policy Repo Rate",
        "EBLR benchmark for floating loans",
        "% p.a.",
        MarketMetricSection.RBI_BENCHMARKS
    ),
    MarketMetricDescriptor(
        ConfigKeys.MARKET_RBI_SDF_RATE,
        "Standing Deposit Facility",
        "RBI policy floor rate (SDF)",
        "% p.a.",
        MarketMetricSection.RBI_BENCHMARKS
    ),
    MarketMetricDescriptor(
        ConfigKeys.MARKET_RBI_MSF_RATE,
        "MSF / Bank Rate",
        "Marginal Standing Facility ceiling",
        "% p.a.",
        MarketMetricSection.RBI_BENCHMARKS
    ),
    MarketMetricDescriptor(
        ConfigKeys.MARKET_RBI_CRR,
        "Cash Reserve Ratio (CRR)",
        "Mandatory bank reserve ratio",
        "%",
        MarketMetricSection.RBI_BENCHMARKS
    ),
    // 2. Bullion
    MarketMetricDescriptor(
        ConfigKeys.MARKET_GOLD_24K_10G,
        "Gold 24K (999)",
        "Per 10 grams (India reference)",
        "INR/10g",
        MarketMetricSection.BULLION
    ),
    MarketMetricDescriptor(
        ConfigKeys.MARKET_GOLD_22K_10G,
        "Gold 22K (916)",
        "Per 10 grams (jewellery / loan)",
        "INR/10g",
        MarketMetricSection.BULLION
    ),
    MarketMetricDescriptor(
        ConfigKeys.MARKET_SILVER_1KG,
        "Silver (999)",
        "Per 1 kilogram (India reference)",
        "INR/kg",
        MarketMetricSection.BULLION
    ),
    // 3. FX & Equity Indices
    MarketMetricDescriptor(
        ConfigKeys.MARKET_USD_INR,
        "USD / INR",
        "US Dollar to Indian Rupee",
        "INR/USD",
        MarketMetricSection.FX_INDICES
    ),
    MarketMetricDescriptor(
        ConfigKeys.MARKET_SENSEX,
        "BSE Sensex",
        "30-stock flagship benchmark",
        "pts",
        MarketMetricSection.FX_INDICES
    ),
    MarketMetricDescriptor(
        ConfigKeys.MARKET_NIFTY_50,
        "NSE Nifty 50",
        "50-stock broad market index",
        "pts",
        MarketMetricSection.FX_INDICES
    ),
    // 4. Sovereign Bond Yields
    MarketMetricDescriptor(
        ConfigKeys.MARKET_GSEC_10Y_YIELD,
        "India 10Y G-Sec Yield",
        "Sovereign benchmark (sets small-savings rates)",
        "% p.a.",
        MarketMetricSection.BONDS
    ),
    // 5. Current Small-Savings Rates (from schemes.json)
    MarketMetricDescriptor(
        ConfigKeys.SCHEME_PPF_RATE,
        "PPF",
        "15-yr EEE tax-free compounding",
        "% p.a.",
        MarketMetricSection.SMALL_SAVINGS
    ),
    MarketMetricDescriptor(
        ConfigKeys.SCHEME_SSY_RATE,
        "Sukanya Samriddhi (SSY)",
        "Girl child EEE sovereign scheme",
        "% p.a.",
        MarketMetricSection.SMALL_SAVINGS
    ),
    MarketMetricDescriptor(
        ConfigKeys.SCHEME_SCSS_RATE,
        "Senior Citizens' Savings",
        "Quarterly payout, 5-yr tenure",
        "% p.a.",
        MarketMetricSection.SMALL_SAVINGS
    ),
    MarketMetricDescriptor(
        ConfigKeys.SCHEME_NSC_RATE,
        "NSC (VIII Issue)",
        "5-yr compounded annually",
        "% p.a.",
        MarketMetricSection.SMALL_SAVINGS
    ),
    MarketMetricDescriptor(
        ConfigKeys.SCHEME_POMIS_RATE,
        "Post Office MIS",
        "Monthly sovereign income",
        "% p.a.",
        MarketMetricSection.SMALL_SAVINGS
    ),
    MarketMetricDescriptor(
        ConfigKeys.SCHEME_POTD_5Y_RATE,
        "Post Office 5Y TD",
        "Eligible for Sec 80C (Old Regime)",
        "% p.a.",
        MarketMetricSection.SMALL_SAVINGS
    ),
    MarketMetricDescriptor(
        ConfigKeys.SCHEME_KVP_RATE,
        "Kisan Vikas Patra (KVP)",
        "Guaranteed money doubling",
        "% p.a.",
        MarketMetricSection.SMALL_SAVINGS
    )
)
