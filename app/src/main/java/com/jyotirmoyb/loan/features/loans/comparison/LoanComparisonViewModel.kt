package com.jyotirmoyb.loan.features.loans.comparison

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.CompareScenarioEntity
import com.jyotirmoyb.loan.data.SavedLoanEntity
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CalculationInput
import com.jyotirmoyb.loan.domain.ComparisonScenarioEvaluated
import com.jyotirmoyb.loan.domain.ComparisonScenarioInput
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.LoanCatalog
import com.jyotirmoyb.loan.domain.LoanCategory
import com.jyotirmoyb.loan.domain.LoanComparisonEngine
import com.jyotirmoyb.loan.domain.LoanComparisonReport
import com.jyotirmoyb.loan.domain.LoanVariant
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import com.jyotirmoyb.loan.util.ScheduleExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LoanComparisonUiState(
    val allScenarios: List<ComparisonScenarioEvaluated> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val report: LoanComparisonReport = LoanComparisonReport(),
    val savedLoans: List<SavedLoanEntity> = emptyList(),
    val catalog: LoanCatalog = LoanCatalog(),
    val currency: CurrencyCode = CurrencyCode.INR,
    val snackbarMessage: String? = null
)

class LoanComparisonViewModel(application: Application) : AndroidViewModel(application),
    CalculatorEngineContract<List<ComparisonScenarioInput>, LoanComparisonReport> {

    override val id: String = "loan_comparison"

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _snackbar = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LoanComparisonUiState> = combine(
        repository.compareScenariosFlow,
        repository.savedLoansFlow,
        repository.catalogFlow,
        repository.preferencesFlow,
        ratesRepository.resolvedMapFlow,
        _selectedIds,
        _snackbar
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val entities = values[0] as List<CompareScenarioEntity>
        @Suppress("UNCHECKED_CAST")
        val savedLoans = values[1] as List<SavedLoanEntity>
        val catalog = values[2] as LoanCatalog
        val prefs = values[3] as com.jyotirmoyb.loan.data.UserPreferences
        @Suppress("UNCHECKED_CAST")
        val resolvedMap = values[4] as Map<String, ResolvedConfigItem>
        @Suppress("UNCHECKED_CAST")
        val manualSelection = values[5] as Set<Long>
        val snackbar = values[6] as String?

        val gstRate = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.GST_BANKING_SERVICES, 18.0)

        val scenarioInputs = entities.map { entity ->
            val cat = catalog.categories.firstOrNull { it.id == entity.categoryId }
            val parsed = repository.parseInput(entity.inputJson)
            val normalizedInput = if (parsed.gstRatePercent <= 0.0) parsed.copy(gstRatePercent = gstRate) else parsed
            ComparisonScenarioInput(
                id = entity.id,
                label = entity.label,
                categoryId = entity.categoryId,
                categoryName = cat?.shortName ?: entity.categoryId.uppercase(),
                variantSummary = entity.variantSummary,
                input = normalizedInput,
                category = cat
            )
        }

        val validManual = manualSelection.filter { id -> scenarioInputs.any { it.id == id } }.toSet()
        val effectiveSelected = if (validManual.isEmpty() && scenarioInputs.isNotEmpty()) {
            scenarioInputs.take(LoanComparisonEngine.MAX_SCENARIOS).map { it.id }.toSet()
        } else {
            validManual
        }

        val allEvaluated = scenarioInputs.map { sc ->
            LoanComparisonEngine.evaluate(listOf(sc)).scenarios.first()
        }
        val selectedInputs = scenarioInputs.filter { it.id in effectiveSelected }
        val report = compute(selectedInputs)

        LoanComparisonUiState(
            allScenarios = allEvaluated,
            selectedIds = effectiveSelected,
            report = report,
            savedLoans = savedLoans,
            catalog = catalog,
            currency = prefs.currency,
            snackbarMessage = snackbar
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoanComparisonUiState())

    override fun compute(input: List<ComparisonScenarioInput>): LoanComparisonReport =
        LoanComparisonEngine.evaluate(input)

    fun toggleSelection(id: Long) {
        val current = uiState.value.selectedIds.toMutableSet()
        if (id in current) {
            current.remove(id)
        } else {
            if (current.size >= LoanComparisonEngine.MAX_SCENARIOS) {
                _snackbar.value = "You can compare up to 3 offers at a time."
                return
            }
            current.add(id)
        }
        _selectedIds.value = current
    }

    fun deleteScenario(id: Long) {
        viewModelScope.launch {
            repository.deleteCompareScenario(id)
            _selectedIds.value = _selectedIds.value - id
            _snackbar.value = "Removed comparison offer."
        }
    }

    fun seedFromSavedLoan(loan: SavedLoanEntity) {
        viewModelScope.launch {
            val newId = repository.saveCompareScenario(
                CompareScenarioEntity(
                    label = loan.nickname,
                    categoryId = loan.categoryId,
                    variantSummary = "${loan.lender} • ${loan.variantSummary}",
                    inputJson = loan.inputJson
                )
            )
            addToActiveSelection(newId)
            _snackbar.value = "Added \"${loan.nickname}\" to comparison."
        }
    }

    fun seedFromCatalogVariant(category: LoanCategory, variant: LoanVariant) {
        viewModelScope.launch {
            val resolved = ratesRepository.resolvedMapFlow.stateIn(viewModelScope).value
            val minRate = DataSourceResolver.resolveDouble(
                resolved,
                variant.rateMinKey ?: category.rateMinKey ?: "",
                variant.rateRangeMin
            )
            val gstRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.GST_BANKING_SERVICES, 18.0)
            val input = CalculationInput(
                categoryId = category.id,
                ruleFlags = listOf(variant.ruleFlag, "EMI_STANDARD", "DISB_FULL"),
                principal = category.defaultAmountInr,
                assetValue = category.defaultAssetValueInr,
                annualRate = minRate,
                tenureMonths = category.defaultTenureMonths,
                processingFeePercent = category.defaultProcessingFeePercent,
                gstOnFeesEnabled = true,
                gstRatePercent = gstRate
            )
            val newId = repository.saveCompareScenario(
                CompareScenarioEntity(
                    label = "${category.shortName} • ${variant.name}",
                    categoryId = category.id,
                    variantSummary = "${category.name} (${variant.name})",
                    inputJson = repository.serializeInput(input)
                )
            )
            addToActiveSelection(newId)
            _snackbar.value = "Added ${category.shortName} (${variant.name}) to comparison."
        }
    }

    fun addQuickIndianHomePresets() {
        viewModelScope.launch {
            val base = CalculationInput(
                categoryId = "home",
                ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD", "DISB_FULL"),
                principal = 5000000.0,
                assetValue = 6500000.0,
                tenureMonths = 240,
                gstOnFeesEnabled = true,
                gstRatePercent = 18.0
            )
            val id1 = repository.saveCompareScenario(
                CompareScenarioEntity(
                    label = "PSU Bank (8.40%)",
                    categoryId = "home",
                    variantSummary = "Floating EBLR • 0.35% Fee + GST",
                    inputJson = repository.serializeInput(base.copy(annualRate = 8.40, processingFeePercent = 0.35))
                )
            )
            val id2 = repository.saveCompareScenario(
                CompareScenarioEntity(
                    label = "Private Bank (8.75%)",
                    categoryId = "home",
                    variantSummary = "Floating EBLR • 0.50% Fee + GST",
                    inputJson = repository.serializeInput(base.copy(annualRate = 8.75, processingFeePercent = 0.50))
                )
            )
            val id3 = repository.saveCompareScenario(
                CompareScenarioEntity(
                    label = "15-Yr Fast Pay (8.60%)",
                    categoryId = "home",
                    variantSummary = "Shorter Tenure • 0.50% Fee + GST",
                    inputJson = repository.serializeInput(base.copy(annualRate = 8.60, tenureMonths = 180, processingFeePercent = 0.50))
                )
            )
            _selectedIds.value = setOf(id1, id2, id3)
            _snackbar.value = "Loaded 3 Indian Home Loan offers to compare."
        }
    }

    fun exportPdf(context: Context) {
        val state = uiState.value
        val result = ScheduleExporter.exportComparisonPdfAndShare(context, state.report, state.currency)
        result.onFailure { _snackbar.value = it.message ?: "Could not export PDF." }
    }

    fun exportImage(context: Context) {
        val state = uiState.value
        val result = ScheduleExporter.exportComparisonImageAndShare(context, state.report, state.currency)
        result.onFailure { _snackbar.value = it.message ?: "Could not export image." }
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }

    private fun addToActiveSelection(newId: Long) {
        val current = uiState.value.selectedIds.toMutableList()
        if (current.size >= LoanComparisonEngine.MAX_SCENARIOS) {
            current.removeAt(0)
        }
        current.add(newId)
        _selectedIds.value = current.toSet()
    }
}
