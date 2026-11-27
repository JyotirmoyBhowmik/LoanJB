package com.jyotirmoyb.loan.data.remote

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.math.BigDecimal

val REMOTE_CONFIG_FILES: List<String> = listOf(
    "rates.json",
    "taxslabs.json",
    "schemes.json",
    "articles.json"
)

const val REMOTE_CONFIG_BASE_URL =
    "https://raw.githubusercontent.com/JyotirmoyBhowmik/LoanJB/main/"

const val UNVERIFIED_MARKER = "UNVERIFIED"

enum class ConfigValueSource {
    MANUAL,
    IMPORTED,
    ONLINE,
    BUILT_IN
}

enum class ConfigCategory(val displayTitle: String) {
    LOAN_RATES("Loan Rates"),
    DEPOSIT_RATES("Deposit Rates"),
    TAX_SLABS("Tax Slabs"),
    SCHEME_RATES("Scheme Rates"),
    MARKET_DATA("Market Data");

    companion object {
        fun fromWireName(raw: String?, fileName: String): ConfigCategory {
            if (!raw.isNullOrBlank()) {
                entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }?.let { return it }
            }
            return when (fileName) {
                "taxslabs.json" -> TAX_SLABS
                "schemes.json" -> SCHEME_RATES
                "articles.json" -> MARKET_DATA
                else -> LOAN_RATES
            }
        }
    }
}

/**
 * Type-safe scalar wrapper supporting both numeric rates/caps and string article values
 * without resorting to `Any`.
 */
data class ConfigScalar(val textValue: String, val numericValue: Double? = textValue.toDoubleOrNull()) {
    val isNumeric: Boolean get() = numericValue != null

    fun formattedNumber(): String {
        val num = numericValue ?: return textValue
        val bd = BigDecimal.valueOf(num).stripTrailingZeros()
        return bd.toPlainString()
    }
}

class ConfigScalarJsonAdapter : JsonAdapter<ConfigScalar>() {
    override fun fromJson(reader: JsonReader): ConfigScalar? = when (reader.peek()) {
        JsonReader.Token.NUMBER -> {
            val raw = reader.nextString()
            val num = raw.toDoubleOrNull()
                ?: throw JsonDataException("Invalid numeric value: $raw")
            ConfigScalar(textValue = raw, numericValue = num)
        }
        JsonReader.Token.STRING -> {
            val text = reader.nextString()
            ConfigScalar(textValue = text, numericValue = null)
        }
        JsonReader.Token.NULL -> {
            reader.nextNull<Unit>()
            null
        }
        else -> throw JsonDataException("Expected number or string for config value at ${reader.path}")
    }

    override fun toJson(writer: JsonWriter, value: ConfigScalar?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        val num = value.numericValue
        if (num != null) {
            writer.value(num)
        } else {
            writer.value(value.textValue)
        }
    }
}

@JsonClass(generateAdapter = true)
data class TaxSlabBand(
    val upTo: Double?, // null represents infinity (top slab)
    val ratePercent: Double
)

@JsonClass(generateAdapter = true)
data class SurchargeBand(val aboveIncome: Double, val ratePercent: Double)

@JsonClass(generateAdapter = true)
data class TaxDeductionRule(
    val id: String,
    val label: String,
    val section: String,
    val capGeneral: Double?, // null = uncapped
    val capSenior: Double? = null,
    val allowedInOld: Boolean,
    val allowedInNew: Boolean,
    val hint: String = ""
)

@JsonClass(generateAdapter = true)
data class TaxRegimeRules(
    val regimeId: String, // "OLD" or "NEW"
    val displayName: String,
    val standardDeductionSalaried: Double,
    val rebateIncomeLimit: Double,
    val maxRebateAmount: Double,
    val rebateMarginalRelief: Boolean,
    val slabsGeneral: List<TaxSlabBand>,
    val slabsSenior: List<TaxSlabBand>? = null,
    val slabsSuperSenior: List<TaxSlabBand>? = null,
    val surchargeBands: List<SurchargeBand> = emptyList(),
    val surchargeMarginalRelief: Boolean = true
)

@JsonClass(generateAdapter = true)
data class TaxYearRules(
    val fyCode: String, // e.g. "FY 2026-27"
    val ayCode: String, // e.g. "AY 2027-28"
    val lawReference: String,
    val effectiveFrom: String,
    val cessPercent: Double,
    val newRegime: TaxRegimeRules,
    val oldRegime: TaxRegimeRules,
    val deductions: List<TaxDeductionRule>
)

