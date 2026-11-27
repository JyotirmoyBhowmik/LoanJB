package com.jyotirmoyb.loan.data

import android.content.Context
import com.jyotirmoyb.loan.data.security.BackupCrypto
import com.jyotirmoyb.loan.domain.CalculationInput
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.LoanCatalog
import com.jyotirmoyb.loan.domain.RegionCode
import com.jyotirmoyb.loan.ui.theme.AppThemeMode
import com.jyotirmoyb.loan.widget.NextEmiWidgetUpdater
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@JsonClass(generateAdapter = true)
data class AppBackupPayload(
    val version: Int = 2,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val region: String = RegionCode.IN.name,
    val currency: String = CurrencyCode.INR.name,
    val themeMode: String = AppThemeMode.SYSTEM.name,
    val languageCode: String = "en",
    val monthlyIncome: Double = 150000.0,
    val preferredTaxRegime: String = "NEW",
    val userAge: Int = 32,
    val riskProfile: String = "MODERATE",
    val catalog: LoanCatalog,
    val savedLoans: List<SavedLoanEntity> = emptyList(),
    val compareScenarios: List<CompareScenarioEntity> = emptyList()
)

class LoanRepository(private val context: Context, private val dao: LoanDao, private val prefsRepo: UserPreferencesRepository) {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val catalogAdapter = moshi.adapter(LoanCatalog::class.java).indent("  ")
    private val inputAdapter = moshi.adapter(CalculationInput::class.java)
    private val backupAdapter = moshi.adapter(AppBackupPayload::class.java).indent("  ")

    private val defaultCatalogJson: String by lazy {
        context.assets.open("catalog.json").bufferedReader().use { it.readText() }
    }

    val catalogFlow: Flow<LoanCatalog> = dao.observeCatalogOverride().map { override ->
        val raw = override?.catalogJson ?: defaultCatalogJson
        parseCatalog(raw) ?: parseCatalog(defaultCatalogJson) ?: LoanCatalog()
    }

    val savedLoansFlow: Flow<List<SavedLoanEntity>> = dao.observeSavedLoans()
    val compareScenariosFlow: Flow<List<CompareScenarioEntity>> = dao.observeCompareScenarios()
    val preferencesFlow: Flow<UserPreferences> = prefsRepo.preferencesFlow

    fun serializeCatalog(catalog: LoanCatalog): String = catalogAdapter.toJson(catalog)

    fun parseCatalog(json: String): LoanCatalog? = runCatching {
        catalogAdapter.fromJson(json)
    }.getOrNull()

    fun serializeInput(input: CalculationInput): String = inputAdapter.toJson(input)

    fun parseInput(json: String): CalculationInput = runCatching {
        inputAdapter.fromJson(json)
    }.getOrNull() ?: CalculationInput()

    suspend fun saveCatalog(catalog: LoanCatalog): Result<Unit> {
        if (catalog.categories.isEmpty()) {
            return Result.failure(IllegalArgumentException("Catalog must contain at least one loan category."))
        }
        val json = serializeCatalog(catalog)
        dao.upsertCatalogOverride(CatalogOverrideEntity(id = 1, catalogJson = json))
        return Result.success(Unit)
    }

    suspend fun saveCatalogRawJson(rawJson: String): Result<LoanCatalog> {
        val parsed = parseCatalog(rawJson)
            ?: return Result.failure(IllegalArgumentException("Invalid JSON format. Please check syntax and required fields."))
        if (parsed.categories.isEmpty()) {
            return Result.failure(IllegalArgumentException("Catalog must contain at least one category."))
        }
        dao.upsertCatalogOverride(CatalogOverrideEntity(id = 1, catalogJson = serializeCatalog(parsed)))
        return Result.success(parsed)
    }

    suspend fun resetCatalogToDefault() {
        dao.clearCatalogOverride()
    }

    suspend fun saveLoan(entity: SavedLoanEntity): Long {
        val id = dao.insertSavedLoan(entity)
        NextEmiWidgetUpdater.refreshAll(context)
        return id
    }

    suspend fun updateLoan(entity: SavedLoanEntity) {
        dao.updateSavedLoan(entity)
        NextEmiWidgetUpdater.refreshAll(context)
    }

    suspend fun deleteLoan(id: Long) {
        dao.deleteSavedLoanById(id)
        NextEmiWidgetUpdater.refreshAll(context)
    }

    suspend fun saveCompareScenario(entity: CompareScenarioEntity): Long = dao.insertCompareScenario(entity)
    suspend fun deleteCompareScenario(id: Long) = dao.deleteCompareScenarioById(id)

