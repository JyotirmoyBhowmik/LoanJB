package com.jyotirmoyb.loan.features.retirement.nps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.features.deposits.fd.HeroMetricItem
import com.jyotirmoyb.loan.features.deposits.fd.KeyValueRow
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.features.retirement.planner.AgeSliderRow
import com.jyotirmoyb.loan.features.retirement.planner.ChartLegendDot
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.ResetToDefaultAction
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun NpsScreen(
    uiState: NpsUiState,
    onUpdateCurrentAge: (Int) -> Unit,
    onUpdateExitAge: (Int) -> Unit,
    onUpdateMonthlyContribution: (Double) -> Unit,
    onUpdateExistingCorpus: (Double) -> Unit,
    onUpdateStepUp: (Double) -> Unit,
    onUpdateExpectedReturn: (Double) -> Unit,
    onUpdateAnnuityAllocation: (Double) -> Unit,
    onUpdateAnnuityRate: (Double) -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val input = uiState.input
    val result = uiState.result
    val currency = uiState.currency

    var stepUpText by remember { mutableStateOf(formatNpsRate(input.annualStepUpPercent)) }
    var retText by remember { mutableStateOf(formatNpsRate(input.expectedReturnPercent)) }
    var annRateText by remember { mutableStateOf(formatNpsRate(input.annuityRatePercent)) }

    LaunchedEffect(input.expectedReturnPercent) {
        if (retText.toDoubleOrNull()?.let { abs(it - input.expectedReturnPercent) > 0.01 } != false) {
            retText = formatNpsRate(input.expectedReturnPercent)
        }
    }
    LaunchedEffect(input.annuityRatePercent) {
        if (annRateText.toDoubleOrNull()?.let { abs(it - input.annuityRatePercent) > 0.01 } != false) {
            annRateText = formatNpsRate(input.annuityRatePercent)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("nps_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero NPS Corpus & Pension Summary
        item(key = "nps_hero") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nps_hero_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.nps_corpus_hero),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = CurrencyFormatter.formatCompact(result.projectedCorpus, currency),
                        style = MonoLargeStyle,
                        color = PrincipalEmerald
                    )
                    Text(
                        text = stringResource(
                            R.string.ui_txt_mo_babe8b,
                            stringResource(R.string.nps_est_pension),
                            CurrencyFormatter.format(result.estimatedMonthlyPension, currency)
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = InterestAmber,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeroMetricItem(
                            label = "${stringResource(R.string.nps_lumpsum_label)} (${result.lumpSumPercentApplied.roundToInt()}%)",
                            value = CurrencyFormatter.formatCompact(result.lumpSumWithdrawal, currency),
                            valueColor = PrincipalEmerald
                        )
                        HeroMetricItem(
                            label = "${stringResource(R.string.nps_annuity_label)} (${result.annuityPercentApplied.roundToInt()}%)",
                            value = CurrencyFormatter.formatCompact(result.annuityCorpus, currency),
                            valueColor = Color.White
                        )
                    }
                }
            }
        }

        // 2. Annuity vs Lump-Sum Split Visual Card
        item(key = "nps_split_visual") {
            FintechCard(modifier = Modifier.testTag("nps_split_card")) {
                SectionAccentHeader(
                    title = "Exit Corpus Split (Lump-Sum vs Annuity)",
                    badge = "${result.lumpSumPercentApplied.roundToInt()}/${result.annuityPercentApplied.roundToInt()}",
                    icon = Icons.Outlined.PieChart
                )
                uiState.minAnnuityItem?.let {
                    Spacer(Modifier.height(6.dp))
                    ValueSourceBadgesRow(item = it)
                }
                Spacer(Modifier.height(12.dp))

                val lumpFrac = (result.lumpSumPercentApplied / 100.0).toFloat().coerceIn(0f, 1f)
                val annFrac = (1f - lumpFrac).coerceIn(0f, 1f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (lumpFrac > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(lumpFrac)
                                .background(PrincipalEmerald)
                        )
                    }
                    if (annFrac > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(annFrac)
                                .background(InfoBlue)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ChartLegendDot(PrincipalEmerald, "Lump Sum ${result.lumpSumPercentApplied.roundToInt()}%")
                    ChartLegendDot(InfoBlue, "Annuity ${result.annuityPercentApplied.roundToInt()}%")
                }

                Spacer(Modifier.height(10.dp))
                val minSlider = if (result.eligibleForFullLumpSumExit) 0f else uiState.minAnnuityPercent.toFloat()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.nps_annuity_split),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.ui_txt_fmt_361cec, result.annuityPercentApplied.roundToInt()),
                        style = MonoSmallStyle,
                        color = InfoBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = result.annuityPercentApplied.toFloat().coerceIn(minSlider, 100f),
                    onValueChange = { onUpdateAnnuityAllocation(it.roundToInt().toDouble()) },
                    valueRange = minSlider..100f,
                    modifier = Modifier.testTag("nps_annuity_slider")
                )
                Text(
                    text = if (result.eligibleForFullLumpSumExit) {
                        "Corpus is ≤ ${CurrencyFormatter.format(
                            uiState.smallCorpusLimit,
                            currency
                        )} — PFRDA permits up to 100% lump-sum withdrawal."
                    } else {
                        "PFRDA mandates minimum ${uiState.minAnnuityPercent.roundToInt()}% annuity purchase at age 60; up to ${(100.0 - uiState.minAnnuityPercent).roundToInt()}% lump-sum withdrawal is exempt under Sec 10(12A)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 3. Contribution & Growth Inputs Card
        item(key = "nps_inputs") {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionAccentHeader(
                        title = "Contribution & Growth",
                        badge = "${result.investmentYears} yrs",
                        icon = Icons.Outlined.AccountBalance,
                        modifier = Modifier.weight(1f)
                    )
                    ResetToDefaultAction(
                        visible = uiState.hasCustomRates,
                        onClick = onResetDefaults,
                        testTag = "nps_reset_btn"
                    )
                }
                uiState.expectedReturnItem?.let {
                    Spacer(Modifier.height(6.dp))
                    ValueSourceBadgesRow(item = it)
                }
                Spacer(Modifier.height(10.dp))

                AgeSliderRow(
                    label = stringResource(R.string.ret_current_age),
                    value = input.currentAge,
                    range = 18f..65f,
                    onValueChange = onUpdateCurrentAge,
                    testTag = "nps_current_age_slider"
                )
                AgeSliderRow(
                    label = stringResource(R.string.ret_retire_age),
                    value = input.retirementAge,
                    range = (input.currentAge + 1).toFloat()..75f,
                    onValueChange = onUpdateExitAge,
                    testTag = "nps_exit_age_slider"
                )

                Spacer(Modifier.height(8.dp))
                AmountField(
                    label = stringResource(R.string.nps_monthly_contrib),
                    amount = input.monthlyContribution,
                    currency = currency,
                    onAmountChange = onUpdateMonthlyContribution,
                    modifier = Modifier.testTag("nps_monthly_input")
                )
                Spacer(Modifier.height(10.dp))
                AmountField(
                    label = stringResource(R.string.nps_existing_corpus),
                    amount = input.existingCorpus,
                    currency = currency,
                    onAmountChange = onUpdateExistingCorpus,
                    modifier = Modifier.testTag("nps_existing_input")
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumericInputField(
                        label = stringResource(R.string.nps_step_up),
                        valueText = stepUpText,
                        onValueChange = { txt ->
                            stepUpText = txt
                            txt.toDoubleOrNull()?.let(onUpdateStepUp)
                        },
                        trailingBadge = "%",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nps_stepup_input")
                    )
                    NumericInputField(
                        label = stringResource(R.string.nps_expected_return),
                        valueText = retText,
                        onValueChange = { txt ->
                            retText = txt
                            txt.toDoubleOrNull()?.let(onUpdateExpectedReturn)
                        },
                        trailingBadge = "%",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nps_return_input")
                    )
                }
                Spacer(Modifier.height(8.dp))
                NumericInputField(
                    label = stringResource(R.string.nps_annuity_rate),
                    valueText = annRateText,
                    onValueChange = { txt ->
                        annRateText = txt
                        txt.toDoubleOrNull()?.let(onUpdateAnnuityRate)
                    },
                    trailingBadge = "%",
                    modifier = Modifier.testTag("nps_annuity_rate_input")
                )
            }
        }

        // 4. NPS Wealth Summary Card
        item(key = "nps_summary") {
            FintechCard {
                SectionAccentHeader(title = "NPS Projection Summary", badge = "Tier I")
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Total Contribution Invested", CurrencyFormatter.format(result.totalInvested, currency))
                KeyValueRow("Estimated Wealth Gained", CurrencyFormatter.format(result.wealthGained, currency), valueColor = SavingsGreen)
                KeyValueRow(
                    "Projected Maturity Corpus",
                    CurrencyFormatter.format(result.projectedCorpus, currency),
                    valueColor = PrincipalEmerald
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                KeyValueRow(
                    "Lump-Sum Withdrawal (${result.lumpSumPercentApplied.roundToInt()}%)",
                    CurrencyFormatter.format(result.lumpSumWithdrawal, currency)
                )
                KeyValueRow(
                    "Annuity Purchase (${result.annuityPercentApplied.roundToInt()}%)",
                    CurrencyFormatter.format(result.annuityCorpus, currency)
                )
                KeyValueRow(
                    "Est. Monthly Pension (@ ${formatNpsRate(input.annuityRatePercent)}%)",
                    CurrencyFormatter.format(result.estimatedMonthlyPension, currency),
                    valueColor = InterestAmber
                )
            }
        }

        item(key = "nps_disclaimer") {
            DisclaimerBanner()
        }
    }
}

private fun formatNpsRate(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.2f", v).trimEnd('0').trimEnd('.')