@JsonClass(generateAdapter = true)
data class AdvanceTaxInstallmentRule(
    val dueDateLabel: String, // e.g. "15 Jun"
    val calendarMonth: Int, // 6, 9, 12, 3
    val calendarDay: Int, // 15
    val cumulativePercent: Double,
    val label: String
)

data class TaxRulesCatalog(
    val taxYears: List<TaxYearRules>,
    val gstSlabs: List<Double>,
    val advanceTaxThreshold: Double,
    val advanceTaxSchedule: List<AdvanceTaxInstallmentRule>,
    val hraMetroPercent: Double,
    val hraNonMetroPercent: Double,
    val fdTdsGeneralThreshold: Double,
    val fdTdsSeniorThreshold: Double,
    val tdsRateWithPan: Double,
    val tdsRateWithoutPan: Double,
    val dataVersion: String,
    val publishedOn: String,
    val asOfDateFormatted: String,
    val sourceName: String,
    val sourceUrl: String,
    val lastVerified: String,
    val source: ConfigValueSource
)

@JsonClass(generateAdapter = true)
data class ApyContributionRow(
    val entryAge: Int,
    val pension1000: Double,
    val pension2000: Double,
    val pension3000: Double,
    val pension4000: Double,
    val pension5000: Double
) {
    fun contributionForSlab(pensionSlab: Int): Double = when (pensionSlab) {
        1000 -> pension1000
        2000 -> pension2000
        3000 -> pension3000
        4000 -> pension4000
        else -> pension5000
    }
}

@JsonClass(generateAdapter = true)
data class PmsymContributionRow(val entryAge: Int, val monthlyContribution: Double)

@JsonClass(generateAdapter = true)
data class SeniorSchemeStatusRule(
    val id: String,
    val name: String,
    val shortName: String,
    val status: String, // "OPEN" or "CLOSED"
    val statusLabel: String,
    val statusNote: String,
    val closedOn: String? = null,
    val ratePercent: Double,
    val payoutFrequency: String, // "MONTHLY", "QUARTERLY", "HALF_YEARLY"
    val tenureYears: Int,
    val maxInvestmentCap: Double?,
    val minAge: Int,
    val sovereignIssuer: String,
    val existingHoldersNote: String = ""
) {
    val isClosed: Boolean get() = status.equals("CLOSED", ignoreCase = true)
}

@JsonClass(generateAdapter = true)
data class AssetAllocationRow(
    val ageMin: Int,
    val ageMax: Int,
    val ageBandLabel: String,
    val riskProfile: String, // "CONSERVATIVE", "MODERATE", "AGGRESSIVE"
    val equityPercent: Double,
    val debtPercent: Double,
    val goldPercent: Double,
    val cashLiquidPercent: Double,
    val horizonHint: String,
    val rebalanceNote: String
)

data class RetirementSchemesCatalog(
    val apyTable: List<ApyContributionRow>,
    val apyCorpusMultipliers: Map<Int, Double>, // pensionSlab -> indicative nominee corpus at 60
    val pmsymTable: List<PmsymContributionRow>,
    val pmsymAssuredPension: Double,
    val pmsymIncomeCeiling: Double,
    val seniorPensionSchemes: List<SeniorSchemeStatusRule>,
    val assetAllocationMatrix: List<AssetAllocationRow>,
    val dataVersion: String,
    val publishedOn: String,
    val asOfDateFormatted: String,
    val sourceName: String,
    val lastVerified: String,
    val source: ConfigValueSource
)

@JsonClass(generateAdapter = true)
data class BankFdRateSeed(
    val bankId: String,
    val bankName: String,
    val generalRate: Double,
    val seniorRate: Double,
    val tenureLabel: String
)

@JsonClass(generateAdapter = true)
data class RemoteArticleRecord(
    val id: String,
    val title: String,
    val summary: String,
    val category: String,
    val lastReviewed: String,
    val tags: List<String> = emptyList(),
    val assetPath: String? = null,
    val markdownBody: String? = null,
    val sourceName: String = "LoanJB Editorial"
)

