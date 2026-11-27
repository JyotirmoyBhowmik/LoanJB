package com.jyotirmoyb.loan.features.planners.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.features.deposits.fd.HeroMetricItem
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.ResetToDefaultAction
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import kotlin.math.roundToInt

@Composable
fun FinancialRulesScreen(
    uiState: FinancialRulesUiState,
    onSelectTab: (FinancialRuleTab) -> Unit,
    onUpdateMonthlyIncome: (Double) -> Unit,
    onUpdateBudgetSplit: (Double, Double, Double) -> Unit,
    onResetBudgetSplit: () -> Unit,
    onUpdateEssentialExpenses: (Double) -> Unit,
    onUpdateTargetMonths: (Int) -> Unit,
    onResetEmergencyMonths: () -> Unit,
    onUpdateCurrentLiquidSavings: (Double) -> Unit,
    onUpdateMonthlyEmergencyAlloc: (Double) -> Unit,
    onUpdateAnnualIncome: (Double) -> Unit,
    onUpdateExistingLiabilities: (Double) -> Unit,
    onUpdateExistingTermCover: (Double) -> Unit,
    onUpdateTermMultiplier: (Double) -> Unit,
    onResetTermMultiplier: () -> Unit,
    onSetMetroCity: (Boolean) -> Unit,
    onUpdateFamilyMembers: (Int) -> Unit,
    onUpdateExistingHealthCover: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = uiState.currency

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("financial_rules_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "rules_tabs") {
            val tabs = listOf(
                FinancialRuleTab.BUDGET_50_30_20 to stringResource(R.string.rules_tab_budget),
                FinancialRuleTab.EMERGENCY_FUND to stringResource(R.string.rules_tab_emergency),
                FinancialRuleTab.INSURANCE_COVER to stringResource(R.string.rules_tab_insurance)
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rules_tab_selector")
            ) {
                tabs.forEachIndexed { idx, (tab, label) ->
                    SegmentedButton(
                        selected = uiState.activeTab == tab,
                        onClick = { onSelectTab(tab) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size),
                        modifier = Modifier.testTag("rules_tab_${tab.name.lowercase()}")
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }

        when (uiState.activeTab) {
            FinancialRuleTab.BUDGET_50_30_20 -> {
                item(key = "budget_content") {
                    BudgetRuleSection(
                        uiState = uiState,
                        currency = currency,
                        onUpdateMonthlyIncome = onUpdateMonthlyIncome,
                        onUpdateBudgetSplit = onUpdateBudgetSplit,
                        onResetBudgetSplit = onResetBudgetSplit
                    )
                }
            }
            FinancialRuleTab.EMERGENCY_FUND -> {
                item(key = "emergency_content") {
                    EmergencyFundSection(
                        uiState = uiState,
                        currency = currency,
                        onUpdateEssentialExpenses = onUpdateEssentialExpenses,
                        onUpdateTargetMonths = onUpdateTargetMonths,
                        onResetEmergencyMonths = onResetEmergencyMonths,
                        onUpdateCurrentLiquidSavings = onUpdateCurrentLiquidSavings,
                        onUpdateMonthlyEmergencyAlloc = onUpdateMonthlyEmergencyAlloc
                    )
                }
            }
            FinancialRuleTab.INSURANCE_COVER -> {
                item(key = "insurance_content") {
                    InsuranceCoverSection(
                        uiState = uiState,
                        currency = currency,
                        onUpdateAnnualIncome = onUpdateAnnualIncome,
                        onUpdateExistingLiabilities = onUpdateExistingLiabilities,
                        onUpdateExistingTermCover = onUpdateExistingTermCover,
                        onUpdateTermMultiplier = onUpdateTermMultiplier,
                        onResetTermMultiplier = onResetTermMultiplier,
                        onSetMetroCity = onSetMetroCity,
                        onUpdateFamilyMembers = onUpdateFamilyMembers,
                        onUpdateExistingHealthCover = onUpdateExistingHealthCover
                    )
                }
            }
        }

        item(key = "rules_disclaimer") {
            DisclaimerBanner()
        }
    }
}

@Composable
private fun BudgetRuleSection(
    uiState: FinancialRulesUiState,
    currency: CurrencyCode,
    onUpdateMonthlyIncome: (Double) -> Unit,
    onUpdateBudgetSplit: (Double, Double, Double) -> Unit,
    onResetBudgetSplit: () -> Unit
) {
    val res = uiState.budgetResult
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FintechCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionAccentHeader(
                    title = "Monthly Budget Split",
                    badge = "${uiState.needsPercent.toInt()}/${uiState.wantsPercent.toInt()}/${uiState.savingsPercent.toInt()}"
                )
                ResetToDefaultAction(
                    visible = uiState.hasCustomSplit,
                    onClick = onResetBudgetSplit,
                    testTag = "rules_budget_reset_btn"
                )
            }
            uiState.budgetConfigItem?.let {
                Spacer(Modifier.height(4.dp))
                ValueSourceBadgesRow(item = it)
            }
            Spacer(Modifier.height(14.dp))

            if (!res.isValid && res.validationError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = res.validationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DonutSplitChart(
                    segments = listOf(
                        InfoBlue to uiState.needsPercent.toFloat(),
                        InterestAmber to uiState.wantsPercent.toFloat(),
                        PrincipalEmerald to uiState.savingsPercent.toFloat()
                    ),
                    centerTitle = CurrencyFormatter.formatCompact(uiState.monthlyIncome, currency),
                    centerSubtitle = "Monthly",
                    modifier = Modifier
                        .size(134.dp)
                        .testTag("rules_budget_donut_chart")
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BucketLegendRow(
                        color = InfoBlue,
                        title = "${stringResource(R.string.rules_needs)} (${uiState.needsPercent.toInt()}%)",
                        monthly = CurrencyFormatter.format(res.needsMonthly, currency),
                        annual = "${CurrencyFormatter.formatCompact(res.needsAnnual, currency)}/yr"
                    )
                    BucketLegendRow(
                        color = InterestAmber,
                        title = "${stringResource(R.string.rules_wants)} (${uiState.wantsPercent.toInt()}%)",
                        monthly = CurrencyFormatter.format(res.wantsMonthly, currency),
                        annual = "${CurrencyFormatter.formatCompact(res.wantsAnnual, currency)}/yr"
                    )
                    BucketLegendRow(
                        color = PrincipalEmerald,
                        title = "${stringResource(R.string.rules_savings)} (${uiState.savingsPercent.toInt()}%)",
                        monthly = CurrencyFormatter.format(res.savingsMonthly, currency),
                        annual = "${CurrencyFormatter.formatCompact(res.savingsAnnual, currency)}/yr"
                    )
                }
            }
        }

        FintechCard {
            SectionAccentHeader(title = "Income & Editable Splits", badge = "Auto-Balanced")
            Spacer(Modifier.height(10.dp))
            AmountField(
                amount = uiState.monthlyIncome,
                currency = currency,
                onAmountChange = onUpdateMonthlyIncome,
                label = stringResource(R.string.rules_monthly_income),
                modifier = Modifier.testTag("rules_monthly_income_input")
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(50.0, 30.0, 20.0) to "50/30/20",
                    Triple(50.0, 20.0, 30.0) to "50/20/30",
                    Triple(40.0, 20.0, 40.0) to "40/20/40"
                ).forEach { (split, label) ->
                    val selected = uiState.needsPercent.toInt() == split.first.toInt() &&
                        uiState.wantsPercent.toInt() == split.second.toInt() &&
                        uiState.savingsPercent.toInt() == split.third.toInt()
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateBudgetSplit(split.first, split.second, split.third) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("rules_preset_$label")
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            SplitSliderRow(
                label = stringResource(R.string.rules_needs),
                percent = uiState.needsPercent.toInt(),
                color = InfoBlue,
                onPercentChange = { newNeeds ->
                    val clamped = newNeeds.coerceIn(10, 80)
                    val remaining = 100 - clamped
                    val newWants = uiState.wantsPercent.toInt().coerceIn(0, remaining)
                    val newSavings = (remaining - newWants).coerceAtLeast(0)
                    onUpdateBudgetSplit(clamped.toDouble(), newWants.toDouble(), newSavings.toDouble())
                },
                testTag = "rules_needs_slider"
            )
            SplitSliderRow(
                label = stringResource(R.string.rules_wants),
                percent = uiState.wantsPercent.toInt(),
                color = InterestAmber,
                onPercentChange = { newWants ->
                    val maxWants = (100 - uiState.needsPercent.toInt()).coerceAtLeast(0)
                    val clamped = newWants.coerceIn(0, maxWants)
                    val newSavings = (100 - uiState.needsPercent.toInt() - clamped).coerceAtLeast(0)
                    onUpdateBudgetSplit(uiState.needsPercent, clamped.toDouble(), newSavings.toDouble())
                },
                testTag = "rules_wants_slider"
            )
            SplitSliderRow(
                label = stringResource(R.string.rules_savings),
                percent = uiState.savingsPercent.toInt(),
                color = PrincipalEmerald,
                onPercentChange = { newSavings ->
                    val maxSav = (100 - uiState.needsPercent.toInt()).coerceAtLeast(0)
                    val clamped = newSavings.coerceIn(0, maxSav)
                    val newWants = (100 - uiState.needsPercent.toInt() - clamped).coerceAtLeast(0)
                    onUpdateBudgetSplit(uiState.needsPercent, newWants.toDouble(), clamped.toDouble())
                },
                testTag = "rules_savings_slider"
            )
        }
    }
}

