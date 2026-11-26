package com.jyotirmoyb.loan.features.emi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CalculationInput
import com.jyotirmoyb.loan.domain.CalculationResult
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.PrepaymentMode
import com.jyotirmoyb.loan.domain.PrepaymentRule
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.theme.DangerRed
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark

@Composable
internal fun AdvanceSettingRow(
    title: String,
    checked: Boolean,
    chipText: String?,
    chipHighlighted: Boolean,
    expanded: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    onExpandToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onExpandToggle)
                    .padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("${testTag}_checkbox")
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!chipText.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = if (chipHighlighted) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = chipText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (chipHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.cd_info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 4.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PrepaymentEditorContent(
    input: CalculationInput,
    result: CalculationResult,
    currency: CurrencyCode,
    onUpdateInput: ((CalculationInput) -> CalculationInput) -> Unit
) {
    val rules = input.prepayments
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        result.insights.prepaymentImpact?.let { impact ->
            if (impact.netSavings > 0.0 || impact.monthsSaved > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SavingsGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SavingsGreen.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prepayment_total_saved_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Savings,
                            contentDescription = null,
                            tint = SavingsGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.emi_total_saved_banner),
                                style = MaterialTheme.typography.labelSmall,
                                color = SavingsGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${CurrencyFormatter.format(impact.netSavings, currency)} net saved • ${impact.monthsSaved} month(s) cut",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        rules.forEachIndexed { idx, rule ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Prepayment #${idx + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimaryDark
                        )
                        IconButton(
                            onClick = {
                                val next = rules.toMutableList().apply { removeAt(idx) }
                                onUpdateInput {
                                    it.copy(
                                        prepayments = next,
                                        prepaymentEnabled = next.isNotEmpty()
                                    )
                                }
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove prepayment",
                                tint = DangerRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericInputField(
                            label = "Amount (${currency.symbol})",
                            valueText = if (rule.amount == 0.0) "" else rule.amount.toLong().toString(),
                            onValueChange = { txt ->
                                val amt = (txt.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                                val updated = rules.toMutableList().apply { set(idx, rule.copy(amount = amt)) }
                                onUpdateInput { it.copy(prepaymentEnabled = true, prepayments = updated) }
                            },
                            trailingBadge = if (rule.amount > 0) {
                                CurrencyFormatter.formatShortLabel(rule.amount, currency)
                            } else null,
                            modifier = Modifier.weight(1.25f)
                        )
                        NumericInputField(
                            label = "At Month #",
                            valueText = rule.startMonth.toString(),
                            onValueChange = { txt ->
                                val m = (txt.toIntOrNull() ?: 1).coerceIn(1, input.tenureMonths)
                                val updated = rules.toMutableList().apply { set(idx, rule.copy(startMonth = m)) }
                                onUpdateInput { it.copy(prepaymentEnabled = true, prepayments = updated) }
                            },
                            modifier = Modifier.weight(0.75f)
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = !rule.isRecurring,
                            onClick = {
                                val updated = rules.toMutableList().apply { set(idx, rule.copy(isRecurring = false)) }
                                onUpdateInput { it.copy(prepayments = updated) }
                            },
                            label = { Text("One-Time") }
                        )
                        FilterChip(
                            selected = rule.isRecurring && rule.intervalMonths == 1,
                            onClick = {
                                val updated = rules.toMutableList().apply { set(idx, rule.copy(isRecurring = true, intervalMonths = 1)) }
                                onUpdateInput { it.copy(prepayments = updated) }
                            },
                            label = { Text("Monthly") }
                        )
                        FilterChip(
                            selected = rule.isRecurring && rule.intervalMonths == 12,
                            onClick = {
                                val updated = rules.toMutableList().apply { set(idx, rule.copy(isRecurring = true, intervalMonths = 12)) }
                                onUpdateInput { it.copy(prepayments = updated) }
                            },
                            label = { Text("Yearly") }
                        )
                    }

                    val reduceTenure = rule.mode != PrepaymentMode.REDUCE_EMI.name
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = reduceTenure,
                            onClick = {
                                val updated = rules.toMutableList().apply {
                                    set(idx, rule.copy(mode = PrepaymentMode.REDUCE_TENURE.name))
                                }
                                onUpdateInput { it.copy(prepayments = updated) }
                            },
                            label = { Text("Reduce Tenure") },
                            modifier = Modifier.testTag("prepay_mode_tenure_$idx")
                        )
                        FilterChip(
                            selected = !reduceTenure,
                            onClick = {
                                val updated = rules.toMutableList().apply {
                                    set(idx, rule.copy(mode = PrepaymentMode.REDUCE_EMI.name))
                                }
                                onUpdateInput { it.copy(prepayments = updated) }
                            },
                            label = { Text("Reduce EMI") },
                            modifier = Modifier.testTag("prepay_mode_emi_$idx")
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NumericInputField(
                label = "Prepayment Fee (%)",
                valueText = if (input.prepaymentFeePercent == 0.0) "0" else input.prepaymentFeePercent.toString(),
                onValueChange = { txt ->
                    onUpdateInput { it.copy(prepaymentFeePercent = (txt.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 10.0)) }
                },
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    val nextMonth = ((rules.lastOrNull()?.startMonth ?: 6) + 12).coerceAtMost(input.tenureMonths)
                    val nextRule = PrepaymentRule(
                        startMonth = nextMonth,
                        amount = 200000.0,
                        isRecurring = false,
                        mode = PrepaymentMode.REDUCE_TENURE.name
                    )
                    onUpdateInput { it.copy(prepaymentEnabled = true, prepayments = it.prepayments + nextRule) }
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("add_prepayment_rule_btn")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
    }
}