@JsonClass(generateAdapter = true)
data class RemoteConfigDocument(
    val schemaVersion: Int,
    val dataVersion: String,
    val publishedOn: String,
    val items: List<RemoteConfigItem>,
    val taxYears: List<TaxYearRules>? = null,
    val gstSlabs: List<Double>? = null,
    val advanceTaxThreshold: Double? = null,
    val advanceTaxSchedule: List<AdvanceTaxInstallmentRule>? = null,
    val apyTable: List<ApyContributionRow>? = null,
    val pmsymTable: List<PmsymContributionRow>? = null,
    val seniorPensionSchemes: List<SeniorSchemeStatusRule>? = null,
    val assetAllocationMatrix: List<AssetAllocationRow>? = null,
    val bankFdRates: List<BankFdRateSeed>? = null,
    val articles: List<RemoteArticleRecord>? = null
)

@JsonClass(generateAdapter = true)
data class RemoteConfigItem(
    val key: String,
    val value: ConfigScalar,
    val unit: String,
    val effectiveFrom: String,
    val sourceName: String,
    val sourceUrl: String = "",
    val lastVerified: String = UNVERIFIED_MARKER,
    val label: String? = null,
    val category: String? = null,
    val previousValue: Double? = null
)

data class ResolvedConfigItem(
    val key: String,
    val label: String,
    val category: ConfigCategory,
    val fileName: String,
    val valueText: String,
    val numericValue: Double?,
    val unit: String,
    val effectiveFrom: String,
    val asOfDate: String,
    val sourceName: String,
    val sourceUrl: String,
    val lastVerified: String,
    val source: ConfigValueSource,
    val defaultValueText: String,
    val defaultNumericValue: Double?
) {
    val isUnverified: Boolean
        get() = source != ConfigValueSource.MANUAL &&
            lastVerified.equals(UNVERIFIED_MARKER, ignoreCase = true)

    val isOverridden: Boolean
        get() = source == ConfigValueSource.MANUAL

    val isEditableNumeric: Boolean
        get() = defaultNumericValue != null || numericValue != null
}

@JsonClass(generateAdapter = true)
data class ManualOverrideRecord(val key: String, val value: String, val updatedOnDate: String)

@JsonClass(generateAdapter = true)
data class ManualOverridesExport(
    val schemaVersion: Int = 1,
    val exportedOn: String,
    val overrides: List<ManualOverrideRecord> = emptyList()
)

data class SyncStatus(
    val lastSyncAttemptMillis: Long = 0L,
    val lastSyncSuccessMillis: Long = 0L,
    val lastResultMessage: String = "Using built-in seed data",
    val lastResultSuccess: Boolean = true,
    val isSyncing: Boolean = false
)

