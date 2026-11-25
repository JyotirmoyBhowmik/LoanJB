package com.jyotirmoyb.loan.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore by preferencesDataStore(name = "loanjb_settings")

data class UserPreferences(
    val currency: CurrencyCode = CurrencyCode.INR,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val monthlyIncome: Double = 150000.0
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val CURRENCY = stringPreferencesKey("currency")
        val THEME = stringPreferencesKey("theme_mode")
        val MONTHLY_INCOME = doublePreferencesKey("monthly_income")
    }

    val preferencesFlow: Flow<UserPreferences> = context.userPrefsDataStore.data.map { prefs ->
        val currency = runCatching {
            CurrencyCode.valueOf(prefs[Keys.CURRENCY] ?: CurrencyCode.INR.name)
        }.getOrDefault(CurrencyCode.INR)
        val theme = runCatching {
            AppThemeMode.valueOf(prefs[Keys.THEME] ?: AppThemeMode.SYSTEM.name)
        }.getOrDefault(AppThemeMode.SYSTEM)
        val income = prefs[Keys.MONTHLY_INCOME] ?: 150000.0
        UserPreferences(currency = currency, themeMode = theme, monthlyIncome = income)
    }

    suspend fun setCurrency(currency: CurrencyCode) {
        context.userPrefsDataStore.edit { it[Keys.CURRENCY] = currency.name }
    }

    suspend fun setThemeMode(themeMode: AppThemeMode) {
        context.userPrefsDataStore.edit { it[Keys.THEME] = themeMode.name }
    }

    suspend fun setMonthlyIncome(income: Double) {
        context.userPrefsDataStore.edit { it[Keys.MONTHLY_INCOME] = income.coerceAtLeast(0.0) }
    }

    suspend fun resetPreferences() {
        context.userPrefsDataStore.edit { it.clear() }
    }
}