    suspend fun setRegion(region: RegionCode) = prefsRepo.setRegion(region)
    suspend fun setCurrency(currency: CurrencyCode) = prefsRepo.setCurrency(currency)
    suspend fun setThemeMode(mode: AppThemeMode) = prefsRepo.setThemeMode(mode)
    suspend fun setLanguageCode(languageCode: String) = prefsRepo.setLanguageCode(languageCode)
    suspend fun setMonthlyIncome(income: Double) = prefsRepo.setMonthlyIncome(income)
    suspend fun setPreferredTaxRegime(regime: String) = prefsRepo.setPreferredTaxRegime(regime)
    suspend fun setUserAge(age: Int) = prefsRepo.setUserAge(age)
    suspend fun setRiskProfile(profile: String) = prefsRepo.setRiskProfile(profile)
    suspend fun setAppLockEnabled(enabled: Boolean) = prefsRepo.setAppLockEnabled(enabled)
    suspend fun setFlagSecureEnabled(enabled: Boolean) = prefsRepo.setFlagSecureEnabled(enabled)
    suspend fun setOnboardingCompleted(completed: Boolean) = prefsRepo.setOnboardingCompleted(completed)
    suspend fun setUseOnlineData(enabled: Boolean) = prefsRepo.setUseOnlineData(enabled)

    suspend fun createBackupJson(): String {
        val prefs = prefsRepo.preferencesFlow.first()
        val catalog = catalogFlow.first()
        val loans = dao.getAllSavedLoansOnce()
        val scenarios = dao.getAllCompareScenariosOnce()
        val payload = AppBackupPayload(
            version = 2,
            region = prefs.region.name,
            currency = prefs.currency.name,
            themeMode = prefs.themeMode.name,
            languageCode = prefs.languageCode,
            monthlyIncome = prefs.monthlyIncome,
            preferredTaxRegime = prefs.preferredTaxRegime,
            userAge = prefs.userAge,
            riskProfile = prefs.riskProfile,
            catalog = catalog,
            savedLoans = loans,
            compareScenarios = scenarios
        )
        return backupAdapter.toJson(payload)
    }

    suspend fun createEncryptedBackupJson(password: String): Result<String> {
        val plain = createBackupJson()
        return BackupCrypto.encryptJson(plain, password)
    }

    suspend fun restoreFromBackupJson(rawJson: String, password: String? = null): Result<String> {
        val plainJson = if (BackupCrypto.isEncryptedEnvelope(rawJson)) {
            val pwd = password ?: return Result.failure(
                IllegalArgumentException("This backup is encrypted. Please enter your backup password.")
            )
            BackupCrypto.decryptJson(rawJson, pwd).getOrElse { return Result.failure(it) }
        } else {
            rawJson
        }

        val payload = runCatching { backupAdapter.fromJson(plainJson) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Could not parse backup JSON file."))

        runCatching { RegionCode.valueOf(payload.region) }.getOrNull()?.let { prefsRepo.setRegion(it) }
        runCatching { CurrencyCode.valueOf(payload.currency) }.getOrNull()?.let { prefsRepo.setCurrency(it) }
        runCatching { AppThemeMode.valueOf(payload.themeMode) }.getOrNull()?.let { prefsRepo.setThemeMode(it) }
        prefsRepo.setLanguageCode(payload.languageCode)
        prefsRepo.setMonthlyIncome(payload.monthlyIncome)
        prefsRepo.setPreferredTaxRegime(payload.preferredTaxRegime)
        prefsRepo.setUserAge(payload.userAge)
        prefsRepo.setRiskProfile(payload.riskProfile)

        if (payload.catalog.categories.isNotEmpty()) {
            saveCatalog(payload.catalog)
        }
        dao.clearAllSavedLoans()
        payload.savedLoans.forEach { dao.insertSavedLoan(it.copy(id = 0)) }

        dao.clearAllCompareScenarios()
        payload.compareScenarios.forEach { dao.insertCompareScenario(it.copy(id = 0)) }

        NextEmiWidgetUpdater.refreshAll(context)
        return Result.success("Restored ${payload.savedLoans.size} loans & ${payload.compareScenarios.size} scenarios.")
    }

    suspend fun resetAllData() {
        dao.clearAllSavedLoans()
        dao.clearAllCompareScenarios()
        dao.clearCatalogOverride()
        prefsRepo.resetPreferences()
        NextEmiWidgetUpdater.refreshAll(context)
    }
}
