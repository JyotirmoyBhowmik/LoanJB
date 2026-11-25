package com.jyotirmoyb.loan.features.emi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.CompareScenarioEntity
import com.jyotirmoyb.loan.data.SavedLoanEntity
import com.jyotirmoyb.loan.engine.CalculationInput
import com.jyotirmoyb.loan.engine.CalculationResult
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import com.jyotirmoyb.loan.engine.FlexiEvent
import com.jyotirmoyb.loan.engine.LoanCalculationEngine
import com.jyotirmoyb.loan.engine.LoanCatalog
import com.jyotirmoyb.loan.engine.LoanCategory
import com.jyotirmoyb.loan.engine.LoanVariant
import com.jyotirmoyb.loan.engine.TrancheEntry
import com.jyotirmoyb.loan.features.CalculatorEngineContract
import com.jyotirmoyb.loan.util.EmiReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class EmiUiState(
    val catalog: LoanCatalog = LoanCatalog(),
    val input: CalculationInput = defaultIndianInput(),
    val result: CalculationResult = LoanCalculationEngine.calculate(defaultIndianInput()),
    val selectedCategory: LoanCategory? = null,
    val variantSummary: String = "",
    val currency: CurrencyCode = CurrencyCode.INR,
    val tenureInYears: Boolean = true,
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null
)

private fun defaultIndianInput(): CalculationInput {
    val cal = Calendar.getInstance()
    return CalculationInput(
        categoryId = "home",
        ruleFlags = listOf("RATE_FLOATING", "EMI_STANDARD", "DISB_FULL", "PURPOSE_PURCHASE"),
        principal = 2800000.0,
        assetValue = 3800000.0,
        annualRate = 10.5,
        tenureMonths = 72,
        startYear = cal.get(Calendar.YEAR),
        startMonth = cal.get(Calendar.MONTH) + 1,
        startDay = cal.get(Calendar.DAY_OF_MONTH),
        processingFeePercent = 0.0,
        prepaymentFeePercent = 0.0
    )
}

