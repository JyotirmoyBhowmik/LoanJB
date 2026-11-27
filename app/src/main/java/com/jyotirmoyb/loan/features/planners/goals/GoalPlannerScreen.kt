package com.jyotirmoyb.loan.features.planners.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.FinancialGoalItem
import com.jyotirmoyb.loan.domain.GoalCalculationResult
import com.jyotirmoyb.loan.domain.GoalCategory
import com.jyotirmoyb.loan.features.deposits.fd.HeroMetricItem
import com.jyotirmoyb.loan.features.deposits.fd.KeyValueRow
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.features.retirement.planner.AgeSliderRow
import com.jyotirmoyb.loan.features.retirement.planner.ChartLegendDot
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.PurpleAccent
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SavingsGreen

private val GOAL_CHART_PALETTE = listOf(
    PrincipalEmerald,
    InfoBlue,
    InterestAmber,
    PurpleAccent,
    SageGreen
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalPlannerScreen(
    uiState: GoalPlannerUiState,
    onAddGoal: (GoalCategory) -> Unit,
    onUpdateGoal: (FinancialGoalItem) -> Unit,
    onToggleGoal: (String) -> Unit,
    onRemoveGoal: (String) -> Unit,
    onToggleExpand: (String) -> Unit,
    onResetGoals: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = uiState.summary
    val currency = uiState.currency
    val activeSipResults = summary.goalResults.filter { it.goal.enabled && it.monthlySipNeeded > 0.0 }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("goal_planner_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Combined SIP Hero Card
        item(key = "goal_hero") {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.goal_combined_sip_hero),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ui_txt_active_goals, summary.activeGoalCount),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.ui_txt_mo, CurrencyFormatter.format(summary.combinedMonthlySipNeeded, currency)),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("goal_combined_sip_value")
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HeroMetricItem(
                        label = "Cost Today",
                        value = CurrencyFormatter.formatCompact(summary.totalCurrentCost, currency),
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                    HeroMetricItem(
                        label = "Future Target",
                        value = CurrencyFormatter.formatCompact(summary.totalInflationAdjustedTarget, currency),
                        valueColor = InfoBlue
                    )
                    HeroMetricItem(
                        label = "Saved Today",
                        value = CurrencyFormatter.formatCompact(summary.totalCurrentSavings, currency),
                        valueColor = PrincipalEmerald
                    )
                }

                // Proportional Stacked Bar Chart of Monthly SIP per Active Goal
                if (activeSipResults.isNotEmpty() && summary.combinedMonthlySipNeeded > 0.0) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.ui_txt_monthly_sip_share_across_goals),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .testTag("goal_sip_stacked_bar"),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        activeSipResults.forEachIndexed { idx, res ->
                            val share = (res.monthlySipNeeded / summary.combinedMonthlySipNeeded)
                                .toFloat()
                                .coerceAtLeast(0.03f)
                            val color = GOAL_CHART_PALETTE[idx % GOAL_CHART_PALETTE.size]
                            Box(
                                modifier = Modifier
                                    .weight(share)
                                    .height(14.dp)
                                    .background(color)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        activeSipResults.forEachIndexed { idx, res ->
                            val color = GOAL_CHART_PALETTE[idx % GOAL_CHART_PALETTE.size]
                            val pct = ((res.monthlySipNeeded / summary.combinedMonthlySipNeeded) * 100).toInt()
                            ChartLegendDot(color = color, label = "${res.goal.name} ($pct%)")
                        }
                    }
                }
            }
        }

        // 2. Quick Add Goal Chips
        item(key = "goal_add_bar") {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionAccentHeader(title = stringResource(R.string.goal_add_btn), badge = "Tap to Add")
                    TextButton(
                        onClick = onResetGoals,
                        modifier = Modifier.testTag("goal_reset_btn")
                    ) {
                        Text(stringResource(R.string.reset_to_default), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GoalCategory.entries.forEach { cat ->
                        AssistChip(
                            onClick = { onAddGoal(cat) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(cat.displayName, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("goal_add_${cat.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // 3. Individual Expandable Goal Cards
        items(items = summary.goalResults, key = { it.goal.id }) { res ->
            val idx = summary.goalResults.indexOf(res)
            val accent = GOAL_CHART_PALETTE[idx % GOAL_CHART_PALETTE.size]
            val isExpanded = uiState.expandedGoalId == res.goal.id
            GoalItemCard(
                result = res,
                accentColor = accent,
                currency = currency,
                isExpanded = isExpanded,
                onToggleExpand = { onToggleExpand(res.goal.id) },
                onToggleEnabled = { onToggleGoal(res.goal.id) },
                onUpdateGoal = onUpdateGoal,
                onRemoveGoal = { onRemoveGoal(res.goal.id) }
            )
        }

        if (summary.goalResults.isEmpty()) {
            item(key = "goal_empty") {
                FintechCard {
                    Text(
                        text = stringResource(R.string.ui_txt_no_goals_added_yet_tap_any_goal_ch),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "goal_disclaimer") {
            DisclaimerBanner()
        }
    }
}

@Composable
private fun GoalItemCard(
    result: GoalCalculationResult,
    accentColor: Color,
    currency: CurrencyCode,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleEnabled: () -> Unit,
    onUpdateGoal: (FinancialGoalItem) -> Unit,
    onRemoveGoal: () -> Unit
) {
    val goal = result.goal
    var infText by remember(goal.id, goal.inflationPercent) { mutableStateOf(formatRate(goal.inflationPercent)) }
    var retText by remember(goal.id, goal.expectedReturnPercent) { mutableStateOf(formatRate(goal.expectedReturnPercent)) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (goal.enabled) 1f else 0.62f)
            .testTag("goal_card_${goal.id}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isExpanded) 1.5.dp else 1.dp,
            color = if (isExpanded) accentColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(accentColor.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = goalCategoryIcon(goal.category),
                        contentDescription = goal.category.displayName,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.name.ifBlank { goal.category.displayName },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.ui_txt_in_yrs_future,
                            goal.yearsToGoal,
                            CurrencyFormatter.formatCompact(result.inflationAdjustedTarget, currency)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (result.isAlreadyFunded) {
                            "Funded!"
                        } else {
                            "${CurrencyFormatter.format(
                                result.monthlySipNeeded,
                                currency
                            )}/mo"
                        },
                        style = MonoSmallStyle,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (result.isAlreadyFunded) SavingsGreen else accentColor
                    )
                    Text(
                        text = stringResource(R.string.ui_txt_sip_needed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = goal.enabled,
                                onCheckedChange = { onToggleEnabled() },
                                modifier = Modifier.testTag("goal_toggle_${goal.id}")
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (goal.enabled) "Included in Combined Plan" else "Paused",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(
                            onClick = onRemoveGoal,
                            modifier = Modifier.testTag("goal_delete_${goal.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove goal",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    OutlinedTextField(
                        value = goal.name,
                        onValueChange = { onUpdateGoal(goal.copy(name = it)) },
                        label = { Text(stringResource(R.string.goal_name_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goal_name_input_${goal.id}")
                    )
                    Spacer(Modifier.height(10.dp))

                    AmountField(
                        amount = goal.currentCost,
                        currency = currency,
                        onAmountChange = { onUpdateGoal(goal.copy(currentCost = it)) },
                        label = stringResource(R.string.goal_cost_today),
                        modifier = Modifier.testTag("goal_cost_input_${goal.id}")
                    )
                    Spacer(Modifier.height(10.dp))

                    AmountField(
                        amount = goal.currentSavings,
                        currency = currency,
                        onAmountChange = { onUpdateGoal(goal.copy(currentSavings = it)) },
                        label = stringResource(R.string.goal_current_saved),
                        modifier = Modifier.testTag("goal_saved_input_${goal.id}")
                    )
                    Spacer(Modifier.height(10.dp))

                    AgeSliderRow(
                        label = stringResource(R.string.goal_years_label),
                        value = goal.yearsToGoal,
                        range = 1f..35f,
                        onValueChange = { onUpdateGoal(goal.copy(yearsToGoal = it)) },
                        testTag = "goal_years_slider_${goal.id}"
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NumericInputField(
                            label = stringResource(R.string.goal_inflation_label),
                            valueText = infText,
                            onValueChange = { txt ->
                                infText = txt
                                txt.toDoubleOrNull()?.let { onUpdateGoal(goal.copy(inflationPercent = it.coerceIn(0.0, 20.0))) }
                            },
                            trailingBadge = "%",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("goal_inflation_input_${goal.id}")
                        )
                        NumericInputField(
                            label = stringResource(R.string.goal_return_label),
                            valueText = retText,
                            onValueChange = { txt ->
                                retText = txt
                                txt.toDoubleOrNull()?.let { onUpdateGoal(goal.copy(expectedReturnPercent = it.coerceIn(0.0, 25.0))) }
                            },
                            trailingBadge = "%",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("goal_return_input_${goal.id}")
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    KeyValueRow("Inflation-Adjusted Target", CurrencyFormatter.format(result.inflationAdjustedTarget, currency), InfoBlue)
                    KeyValueRow(
                        "Future Value of Current Savings",
                        CurrencyFormatter.format(result.futureValueOfCurrentSavings, currency),
                        PrincipalEmerald
                    )
                    KeyValueRow("Net Target Gap", CurrencyFormatter.format(result.netTargetGap, currency))
                    KeyValueRow("Total SIP Outflow", CurrencyFormatter.format(result.totalSipInvestment, currency))
                    KeyValueRow("Estimated SIP Gains", CurrencyFormatter.format(result.estimatedSipGain, currency), SavingsGreen)
                }
            }
        }
    }
}

private fun goalCategoryIcon(category: GoalCategory): ImageVector = when (category) {
    GoalCategory.CHILD_EDUCATION -> Icons.Outlined.School
    GoalCategory.HOME -> Icons.Outlined.Home
    GoalCategory.CAR -> Icons.Outlined.DirectionsCar
    GoalCategory.WEDDING -> Icons.Outlined.FavoriteBorder
    GoalCategory.TRAVEL -> Icons.Outlined.Flight
    GoalCategory.CUSTOM -> Icons.Outlined.TrackChanges
}

private fun formatRate(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.2f", v).trimEnd('0').trimEnd('.')