object RemoteConfigMoshi {
    val instance: Moshi by lazy {
        Moshi.Builder()
            .add(ConfigScalar::class.java, ConfigScalarJsonAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }
}

object ConfigKeys {
    const val RBI_REPO_RATE = "market.rbi_repo_rate"
    const val MARKET_RBI_SDF_RATE = "market.rbi_sdf_rate"
    const val MARKET_RBI_MSF_RATE = "market.rbi_msf_rate"
    const val MARKET_RBI_CRR = "market.rbi_crr"
    const val MARKET_GSEC_10Y_YIELD = "market.gsec_10y_yield"
    const val MARKET_CPI_INFLATION = "market.cpi_inflation"
    const val MARKET_GOLD_24K_10G = "market.gold_24k_10g"
    const val MARKET_GOLD_22K_10G = "market.gold_22k_10g"
    const val MARKET_SILVER_1KG = "market.silver_1kg"
    const val MARKET_USD_INR = "market.usd_inr"
    const val MARKET_SENSEX = "market.sensex"
    const val MARKET_NIFTY_50 = "market.nifty_50"
    const val HOME_EBLR_SPREAD = "loan.home.eblr_spread"
    const val HOME_RATE_MIN = "loan.home.rate_min"
    const val HOME_RATE_MAX = "loan.home.rate_max"
    const val HOME_FIXED_MIN = "loan.home.fixed_min"
    const val HOME_FIXED_MAX = "loan.home.fixed_max"
    const val HOME_LTV_CAP = "loan.home.ltv_cap"
    const val PERSONAL_RATE_MIN = "loan.personal.rate_min"
    const val PERSONAL_RATE_MAX = "loan.personal.rate_max"
    const val VEHICLE_CAR_MIN = "loan.vehicle.car_min"
    const val VEHICLE_CAR_MAX = "loan.vehicle.car_max"
    const val VEHICLE_EV_MIN = "loan.vehicle.ev_min"
    const val VEHICLE_TWOWHEELER_MAX = "loan.vehicle.twowheeler_max"
    const val VEHICLE_LTV_CAP = "loan.vehicle.ltv_cap"
    const val EDUCATION_INDIA_MIN = "loan.education.india_min"
    const val EDUCATION_ABROAD_MAX = "loan.education.abroad_max"
    const val GOLD_RATE_MIN = "loan.gold.rate_min"
    const val GOLD_RATE_MAX = "loan.gold.rate_max"
    const val GOLD_LTV_CAP = "loan.gold.ltv_cap"
    const val GOLD_AUCTION_RISK_LTV = "loan.gold.auction_risk_ltv"
    const val LAP_RATE_MIN = "loan.lap.rate_min"
    const val LAP_RATE_MAX = "loan.lap.rate_max"
    const val LAP_LTV_CAP = "loan.lap.ltv_cap"
    const val SECURITIES_RATE_MIN = "loan.securities.rate_min"
    const val SECURITIES_RATE_MAX = "loan.securities.rate_max"
    const val SECURITIES_LTV_CAP = "loan.securities.ltv_cap"
    const val BUSINESS_RATE_MIN = "loan.business.rate_min"
    const val BUSINESS_RATE_MAX = "loan.business.rate_max"
    const val CC_EMI_RATE_MIN = "loan.cc_emi.rate_min"
    const val CC_EMI_RATE_MAX = "loan.cc_emi.rate_max"
    const val BANK_STAFF_HOUSING = "loan.bank_staff.housing"
    const val BANK_STAFF_VEHICLE = "loan.bank_staff.vehicle"
    const val BANK_STAFF_PERSONAL = "loan.bank_staff.personal"
    const val BANK_STAFF_EDUCATION = "loan.bank_staff.education"
    const val BANK_STAFF_HOUSING_AMOUNT = "loan.bank_staff.housing_default_amount"
    const val BANK_STAFF_VEHICLE_AMOUNT = "loan.bank_staff.vehicle_default_amount"
    const val BANK_STAFF_PERSONAL_AMOUNT = "loan.bank_staff.personal_default_amount"
    const val BANK_STAFF_EDUCATION_AMOUNT = "loan.bank_staff.education_default_amount"
    const val BANK_STAFF_HOUSING_TENURE = "loan.bank_staff.housing_max_tenure"
    const val BANK_STAFF_VEHICLE_TENURE = "loan.bank_staff.vehicle_max_tenure"
    const val BANK_STAFF_PERSONAL_TENURE = "loan.bank_staff.personal_max_tenure"
    const val BANK_STAFF_EDUCATION_TENURE = "loan.bank_staff.education_max_tenure"

    const val FOIR_LOW_PERCENT = "loan.foir.low_income_percent"
    const val FOIR_MID_PERCENT = "loan.foir.mid_income_percent"
    const val FOIR_HIGH_PERCENT = "loan.foir.high_income_percent"
    const val FOIR_LOW_THRESHOLD = "loan.foir.low_income_threshold"
    const val FOIR_HIGH_THRESHOLD = "loan.foir.high_income_threshold"

