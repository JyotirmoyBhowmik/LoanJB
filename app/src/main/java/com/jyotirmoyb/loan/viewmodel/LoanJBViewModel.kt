package com.jyotirmoyb.loan.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.CompareScenarioEntity
import com.jyotirmoyb.loan.data.LoanDatabase
import com.jyotirmoyb.loan.data.LoanRepository
import com.jyotirmoyb.loan.data.SavedLoanEntity
import com.jyotirmoyb.loan.data.UserPreferences
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.engine.CalculationInput
import com.jyotirmoyb.loan.engine.CalculationResult
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import com.jyotirmoyb.loan.engine.LoanCalculationEngine
import com.jyotirmoyb.loan.engine.LoanCatalog
import com.jyotirmoyb.loan.engine.LoanCategory
import com.jyotirmoyb.loan.engine.LoanVariant
import com.jyotirmoyb.loan.engine.PrepaymentMode
import com.jyotirmoyb.loan.engine.PrepaymentRule
import com.jyotirmoyb.loan.engine.RateChangeEvent
import com.jyotirmoyb.loan.ui.theme.AppThemeMode
import com.jyotirmoyb.loan.util.EmiReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainTab { EXPLORE, CALCULATOR, MY_LOANS, COMPARE, SETTINGS }

data class TrackedLoanSummary(
    val entity: SavedLoanEntity,
    val input: CalculationInput,
    val result: CalculationResult,
    val paidMonthNumbers: Set<Int>,
    val completedChecklist: Set<Int>,
    val nextDueMonthNumber: Int?,
    val nextDueDateLabel: String,
    val nextDueYear: Int,
    val nextDueMonth: Int,
    val outstandingPrincipal: Double,
    val totalPaidSoFar: Double,
    val progressFraction: Float,
    val checklistItems: List<String>
)

data class CompareItemModel(
    val id: Long,
    val label: String,
    val categoryName: String,
    val variantSummary: String,
    val input: CalculationInput,
    val result: CalculationResult
)

data class PortfolioMetrics(
    val totalOutstandingDebt: Double = 0.0,
    val totalMonthlyEmiBurden: Double = 0.0,
    val dtiPercent: Double = 0.0,
    val activeLoanCount: Int = 0
)

class LoanJBViewModel(application: Application) : AndroidViewModel(application) {

    private val db = LoanDatabase.getInstance(application)
    private val prefsRepo = UserPreferencesRepository(application)
    val repository = LoanRepository(application, db.loanDao(), prefsRepo)

