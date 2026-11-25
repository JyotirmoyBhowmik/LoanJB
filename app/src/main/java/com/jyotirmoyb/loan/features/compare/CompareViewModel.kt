package com.jyotirmoyb.loan.features.compare

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.CompareScenarioEntity
import com.jyotirmoyb.loan.engine.CalculationInput
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.LoanCalculationEngine
import com.jyotirmoyb.loan.engine.RateChangeEvent
import com.jyotirmoyb.loan.viewmodel.CompareItemModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CompareUiState(
    val items: List<CompareItemModel> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val currency: CurrencyCode = CurrencyCode.INR,
    val snackbarMessage: String? = null
)

class CompareViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppContainer.provideRepository(application)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _snackbar = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CompareUiState> = combine(
        repository.compareScenariosFlow,
        repository.catalogFlow,
        repository.preferencesFlow,
        _selectedIds,
        _snackbar
    ) { scenarios, cat, prefs, selIds, msg ->
        val models = scenarios.map { sc ->
            val input = repository.parseInput(sc.inputJson)
            val category = cat.categories.firstOrNull { it.id == sc.categoryId }
            val result = LoanCalculationEngine.calculate(input, category)
            CompareItemModel(
                id = sc.id,
                label = sc.label,
                categoryName = category?.shortName ?: sc.categoryId,
                variantSummary = sc.variantSummary,
                input = input,
                result = result
            )
        }
        val validSel = selIds.filter { id -> models.any { it.id == id } }.toSet()
        val effectiveSel = if (validSel.isEmpty() && models.isNotEmpty()) {
            models.take(3).map { it.id }.toSet()
        } else {
            validSel
        }
        CompareUiState(
            items = models,
            selectedIds = effectiveSel,
            currency = prefs.currency,
            snackbarMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompareUiState())

    fun clearSnackbar() {
        _snackbar.value = null
    }

    fun toggleCompareSelection(id: Long) {
        val current = uiState.value.selectedIds.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else if (current.size < 3) {
            current.add(id)
        } else {
            _snackbar.value = "You can compare up to 3 scenarios at once"
            return
        }
        _selectedIds.value = current
    }

    fun deleteCompareScenario(id: Long) {
        viewModelScope.launch {
            repository.deleteCompareScenario(id)
            _selectedIds.value = _selectedIds.value - id
            _snackbar.value = "Comparison scenario removed"
        }
    }

    fun addPresetComparisonSet(presetType: String) {
        viewModelScope.launch {
            val baseInput = CalculationInput(
                categoryId = "home",
                principal = 2800000.0,
                assetValue = 3800000.0,
                tenureMonths = 72
            )
            val createdIds = mutableListOf<Long>()
            when (presetType) {
                "FIXED_FLOAT_HYBRID" -> {
                    val fixed = baseInput.copy(
                        ruleFlags = listOf("RATE_FIXED", "EMI_STANDARD", "DISB_FULL", "PURPOSE_PURCHASE"),
                        annualRate = 9.5,
                        prepaymentFeePercent = 2.0
                    )
                    val floating = baseInput.copy(
                        ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD", "DISB_FULL", "PURPOSE_PURCHASE"),
                        annualRate = 8.75,
                        rateChanges = listOf(RateChangeEvent(36, 8.25)),
                        prepaymentFeePercent = 0.0
                    )
                    val hybrid = baseInput.copy(
                        ruleFlags = listOf("RATE_HYBRID", "EMI_STANDARD", "DISB_FULL", "PURPOSE_PURCHASE"),
                        annualRate = 9.0,
                        hybridFixedMonths = 36,
                        hybridFloatingRate = 9.25,
                        prepaymentFeePercent = 1.0
                    )
                    createdIds += repository.saveCompareScenario(
                        CompareScenarioEntity(
                            label = "Home Fixed 9.5%",
                            categoryId = "home",
                            variantSummary = "Fixed • Standard EMI",
                            inputJson = repository.serializeInput(fixed)
                        )
                    )
                    createdIds += repository.saveCompareScenario(
                        CompareScenarioEntity(
                            label = "Home Floating 8.75%",
                            categoryId = "home",
                            variantSummary = "Floating • -0.5% @ Yr 3",
                            inputJson = repository.serializeInput(floating)
                        )
                    )
                    createdIds += repository.saveCompareScenario(
                        CompareScenarioEntity(
                            label = "Home Hybrid 9.0%",
                            categoryId = "home",
                            variantSummary = "Fixed 3y then 9.25%",
                            inputJson = repository.serializeInput(hybrid)
                        )
                    )
                }
                "CURRENT_VS_BT" -> {
                    val cur = baseInput.copy(
                        ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD", "DISB_FULL"),
                        annualRate = 10.5,
                        processingFeePercent = 0.0
                    )
                    val btOffer = baseInput.copy(
                        ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD", "DISB_FULL"),
                        annualRate = 8.75,
                        processingFeePercent = 0.75
                    )
                    createdIds += repository.saveCompareScenario(
                        CompareScenarioEntity(
                            label = "Current Bank 10.5%",
                            categoryId = "home",
                            variantSummary = "Existing Floating Rate",
                            inputJson = repository.serializeInput(cur)
                        )
                    )
                    createdIds += repository.saveCompareScenario(
                        CompareScenarioEntity(
                            label = "Transfer Offer 8.75%",
                            categoryId = "home",
                            variantSummary = "New Bank + 0.75% Fee",
                            inputJson = repository.serializeInput(btOffer)
                        )
                    )
                }
            }
            _selectedIds.value = createdIds.take(3).toSet()
            _snackbar.value = "Loaded comparison preset"
        }
    }
}
