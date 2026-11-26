package com.jyotirmoyb.loan.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.RegionCode
import com.jyotirmoyb.loan.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore by preferencesDataStore(name = "loanjb_settings")

data class UserPreferences(
    val region: RegionCode = RegionCode.IN,
    val currency: CurrencyCode = CurrencyCode.INR,
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val languageCode: String = "en",
    val monthlyIncome: Double = 150000.0,
    val preferredTaxRegime: String = "NEW", // "NEW" or "OLD"
    val userAge: Int = 32,
    val riskProfile: String = "MODERATE", // "CONSERVATIVE", "MODERATE", "AGGRESSIVE"
    val appLockEnabled: Boolean = false,
    val flagSecureEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val useOnlineData: Boolean = true,
    val marketStaleHours: Int = 24
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val REGION = stringPreferencesKey("region_code")
        val CURRENCY = stringPreferencesKey("currency")
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language_code")
        val MONTHLY_INCOME = doublePreferencesKey("monthly_income")
        val TAX_REGIME = stringPreferencesKey("preferred_tax_regime")
        val USER_AGE = intPreferencesKey("user_age")
        val RISK_PROFILE = stringPreferencesKey("risk_profile")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val FLAG_SECURE_ENABLED = booleanPreferencesKey("flag_secure_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val USE_ONLINE_DATA = booleanPreferencesKey("use_online_data")
        val MARKET_STALE_HOURS = intPreferencesKey("market_stale_hours")
    }

    val preferencesFlow: Flow<UserPreferences> = context.userPrefsDataStore.data.map { prefs ->
        val region = runCatching {
            RegionCode.valueOf(prefs[Keys.REGION] ?: RegionCode.IN.name)
        }.getOrDefault(RegionCode.IN)
        val currency = runCatching {
            CurrencyCode.valueOf(prefs[Keys.CURRENCY] ?: region.defaultCurrency.name)
        }.getOrDefault(CurrencyCode.INR)
        val theme = runCatching {
            AppThemeMode.valueOf(prefs[Keys.THEME] ?: AppThemeMode.LIGHT.name)
        }.getOrDefault(AppThemeMode.LIGHT)
        val language = (prefs[Keys.LANGUAGE] ?: "en").takeIf { it == "en" || it == "hi" } ?: "en"
        val income = prefs[Keys.MONTHLY_INCOME] ?: 150000.0
        val taxRegime = (prefs[Keys.TAX_REGIME] ?: "NEW").takeIf { it == "NEW" || it == "OLD" } ?: "NEW"
        val userAge = (prefs[Keys.USER_AGE] ?: 32).coerceIn(18, 85)
        val riskProfile = (prefs[Keys.RISK_PROFILE] ?: "MODERATE")
            .takeIf { it in setOf("CONSERVATIVE", "MODERATE", "AGGRESSIVE") } ?: "MODERATE"
        val appLock = prefs[Keys.APP_LOCK_ENABLED] ?: false
        val flagSecure = prefs[Keys.FLAG_SECURE_ENABLED] ?: false
        val onboarded = prefs[Keys.ONBOARDING_COMPLETED] ?: false
        val useOnlineData = prefs[Keys.USE_ONLINE_DATA] ?: true
        val staleHours = (prefs[Keys.MARKET_STALE_HOURS] ?: 24).coerceIn(1, 720)
        UserPreferences(
            region = region,
            currency = currency,
            themeMode = theme,
            languageCode = language,
            monthlyIncome = income,
            preferredTaxRegime = taxRegime,
            userAge = userAge,
            riskProfile = riskProfile,
            appLockEnabled = appLock,
            flagSecureEnabled = flagSecure,
            onboardingCompleted = onboarded,
            useOnlineData = useOnlineData,
            marketStaleHours = staleHours
        )
    }

    suspend fun setRegion(region: RegionCode) {
        context.userPrefsDataStore.edit {
            it[Keys.REGION] = region.name
            it[Keys.CURRENCY] = region.defaultCurrency.name
        }
    }

    suspend fun setCurrency(currency: CurrencyCode) {
        context.userPrefsDataStore.edit { it[Keys.CURRENCY] = currency.name }
    }

    suspend fun setThemeMode(themeMode: AppThemeMode) {
        context.userPrefsDataStore.edit { it[Keys.THEME] = themeMode.name }
    }

    suspend fun setLanguageCode(languageCode: String) {
        val normalized = if (languageCode == "hi") "hi" else "en"
        context.userPrefsDataStore.edit { it[Keys.LANGUAGE] = normalized }
    }

    suspend fun setMonthlyIncome(income: Double) {
        context.userPrefsDataStore.edit { it[Keys.MONTHLY_INCOME] = income.coerceAtLeast(0.0) }
    }

    suspend fun setPreferredTaxRegime(regime: String) {
        val normalized = if (regime.equals("OLD", ignoreCase = true)) "OLD" else "NEW"
        context.userPrefsDataStore.edit { it[Keys.TAX_REGIME] = normalized }
    }

    suspend fun setUserAge(age: Int) {
        context.userPrefsDataStore.edit { it[Keys.USER_AGE] = age.coerceIn(18, 85) }
    }

    suspend fun setRiskProfile(profile: String) {
        val normalized = when (profile.uppercase()) {
            "CONSERVATIVE" -> "CONSERVATIVE"
            "AGGRESSIVE" -> "AGGRESSIVE"
            else -> "MODERATE"
        }
        context.userPrefsDataStore.edit { it[Keys.RISK_PROFILE] = normalized }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setFlagSecureEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[Keys.FLAG_SECURE_ENABLED] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.userPrefsDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setUseOnlineData(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[Keys.USE_ONLINE_DATA] = enabled }
    }

    suspend fun setMarketStaleHours(hours: Int) {
        context.userPrefsDataStore.edit { it[Keys.MARKET_STALE_HOURS] = hours.coerceIn(1, 720) }
    }

    suspend fun resetPreferences() {
        context.userPrefsDataStore.edit { prefs ->
            val wasOnboarded = prefs[Keys.ONBOARDING_COMPLETED] ?: true
            prefs.clear()
            prefs[Keys.ONBOARDING_COMPLETED] = wasOnboarded
        }
    }
}
