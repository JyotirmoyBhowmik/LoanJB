package com.jyotirmoyb.loan.features.planners.wheretoinvest

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.remote.UNVERIFIED_MARKER
import com.jyotirmoyb.loan.domain.AssetAllocationEngine
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.RiskProfile
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.features.planners.rules.DonutSplitChart
import com.jyotirmoyb.loan.features.retirement.planner.AgeSliderRow
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.PurpleAccent

@Composable
fun WhereToInvestScreen(
    uiState: WhereToInvestUiState,
    onUpdateAge: (Int) -> Unit,
    onSelectAnswer: (String, Int) -> Unit,
    onOverrideProfile: (RiskProfile?) -> Unit,
    onUpdateInvestableAmount: (Double) -> Unit,
    onResetQuestionnaire: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = uiState.currency
    val alloc = uiState.allocationResult
    val catalog = uiState.catalog

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("where_to_invest_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Prominent Educational-Only / Non-SEBI Disclaimer Banner
        item(key = "wti_sebi_banner") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = InterestAmber.copy(alpha = 0.14f),
                border = BorderStroke(1.5.dp, InterestAmber.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wti_educational_disclaimer")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.wti_edu_disclaimer_title),
                        tint = InterestAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.wti_edu_disclaimer_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.wti_edu_disclaimer_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Illustrative Asset Mix Donut Chart & Split
        if (alloc != null) {
            item(key = "wti_allocation_hero") {
                FintechCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionAccentHeader(
                            title = "Illustrative Asset-Class Mix",
                            badge = "${alloc.ageBandLabel} • ${alloc.riskProfile.displayName}"
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    ValueSourceBadgesRow(
                        source = catalog.source,
                        asOfDate = catalog.asOfDateFormatted,
                        isUnverified = catalog.lastVerified.equals(UNVERIFIED_MARKER, ignoreCase = true)
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DonutSplitChart(
                            segments = listOf(
                                PrincipalEmerald to alloc.equityPercent.toFloat(),
                                InfoBlue to alloc.debtPercent.toFloat(),
                                InterestAmber to alloc.goldPercent.toFloat(),
                                PurpleAccent to alloc.cashLiquidPercent.toFloat()
                            ),
                            centerTitle = "${alloc.equityPercent.toInt()}% Eq",
                            centerSubtitle = alloc.riskProfile.displayName,
                            modifier = Modifier
                                .size(134.dp)
                                .testTag("wti_allocation_donut")
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssetMixRow(
                                color = PrincipalEmerald,
                                label = "Equity Asset Class",
                                percent = alloc.equityPercent,
                                amountText = if (uiState.investableAmount > 0) CurrencyFormatter.format(alloc.equityAmount, currency) else null
                            )
                            AssetMixRow(
                                color = InfoBlue,
                                label = "Debt / Fixed Income",
                                percent = alloc.debtPercent,
                                amountText = if (uiState.investableAmount > 0) CurrencyFormatter.format(alloc.debtAmount, currency) else null
                            )
                            AssetMixRow(
                                color = InterestAmber,
                                label = "Gold / Commodity Hedge",
                                percent = alloc.goldPercent,
                                amountText = if (uiState.investableAmount > 0) CurrencyFormatter.format(alloc.goldAmount, currency) else null
                            )
                            AssetMixRow(
                                color = PurpleAccent,
                                label = "Cash / Liquid Reserve",
                                percent = alloc.cashLiquidPercent,
                                amountText = if (uiState.investableAmount > 0) CurrencyFormatter.format(alloc.cashLiquidAmount, currency) else null
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = alloc.riskProfile.summary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Horizon: ${alloc.horizonHint} • Rebalance: ${alloc.rebalanceNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. Age, Risk Override & Optional Split Amount
        item(key = "wti_profile_controls") {
            FintechCard {
                SectionAccentHeader(title = "Age & Risk Profile", badge = "Score ${uiState.questionnaireScore}/15")
                Spacer(Modifier.height(10.dp))
                AgeSliderRow(
                    label = stringResource(R.string.wti_age_label),
                    value = uiState.age,
                    range = 18f..80f,
                    onValueChange = onUpdateAge,
                    testTag = "wti_age_slider"
                )
                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Risk Profile (Questionnaire: ${uiState.computedProfile.displayName})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RiskProfile.entries.forEach { profile ->
                        FilterChip(
                            selected = uiState.effectiveProfile == profile,
                            onClick = { onOverrideProfile(profile) },
                            label = { Text(profile.displayName, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("wti_profile_${profile.id.lowercase()}")
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                AmountField(
                    amount = uiState.investableAmount,
                    currency = currency,
                    onAmountChange = onUpdateInvestableAmount,
                    label = stringResource(R.string.wti_investable_surplus),
                    modifier = Modifier.testTag("wti_amount_input")
                )
            }
        }

        // 4. 5-Question Risk Profile Questionnaire
        item(key = "wti_questionnaire") {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionAccentHeader(
                        title = stringResource(R.string.wti_questionnaire_title),
                        badge = "5 Questions"
                    )
                    TextButton(
                        onClick = onResetQuestionnaire,
                        modifier = Modifier.testTag("wti_reset_quiz_btn")
                    ) {
                        Text(stringResource(R.string.reset_to_default), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))

                AssetAllocationEngine.questionnaire.forEachIndexed { qIdx, question ->
                    val selectedScore = uiState.answers[question.id] ?: 2
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = question.prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        question.options.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedScore == option.score,
                                    onClick = { onSelectAnswer(question.id, option.score) },
                                    modifier = Modifier.testTag("wti_q_${question.id}_${option.score}")
                                )
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                        if (qIdx < AssetAllocationEngine.questionnaire.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }

        // 5. Data-Driven Asset Allocation Reference Matrix (from schemes.json)
        if (catalog.assetAllocationMatrix.isNotEmpty()) {
            item(key = "wti_matrix") {
                FintechCard {
                    SectionAccentHeader(
                        title = stringResource(R.string.wti_matrix_title),
                        badge = "${catalog.assetAllocationMatrix.size} Profiles"
                    )
                    Spacer(Modifier.height(8.dp))

                    // Table header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("Age / Risk", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.7f))
                        Text("Eq", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrincipalEmerald, modifier = Modifier.weight(0.7f))
                        Text("Debt", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = InfoBlue, modifier = Modifier.weight(0.7f))
                        Text("Gold", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = InterestAmber, modifier = Modifier.weight(0.7f))
                        Text("Cash", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PurpleAccent, modifier = Modifier.weight(0.7f))
                    }
                    Spacer(Modifier.height(4.dp))

                    catalog.assetAllocationMatrix.forEach { row ->
                        val isActiveRow = alloc != null &&
                            row.ageBandLabel == alloc.ageBandLabel &&
                            row.riskProfile.equals(alloc.riskProfile.id, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isActiveRow) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${row.ageBandLabel} • ${row.riskProfile.lowercase().replaceFirstChar { it.uppercase() }}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isActiveRow) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1.7f)
                            )
                            Text("${row.equityPercent.toInt()}%", style = MonoSmallStyle, modifier = Modifier.weight(0.7f))
                            Text("${row.debtPercent.toInt()}%", style = MonoSmallStyle, modifier = Modifier.weight(0.7f))
                            Text("${row.goldPercent.toInt()}%", style = MonoSmallStyle, modifier = Modifier.weight(0.7f))
                            Text("${row.cashLiquidPercent.toInt()}%", style = MonoSmallStyle, modifier = Modifier.weight(0.7f))
                        }
                    }
                }
            }
        }

        item(key = "wti_disclaimer") {
            DisclaimerBanner()
        }
    }
}

@Composable
private fun AssetMixRow(
    color: Color,
    label: String,
    percent: Double,
    amountText: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${percent.toInt()}%", style = MonoSmallStyle, fontWeight = FontWeight.Bold, color = color)
            if (amountText != null) {
                Text(amountText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
