package com.jyotirmoyb.loan.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.ui.components.CompareBarChart
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy
import com.jyotirmoyb.loan.viewmodel.CompareItemModel

@Composable
fun CompareScreen(
    allScenarios: List<CompareItemModel>,
    selectedIds: Set<Long>,
    currency: CurrencyCode,
    onToggleSelect: (Long) -> Unit,
    onDeleteScenario: (Long) -> Unit,
    onLoadPreset: (String) -> Unit,
    onOpenCalculator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeItems = allScenarios.filter { selectedIds.contains(it.id) }.take(3).ifEmpty {
        allScenarios.take(3)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Presets Banner
        item {
            FintechCard {
                SectionAccentHeader("Quick Comparison Presets", badge = "1-TAP", icon = Icons.Outlined.AutoAwesome)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onLoadPreset("FIXED_FLOAT_HYBRID") },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("compare_preset_ffh_btn")
                    ) {
                        Text(
                            stringResource(R.string.ui_txt_fixed_vs_float_vs_hybrid),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                    OutlinedButton(
                        onClick = { onLoadPreset("CURRENT_VS_BT") },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("compare_preset_bt_btn")
                    ) {
                        Text(
                            stringResource(R.string.ui_txt_current_vs_balance_transfer),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Scenario Picker
        if (allScenarios.isEmpty()) {
            item {
                FintechCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Outlined.CompareArrows,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            stringResource(R.string.ui_txt_compare_2_3_loan_offers_side_by_si),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.ui_txt_tap_a_preset_above_or_tap_compare),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = onOpenCalculator, shape = RoundedCornerShape(12.dp)) {
                            Text(stringResource(R.string.ui_txt_go_to_calculator))
                        }
                    }
                }
            }
        } else {
            item {
                FintechCard {
                    SectionAccentHeader(
                        title = "Saved Scenarios (Select 2–3)",
                        badge = "${activeItems.size}/3 Active"
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allScenarios.forEach { item ->
                            val isSelected = activeItems.any { it.id == item.id }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(
                                        alpha = 0.45f
                                    )
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleSelect(item.id) }
                                    .testTag("compare_scenario_row_${item.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            stringResource(
                                                R.string.ui_txt_emi_4ca96e,
                                                item.variantSummary,
                                                CurrencyFormatter.format(item.result.monthlyEmi, currency)
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { onDeleteScenario(item.id) }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Remove scenario", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Side-by-side Table + Winner Banner + Bar Chart
        if (activeItems.size >= 2) {
            val cheapest = activeItems.minByOrNull { it.result.effectiveTotalCost }!!
            val priciest = activeItems.maxByOrNull { it.result.effectiveTotalCost }!!
            val maxSavings = (priciest.result.effectiveTotalCost - cheapest.result.effectiveTotalCost).coerceAtLeast(0.0)

            item {
                FintechCard(
                    containerColor = SavingsGreen.copy(alpha = 0.08f),
                    borderColor = SavingsGreen.copy(alpha = 0.4f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.EmojiEvents,
                            contentDescription = "Best value",
                            tint = SavingsGreen,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                stringResource(R.string.ui_txt_cheapest_option),
                                style = MaterialTheme.typography.labelSmall,
                                color = SavingsGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(cheapest.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(
                                    R.string.ui_txt_saves_in_total_effective_cost_vs,
                                    CurrencyFormatter.format(maxSavings, currency),
                                    priciest.label
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                FintechCard {
                    SectionAccentHeader("Side-by-Side Breakdown", badge = "${activeItems.size} OFFERS")
                    Spacer(Modifier.height(10.dp))

                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateNavy, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                            .padding(horizontal = 8.dp, vertical = 9.dp)
                    ) {
                        Text(
                            stringResource(R.string.ui_txt_metric),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.weight(0.95f)
                        )
                        activeItems.forEach { item ->
                            val isWin = item.id == cheapest.id
                            Text(
                                text = (if (isWin) "★ " else "") + item.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isWin) Color(0xFF5EEAD4) else Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    CompareMetricRow(
                        label = "Monthly EMI",
                        values = activeItems.map { CurrencyFormatter.format(it.result.monthlyEmi, currency) },
                        highlightIndex = activeItems.indexOfFirst { it.id == cheapest.id }
                    )
                    CompareMetricRow(
                        label = "Rate (% p.a.)",
                        values = activeItems.map { "${String.format("%.2f", it.input.annualRate)}%" },
                        highlightIndex = activeItems.indexOfFirst { it.id == cheapest.id }
                    )
                    CompareMetricRow(
                        label = "Tenure",
                        values = activeItems.map { "${it.result.actualTenureMonths}m" },
                        highlightIndex = activeItems.indexOfFirst { it.id == cheapest.id }
                    )
                    CompareMetricRow(
                        label = "Total Interest",
                        values = activeItems.map { CurrencyFormatter.formatCompact(it.result.totalInterest, currency) },
                        highlightIndex = activeItems.indexOfFirst { it.id == cheapest.id },
                        valueColor = InterestAmber
                    )
                    CompareMetricRow(
                        label = "Fees",
                        values = activeItems.map { CurrencyFormatter.formatCompact(it.result.totalFees, currency) },
                        highlightIndex = activeItems.indexOfFirst { it.id == cheapest.id }
                    )
                    CompareMetricRow(
                        label = "Total Cost",
                        values = activeItems.map { CurrencyFormatter.formatCompact(it.result.effectiveTotalCost, currency) },
                        highlightIndex = activeItems.indexOfFirst { it.id == cheapest.id },
                        bold = true,
                        valueColor = PrincipalEmerald
                    )
                }
            }

            item {
                FintechCard {
                    SectionAccentHeader("Visual Cost Comparison", badge = "BAR CHART")
                    Spacer(Modifier.height(12.dp))
                    CompareBarChart(items = activeItems, currency = currency)
                }
            }
        }

        item {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CompareMetricRow(label: String, values: List<String>, highlightIndex: Int, bold: Boolean = false, valueColor: Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.95f)
        )
        values.forEachIndexed { idx, v ->
            val isWinner = idx == highlightIndex
            Text(
                text = v,
                style = if (bold) MonoMediumStyle else MonoSmallStyle,
                fontWeight = if (isWinner || bold) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isWinner -> SavingsGreen
                    valueColor != null -> valueColor
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
