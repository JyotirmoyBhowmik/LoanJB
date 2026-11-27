package com.jyotirmoyb.loan.features.emi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CalculationInput
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.LoanCalculationEngine
import com.jyotirmoyb.loan.domain.LoanCategory
import com.jyotirmoyb.loan.domain.RateChangeEvent
import com.jyotirmoyb.loan.domain.TrancheEntry
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.DangerRed

/**
 * Renders catalog-variant-specific rows inside AdvanceSettingsCard ONLY when the
 * currently selected loan category/variant requires those specialized inputs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VariantAdaptiveAdvanceRows(
    input: CalculationInput,
    category: LoanCategory?,
    currency: CurrencyCode,
    resolvedConfig: Map<String, ResolvedConfigItem>,
    expandedRowKey: String?,
    onToggleExpand: (String) -> Unit,
    onShowInfo: (String, String) -> Unit,
    onUpdateInput: ((CalculationInput) -> CalculationInput) -> Unit,
    onApplyRepoScenario: (Double) -> Unit
) {
    val flags = input.ruleFlags.toSet()

    // Asset Value & LTV (secured loans)
    if (category?.secured == true) {
        val ltvPct = if (input.assetValue > 0.0) (input.principal / input.assetValue) * 100.0 else 0.0
        val ltvConfigItem = category.ltvCapKey?.let { resolvedConfig[it] }
        AdvanceSettingRow(
            title = stringResource(R.string.emi_ltv_row),
            checked = input.assetValue > 0.0,
            chipText = if (input.assetValue > 0.0) "LTV ${String.format("%.0f", ltvPct)}%" else null,
            chipHighlighted = false,
            expanded = expandedRowKey == "ltv",
            testTag = "adv_row_ltv",
            onCheckedChange = { checked ->
                if (checked) {
                    onUpdateInput { it.copy(assetValue = if (it.assetValue <= 0.0) it.principal * 1.25 else it.assetValue) }
                    onToggleExpand("ltv")
                } else {
                    onUpdateInput { it.copy(assetValue = 0.0) }
                }
            },
            onInfoClick = {
                onShowInfo(
                    "Asset Value & RBI LTV Cap",
                    "Loan-to-Value (LTV) is the percentage of property/vehicle/gold value financed. RBI LTV caps are loaded from Data & Rates."
                )
            },
            onExpandToggle = { onToggleExpand("ltv") }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NumericInputField(
                    label = "Property / Asset / Gold Value (${currency.symbol})",
                    valueText = if (input.assetValue == 0.0) "" else input.assetValue.toLong().toString(),
                    onValueChange = { txt ->
                        onUpdateInput { it.copy(assetValue = (txt.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)) }
                    },
                    trailingBadge = CurrencyFormatter.formatShortLabel(input.assetValue, currency)
                )
                ltvConfigItem?.let {
                    Text(
                        text = stringResource(R.string.ui_txt_max_ltv_cap, it.valueText, it.label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ValueSourceBadgesRow(item = it)
                }
            }
        }
    }

    // Floating Rate Timeline + EBLR Repo/Spread + MCLR Reset Frequency + One-tap Repo Cut/Hike
    val isEblr = flags.contains("RATE_EBLR")
    val isMclr = flags.contains("RATE_MCLR")
    if (isEblr || isMclr || flags.contains("RATE_FLOATING") || flags.contains("RATE_HYBRID")) {
        val repoConfigItem = resolvedConfig[ConfigKeys.RBI_REPO_RATE]
        AdvanceSettingRow(
            title = stringResource(R.string.emi_rate_timeline_row),
            checked = input.rateChanges.isNotEmpty() || isEblr || isMclr,
            chipText = when {
                input.rateChanges.isNotEmpty() -> "${input.rateChanges.size} Reset(s)"
                isEblr -> "Repo+${String.format("%.2f", input.spreadPercent)}%"
                isMclr -> "MCLR ${input.mclrResetMonths}m"
                else -> null
            },
            chipHighlighted = input.rateChanges.isNotEmpty() || isEblr || isMclr,
            expanded = expandedRowKey == "rate_timeline",
            testTag = "adv_row_rate_timeline",
            onCheckedChange = { checked ->
                if (checked) {
                    if (input.rateChanges.isEmpty()) {
                        onApplyRepoScenario(-0.25)
                    }
                    onToggleExpand("rate_timeline")
                } else {
                    onUpdateInput { it.copy(rateChanges = emptyList()) }
                }
            },
            onInfoClick = {
                onShowInfo(
                    "EBLR / MCLR & Floating Rate Timeline",
                    "For Repo-linked EBLR loans, Effective Rate = RBI Repo Rate + Bank Spread. Legacy MCLR loans reset every 6 or 12 months. Use the one-tap buttons to simulate RBI repo cuts or hikes."
                )
            },
            onExpandToggle = { onToggleExpand("rate_timeline") }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isEblr) {
                    repoConfigItem?.let { ValueSourceBadgesRow(item = it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericInputField(
                            label = "RBI Repo (%)",
                            valueText = input.benchmarkRatePercent.toString(),
                            onValueChange = { txt ->
                                txt.toDoubleOrNull()?.let { r ->
                                    val clean = r.coerceIn(1.0, 20.0)
                                    onUpdateInput {
                                        it.copy(
                                            benchmarkRatePercent = clean,
                                            annualRate = LoanCalculationEngine.roundPaisa(clean + it.spreadPercent)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        NumericInputField(
                            label = "Bank Spread (%)",
                            valueText = input.spreadPercent.toString(),
                            onValueChange = { txt ->
                                txt.toDoubleOrNull()?.let { s ->
                                    val clean = s.coerceIn(0.0, 15.0)
                                    onUpdateInput {
                                        it.copy(
                                            spreadPercent = clean,
                                            annualRate = LoanCalculationEngine.roundPaisa(it.benchmarkRatePercent + clean)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (isMclr) {
                    Text(
                        text = stringResource(R.string.ui_txt_mclr_reset_frequency),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(6, 12).forEach { resetM ->
                            FilterChip(
                                selected = input.mclrResetMonths == resetM,
                                onClick = { onUpdateInput { it.copy(mclrResetMonths = resetM) } },
                                label = { Text(stringResource(R.string.ui_txt_every_months, resetM)) },
                                modifier = Modifier.testTag("mclr_reset_$resetM")
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.emi_repo_scenario_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(-0.50, -0.25, +0.25, +0.50).forEach { delta ->
                        val label = if (delta > 0) "+${String.format("%.2f", delta)}% Hike" else "${String.format("%.2f", delta)}% Cut"
                        AssistChip(
                            onClick = { onApplyRepoScenario(delta) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag(
                                "repo_scenario_${if (delta > 0) "plus" else "minus"}_${kotlin.math.abs((delta * 100).toInt())}"
                            )
                        )
                    }
                }

                input.rateChanges.forEachIndexed { idx, ev ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NumericInputField(
                            label = "After Month",
                            valueText = ev.afterMonth.toString(),
                            onValueChange = { txt ->
                                val m = (txt.toIntOrNull() ?: 12).coerceIn(1, input.tenureMonths)
                                val list = input.rateChanges.toMutableList().apply { set(idx, ev.copy(afterMonth = m)) }
                                onUpdateInput { it.copy(rateChanges = list) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        NumericInputField(
                            label = "New Rate %",
                            valueText = ev.newAnnualRate.toString(),
                            onValueChange = { txt ->
                                val r = (txt.toDoubleOrNull() ?: ev.newAnnualRate).coerceIn(1.0, 35.0)
                                val list = input.rateChanges.toMutableList().apply { set(idx, ev.copy(newAnnualRate = r)) }
                                onUpdateInput { it.copy(rateChanges = list) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            val list = input.rateChanges.toMutableList().apply { removeAt(idx) }
                            onUpdateInput { it.copy(rateChanges = list) }
                        }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete rate event", tint = DangerRed)
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        val nextM = ((input.rateChanges.lastOrNull()?.afterMonth ?: 12) + 12).coerceAtMost(input.tenureMonths)
                        onUpdateInput {
                            it.copy(rateChanges = it.rateChanges + RateChangeEvent(nextM, (it.annualRate + 0.5).coerceAtMost(25.0)))
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ui_txt_add_rate_change))
                }
            }
        }
    }

    // PMAY-style Interest Subsidy (Home Loan)
    if (input.categoryId == "home") {
        val pmayConfig = resolvedConfig[ConfigKeys.PMAY_MAX_SUBSIDY]
        AdvanceSettingRow(
            title = stringResource(R.string.emi_pmay_row),
            checked = input.pmaySubsidyEnabled,
            chipText = if (input.pmaySubsidyEnabled) CurrencyFormatter.formatShortLabel(input.pmaySubsidyAmount, currency) else null,
            chipHighlighted = input.pmaySubsidyEnabled,
            expanded = expandedRowKey == "pmay_subsidy",
            testTag = "adv_row_pmay",
            onCheckedChange = { checked ->
                onUpdateInput { it.copy(pmaySubsidyEnabled = checked) }
                if (checked) onToggleExpand("pmay_subsidy")
            },
            onInfoClick = {
                onShowInfo(
                    "PMAY-Style Interest Subsidy",
                    "Optional upfront/NPV subsidy credited directly toward reducing your loan principal in the chosen month. Off by default; edit amount & credit month freely."
                )
            },
            onExpandToggle = { onToggleExpand("pmay_subsidy") }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pmayConfig?.let { ValueSourceBadgesRow(item = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericInputField(
                        label = "Subsidy Amount (${currency.symbol})",
                        valueText = input.pmaySubsidyAmount.toLong().toString(),
                        onValueChange = { txt ->
                            onUpdateInput {
                                it.copy(pmaySubsidyEnabled = true, pmaySubsidyAmount = (txt.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0))
                            }
                        },
                        trailingBadge = CurrencyFormatter.formatShortLabel(input.pmaySubsidyAmount, currency),
                        modifier = Modifier.weight(1.3f)
                    )
                    NumericInputField(
                        label = "Credit Month #",
                        valueText = input.pmaySubsidyMonth.toString(),
                        onValueChange = { txt ->
                            txt.toIntOrNull()?.let { m ->
                                onUpdateInput { it.copy(pmaySubsidyEnabled = true, pmaySubsidyMonth = m.coerceIn(1, input.tenureMonths)) }
                            }
                        },
                        modifier = Modifier.weight(0.7f)
                    )
                }
            }
        }
    }

    // Offset / Overdraft Parked Cash
    if (flags.contains("EMI_OFFSET")) {
        AdvanceSettingRow(
            title = stringResource(R.string.emi_offset_row),
            checked = input.offsetBalance > 0.0,
            chipText = CurrencyFormatter.formatShortLabel(input.offsetBalance, currency),
            chipHighlighted = input.offsetBalance > 0.0,
            expanded = expandedRowKey == "offset",
            testTag = "adv_row_offset",
            onCheckedChange = { checked ->
                onUpdateInput { it.copy(offsetBalance = if (checked) (it.principal * 0.15) else 0.0) }
                if (checked) onToggleExpand("offset")
            },
            onInfoClick = {
                onShowInfo(
                    "Home Saver / Offset Account",
                    "Surplus cash parked in a linked overdraft account reduces your daily interest-bearing principal while remaining 100% withdrawable."
                )
            },
            onExpandToggle = { onToggleExpand("offset") }
        ) {
            NumericInputField(
                label = "Parked Offset Balance (${currency.symbol})",
                valueText = input.offsetBalance.toLong().toString(),
                onValueChange = { txt ->
                    onUpdateInput { it.copy(offsetBalance = (txt.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)) }
                },
                trailingBadge = CurrencyFormatter.formatShortLabel(input.offsetBalance, currency)
            )
        }
    }

    // Tranche + Pre-EMI Schedule
    if (flags.contains("DISB_TRANCHE_PRE_EMI") || flags.contains("EDU_TRANCHES")) {
        AdvanceSettingRow(
            title = stringResource(R.string.emi_tranche_row),
            checked = input.tranches.isNotEmpty(),
            chipText = "${input.tranches.size} Tranches",
            chipHighlighted = true,
            expanded = expandedRowKey == "tranches",
            testTag = "adv_row_tranches",
            onCheckedChange = { onToggleExpand("tranches") },
            onInfoClick = {
                onShowInfo(
                    "Tranche Disbursement & Pre-EMI",
                    "Funds are released in construction or semester stages. Interest accrues only on disbursed tranches until handover; part-payments mapped during Pre-EMI reduce drawn principal immediately."
                )
            },
            onExpandToggle = { onToggleExpand("tranches") }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                input.tranches.forEachIndexed { idx, tr ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NumericInputField(
                            label = "Month #",
                            valueText = tr.monthNumber.toString(),
                            onValueChange = { txt ->
                                val m = (txt.toIntOrNull() ?: 1).coerceIn(1, input.tenureMonths)
                                val updated = input.tranches.toMutableList().apply { set(idx, tr.copy(monthNumber = m)) }
                                onUpdateInput { it.copy(tranches = updated) }
                            },
                            modifier = Modifier.weight(0.75f)
                        )
                        NumericInputField(
                            label = "Amount (${currency.symbol})",
                            valueText = tr.amount.toLong().toString(),
                            onValueChange = { txt ->
                                val a = (txt.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                                val updated = input.tranches.toMutableList().apply { set(idx, tr.copy(amount = a)) }
                                onUpdateInput { it.copy(tranches = updated) }
                            },
                            trailingBadge = CurrencyFormatter.formatShortLabel(tr.amount, currency),
                            modifier = Modifier.weight(1.25f)
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        val nextM = (input.tranches.lastOrNull()?.monthNumber ?: 1) + 6
                        onUpdateInput { it.copy(tranches = it.tranches + TrancheEntry(nextM, 500000.0)) }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ui_txt_add_tranche))
                }
            }
        }
    }

    VariantCommercialAndTransferRows(
        input = input,
        flags = flags,
        currency = currency,
        resolvedConfig = resolvedConfig,
        expandedRowKey = expandedRowKey,
        onToggleExpand = onToggleExpand,
        onShowInfo = onShowInfo,
        onUpdateInput = onUpdateInput
    )
}
