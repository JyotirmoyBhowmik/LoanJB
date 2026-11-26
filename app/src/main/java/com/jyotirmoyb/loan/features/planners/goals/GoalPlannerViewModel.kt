package com.jyotirmoyb.loan.features.planners.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.FinancialGoalItem
import com.jyotirmoyb.loan.domain.GoalCategory
import com.jyotirmoyb.loan.domain.MultiGoalPlannerEngine
import com.jyotirmoyb.loan.domain.MultiGoalSummaryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.UUID

data class GoalPlannerUiState(
    val goals: List<FinancialGoalItem> = defaultStarterGoals(),
    val summary: MultiGoalSummaryResult = MultiGoalPlannerEngine.calculateAll(defaultStarterGoals()),
    val expandedGoalId: String? = "goal_child_edu",
    val currency: CurrencyCode = CurrencyCode.INR
) {
    companion object {
        fun defaultStarterGoals(): List<FinancialGoalItem> = listOf(
            FinancialGoalItem(
                id = "goal_child_edu",
                name = "Child Higher Education",
                category = GoalCategory.CHILD_EDUCATION,
                currentCost = 2500000.0,
                yearsToGoal = 14,
                inflationPercent = 8.0,
                currentSavings = 300000.0,
                expectedReturnPercent = 12.0,
                enabled = true
            ),
            FinancialGoalItem(
                id = "goal_home",
                name = "Home Down-Payment",
                category = GoalCategory.HOME,
                currentCost = 2000000.0,
                yearsToGoal = 6,
                inflationPercent = 7.0,
                currentSavings = 400000.0,
                expectedReturnPercent = 11.0,
                enabled = true
            ),
            FinancialGoalItem(
                id = "goal_car",
                name = "Family Car Upgrade",
                category = GoalCategory.CAR,
                currentCost = 1200000.0,
                yearsToGoal = 4,
                inflationPercent = 6.0,
                currentSavings = 150000.0,
                expectedReturnPercent = 9.0,
                enabled = true
            )
        )
    }
}

class GoalPlannerViewModel(
    prefsRepo: UserPreferencesRepository
) : ViewModel() {

    private data class Draft(
        val goals: List<FinancialGoalItem> = GoalPlannerUiState.defaultStarterGoals(),
        val expandedGoalId: String? = "goal_child_edu"
    )

    private val draft = MutableStateFlow(Draft())

    val uiState: StateFlow<GoalPlannerUiState> = combine(
        draft,
        prefsRepo.preferencesFlow
    ) { d, prefs ->
        GoalPlannerUiState(
            goals = d.goals,
            summary = MultiGoalPlannerEngine.calculateAll(d.goals),
            expandedGoalId = d.expandedGoalId,
            currency = prefs.currency
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalPlannerUiState())

    fun addGoal(category: GoalCategory) {
        val newId = "goal_${UUID.randomUUID().toString().take(8)}"
        val countSame = draft.value.goals.count { it.category == category }
        val label = if (countSame == 0) category.displayName else "${category.displayName} ${countSame + 1}"
        val newItem = FinancialGoalItem(
            id = newId,
            name = label,
            category = category,
            currentCost = category.defaultCost,
            yearsToGoal = category.defaultYears,
            inflationPercent = category.defaultInflation,
            currentSavings = 0.0,
            expectedReturnPercent = category.defaultReturn,
            enabled = true
        )
        draft.update { it.copy(goals = it.goals + newItem, expandedGoalId = newId) }
    }

    fun updateGoal(updated: FinancialGoalItem) {
        draft.update { d ->
            d.copy(goals = d.goals.map { if (it.id == updated.id) updated else it })
        }
    }

    fun toggleGoal(id: String) {
        draft.update { d ->
            d.copy(goals = d.goals.map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
        }
    }

    fun removeGoal(id: String) {
        draft.update { d ->
            val remaining = d.goals.filterNot { it.id == id }
            val nextExpanded = if (d.expandedGoalId == id) remaining.firstOrNull()?.id else d.expandedGoalId
            d.copy(goals = remaining, expandedGoalId = nextExpanded)
        }
    }

    fun toggleExpand(id: String) {
        draft.update { d ->
            d.copy(expandedGoalId = if (d.expandedGoalId == id) null else id)
        }
    }

    fun resetToStarterGoals() {
        draft.update {
            Draft(
                goals = GoalPlannerUiState.defaultStarterGoals(),
                expandedGoalId = "goal_child_edu"
            )
        }
    }

    class Factory(
        private val prefsRepo: UserPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GoalPlannerViewModel(prefsRepo) as T
    }
}
