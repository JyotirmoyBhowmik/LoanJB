package com.jyotirmoyb.loan.features.emi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.data.CompareScenarioEntity
import com.jyotirmoyb.loan.data.SavedLoanEntity
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CalculationInput
import com.jyotirmoyb.loan.domain.CalculationResult
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.FlexiEvent
import com.jyotirmoyb.loan.domain.LoanCalculationEngine
import com.jyotirmoyb.loan.domain.LoanCatalog
import com.jyotirmoyb.loan.domain.LoanCategory
import com.jyotirmoyb.loan.domain.LoanVariant
import com.jyotirmoyb.loan.domain.RateChangeEvent
import com.jyotirmoyb.loan.domain.STANDARD_PLANNING_DISCLAIMER
import com.jyotirmoyb.loan.domain.TrancheEntry
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
import kotlin.math.abs

data class EmiUiState(
    val catalog: LoanCatalog = LoanCatalog(),
    val input: CalculationInput = defaultIndianInput(),
    val result: CalculationResult = LoanCalculationEngine.calculate(defaultIndianInput()),
    val selectedCategory: LoanCategory? = null,
    val variantSummary: String = "",
    val currency: CurrencyCode = CurrencyCode.INR,
    val tenureInYears: Boolean = true,
    val resolvedConfig: Map<String, ResolvedConfigItem> = emptyMap(),
    val rateSourceItem: ResolvedConfigItem? = null,
    val gstSourceItem: ResolvedConfigItem? = null,
    val defaultSuggestedRate: Double = 8.5,
    val isRateCustomized: Boolean = false,
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null
)

private fun defaultIndianInput(): CalculationInput {
    val cal = Calendar.getInstance()
    return CalculationInput(
        categoryId = "home",
        ruleFlags = listOf("RATE_EBLR", "EMI_STANDARD", "DISB_FULL", "PURPOSE_PURCHASE"),
        principal = 2800000.0,
        assetValue = 3800000.0,
        annualRate = 8.5,
        tenureMonths = 72,
        startYear = cal.get(Calendar.YEAR),
        startMonth = cal.get(Calendar.MONTH) + 1,
        startDay = cal.get(Calendar.DAY_OF_MONTH),
        emiDayOfMonth = 5,
        benchmarkRatePercent = 6.5,
        spreadPercent = 2.0,
        processingFeePercent = 0.0,
        prepaymentFeePercent = 0.0,
        gstRatePercent = 18.0,
        goldAuctionRiskLtvPercent = 72.0
    )
}