@Composable
private fun EmergencyFundSection(
    uiState: FinancialRulesUiState,
    currency: CurrencyCode,
    onUpdateEssentialExpenses: (Double) -> Unit,
    onUpdateTargetMonths: (Int) -> Unit,
    onResetEmergencyMonths: () -> Unit,
    onUpdateCurrentLiquidSavings: (Double) -> Unit,
    onUpdateMonthlyEmergencyAlloc: (Double) -> Unit
) {
    val res = uiState.emergencyResult
    val progress = (res.fundedPercent / 100.0).toFloat().coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FintechCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionAccentHeader(title = "Emergency Fund Target", badge = "${res.targetMonths} Months")
                ResetToDefaultAction(
                    visible = uiState.hasCustomEmergencyMonths,
                    onClick = onResetEmergencyMonths,
                    testTag = "rules_emg_reset_btn"
                )
            }
            uiState.emergencyConfigItem?.let {
                Spacer(Modifier.height(4.dp))
                ValueSourceBadgesRow(item = it)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = CurrencyFormatter.format(res.targetCorpus, currency),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("rules_emg_target_value")
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.ui_txt_funded, res.fundedPercent),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (res.shortfall <= 0.0) SavingsGreen else InfoBlue
                )
                Text(
                    text = if (res.shortfall <= 0.0) "Fully Funded" else "Gap: ${CurrencyFormatter.format(res.shortfall, currency)}",
                    style = MonoSmallStyle,
                    color = if (res.shortfall <= 0.0) SavingsGreen else InterestAmber
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("rules_emg_progress_bar")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (res.shortfall <= 0.0) PrincipalEmerald else InfoBlue)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HeroMetricItem("Saved Now", CurrencyFormatter.formatCompact(res.currentLiquidSavings, currency), PrincipalEmerald)
                HeroMetricItem(
                    "Shortfall",
                    CurrencyFormatter.formatCompact(res.shortfall, currency),
                    if (res.shortfall >
                        0.0
                    ) {
                        InterestAmber
                    } else {
                        SavingsGreen
                    }
                )
                HeroMetricItem(
                    "Time to Build",
                    when (val m = res.monthsToBuild) {
                        null -> "Set savings"
                        0 -> "Ready!"
                        else -> "$m mos"
                    },
                    InfoBlue
                )
            }
        }

        FintechCard {
            SectionAccentHeader(title = "Essential Expenses & Buffer", badge = "Liquid Only")
            Spacer(Modifier.height(10.dp))
            AmountField(
                amount = uiState.monthlyEssentialExpenses,
                currency = currency,
                onAmountChange = onUpdateEssentialExpenses,
                label = stringResource(R.string.rules_essential_exp),
                modifier = Modifier.testTag("rules_emg_expenses_input")
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.ui_txt_safety_buffer_duration),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(R.string.ui_txt_months, uiState.targetMonths),
                    style = MonoSmallStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = uiState.targetMonths.toFloat(),
                onValueChange = { onUpdateTargetMonths(it.roundToInt()) },
                valueRange = 3f..12f,
                steps = 8,
                modifier = Modifier.testTag("rules_emg_months_slider")
            )
            AmountField(
                amount = uiState.currentLiquidSavings,
                currency = currency,
                onAmountChange = onUpdateCurrentLiquidSavings,
                label = stringResource(R.string.rules_liquid_saved),
                modifier = Modifier.testTag("rules_emg_liquid_input")
            )
            Spacer(Modifier.height(10.dp))
            AmountField(
                amount = uiState.monthlyEmergencyAllocation,
                currency = currency,
                onAmountChange = onUpdateMonthlyEmergencyAlloc,
                label = stringResource(R.string.rules_monthly_alloc),
                modifier = Modifier.testTag("rules_emg_alloc_input")
            )
        }
    }
}
