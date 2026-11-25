package com.jyotirmoyb.loan.features.myloans

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.CompareScenarioEntity
import com.jyotirmoyb.loan.data.SavedLoanEntity
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import com.jyotirmoyb.loan.engine.LoanCalculationEngine
import com.jyotirmoyb.loan.engine.LoanCatalog
import com.jyotirmoyb.loan.engine.PrepaymentMode
import com.jyotirmoyb.loan.engine.PrepaymentRule
import com.jyotirmoyb.loan.engine.RateChangeEvent
import com.jyotirmoyb.loan.util.EmiReminderScheduler
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
        val totalDebt = loans.sumOf { it.outstandingPrincipal }
        val activeEmi = loans.filter { it.outstandingPrincipal > 0.01 }.sumOf { it.result.monthlyEmi }
        val dti = if (prefs.monthlyIncome > 0.0) (activeEmi / prefs.monthlyIncome) * 100.0 else 0.0
        MyLoansUiState(
            loans = loans,
            portfolioMetrics = PortfolioMetrics(
                totalOutstandingDebt = LoanCalculationEngine.roundPaisa(totalDebt),
                totalMonthlyEmiBurden = LoanCalculationEngine.roundPaisa(activeEmi),
                dtiPercent = LoanCalculationEngine.roundPaisa(dti),
                activeLoanCount = loans.count { it.outstandingPrincipal > 0.01 }
            ),
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
            val newRule = PrepaymentRule(
                startMonth = monthNumber,
                amount = amount,
                isRecurring = false,
                mode = if (reduceTenure) PrepaymentMode.REDUCE_TENURE.name else PrepaymentMode.REDUCE_EMI.name
            )
            val updatedInput = summary.input.copy(
                prepaymentEnabled = true,
                prepayments = summary.input.prepayments + newRule
            )
            val updatedEntity = summary.entity.copy(inputJson = repository.serializeInput(updatedInput))
            repository.updateLoan(updatedEntity)
            _snackbar.value = "Logged prepayment — schedule recalculated"
        }
    }

    fun logActualRateChange(summary: TrackedLoanSummary, afterMonth: Int, newAnnualRate: Double) {
        if (newAnnualRate !in 0.0..40.0) return
        viewModelScope.launch {
            val flags = summary.input.ruleFlags.toMutableList()
            if (!flags.contains("RATE_FLOATING") && !flags.contains("RATE_HYBRID")) {
                flags.add("RATE_FLOATING")
            }
            val updatedEvents = (summary.input.rateChanges.filterNot { it.afterMonth == afterMonth } +
                RateChangeEvent(afterMonth = afterMonth, newAnnualRate = newAnnualRate)).sortedBy { it.afterMonth }
            val updatedInput = summary.input.copy(
                ruleFlags = flags,
                rateChanges = updatedEvents
            )
            val updatedEntity = summary.entity.copy(inputJson = repository.serializeInput(updatedInput))
            repository.updateLoan(updatedEntity)
            _snackbar.value = "Updated rate to ${String.format("%.2f", newAnnualRate)}% from month ${afterMonth + 1}"
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
                scheduleReminderForSummary(buildTrackedSummary(updated, repository.catalogFlow.first()))
                _snackbar.value = "Reminder scheduled $daysBefore day(s) before next EMI"
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

    private fun scheduleReminderForSummary(summary: TrackedLoanSummary) {
        val nextRow = summary.result.schedule.firstOrNull { it.monthNumber == summary.nextDueMonthNumber } ?: return
        val emiStr = CurrencyFormatter.format(nextRow.emiPaid, uiState.value.currency, false)
        EmiReminderScheduler.scheduleNextReminder(
            context = getApplication(),
            loanId = summary.entity.id,
            nickname = summary.entity.nickname,
            emiFormatted = emiStr,
            dueYear = summary.nextDueYear,
            dueMonthOneBased = summary.nextDueMonth,
            dueDay = summary.input.startDay,
            daysBefore = summary.entity.reminderDaysBefore
        )
    }

    private fun buildTrackedSummary(entity: SavedLoanEntity, cat: LoanCatalog): TrackedLoanSummary {
        val input = repository.parseInput(entity.inputJson)
        val category = cat.categories.firstOrNull { it.id == entity.categoryId }
        val result = LoanCalculationEngine.calculate(input, category)
        val paidSet = entity.paidMonthsCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
        val checklistSet = entity.completedChecklistCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()

        val paidPrincipal = result.schedule.filter { paidSet.contains(it.monthNumber) }.sumOf { it.principalPaid }
        val totalPaidSoFar = result.schedule.filter { paidSet.contains(it.monthNumber) }.sumOf { it.emiPaid }
        val outstanding = LoanCalculationEngine.roundPaisa(maxOf(0.0, result.totalPrincipal - paidPrincipal))
        val progress = if (result.schedule.isNotEmpty()) {
            (paidSet.size.toFloat() / result.schedule.size.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val nextRow = result.schedule.firstOrNull { !paidSet.contains(it.monthNumber) }
        val nextNum = nextRow?.monthNumber
        val nextOffset = (nextNum ?: 1) - 1
        val totalM = (input.startMonth - 1) + nextOffset
        val nextYear = input.startYear + (totalM / 12)
        val nextMonth = ((totalM % 12) + 12) % 12 + 1
        val nextLabel = if (nextRow != null) "${input.startDay} ${nextRow.monthLabel}" else "Fully Paid"

        return TrackedLoanSummary(
            entity = entity,
            input = input,
            result = result,
            paidMonthNumbers = paidSet,
            completedChecklist = checklistSet,
            nextDueMonthNumber = nextNum,
            nextDueDateLabel = nextLabel,
            nextDueYear = nextYear,
            nextDueMonth = nextMonth,
            outstandingPrincipal = outstanding,
            totalPaidSoFar = LoanCalculationEngine.roundPaisa(totalPaidSoFar),
            progressFraction = progress,
            checklistItems = category?.checklistItems.orEmpty()
        )
    }
}
