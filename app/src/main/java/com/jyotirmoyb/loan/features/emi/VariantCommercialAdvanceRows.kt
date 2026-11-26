package com.jyotirmoyb.loan.features.emi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.remote.ConfigKeys
import com.jyotirmoyb.loan.data.remote.DataSourceResolver
import com.jyotirmoyb.loan.data.remote.ResolvedConfigItem
import com.jyotirmoyb.loan.domain.CalculationInput
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VariantCommercialAndTransferRows(
    input: CalculationInput,
    flags: Set<String>,
    currency: CurrencyCode,
    resolvedConfig: Map<String, ResolvedConfigItem>,
    expandedRowKey: String?,
    onToggleExpand: (String) -> Unit,
    onShowInfo: (String, String) -> Unit,
    onUpdateInput: ((CalculationInput) -> CalculationInput) -> Unit
) {
    // PMMY Mudra Tiers Quick Selector (Business / MSME)
    if (input.categoryId == "business") {
        val shishu = DataSourceResolver.resolveDouble(resolvedConfig, ConfigKeys.MUDRA_SHISHU_CAP, 50000.0)
        val kishore = DataSourceResolver.resolveDouble(resolvedConfig, ConfigKeys.MUDRA_KISHORE_CAP, 500000.0)
        val tarun = DataSourceResolver.resolveDouble(resolvedConfig, ConfigKeys.MUDRA_TARUN_CAP, 1000000.0)
        val tarunPlus = DataSourceResolver.resolveDouble(resolvedConfig, ConfigKeys.MUDRA_TARUN_PLUS_CAP, 2000000.0)
        AdvanceSettingRow(
            title = "PMMY Mudra Tier Presets",
            checked = false,
            chipText = "Data Caps",
            chipHighlighted = false,
            expanded = expandedRowKey == "mudra_tiers",
            testTag = "adv_row_mudra_tiers",
            onCheckedChange = { onToggleExpand("mudra_tiers") },
            onInfoClick = {
                onShowInfo(
                    "PMMY Mudra Loan Tiers",
                    "Tap any tier to load its official cap from schemes.json (editable in Profile → Data & Rates)."
                )
            },
            onExpandToggle = { onToggleExpand("mudra_tiers") }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                resolvedConfig[ConfigKeys.MUDRA_TARUN_PLUS_CAP]?.let { ValueSourceBadgesRow(item = it) }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "Shishu" to shishu,
                        "Kishore" to kishore,
                        "Tarun" to tarun,
                        "Tarun+" to tarunPlus
                    ).forEach { (name, cap) ->
                        AssistChip(
                            onClick = { onUpdateInput { it.copy(principal = cap) } },
                            label = { Text("$name (${CurrencyFormatter.formatShortLabel(cap, currency)})") }
                        )
                    }
                }
            }
        }
    }

    // Loan Against FD Spread
    if (flags.contains("ASSET_FD")) {
        AdvanceSettingRow(
            title = "FD Yield & Spread",
            checked = true,
            chipText = "FD ${String.format("%.2f", input.fdRatePercent)}%",
            chipHighlighted = true,
            expanded = expandedRowKey == "fd_spread",
            testTag = "adv_row_fd_spread",
            onCheckedChange = { onToggleExpand("fd_spread") },
            onInfoClick = {
                onShowInfo(
                    "Loan Against Fixed Deposit",
                    "Banks typically charge 1%–2% over your FD rate. Your FD keeps earning interest, so your true cost is only the net spread."
                )
            },
            onExpandToggle = { onToggleExpand("fd_spread") }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericInputField(
                    label = "FD Interest %",
                    valueText = input.fdRatePercent.toString(),
                    onValueChange = { txt ->
                        txt.toDoubleOrNull()?.let { r -> onUpdateInput { it.copy(fdRatePercent = r.coerceIn(1.0, 15.0)) } }
                    },
                    modifier = Modifier.weight(1f)
                )
                NumericInputField(
                    label = "Bank Spread %",
                    valueText = input.fdSpreadPercent.toString(),
                    onValueChange = { txt ->
                        txt.toDoubleOrNull()?.let { s -> onUpdateInput { it.copy(fdSpreadPercent = s.coerceIn(0.25, 6.0)) } }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Margin Call (Shares / Mutual Funds)
    if (flags.contains("ASSET_MARGIN_SHARE")) {
        AdvanceSettingRow(
            title = "Pledged Shares / MF & Margin",
            checked = true,
            chipText = CurrencyFormatter.formatShortLabel(input.shareValue, currency),
            chipHighlighted = true,
            expanded = expandedRowKey == "margin_share",
            testTag = "adv_row_margin_share",
            onCheckedChange = { onToggleExpand("margin_share") },
            onInfoClick = {
                onShowInfo(
                    "Loan Against Shares / MF — Margin Call",
                    "Tracks the exact portfolio drop percentage at which your lender will issue a margin call."
                )
            },
            onExpandToggle = { onToggleExpand("margin_share") }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericInputField(
                    label = "Pledged Value",
                    valueText = input.shareValue.toLong().toString(),
                    onValueChange = { txt ->
                        onUpdateInput { it.copy(shareValue = (txt.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)) }
                    },
                    trailingBadge = CurrencyFormatter.formatShortLabel(input.shareValue, currency),
                    modifier = Modifier.weight(1.25f)
                )
                NumericInputField(
                    label = "Min Margin %",
                    valueText = input.minMarginPercent.toString(),
                    onValueChange = { txt ->
                        txt.toDoubleOrNull()?.let { m -> onUpdateInput { it.copy(minMarginPercent = m.coerceIn(10.0, 70.0)) } }
                    },
                    modifier = Modifier.weight(0.75f)
                )
            }
        }
    }

    // Invoice / Bill Discounting
    if (flags.contains("BIZ_BILL_DISCOUNT")) {
        AdvanceSettingRow(
            title = "Invoice Discounting Terms",
            checked = true,
            chipText = "${input.billDays}d @ ${String.format("%.1f", input.billDiscountRatePercent)}%",
            chipHighlighted = true,
            expanded = expandedRowKey == "bill_discount",
            testTag = "adv_row_bill_discount",
            onCheckedChange = { onToggleExpand("bill_discount") },
            onInfoClick = {
                onShowInfo(
                    "Bill / Invoice Discounting",
                    "Advance 70%–85% of an unpaid invoice value immediately with a short-term discount fee for 30–90 days."
                )
            },
            onExpandToggle = { onToggleExpand("bill_discount") }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericInputField(
                    label = "Advance %",
                    valueText = input.billAdvancePercent.toString(),
                    onValueChange = { txt ->
                        txt.toDoubleOrNull()?.let { p -> onUpdateInput { it.copy(billAdvancePercent = p.coerceIn(50.0, 95.0)) } }
                    },
                    modifier = Modifier.weight(1f)
                )
                NumericInputField(
                    label = "Days",
                    valueText = input.billDays.toString(),
                    onValueChange = { txt ->
                        txt.toIntOrNull()?.let { d -> onUpdateInput { it.copy(billDays = d.coerceIn(7, 180)) } }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Balance Transfer Evaluator
    if (input.categoryId == "home" || input.categoryId == "lap" || flags.contains("PURPOSE_BALANCE_TRANSFER") || input.balanceTransfer.enabled) {
        AdvanceSettingRow(
            title = stringResource(R.string.emi_bt_row),
            checked = input.balanceTransfer.enabled,
            chipText = if (input.balanceTransfer.enabled) "${String.format("%.2f", input.balanceTransfer.newAnnualRate)}%" else null,
            chipHighlighted = input.balanceTransfer.enabled,
            expanded = expandedRowKey == "balance_transfer",
            testTag = "adv_row_balance_transfer",
            onCheckedChange = { checked ->
                onUpdateInput { it.copy(balanceTransfer = it.balanceTransfer.copy(enabled = checked)) }
                if (checked) onToggleExpand("balance_transfer")
            },
            onInfoClick = {
                onShowInfo(
                    "Balance Transfer Evaluator",
                    "Compares your existing bank's remaining interest against switching to a lower-rate lender after factoring in processing & legal fees."
                )
            },
            onExpandToggle = { onToggleExpand("balance_transfer") }
        ) {
            val bt = input.balanceTransfer
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericInputField(
                        label = "Current Rate %",
                        valueText = bt.currentAnnualRate.toString(),
                        onValueChange = { txt ->
                            txt.toDoubleOrNull()?.let { r -> onUpdateInput { it.copy(balanceTransfer = bt.copy(enabled = true, currentAnnualRate = r)) } }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    NumericInputField(
                        label = "New Rate %",
                        valueText = bt.newAnnualRate.toString(),
                        onValueChange = { txt ->
                            txt.toDoubleOrNull()?.let { r -> onUpdateInput { it.copy(balanceTransfer = bt.copy(enabled = true, newAnnualRate = r)) } }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericInputField(
                        label = "Remaining Months",
                        valueText = bt.remainingMonths.toString(),
                        onValueChange = { txt ->
                            txt.toIntOrNull()?.let { m -> onUpdateInput { it.copy(balanceTransfer = bt.copy(enabled = true, remainingMonths = m.coerceIn(6, 360))) } }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    NumericInputField(
                        label = "Transfer Fees",
                        valueText = bt.transferFees.toLong().toString(),
                        onValueChange = { txt ->
                            onUpdateInput { it.copy(balanceTransfer = bt.copy(enabled = true, transferFees = txt.toDoubleOrNull() ?: 0.0)) }
                        },
                        trailingBadge = CurrencyFormatter.formatShortLabel(bt.transferFees, currency),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Top-Up Blended EMI
    if (flags.contains("PURPOSE_TOP_UP") || input.topUp.enabled) {
        AdvanceSettingRow(
            title = stringResource(R.string.emi_topup_row),
            checked = input.topUp.enabled,
            chipText = if (input.topUp.enabled) CurrencyFormatter.formatShortLabel(input.topUp.topUpAmount, currency) else null,
            chipHighlighted = input.topUp.enabled,
            expanded = expandedRowKey == "topup",
            testTag = "adv_row_topup",
            onCheckedChange = { checked ->
                onUpdateInput { it.copy(topUp = it.topUp.copy(enabled = checked)) }
                if (checked) onToggleExpand("topup")
            },
            onInfoClick = {
                onShowInfo(
                    "Top-Up Blended EMI",
                    "Combines your existing home loan EMI with an additional top-up loan at its own interest rate and tenure."
                )
            },
            onExpandToggle = { onToggleExpand("topup") }
        ) {
            val tu = input.topUp
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericInputField(
                    label = "Top-Up Amount",
                    valueText = tu.topUpAmount.toLong().toString(),
                    onValueChange = { txt ->
                        onUpdateInput { it.copy(topUp = tu.copy(enabled = true, topUpAmount = txt.toDoubleOrNull() ?: 0.0)) }
                    },
                    trailingBadge = CurrencyFormatter.formatShortLabel(tu.topUpAmount, currency),
                    modifier = Modifier.weight(1.25f)
                )
                NumericInputField(
                    label = "Top-Up Rate %",
                    valueText = tu.topUpAnnualRate.toString(),
                    onValueChange = { txt ->
                        txt.toDoubleOrNull()?.let { r -> onUpdateInput { it.copy(topUp = tu.copy(enabled = true, topUpAnnualRate = r)) } }
                    },
                    modifier = Modifier.weight(0.75f)
                )
            }
        }
    }
}