    const val GST_BANKING_SERVICES = "tax.gst.banking_services"
    const val SEC_24B_CAP = "tax.sec24b.self_occupied_cap"
    const val SEC_24B_LETOUT_SETOFF_CAP = "tax.sec24b.let_out_loss_setoff_cap"
    const val SEC_80C_CAP = "tax.sec80c.cap"
    const val SEC_80E_MAX_YEARS = "tax.sec80e.max_years"
    const val TAX_NEW_STD_DEDUCTION = "tax.new_regime.std_deduction"
    const val TAX_OLD_STD_DEDUCTION = "tax.old_regime.std_deduction"
    const val TAX_NEW_REBATE_LIMIT = "tax.new_regime.rebate_limit"
    const val TAX_OLD_REBATE_LIMIT = "tax.old_regime.rebate_limit"
    const val TAX_CESS_PERCENT = "tax.cess_percent"
    const val TAX_OLD_SLAB1 = "tax.old_regime.slab1_limit"
    const val TAX_OLD_SLAB2 = "tax.old_regime.slab2_limit"
    const val TAX_OLD_SLAB3 = "tax.old_regime.slab3_limit"
    const val TAX_NEW_SLAB1 = "tax.new_regime.slab1_limit"
    const val TAX_NEW_SLAB2 = "tax.new_regime.slab2_limit"
    const val TAX_NEW_SLAB3 = "tax.new_regime.slab3_limit"
    const val TAX_NEW_SLAB4 = "tax.new_regime.slab4_limit"
    const val TAX_NEW_SLAB5 = "tax.new_regime.slab5_limit"
    const val TAX_NEW_SLAB6 = "tax.new_regime.slab6_limit"
    const val TAX_NEW_SEC24B_SO_ALLOWED = "tax.new_regime.sec24b_self_occupied_allowed"
    const val TAX_NEW_SEC80C_ALLOWED = "tax.new_regime.sec80c_allowed"
    const val TAX_NEW_SEC80E_ALLOWED = "tax.new_regime.sec80e_allowed"

    const val DEPOSIT_FD_GENERAL = "deposit.fd.general_1y_5y"
    const val DEPOSIT_FD_SENIOR_BONUS = "deposit.fd.senior_bonus"
    const val DEPOSIT_RD_GENERAL = "deposit.rd.general"
    const val DEPOSIT_DEBT_FUND_EXPECTED = "deposit.debt_fund.expected_return"

    const val TDS_FD_GENERAL_THRESHOLD = "tax.tds.fd_general_threshold"
    const val TDS_FD_SENIOR_THRESHOLD = "tax.tds.fd_senior_threshold"
    const val TDS_RATE_WITH_PAN = "tax.tds.rate_with_pan"
    const val TDS_RATE_WITHOUT_PAN = "tax.tds.rate_without_pan"

    const val SCHEME_QUARTER_LABEL = "scheme.small_savings.quarter"
    const val SCHEME_PPF_RATE = "scheme.ppf.rate"
    const val SCHEME_PPF_YEARLY_CAP = "scheme.ppf.yearly_cap"
    const val SCHEME_PPF_TENURE_YEARS = "scheme.ppf.tenure_years"
    const val SCHEME_PPF_EXTENSION_YEARS = "scheme.ppf.extension_block_years"
    const val SCHEME_SSY_RATE = "scheme.ssy.rate"
    const val SCHEME_SSY_YEARLY_CAP = "scheme.ssy.yearly_cap"
    const val SCHEME_SSY_DEPOSIT_YEARS = "scheme.ssy.deposit_years"
    const val SCHEME_SSY_MATURITY_YEARS = "scheme.ssy.maturity_years"
    const val SCHEME_NSC_RATE = "scheme.nsc.rate"
    const val SCHEME_NSC_TENURE_YEARS = "scheme.nsc.tenure_years"
    const val SCHEME_SCSS_RATE = "scheme.scss.rate"
    const val SCHEME_SCSS_MAX_CAP = "scheme.scss.max_cap"
    const val SCHEME_SCSS_TENURE_YEARS = "scheme.scss.tenure_years"
    const val SCHEME_POMIS_RATE = "scheme.pomis.rate"
    const val SCHEME_POMIS_SINGLE_CAP = "scheme.pomis.max_single_cap"
    const val SCHEME_POMIS_JOINT_CAP = "scheme.pomis.max_joint_cap"
    const val SCHEME_POMIS_TENURE_YEARS = "scheme.pomis.tenure_years"
    const val SCHEME_POTD_1Y_RATE = "scheme.potd.rate_1y"
    const val SCHEME_POTD_2Y_RATE = "scheme.potd.rate_2y"
    const val SCHEME_POTD_3Y_RATE = "scheme.potd.rate_3y"
    const val SCHEME_POTD_5Y_RATE = "scheme.potd.rate_5y"
    const val SCHEME_PORD_RATE = "scheme.pord.rate"
    const val SCHEME_PORD_TENURE_MONTHS = "scheme.pord.tenure_months"
    const val SCHEME_KVP_RATE = "scheme.kvp.rate"
    const val SCHEME_KVP_DOUBLING_MONTHS = "scheme.kvp.doubling_months"