    val catalog: StateFlow<LoanCatalog> = repository.catalogFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), LoanCatalog()
    )

    val preferences: StateFlow<UserPreferences> = repository.preferencesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences()
    )

    private val _activeTab = MutableStateFlow(MainTab.EXPLORE)
    val activeTab: StateFlow<MainTab> = _activeTab.asStateFlow()

    private val _calcInput = MutableStateFlow(CalculationInput())
    val calcInput: StateFlow<CalculationInput> = _calcInput.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _selectedCompareIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedCompareIds: StateFlow<Set<Long>> = _selectedCompareIds.asStateFlow()

    val calcResult: StateFlow<CalculationResult> = combine(calcInput, catalog) { input, cat ->
        val category = cat.categories.firstOrNull { it.id == input.categoryId }
        LoanCalculationEngine.calculate(input, category)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalculationResult(isValid = false))

    val trackedLoans: StateFlow<List<TrackedLoanSummary>> = combine(
        repository.savedLoansFlow,
        catalog
    ) { entities, cat ->
        entities.map { entity -> buildTrackedSummary(entity, cat) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val portfolioMetrics: StateFlow<PortfolioMetrics> = combine(
        trackedLoans,
        preferences
    ) { loans, prefs ->
        val totalDebt = loans.sumOf { it.outstandingPrincipal }
        val activeEmi = loans.filter { it.outstandingPrincipal > 0.01 }.sumOf { it.result.monthlyEmi }
        val dti = if (prefs.monthlyIncome > 0.0) (activeEmi / prefs.monthlyIncome) * 100.0 else 0.0
        PortfolioMetrics(
            totalOutstandingDebt = LoanCalculationEngine.roundPaisa(totalDebt),
            totalMonthlyEmiBurden = LoanCalculationEngine.roundPaisa(activeEmi),
            dtiPercent = LoanCalculationEngine.roundPaisa(dti),
            activeLoanCount = loans.count { it.outstandingPrincipal > 0.01 }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PortfolioMetrics())

    val compareItems: StateFlow<List<CompareItemModel>> = combine(
        repository.compareScenariosFlow,
        catalog
    ) { scenarios, cat ->
        scenarios.map { sc ->
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val loaded = catalog.first { it.categories.isNotEmpty() }
            loaded.categories.firstOrNull()?.let { firstCat ->
                selectCategory(firstCat, navigateToCalc = false)
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _activeTab.value = tab
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun selectCategory(category: LoanCategory, preselectedVariant: LoanVariant? = null, navigateToCalc: Boolean = false) {
        val selectedMap = mutableMapOf<String, String>()
        val flags = mutableListOf<String>()

        for (group in category.variantGroups) {
            val matched = if (preselectedVariant != null && group.variants.any { it.id == preselectedVariant.id }) {
                preselectedVariant
            } else {
                group.variants.firstOrNull()
            }
            if (matched != null) {
                selectedMap[group.id] = matched.id
                flags.add(matched.ruleFlag)
            }
        }

        val midRate = if (preselectedVariant != null) {
            LoanCalculationEngine.roundPaisa((preselectedVariant.rateRangeMin + preselectedVariant.rateRangeMax) / 2.0)
        } else {
            LoanCalculationEngine.roundPaisa((category.rateRangeMin + category.rateRangeMax) / 2.0)
        }

        val isFloating = flags.contains("RATE_FLOATING")
        val prepayFee = if (isFloating) category.defaultPrepaymentFeeFloating else category.defaultPrepaymentFeeFixed
        val cur = preferences.value.currency
        val scale = if (cur == CurrencyCode.USD) 0.02 else 1.0
        val defaultPrincipal = LoanCalculationEngine.roundPaisa(category.defaultAmountInr * scale)
        val defaultAsset = LoanCalculationEngine.roundPaisa(category.defaultAssetValueInr * scale)

        _calcInput.value = CalculationInput(
            categoryId = category.id,
            selectedVariantsByGroup = selectedMap,
            ruleFlags = flags,
            principal = defaultPrincipal,
            assetValue = defaultAsset,
            annualRate = midRate,
            tenureMonths = category.defaultTenureMonths,
            processingFeePercent = category.defaultProcessingFeePercent,
            prepaymentFeePercent = prepayFee,
            isStepDown = flags.contains("EMI_STEP_DOWN"),
            moratoriumCompound = flags.contains("MOR_COMPOUND"),
            offsetBalance = LoanCalculationEngine.roundPaisa(defaultPrincipal * 0.15),
            shareValue = if (defaultAsset > 0) defaultAsset else LoanCalculationEngine.roundPaisa(defaultPrincipal * 1.6),
            billInvoiceAmount = defaultPrincipal,
            tranches = listOf(
                com.jyotirmoyb.loan.engine.TrancheEntry(1, LoanCalculationEngine.roundPaisa(defaultPrincipal * 0.30)),
                com.jyotirmoyb.loan.engine.TrancheEntry(7, LoanCalculationEngine.roundPaisa(defaultPrincipal * 0.35)),
                com.jyotirmoyb.loan.engine.TrancheEntry(13, LoanCalculationEngine.roundPaisa(defaultPrincipal * 0.35))
            ),
            flexiEvents = listOf(
                com.jyotirmoyb.loan.engine.FlexiEvent(1, drawAmount = LoanCalculationEngine.roundPaisa(defaultPrincipal * 0.60)),
                com.jyotirmoyb.loan.engine.FlexiEvent(5, drawAmount = LoanCalculationEngine.roundPaisa(defaultPrincipal * 0.25)),
                com.jyotirmoyb.loan.engine.FlexiEvent(9, repayAmount = LoanCalculationEngine.roundPaisa(defaultPrincipal * 0.40))
            )
        )

        if (navigateToCalc) {
            _activeTab.value = MainTab.CALCULATOR
        }
    }

    fun selectVariantInGroup(category: LoanCategory, groupId: String, variant: LoanVariant) {
        val current = _calcInput.value
        val updatedMap = current.selectedVariantsByGroup.toMutableMap().apply {
            put(groupId, variant.id)
        }
        val flags = category.variantGroups.mapNotNull { g ->
            val vid = updatedMap[g.id]
            g.variants.firstOrNull { it.id == vid }?.ruleFlag
        }

        val midRate = LoanCalculationEngine.roundPaisa((variant.rateRangeMin + variant.rateRangeMax) / 2.0)
        val isFloating = flags.contains("RATE_FLOATING")
        val prepayFee = variant.prepaymentFeePercent
            ?: if (isFloating) category.defaultPrepaymentFeeFloating else category.defaultPrepaymentFeeFixed

        // Adjust principal to respect new LTV cap if needed
        val ltvCap = variant.ltvCapOverride ?: category.ltvMaxPercent
        var adjustedAsset = current.assetValue
        if (category.secured && adjustedAsset > 0.0 && (current.principal / adjustedAsset) * 100.0 > ltvCap) {
            adjustedAsset = LoanCalculationEngine.roundPaisa(current.principal / (ltvCap / 100.0))
        }

        _calcInput.value = current.copy(
            selectedVariantsByGroup = updatedMap,
            ruleFlags = flags,
            annualRate = if (groupId == "rate_type" || groupId == "asset_type" || groupId == "product" || groupId == "facility") midRate else current.annualRate,
            assetValue = adjustedAsset,
            prepaymentFeePercent = prepayFee,
            isStepDown = flags.contains("EMI_STEP_DOWN"),
            moratoriumCompound = flags.contains("MOR_COMPOUND"),
            balanceTransfer = current.balanceTransfer.copy(
                enabled = current.balanceTransfer.enabled || flags.contains("PURPOSE_BALANCE_TRANSFER")
            ),
            topUp = current.topUp.copy(
                enabled = current.topUp.enabled || flags.contains("PURPOSE_TOP_UP")
            )
        )
    }

    fun updateCalcInput(transform: (CalculationInput) -> CalculationInput) {
        _calcInput.value = transform(_calcInput.value)
    }

    fun buildVariantSummary(input: CalculationInput, cat: LoanCatalog): String {
        val category = cat.categories.firstOrNull { it.id == input.categoryId } ?: return input.categoryId
        val names = category.variantGroups.mapNotNull { g ->
            val vid = input.selectedVariantsByGroup[g.id]
            g.variants.firstOrNull { it.id == vid }?.name
        }
        return if (names.isEmpty()) category.name else names.joinToString(" • ")
    }

    fun saveCurrentToMyLoans(nickname: String, lender: String, enableReminder: Boolean, daysBefore: Int) {
        viewModelScope.launch {
            val input = _calcInput.value
            val cat = catalog.value
            val category = cat.categories.firstOrNull { it.id == input.categoryId }
            val entity = SavedLoanEntity(
                nickname = nickname.trim().ifBlank { "${category?.name ?: "Loan"} Plan" },
                lender = lender.trim().ifBlank { "My Bank" },
                categoryId = input.categoryId,
                categoryName = category?.name ?: "Loan",
                variantSummary = buildVariantSummary(input, cat),
                inputJson = repository.serializeInput(input),
                reminderEnabled = enableReminder,
                reminderDaysBefore = daysBefore
            )
            val id = repository.saveLoan(entity)
            if (enableReminder) {
                val summary = buildTrackedSummary(entity.copy(id = id), cat)
                scheduleReminderForSummary(summary)
            }
            _snackbarMessage.value = "Saved \"${entity.nickname}\" to My Loans"
        }
    }

    fun addCurrentToCompare(customLabel: String? = null) {
        viewModelScope.launch {
            val input = _calcInput.value
            val cat = catalog.value
            val category = cat.categories.firstOrNull { it.id == input.categoryId }
            val summary = buildVariantSummary(input, cat)
            val label = customLabel?.takeIf { it.isNotBlank() }
                ?: "${category?.shortName ?: "Loan"} (${String.format("%.2f", input.annualRate)}%)"
            val id = repository.saveCompareScenario(
                CompareScenarioEntity(
                    label = label,
                    categoryId = input.categoryId,
                    variantSummary = summary,
                    inputJson = repository.serializeInput(input)
                )
            )
            val currentSel = _selectedCompareIds.value.toMutableSet()
            if (currentSel.size < 3) {
                currentSel.add(id)
                _selectedCompareIds.value = currentSel
            }
            _snackbarMessage.value = "Added \"$label\" to Compare"
        }
    }

    fun addPresetComparisonSet(presetType: String) {
        viewModelScope.launch {
            val cat = catalog.value
            val homeCat = cat.categories.firstOrNull { it.id == "home" } ?: return@launch
            val baseInput = _calcInput.value.let { if (it.principal > 0) it else CalculationInput() }

            val createdIds = mutableListOf<Long>()
            when (presetType) {
                "FIXED_FLOAT_HYBRID" -> {
                    val fixed = baseInput.copy(
                        categoryId = "home",
                        ruleFlags = listOf("RATE_FIXED", "EMI_STANDARD", "DISB_FULL", "PURPOSE_PURCHASE"),
                        annualRate = 9.5,
                        prepaymentFeePercent = 2.0
                    )
                    val floating = baseInput.copy(
                        categoryId = "home",
                        ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD", "DISB_FULL", "PURPOSE_PURCHASE"),
                        annualRate = 8.75,
                        rateChanges = listOf(RateChangeEvent(36, 8.25)),
                        prepaymentFeePercent = 0.0
                    )
                    val hybrid = baseInput.copy(
                        categoryId = "home",
                        ruleFlags = listOf("RATE_HYBRID", "EMI_STANDARD", "DISB_FULL", "PURPOSE_PURCHASE"),
                        annualRate = 9.0,
                        hybridFixedMonths = 36,
                        hybridFloatingRate = 9.25,
                        prepaymentFeePercent = 1.0
                    )
                    createdIds += repository.saveCompareScenario(
                        CompareScenarioEntity(label = "Home Fixed 9.5%", categoryId = "home", variantSummary = "Fixed • Standard EMI", inputJson = repository.serializeInput(fixed))
                    )
                    createdIds += repository.saveCompareScenario(
                        CompareScenarioEntity(label = "Home Floating 8.75%", categoryId = "home", variantSummary = "Floating • -0.5% @ Yr 3", inputJson = repository.serializeInput(floating))
                    )
                    createdIds += repository.saveCompareScenario(
                        CompareScenarioEntity(label = "Home Hybrid 9.0%", categoryId = "home", variantSummary = "Fixed 3y then 9.25%", inputJson = repository.serializeInput(hybrid))
                    )
                }
                "CURRENT_VS_BT" -> {
                    val cur = baseInput.copy(
                        categoryId = "home",
                        ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD", "DISB_FULL"),
                        annualRate = 10.5,
                        processingFeePercent = 0.0
                    )
                    val btOffer = baseInput.copy(
                        categoryId = "home",
                        ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD", "DISB_FULL"),
                        annualRate = 8.75,
                        processingFeePercent = 0.75
                    )
                    createdIds += repository.saveCompareScenario(
                        CompareScenarioEntity(label = "Current Bank 10.5%", categoryId = "home", variantSummary = "Existing Floating Rate", inputJson = repository.serializeInput(cur))
                    )
                    createdIds += repository.saveCompareScenario(
                        CompareScenarioEntity(label = "Transfer Offer 8.75%", categoryId = "home", variantSummary = "New Bank + 0.75% Fee", inputJson = repository.serializeInput(btOffer))
                    )
                }
            }
            _selectedCompareIds.value = createdIds.take(3).toSet()
            _snackbarMessage.value = "Loaded comparison preset"
        }
    }

    fun toggleCompareSelection(id: Long) {
        val current = _selectedCompareIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else if (current.size < 3) {
            current.add(id)
        } else {
            _snackbarMessage.value = "You can compare up to 3 scenarios at once"
            return
        }
        _selectedCompareIds.value = current
    }

    fun deleteCompareScenario(id: Long) {
        viewModelScope.launch {
            repository.deleteCompareScenario(id)
            _selectedCompareIds.value = _selectedCompareIds.value - id
        }
    }

    fun addLoanToCompare(summary: TrackedLoanSummary) {
        viewModelScope.launch {
            val id = repository.saveCompareScenario(
                CompareScenarioEntity(
                    label = summary.entity.nickname,
                    categoryId = summary.entity.categoryId,
                    variantSummary = summary.entity.variantSummary,
                    inputJson = summary.entity.inputJson
                )
            )
            val sel = _selectedCompareIds.value.toMutableSet()
            if (sel.size < 3) sel.add(id)
            _selectedCompareIds.value = sel
            _snackbarMessage.value = "Added \"${summary.entity.nickname}\" to Compare"
        }
    }

    fun toggleEmiPaid(summary: TrackedLoanSummary, monthNumber: Int) {
        viewModelScope.launch {
            val paid = summary.paidMonthNumbers.toMutableSet()
            if (paid.contains(monthNumber)) paid.remove(monthNumber) else paid.add(monthNumber)
            val updated = summary.entity.copy(paidMonthsCsv = paid.sorted().joinToString(","))
            repository.updateLoan(updated)
            if (updated.reminderEnabled) {
                scheduleReminderForSummary(buildTrackedSummary(updated, catalog.value))
            }
        }
    }

    fun markNextEmiPaid(summary: TrackedLoanSummary) {
        val next = summary.nextDueMonthNumber ?: return
        toggleEmiPaid(summary, next)
        _snackbarMessage.value = "Marked Month #$next EMI as paid"
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
            _snackbarMessage.value = "Logged prepayment — schedule recalculated"
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
            _snackbarMessage.value = "Updated interest rate to ${String.format("%.2f", newAnnualRate)}% from month ${afterMonth + 1}"
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
                scheduleReminderForSummary(buildTrackedSummary(updated, catalog.value))
                _snackbarMessage.value = "Reminder scheduled $daysBefore day(s) before next EMI"
            } else {
                EmiReminderScheduler.cancelReminder(getApplication(), updated.id)
                _snackbarMessage.value = "EMI reminder turned off"
            }
        }
    }

    private fun scheduleReminderForSummary(summary: TrackedLoanSummary) {
        val nextRow = summary.result.schedule.firstOrNull { it.monthNumber == summary.nextDueMonthNumber } ?: return
        val emiStr = CurrencyFormatter.format(nextRow.emiPaid, preferences.value.currency, false)
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

    fun deleteLoan(id: Long) {
        viewModelScope.launch {
            EmiReminderScheduler.cancelReminder(getApplication(), id)
            repository.deleteLoan(id)
            _snackbarMessage.value = "Loan removed"
        }
    }

    // Settings actions
    fun setCurrency(currency: CurrencyCode) {
        viewModelScope.launch { repository.setCurrency(currency) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setMonthlyIncome(income: Double) {
        viewModelScope.launch { repository.setMonthlyIncome(income) }
    }

    fun saveCatalogObject(updatedCatalog: LoanCatalog, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val res = repository.saveCatalog(updatedCatalog)
            if (res.isSuccess) {
                _snackbarMessage.value = "Catalog saved"
                onDone(true, "Catalog saved")
            } else {
                val msg = res.exceptionOrNull()?.message ?: "Could not save catalog"
                _snackbarMessage.value = msg
                onDone(false, msg)
            }
        }
    }

    fun saveCatalogRawJson(rawJson: String, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val res = repository.saveCatalogRawJson(rawJson)
            if (res.isSuccess) {
                _snackbarMessage.value = "Catalog JSON updated"
                onDone(true, "Catalog JSON updated")
            } else {
                val msg = res.exceptionOrNull()?.message ?: "Invalid JSON"
                _snackbarMessage.value = msg
                onDone(false, msg)
            }
        }
    }

    fun resetCatalogToDefault() {
        viewModelScope.launch {
            repository.resetCatalogToDefault()
            _snackbarMessage.value = "Catalog reset to default seed"
        }
    }

    fun restoreBackupJson(rawJson: String) {
        viewModelScope.launch {
            val res = repository.restoreFromBackupJson(rawJson)
            _snackbarMessage.value = res.fold(
                onSuccess = { it },
                onFailure = { it.message ?: "Restore failed" }
            )
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            repository.resetAllData()
            _selectedCompareIds.value = emptySet()
            _snackbarMessage.value = "All local data has been reset"
        }
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
