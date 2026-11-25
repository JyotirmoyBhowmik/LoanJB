package com.jyotirmoyb.loan.features.emi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.engine.CalculationInput
import com.jyotirmoyb.loan.engine.CurrencyCode
import com.jyotirmoyb.loan.engine.CurrencyFormatter
import com.jyotirmoyb.loan.engine.LoanCategory
import com.jyotirmoyb.loan.engine.RateChangeEvent
import com.jyotirmoyb.loan.engine.TrancheEntry
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.theme.DangerRed

/**
 * Renders catalog-variant-specific rows inside AdvanceSettingsCard ONLY when the
 * currently selected loan category/variant requires those specialized inputs.
 */
@Composable
internal fun VariantAdaptiveAdvanceRows(
    input: CalculationInput,
    category: LoanCategory?,
    currency: CurrencyCode,
    expandedRowKey: String?,
    onToggleExpand: (String) -> Unit,
    onShowInfo: (String, String) -> Unit,
    onUpdateInput: ((CalculationInput) -> CalculationInput) -> Unit
) {
    val flags = input.ruleFlags.toSet()

    // Asset Value & LTV (secured loans)
    if (category?.secured == true) {
        val ltvPct = if (input.assetValue > 0.0) (input.principal / input.assetValue) * 100.0 else 0.0
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
                    "Asset Value & LTV Cap",
                    "Loan-to-Value (LTV) is the percentage of the property/vehicle value financed by the bank. The remainder is your down payment."
                )
            },
            onExpandToggle = { onToggleExpand("ltv") }
        ) {
            NumericInputField(
                label = "Property / Asset Value (${currency.symbol})",
                valueText = if (input.assetValue == 0.0) "" else input.assetValue.toLong().toString(),
                onValueChange = { txt ->
                    onUpdateInput { it.copy(assetValue = (txt.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)) }
                },
                trailingBadge = CurrencyFormatter.formatCompact(input.assetValue, currency)
            )
        }
    }

    // Floating Rate Change Timeline
    if (flags.contains("RATE_FLOATING") || flags.contains("RATE_HYBRID")) {
        AdvanceSettingRow(
            title = stringResource(R.string.emi_rate_timeline_row),
            checked = input.rateChanges.isNotEmpty(),
            chipText = if (input.rateChanges.isNotEmpty()) "${input.rateChanges.size} Reset(s)" else null,
            chipHighlighted = input.rateChanges.isNotEmpty(),
            expanded = expandedRowKey == "rate_timeline",
            testTag = "adv_row_rate_timeline",
            onCheckedChange = { checked ->
                if (checked) {
                    if (input.rateChanges.isEmpty()) {
                        onUpdateInput {
                            it.copy(rateChanges = listOf(RateChangeEvent(afterMonth = 24.coerceAtMost(it.tenureMonths), newAnnualRate = (it.annualRate - 0.5).coerceAtLeast(4.0))))
                        }
                    }
                    onToggleExpand("rate_timeline")
                } else {
                    onUpdateInput { it.copy(rateChanges = emptyList()) }
                }
            },
            onInfoClick = {
                onShowInfo(
                    "Floating Rate Reset Timeline",
                    "Simulate RBI repo rate hikes or cuts after any month to see how your EMI and total interest respond."
                )
            },
            onExpandToggle = { onToggleExpand("rate_timeline") }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Text("Add Rate Change")
                }
            }
        }
    }

    // Offset / Overdraft Parked Cash
    if (flags.contains("EMI_OFFSET")) {
        AdvanceSettingRow(
            title = stringResource(R.string.emi_offset_row),
            checked = input.offsetBalance > 0.0,
            chipText = CurrencyFormatter.formatCompact(input.offsetBalance, currency),
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
                }
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
                    "Funds are released in construction or semester stages. Interest accrues only on the disbursed amount until full handover."
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
                    Text("Add Tranche")
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

    // Margin Call (Shares)
    if (flags.contains("ASSET_MARGIN_SHARE")) {
        AdvanceSettingRow(
            title = "Pledged Shares & Margin",
            checked = true,
            chipText = CurrencyFormatter.formatCompact(input.shareValue, currency),
            chipHighlighted = true,
            expanded = expandedRowKey == "margin_share",
            testTag = "adv_row_margin_share",
            onCheckedChange = { onToggleExpand("margin_share") },
            onInfoClick = {
                onShowInfo(
                    "Loan Against Shares — Margin Call",
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
                    modifier = Modifier.weight(1.2f)
                )
                NumericInputField(
                    label = "Min Margin %",
                    valueText = input.minMarginPercent.toString(),
                    onValueChange = { txt ->
                        txt.toDoubleOrNull()?.let { m -> onUpdateInput { it.copy(minMarginPercent = m.coerceIn(10.0, 70.0)) } }
                    },
                    modifier = Modifier.weight(0.8f)
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

    // Balance Transfer Evaluator (Home Loans or when enabled)
    if (input.categoryId == "home" || flags.contains("PURPOSE_BALANCE_TRANSFER") || input.balanceTransfer.enabled) {
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
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Top-Up Blended EMI (Home Loans or when enabled)
    if (flags.contains("PURPOSE_TOP_UP") || input.topUp.enabled) {
        AdvanceSettingRow(
            title = stringResource(R.string.emi_topup_row),
            checked = input.topUp.enabled,
            chipText = if (input.topUp.enabled) CurrencyFormatter.formatCompact(input.topUp.topUpAmount, currency) else null,
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
                    modifier = Modifier.weight(1.2f)
                )
                NumericInputField(
                    label = "Top-Up Rate %",
                    valueText = tu.topUpAnnualRate.toString(),
                    onValueChange = { txt ->
                        txt.toDoubleOrNull()?.let { r -> onUpdateInput { it.copy(topUp = tu.copy(enabled = true, topUpAnnualRate = r)) } }
                    },
                    modifier = Modifier.weight(0.8f)
                )
            }
        }
    }
}
