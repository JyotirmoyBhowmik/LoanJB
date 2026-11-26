package com.jyotirmoyb.loan.features.myloans

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.CompareScenarioEntity
import com.jyotirmoyb.loan.data.SavedLoanEntity
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.LoanCatalog
import com.jyotirmoyb.loan.domain.LoanTrackerEngine
import com.jyotirmoyb.loan.util.EmiReminderScheduler
import com.jyotirmoyb.loan.util.ReminderScheduleMode
import com.jyotirmoyb.loan.viewmodel.PortfolioMetrics
import com.jyotirmoyb.loan.viewmodel.TrackedLoanSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MyLoansUiState(
    val loans: List<TrackedLoanSummary> = emptyList(),
    val portfolioMetrics: PortfolioMetrics = PortfolioMetrics(),
    val currency: CurrencyCode = CurrencyCode.INR,
    val monthlyIncome: Double = 0.0,
    val snackbarMessage: String? = null
)

class MyLoansViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppContainer.provideRepository(application)
    private val _snackbar = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MyLoansUiState> = combine(
        repository.savedLoansFlow,
        repository.catalogFlow,
        repository.preferencesFlow,
        _snackbar
    ) { entities, catalog, prefs, msg ->
        val loans = entities.map { buildTrackedSummary(it, catalog) }
        val metrics = LoanTrackerEngine.computePortfolioMetrics(loans, prefs.monthlyIncome)
        MyLoansUiState(
            loans = loans,
            portfolioMetrics = metrics,
            currency = prefs.currency,
            monthlyIncome = prefs.monthlyIncome,
            snackbarMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MyLoansUiState())

    fun clearSnackbar() {
        _snackbar.value = null
    }

    fun toggleEmiPaid(summary: TrackedLoanSummary, monthNumber: Int) {
        viewModelScope.launch {
            val paid = summary.paidMonthNumbers.toMutableSet()
            if (paid.contains(monthNumber)) paid.remove(monthNumber) else paid.add(monthNumber)
            val updated = summary.entity.copy(paidMonthsCsv = paid.sorted().joinToString(","))
            repository.updateLoan(updated)
            if (updated.reminderEnabled) {
                scheduleReminderForSummary(buildTrackedSummary(updated, repository.catalogFlow.first()))
            }
        }
    }

    fun markNextEmiPaid(summary: TrackedLoanSummary) {
        val next = summary.nextDueMonthNumber ?: return
        toggleEmiPaid(summary, next)
        _snackbar.value = "Marked Month #$next EMI as paid"
    }

    fun logActualPrepayment(summary: TrackedLoanSummary, monthNumber: Int, amount: Double, reduceTenure: Boolean) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            val updatedInput = LoanTrackerEngine.applyPrepayment(
                input = summary.input,
                monthNumber = monthNumber,
                amount = amount,
                reduceTenure = reduceTenure
            )
            val updatedEntity = summary.entity.copy(inputJson = repository.serializeInput(updatedInput))
            repository.updateLoan(updatedEntity)
            val recomputed = buildTrackedSummary(updatedEntity, repository.catalogFlow.first())
            val savedStr = CurrencyFormatter.format(recomputed.interestSaved, uiState.value.currency, false)
            _snackbar.value = "Prepayment logged — schedule recalculated (Interest saved: $savedStr)"
        }
    }

    fun logActualRateChange(summary: TrackedLoanSummary, afterMonth: Int, newAnnualRate: Double) {
        if (newAnnualRate !in 0.0..40.0) return
        viewModelScope.launch {
            val updatedInput = LoanTrackerEngine.applyRateChange(
                input = summary.input,
                afterMonth = afterMonth,
                newAnnualRate = newAnnualRate
            )
            val updatedEntity = summary.entity.copy(inputJson = repository.serializeInput(updatedInput))
            repository.updateLoan(updatedEntity)
            _snackbar.value = "Floating rate reset to ${String.format("%.2f", newAnnualRate)}% after month $afterMonth — schedule recomputed"
        }
    }

    fun toggleAutoDebitNach(summary: TrackedLoanSummary, enabled: Boolean) {
        viewModelScope.launch {
            val updated = summary.entity.copy(autoDebitNach = enabled)
            repository.updateLoan(updated)
            if (updated.reminderEnabled) {
                scheduleReminderForSummary(buildTrackedSummary(updated, repository.catalogFlow.first()))
            }
            _snackbar.value = if (enabled) "NACH Auto-Debit marked active" else "Switched to manual EMI payment"
        }
    }

    fun updateEmiDayOfMonth(summary: TrackedLoanSummary, emiDay: Int) {
        val clamped = emiDay.coerceIn(1, 28)
        viewModelScope.launch {
            val updatedInput = summary.input.copy(emiDayOfMonth = clamped)
            val updated = summary.entity.copy(
                emiDayOfMonth = clamped,
                inputJson = repository.serializeInput(updatedInput)
            )
            repository.updateLoan(updated)
            if (updated.reminderEnabled) {
                scheduleReminderForSummary(buildTrackedSummary(updated, repository.catalogFlow.first()))
            }
            _snackbar.value = "EMI due date updated to day $clamped of every month"
        }
    }

    fun toggleChecklistItem(summary: TrackedLoanSummary, itemIndex: Int) {
        viewModelScope.launch {
            val done = summary.completedChecklist.toMutableSet()
            if (done.contains(itemIndex)) done.remove(itemIndex) else done.add(itemIndex)
            val updated = summary.entity.copy(completedChecklistCsv = done.sorted().joinToString(","))
            repository.updateLoan(updated)
        }
    }

    fun toggleLoanReminder(summary: TrackedLoanSummary, enabled: Boolean, daysBefore: Int = summary.entity.reminderDaysBefore) {
        viewModelScope.launch {
            val updated = summary.entity.copy(reminderEnabled = enabled, reminderDaysBefore = daysBefore)
            repository.updateLoan(updated)
            if (enabled) {
                val mode = scheduleReminderForSummary(buildTrackedSummary(updated, repository.catalogFlow.first()))
                _snackbar.value = when (mode) {
                    ReminderScheduleMode.EXACT_ALARM ->
                        "Exact reminder set $daysBefore day(s) before next EMI"
                    ReminderScheduleMode.INEXACT_FALLBACK ->
                        "Reminder set $daysBefore day(s) before next EMI (battery-friendly fallback mode)"
                    null -> "No unpaid EMIs left to remind"
                }
            } else {
                EmiReminderScheduler.cancelReminder(getApplication(), updated.id)
                _snackbar.value = "EMI reminder turned off"
            }
        }
    }

    fun addLoanToCompare(summary: TrackedLoanSummary) {
        viewModelScope.launch {
            repository.saveCompareScenario(
                CompareScenarioEntity(
                    label = summary.entity.nickname,
                    categoryId = summary.entity.categoryId,
                    variantSummary = summary.entity.variantSummary,
                    inputJson = summary.entity.inputJson
                )
            )
            _snackbar.value = "Added \"${summary.entity.nickname}\" to Loan Comparison"
        }
    }

    fun deleteLoan(id: Long) {
        viewModelScope.launch {
            EmiReminderScheduler.cancelReminder(getApplication(), id)
            repository.deleteLoan(id)
            _snackbar.value = "Loan removed"
        }
    }

    private fun scheduleReminderForSummary(summary: TrackedLoanSummary): ReminderScheduleMode? {
        val nextRow = summary.result.schedule.firstOrNull { it.monthNumber == summary.nextDueMonthNumber } ?: return null
        val emiStr = CurrencyFormatter.format(nextRow.emiPaid, uiState.value.currency, false)
        return EmiReminderScheduler.scheduleNextReminder(
            context = getApplication(),
            loanId = summary.entity.id,
            nickname = summary.entity.nickname,
            emiFormatted = emiStr,
            dueYear = summary.nextDueYear,
            dueMonthOneBased = summary.nextDueMonth,
            dueDay = summary.emiDayOfMonth,
            daysBefore = summary.entity.reminderDaysBefore,
            autoDebitNach = summary.autoDebitNach
        )
    }

    private fun buildTrackedSummary(entity: SavedLoanEntity, cat: LoanCatalog): TrackedLoanSummary {
        val input = repository.parseInput(entity.inputJson)
        val category = cat.categories.firstOrNull { it.id == entity.categoryId }
        return LoanTrackerEngine.evaluateTrackedLoan(entity, input, category)
    }
}