class EmiViewModel(application: Application) : AndroidViewModel(application),
    CalculatorEngineContract<CalculationInput, CalculationResult> {

    override val id: String = "loan_emi"

    private val repository = AppContainer.provideRepository(application)

    private val _input = MutableStateFlow(defaultIndianInput())
    private val _tenureInYears = MutableStateFlow(true)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private var initializedFromCatalog = false

    val uiState: StateFlow<EmiUiState> = combine(
        repository.catalogFlow,
        _input,
        repository.preferencesFlow,
        _tenureInYears,
        _snackbarMessage
    ) { catalog, input, prefs, inYears, msg ->
        val category = catalog.categories.firstOrNull { it.id == input.categoryId }
        val res = LoanCalculationEngine.calculate(input, category)
        EmiUiState(
            catalog = catalog,
            input = input,
            result = res,
            selectedCategory = category,
            variantSummary = buildVariantSummary(input, catalog),
            currency = prefs.currency,
            tenureInYears = inYears,
            isLoading = catalog.categories.isEmpty(),
            snackbarMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EmiUiState())

    init {
        viewModelScope.launch {
            val cat = repository.catalogFlow.first { it.categories.isNotEmpty() }
            if (!initializedFromCatalog) {
                initializedFromCatalog = true
                val home = cat.categories.firstOrNull { it.id == "home" } ?: cat.categories.first()
                val selectedMap = home.variantGroups.mapNotNull { g ->
                    val preferred = if (g.id == "rate_type") {
                        g.variants.firstOrNull { it.ruleFlag == "RATE_FLOATING" } ?: g.variants.firstOrNull()
                    } else {
                        g.variants.firstOrNull()
                    }
                    preferred?.let { g.id to it.id }
                }.toMap()
                val flags = home.variantGroups.mapNotNull { g ->
                    val vid = selectedMap[g.id]
                    g.variants.firstOrNull { it.id == vid }?.ruleFlag
                }
                _input.value = _input.value.copy(
                    categoryId = home.id,
                    selectedVariantsByGroup = selectedMap,
                    ruleFlags = flags
                )
            }
        }
    }

    override fun compute(input: CalculationInput): CalculationResult {
        val cat = uiState.value.catalog.categories.firstOrNull { it.id == input.categoryId }
        return LoanCalculationEngine.calculate(input, cat)
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun setTenureUnit(inYears: Boolean) {
        _tenureInYears.value = inYears
    }

    fun updateInput(transform: (CalculationInput) -> CalculationInput) {
        _input.value = transform(_input.value)
    }

    fun setStartDateToday() {
        val cal = Calendar.getInstance()
        _input.value = _input.value.copy(
            startYear = cal.get(Calendar.YEAR),
            startMonth = cal.get(Calendar.MONTH) + 1,
            startDay = cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun setStartDate(year: Int, monthOneBased: Int, day: Int) {
        _input.value = _input.value.copy(
            startYear = year,
            startMonth = monthOneBased.coerceIn(1, 12),
            startDay = day.coerceIn(1, 28)
        )
    }

    fun selectCategory(category: LoanCategory, preselectedVariant: LoanVariant? = null) {
        initializedFromCatalog = true
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
        val scale = if (uiState.value.currency == CurrencyCode.USD) 0.02 else 1.0
        val defaultPrincipal = LoanCalculationEngine.roundPaisa(category.defaultAmountInr * scale)
        val defaultAsset = LoanCalculationEngine.roundPaisa(category.defaultAssetValueInr * scale)
        val prev = _input.value

        _input.value = CalculationInput(
            categoryId = category.id,
            selectedVariantsByGroup = selectedMap,
            ruleFlags = flags,
            principal = defaultPrincipal,
            assetValue = defaultAsset,
            annualRate = midRate,
            tenureMonths = category.defaultTenureMonths,
            startYear = prev.startYear,
            startMonth = prev.startMonth,
            startDay = prev.startDay,
            processingFeePercent = category.defaultProcessingFeePercent,
            prepaymentFeePercent = prepayFee,
            isStepDown = flags.contains("EMI_STEP_DOWN"),
            moratoriumCompound = flags.contains("MOR_COMPOUND"),
            offsetBalance = LoanCalculationEngine.roundPaisa(defaultPrincipal * 0.15),
            shareValue = if (defaultAsset > 0) defaultAsset else LoanCalculationEngine.roundPaisa(defaultPrincipal * 1.6),
            billInvoiceAmount = defaultPrincipal,
            tranches = listOf(
                TrancheEntry(1, LoanCalculationEngine.roundPaisa(defaultPrincipal * 0.30)),
                TrancheEntry(7, LoanCalculationEngine.roundPaisa(defaultPrincipal * 0.35)),
                TrancheEntry(13, LoanCalculationEngine.roundPaisa(defaultPrincipal * 0.35))
            ),
            flexiEvents = listOf(
                FlexiEvent(1, drawAmount = LoanCalculationEngine.roundPaisa(defaultPrincipal * 0.60)),
                FlexiEvent(5, drawAmount = LoanCalculationEngine.roundPaisa(defaultPrincipal * 0.25)),
                FlexiEvent(9, repayAmount = LoanCalculationEngine.roundPaisa(defaultPrincipal * 0.40))
            )
        )
        _tenureInYears.value = category.defaultTenureMonths >= 12 && category.defaultTenureMonths % 12 == 0
    }

    fun selectVariantInGroup(category: LoanCategory, groupId: String, variant: LoanVariant) {
        val current = _input.value
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

        val ltvCap = variant.ltvCapOverride ?: category.ltvMaxPercent
        var adjustedAsset = current.assetValue
        if (category.secured && adjustedAsset > 0.0 && (current.principal / adjustedAsset) * 100.0 > ltvCap) {
            adjustedAsset = LoanCalculationEngine.roundPaisa(current.principal / (ltvCap / 100.0))
        }

        _input.value = current.copy(
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

    /**
     * Switches the EMI structure flag directly from Advance Settings (Reducing, Flat, Step-Up, Step-Down, Balloon, Hybrid).
     */
    fun selectEmiModeFlag(modeFlag: String) {
        val current = _input.value
        val emiFlags = setOf(
            "EMI_STANDARD", "RATE_FLAT", "EMI_STEP_UP", "EMI_STEP_DOWN",
            "EMI_BALLOON", "RATE_HYBRID", "EMI_OFFSET"
        )
        val filtered = current.ruleFlags.filterNot { it in emiFlags }.toMutableList()
        filtered.add(modeFlag)
        _input.value = current.copy(
            ruleFlags = filtered,
            isStepDown = modeFlag == "EMI_STEP_DOWN"
        )
    }

    fun toggleMoratorium(enabled: Boolean) {
        val current = _input.value
        val morFlags = setOf("MOR_SIMPLE", "MOR_COMPOUND", "MORATORIUM")
        val filtered = current.ruleFlags.filterNot { it in morFlags }.toMutableList()
        if (enabled) {
            filtered.add(if (current.moratoriumCompound) "MOR_COMPOUND" else "MOR_SIMPLE")
        }
        _input.value = current.copy(
            ruleFlags = filtered,
            moratoriumMonths = if (enabled && current.moratoriumMonths <= 0) 6 else current.moratoriumMonths
        )
    }

    fun saveCurrentToMyLoans(nickname: String, lender: String, enableReminder: Boolean, daysBefore: Int) {
        viewModelScope.launch {
            val state = uiState.value
            val input = state.input
            val category = state.selectedCategory
            val entity = SavedLoanEntity(
                nickname = nickname.trim().ifBlank { "${category?.name ?: "Loan"} Plan" },
                lender = lender.trim().ifBlank { "My Bank" },
                categoryId = input.categoryId,
                categoryName = category?.name ?: "Loan",
                variantSummary = state.variantSummary,
                inputJson = repository.serializeInput(input),
                reminderEnabled = enableReminder,
                reminderDaysBefore = daysBefore
            )
            val id = repository.saveLoan(entity)
            if (enableReminder && state.result.schedule.isNotEmpty()) {
                val firstRow = state.result.schedule.first()
                EmiReminderScheduler.scheduleNextReminder(
                    context = getApplication(),
                    loanId = id,
                    nickname = entity.nickname,
                    emiFormatted = CurrencyFormatter.format(firstRow.emiPaid, state.currency, false),
                    dueYear = input.startYear,
                    dueMonthOneBased = input.startMonth,
                    dueDay = input.startDay,
                    daysBefore = daysBefore
                )
            }
            _snackbarMessage.value = "Saved \"${entity.nickname}\" to Saved Loans"
        }
    }

    fun addCurrentToCompare(customLabel: String? = null) {
        viewModelScope.launch {
            val state = uiState.value
            val input = state.input
            val category = state.selectedCategory
            val label = customLabel?.takeIf { it.isNotBlank() }
                ?: "${category?.shortName ?: "Loan"} (${String.format("%.2f", input.annualRate)}%)"
            repository.saveCompareScenario(
                CompareScenarioEntity(
                    label = label,
                    categoryId = input.categoryId,
                    variantSummary = state.variantSummary,
                    inputJson = repository.serializeInput(input)
                )
            )
            _snackbarMessage.value = "Added \"$label\" to Loan Comparison"
        }
    }

    fun buildShareSummaryText(): String {
        val state = uiState.value
        val r = state.result
        val cur = state.currency
        val name = state.selectedCategory?.name ?: "Loan (EMI)"
        return buildString {
            appendLine("LoanJB — $name Summary")
            if (state.variantSummary.isNotBlank()) appendLine("Plan: ${state.variantSummary}")
            appendLine("Loan Amount: ${CurrencyFormatter.format(r.totalPrincipal, cur)}")
            appendLine("Interest Rate: ${CurrencyFormatter.formatPercent(state.input.annualRate)}")
            appendLine("Tenure: ${r.actualTenureMonths} months")
            appendLine("Monthly EMI: ${CurrencyFormatter.format(r.monthlyEmi, cur, true)}")
            appendLine("Total Interest: ${CurrencyFormatter.format(r.totalInterest, cur)}")
            appendLine("Total Payment: ${CurrencyFormatter.format(r.totalPayment, cur)}")
            r.insights.prepaymentImpact?.let { p ->
                appendLine("Prepayment Savings: ${CurrencyFormatter.format(p.interestSaved, cur)} interest, ${p.monthsSaved} month(s) saved")
            }
            appendLine("Estimates only. Actual bank terms may differ.")
        }
    }

    private fun buildVariantSummary(input: CalculationInput, cat: LoanCatalog): String {
        val category = cat.categories.firstOrNull { it.id == input.categoryId } ?: return input.categoryId
        val names = category.variantGroups.mapNotNull { g ->
            val vid = input.selectedVariantsByGroup[g.id]
            g.variants.firstOrNull { it.id == vid }?.name
        }
        return if (names.isEmpty()) category.name else names.joinToString(" • ")
    }
}