class EmiViewModel(application: Application) : AndroidViewModel(application),
    CalculatorEngineContract<CalculationInput, CalculationResult> {

    override val id: String = "loan_emi"

    private val repository = AppContainer.provideRepository(application)
    private val ratesRepository = AppContainer.provideRatesRepository(application)

    private val _input = MutableStateFlow(defaultIndianInput())
    private val _tenureInYears = MutableStateFlow(true)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private var initializedFromCatalog = false

    val uiState: StateFlow<EmiUiState> = combine(
        repository.catalogFlow,
        ratesRepository.resolvedMapFlow,
        _input,
        repository.preferencesFlow,
        _tenureInYears,
        _snackbarMessage
    ) { values ->
        val rawCatalog = values[0] as LoanCatalog
        @Suppress("UNCHECKED_CAST")
        val resolvedMap = values[1] as Map<String, ResolvedConfigItem>
        val rawInput = values[2] as CalculationInput
        val prefs = values[3] as com.jyotirmoyb.loan.data.UserPreferences
        val inYears = values[4] as Boolean
        val msg = values[5] as String?

        val catalog = DataSourceResolver.resolveCatalog(rawCatalog, resolvedMap)
        val category = catalog.categories.firstOrNull { it.id == rawInput.categoryId }

        val effectiveInput = applyDataDrivenBounds(rawInput, resolvedMap)
        val res = LoanCalculationEngine.calculate(effectiveInput, category)

        val rateSourceKey = category?.rateMinKey ?: ConfigKeys.HOME_RATE_MIN
        val rateSourceItem = resolvedMap[rateSourceKey] ?: resolvedMap[ConfigKeys.RBI_REPO_RATE]
        val gstSourceItem = resolvedMap[ConfigKeys.GST_BANKING_SERVICES]
        val defaultRate = category?.let {
            LoanCalculationEngine.roundPaisa((it.rateRangeMin + it.rateRangeMax) / 2.0)
        } ?: 8.5

        EmiUiState(
            catalog = catalog,
            input = effectiveInput,
            result = res,
            selectedCategory = category,
            variantSummary = buildVariantSummary(effectiveInput, catalog),
            currency = prefs.currency,
            tenureInYears = inYears,
            resolvedConfig = resolvedMap,
            rateSourceItem = rateSourceItem,
            gstSourceItem = gstSourceItem,
            defaultSuggestedRate = defaultRate,
            isRateCustomized = abs(effectiveInput.annualRate - defaultRate) > 0.01,
            isLoading = catalog.categories.isEmpty(),
            snackbarMessage = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EmiUiState())

    init {
        viewModelScope.launch {
            val rawCat = repository.catalogFlow.first { it.categories.isNotEmpty() }
            val resolvedMap = ratesRepository.resolvedMapFlow.first()
            val cat = DataSourceResolver.resolveCatalog(rawCat, resolvedMap)
            if (!initializedFromCatalog) {
                initializedFromCatalog = true
                val home = cat.categories.firstOrNull { it.id == "home" } ?: cat.categories.first()
                val selectedMap = home.variantGroups.mapNotNull { g ->
                    val preferred = if (g.id == "rate_type") {
                        g.variants.firstOrNull { it.ruleFlag == "RATE_EBLR" } ?: g.variants.firstOrNull()
                    } else {
                        g.variants.firstOrNull()
                    }
                    preferred?.let { g.id to it.id }
                }.toMap()
                val flags = home.variantGroups.mapNotNull { g ->
                    val vid = selectedMap[g.id]
                    g.variants.firstOrNull { it.id == vid }?.ruleFlag
                }
                val repo = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.RBI_REPO_RATE, 6.5)
                val spread = DataSourceResolver.resolveDouble(resolvedMap, ConfigKeys.HOME_EBLR_SPREAD, 2.0)
                val defaultEblr = LoanCalculationEngine.roundPaisa(repo + spread)
                _input.value = _input.value.copy(
                    categoryId = home.id,
                    selectedVariantsByGroup = selectedMap,
                    ruleFlags = flags,
                    benchmarkRatePercent = repo,
                    spreadPercent = spread,
                    annualRate = defaultEblr
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

    fun resetRateToDefault() {
        val state = uiState.value
        val resolved = state.resolvedConfig
        val flags = state.input.ruleFlags.toSet()
        if (flags.contains("RATE_EBLR")) {
            val repo = DataSourceResolver.resolveDouble(resolved, ConfigKeys.RBI_REPO_RATE, 6.5)
            val spread = DataSourceResolver.resolveDouble(resolved, ConfigKeys.HOME_EBLR_SPREAD, 2.0)
            _input.value = _input.value.copy(
                benchmarkRatePercent = repo,
                spreadPercent = spread,
                annualRate = LoanCalculationEngine.roundPaisa(repo + spread)
            )
        } else {
            _input.value = _input.value.copy(annualRate = state.defaultSuggestedRate)
        }
        _snackbarMessage.value = "Interest rate reset to data default"
    }

    fun applyRepoRateScenario(deltaPercent: Double) {
        val curr = _input.value
        val afterMonth = ((curr.rateChanges.lastOrNull()?.afterMonth ?: 6) + 6).coerceAtMost(curr.tenureMonths)
        val baseRate = curr.rateChanges.lastOrNull()?.newAnnualRate ?: curr.annualRate
        val newRate = LoanCalculationEngine.roundPaisa((baseRate + deltaPercent).coerceIn(1.0, 35.0))
        _input.value = curr.copy(
            rateChanges = curr.rateChanges + RateChangeEvent(afterMonth = afterMonth, newAnnualRate = newRate)
        )
        val dir = if (deltaPercent > 0) "hike" else "cut"
        _snackbarMessage.value = "Added repo $dir (${if (deltaPercent > 0) "+" else ""}${String.format("%.2f", deltaPercent)}%) at Month $afterMonth → ${String.format("%.2f", newRate)}%"
    }

    fun updateEmiDayOfMonth(day: Int) {
        _input.value = _input.value.copy(emiDayOfMonth = day.coerceIn(1, 31))
    }

    fun toggleProRataFirstMonth(enabled: Boolean) {
        _input.value = _input.value.copy(proRataFirstMonthEnabled = enabled)
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
        val resolved = uiState.value.resolvedConfig
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

        val repo = DataSourceResolver.resolveDouble(resolved, ConfigKeys.RBI_REPO_RATE, 6.5)
        val spread = DataSourceResolver.resolveDouble(resolved, ConfigKeys.HOME_EBLR_SPREAD, 2.0)

        val midRate = when {
            flags.contains("RATE_EBLR") -> LoanCalculationEngine.roundPaisa(repo + spread)
            preselectedVariant != null -> LoanCalculationEngine.roundPaisa((preselectedVariant.rateRangeMin + preselectedVariant.rateRangeMax) / 2.0)
            else -> LoanCalculationEngine.roundPaisa((category.rateRangeMin + category.rateRangeMax) / 2.0)
        }

        val isFloating = flags.contains("RATE_FLOATING") || flags.contains("RATE_EBLR") || flags.contains("RATE_MCLR")
        val prepayFee = if (isFloating) category.defaultPrepaymentFeeFloating else category.defaultPrepaymentFeeFixed
        val scale = if (uiState.value.currency == CurrencyCode.USD) 0.02 else 1.0
        val defaultPrincipal = LoanCalculationEngine.roundPaisa(category.defaultAmountInr * scale)
        val defaultAsset = LoanCalculationEngine.roundPaisa(category.defaultAssetValueInr * scale)
        val prev = _input.value

        val gstRate = DataSourceResolver.resolveDouble(resolved, ConfigKeys.GST_BANKING_SERVICES, 18.0)
        val pmayCap = DataSourceResolver.resolveDouble(resolved, ConfigKeys.PMAY_MAX_SUBSIDY, 180000.0)
        val isCcEmi = category.id == "credit_card_emi" || flags.contains("CC_EMI")

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
            emiDayOfMonth = prev.emiDayOfMonth,
            proRataFirstMonthEnabled = prev.proRataFirstMonthEnabled,
            benchmarkRatePercent = repo,
            spreadPercent = spread,
            processingFeePercent = category.defaultProcessingFeePercent,
            gstOnFeesEnabled = isCcEmi || prev.gstOnFeesEnabled,
            gstRatePercent = gstRate,
            prepaymentFeePercent = prepayFee,
            pmaySubsidyEnabled = false,
            pmaySubsidyAmount = pmayCap,
            isStepDown = flags.contains("EMI_STEP_DOWN"),
            moratoriumCompound = flags.contains("MOR_COMPOUND"),
            offsetBalance = LoanCalculationEngine.roundPaisa(defaultPrincipal * 0.15),
            shareValue = if (defaultAsset > 0) defaultAsset else LoanCalculationEngine.roundPaisa(defaultPrincipal * 2.0),
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
        val resolved = uiState.value.resolvedConfig
        val updatedMap = current.selectedVariantsByGroup.toMutableMap().apply {
            put(groupId, variant.id)
        }
        val flags = category.variantGroups.mapNotNull { g ->
            val vid = updatedMap[g.id]
            g.variants.firstOrNull { it.id == vid }?.ruleFlag
        }

        val repo = DataSourceResolver.resolveDouble(resolved, ConfigKeys.RBI_REPO_RATE, current.benchmarkRatePercent)
        val spread = DataSourceResolver.resolveDouble(resolved, ConfigKeys.HOME_EBLR_SPREAD, current.spreadPercent)
        val midRate = if (variant.ruleFlag == "RATE_EBLR") {
            LoanCalculationEngine.roundPaisa(repo + spread)
        } else {
            LoanCalculationEngine.roundPaisa((variant.rateRangeMin + variant.rateRangeMax) / 2.0)
        }

        val isFloating = flags.contains("RATE_FLOATING") || flags.contains("RATE_EBLR") || flags.contains("RATE_MCLR")
        val prepayFee = variant.prepaymentFeePercent
            ?: if (isFloating) category.defaultPrepaymentFeeFloating else category.defaultPrepaymentFeeFixed

        val ltvCap = variant.ltvCapOverride ?: category.ltvMaxPercent
        var adjustedAsset = current.assetValue
        if (category.secured && ltvCap > 0.0 && adjustedAsset > 0.0 && (current.principal / adjustedAsset) * 100.0 > ltvCap) {
            adjustedAsset = LoanCalculationEngine.roundPaisa(current.principal / (ltvCap / 100.0))
        }

        val shouldUpdateRate = groupId in setOf(
            "rate_type", "vehicle_type", "study_region", "gold_repayment",
            "lap_structure", "collateral_type", "biz_facility", "cc_emi_type",
            "facility_type", "rate_basis"
        )

        _input.value = current.copy(
            selectedVariantsByGroup = updatedMap,
            ruleFlags = flags,
            benchmarkRatePercent = repo,
            spreadPercent = spread,
            annualRate = if (shouldUpdateRate) midRate else current.annualRate,
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

    fun saveCurrentToMyLoans(
        nickname: String,
        lender: String,
        enableReminder: Boolean,
        daysBefore: Int,
        emiDayOfMonth: Int = _input.value.emiDayOfMonth,
        autoDebitNach: Boolean = false
    ) {
        viewModelScope.launch {
            val state = uiState.value
            val clampedDay = emiDayOfMonth.coerceIn(1, 28)
            val input = state.input.copy(emiDayOfMonth = clampedDay)
            val category = state.selectedCategory
            val entity = SavedLoanEntity(
                nickname = nickname.trim().ifBlank { "${category?.name ?: "Loan"} Plan" },
                lender = lender.trim().ifBlank { "My Bank" },
                categoryId = input.categoryId,
                categoryName = category?.name ?: "Loan",
                variantSummary = state.variantSummary,
                inputJson = repository.serializeInput(input),
                reminderEnabled = enableReminder,
                reminderDaysBefore = daysBefore,
                autoDebitNach = autoDebitNach,
                emiDayOfMonth = clampedDay
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
                    dueDay = clampedDay,
                    daysBefore = daysBefore,
                    autoDebitNach = autoDebitNach
                )
            }
            val nachTag = if (autoDebitNach) " (NACH Auto-Debit)" else ""
            _snackbarMessage.value = "Saved \"${entity.nickname}\"$nachTag to Saved Loans"
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
            appendLine("Tenure: ${r.actualTenureMonths} months (Loan-Free: ${r.loanFreeDateLabel})")
            appendLine("Monthly EMI: ${CurrencyFormatter.format(r.monthlyEmi, cur, true)}")
            appendLine("Total Interest: ${CurrencyFormatter.format(r.totalInterest, cur)} (${String.format("%.1f", r.interestToPrincipalPercent)}% of principal)")
            appendLine("Total Payable: ${CurrencyFormatter.format(r.totalPayment, cur)}")
            r.insights.prepaymentImpact?.let { p ->
                appendLine("Prepayment Savings: ${CurrencyFormatter.format(p.interestSaved, cur)} interest, ${p.monthsSaved} month(s) saved")
            }
            appendLine(STANDARD_PLANNING_DISCLAIMER)
        }
    }

    private fun applyDataDrivenBounds(
        input: CalculationInput,
        resolved: Map<String, ResolvedConfigItem>
    ): CalculationInput {
        if (resolved.isEmpty()) return input
        return input.copy(
            gstRatePercent = DataSourceResolver.resolveDouble(resolved, ConfigKeys.GST_BANKING_SERVICES, input.gstRatePercent),
            goldAuctionRiskLtvPercent = DataSourceResolver.resolveDouble(resolved, ConfigKeys.GOLD_AUCTION_RISK_LTV, input.goldAuctionRiskLtvPercent),
            section24bCap = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SEC_24B_CAP, input.section24bCap),
            section80cCap = DataSourceResolver.resolveDouble(resolved, ConfigKeys.SEC_80C_CAP, input.section80cCap),
            section80eMaxYears = DataSourceResolver.resolveInt(resolved, ConfigKeys.SEC_80E_MAX_YEARS, input.section80eMaxYears)
        )
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
