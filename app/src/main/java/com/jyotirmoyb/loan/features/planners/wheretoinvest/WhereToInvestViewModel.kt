package com.jyotirmoyb.loan.features.planners.wheretoinvest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.data.remote.RatesRepository
import com.jyotirmoyb.loan.data.remote.RetirementSchemesCatalog
import com.jyotirmoyb.loan.domain.AssetAllocationEngine
import com.jyotirmoyb.loan.domain.AssetAllocationResult
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.RiskProfile
import com.jyotirmoyb.loan.features.retirement.govschemes.GovPensionUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class WhereToInvestUiState(
    val age: Int = 32,
    val answers: Map<String, Int> = defaultAnswers(),
    val manualProfileOverride: RiskProfile? = null,
    val computedProfile: RiskProfile = RiskProfile.MODERATE,
    val effectiveProfile: RiskProfile = RiskProfile.MODERATE,
    val questionnaireScore: Int = 10,
    val investableAmount: Double = 25000.0,
    val catalog: RetirementSchemesCatalog = GovPensionUiState.emptyCatalog(),
    val allocationResult: AssetAllocationResult? = null,
    val currency: CurrencyCode = CurrencyCode.INR
) {
    companion object {
        fun defaultAnswers(): Map<String, Int> = AssetAllocationEngine.questionnaire.associate { it.id to 2 }
    }
}

class WhereToInvestViewModel(
    prefsRepo: UserPreferencesRepository,
    ratesRepo: RatesRepository
) : ViewModel() {

    private data class Draft(
        val age: Int = 32,
        val hasCustomAge: Boolean = false,
        val answers: Map<String, Int> = WhereToInvestUiState.defaultAnswers(),
        val hasCustomQuestionnaire: Boolean = false,
        val manualProfileOverride: RiskProfile? = null,
        val investableAmount: Double = 25000.0
    )

    private val draft = MutableStateFlow(Draft())

    val uiState: StateFlow<WhereToInvestUiState> = combine(
        draft,
        prefsRepo.preferencesFlow,
        ratesRepo.retirementSchemesCatalogFlow
    ) { d, prefs, catalog ->
        val effAge = if (d.hasCustomAge) d.age else prefs.userAge.coerceIn(18, 85)
        val profileDefaultRisk = runCatching { RiskProfile.valueOf(prefs.riskProfile) }
            .getOrDefault(RiskProfile.MODERATE)
        val (score, computedProfile) = AssetAllocationEngine.scoreRiskProfile(d.answers)
        val effectiveProfile = when {
            d.manualProfileOverride != null -> d.manualProfileOverride
            d.hasCustomQuestionnaire -> computedProfile
            else -> profileDefaultRisk
        }
        val result = AssetAllocationEngine.resolveAllocation(
            age = effAge,
            riskProfile = effectiveProfile,
            questionnaireScore = score,
            investableAmount = d.investableAmount,
            matrix = catalog.assetAllocationMatrix
        )
        WhereToInvestUiState(
            age = effAge,
            answers = d.answers,
            manualProfileOverride = d.manualProfileOverride,
            computedProfile = computedProfile,
            effectiveProfile = effectiveProfile,
            questionnaireScore = score,
            investableAmount = d.investableAmount,
            catalog = catalog,
            allocationResult = if (result.isValid) result else null,
            currency = prefs.currency
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhereToInvestUiState())

    fun updateAge(age: Int) {
        draft.update { it.copy(age = age.coerceIn(18, 85), hasCustomAge = true) }
    }

    fun selectAnswer(questionId: String, score: Int) {
        draft.update { d ->
            d.copy(
                answers = d.answers + (questionId to score.coerceIn(1, 3)),
                hasCustomQuestionnaire = true,
                manualProfileOverride = null
            )
        }
    }

    fun overrideProfile(profile: RiskProfile?) {
        draft.update { it.copy(manualProfileOverride = profile) }
    }

    fun updateInvestableAmount(amount: Double) {
        draft.update { it.copy(investableAmount = amount.coerceAtLeast(0.0)) }
    }

    fun resetQuestionnaire() {
        draft.update {
            it.copy(
                answers = WhereToInvestUiState.defaultAnswers(),
                hasCustomQuestionnaire = false,
                manualProfileOverride = null
            )
        }
    }

    class Factory(
        private val prefsRepo: UserPreferencesRepository,
        private val ratesRepo: RatesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WhereToInvestViewModel(prefsRepo, ratesRepo) as T
    }
}