    const val PMAY_MAX_SUBSIDY = "scheme.pmay.max_subsidy"
    const val MUDRA_SHISHU_CAP = "scheme.mudra.shishu_cap"
    const val MUDRA_KISHORE_CAP = "scheme.mudra.kishore_cap"
    const val MUDRA_TARUN_CAP = "scheme.mudra.tarun_cap"
    const val MUDRA_TARUN_PLUS_CAP = "scheme.mudra.tarun_plus_cap"

    // Mutual Fund Defaults & Capital Gains Tax Keys
    const val MF_EQUITY_EXPECTED_RETURN = "mf.equity.expected_return"
    const val MF_DEFAULT_INFLATION = "mf.inflation.default_rate"
    const val TAX_MF_EQUITY_LTCG_MONTHS = "tax.mf.equity_ltcg_holding_months"
    const val TAX_MF_EQUITY_LTCG_EXEMPTION = "tax.mf.equity_ltcg_exemption"
    const val TAX_MF_EQUITY_LTCG_RATE = "tax.mf.equity_ltcg_rate"
    const val TAX_MF_EQUITY_STCG_RATE = "tax.mf.equity_stcg_rate"
    const val TAX_MF_HYBRID_LTCG_MONTHS = "tax.mf.hybrid_ltcg_holding_months"
    const val TAX_MF_HYBRID_LTCG_RATE = "tax.mf.hybrid_ltcg_rate"

    // Additional Taxation Keys (HRA, 80D, Advance Tax)
    const val TAX_HRA_METRO_PERCENT = "tax.hra.metro_percent"
    const val TAX_HRA_NON_METRO_PERCENT = "tax.hra.non_metro_percent"
    const val TAX_SEC_80D_SELF_CAP = "tax.sec80d.self_cap"
    const val TAX_SEC_80D_SENIOR_CAP = "tax.sec80d.senior_cap"
    const val TAX_ADVANCE_TAX_THRESHOLD = "tax.advance_tax.threshold"

    // Retirement, EPF, NPS, Pension & Financial Planner Keys
    const val SCHEME_EPF_RATE = "scheme.epf.rate"
    const val SCHEME_EPF_EMPLOYEE_PERCENT = "scheme.epf.employee_percent"
    const val SCHEME_EPF_EMPLOYER_EPF_PERCENT = "scheme.epf.employer_epf_percent"
    const val SCHEME_EPF_EMPLOYER_EPS_PERCENT = "scheme.epf.employer_eps_percent"
    const val SCHEME_EPF_WAGE_CEILING = "scheme.epf.wage_ceiling"
    const val SCHEME_EPF_EPS_MONTHLY_CAP = "scheme.epf.eps_monthly_cap"
    const val SCHEME_NPS_EXPECTED_RETURN = "scheme.nps.expected_return"
    const val SCHEME_NPS_MIN_ANNUITY_PERCENT = "scheme.nps.min_annuity_percent"
    const val SCHEME_NPS_MAX_LUMPSUM_PERCENT = "scheme.nps.max_lumpsum_percent"
    const val SCHEME_NPS_ANNUITY_RATE = "scheme.nps.annuity_rate"
    const val SCHEME_NPS_SMALL_CORPUS_LIMIT = "scheme.nps.small_corpus_full_exit_limit"
    const val SCHEME_PMSYM_ASSURED_PENSION = "scheme.pmsym.assured_pension"
    const val SCHEME_PMSYM_INCOME_CEILING = "scheme.pmsym.income_ceiling"
    const val SCHEME_RETIREMENT_INFLATION = "scheme.retirement.default_inflation"
    const val SCHEME_RETIREMENT_PRE_RETURN = "scheme.retirement.pre_return"
    const val SCHEME_RETIREMENT_POST_RETURN = "scheme.retirement.post_return"
    const val PLANNER_NEEDS_PERCENT = "planner.rules.needs_percent"
    const val PLANNER_WANTS_PERCENT = "planner.rules.wants_percent"
    const val PLANNER_SAVINGS_PERCENT = "planner.rules.savings_percent"
    const val PLANNER_EMERGENCY_MONTHS = "planner.rules.emergency_months_default"
    const val PLANNER_TERM_MULTIPLIER = "planner.rules.term_insurance_multiplier"
    const val PLANNER_HEALTH_METRO = "planner.rules.health_cover_metro"
    const val PLANNER_HEALTH_NON_METRO = "planner.rules.health_cover_non_metro"
}
