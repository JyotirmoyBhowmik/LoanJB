package com.jyotirmoyb.loan.features.retirement.epf

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.ResetToDefaultAction
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy
import kotlin.math.abs

@Composable
fun EpfScreen(
    uiState: EpfUiState,
    onUpdateCurrentAge: (Int) -> Unit,
    onUpdateRetirementAge: (Int) -> Unit,
    onUpdateBasicDa: (Double) -> Unit,
    onUpdateExistingBalance: (Double) -> Unit,
    onUpdateVpf: (Double) -> Unit,
    onUpdateSalaryGrowth: (Double) -> Unit,
    onToggleWageCeiling: (Boolean) -> Unit,
    onUpdateInterestRate: (Double) -> Unit,
    onResetRate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val input = uiState.input
    val result = uiState.result
    val currency = uiState.currency

    var growthText by remember { mutableStateOf(formatEpfRate(input.annualSalaryGrowthPercent)) }
    var rateText by remember { mutableStateOf(formatEpfRate(input.epfInterestRatePercent)) }

    LaunchedEffect(input.epfInterestRatePercent) {
        if (rateText.toDoubleOrNull()?.let { abs(it - input.epfInterestRatePercent) > 0.01 } != false) {
            rateText = formatEpfRate(input.epfInterestRatePercent)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("epf_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero EPF Maturity Card
        item(key = "epf_hero") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("epf_hero_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.epf_corpus_hero),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = CurrencyFormatter.formatCompact(result.projectedEpfCorpus, currency),
                        style = MonoLargeStyle,
                        color = PrincipalEmerald
                    )
                    Text(
                        text = "At Age ${input.retirementAge} (${result.yearsToRetirement} years) • EPF Rate ${formatEpfRate(input.epfInterestRatePercent)}% p.a.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeroMetricItem(
                            label = "Employee Share",
                            value = CurrencyFormatter.formatCompact(result.employeeTotalContribution, currency),
                            valueColor = Color.White
                        )
                        HeroMetricItem(
                            label = "Employer EPF",
                            value = CurrencyFormatter.formatCompact(result.employerEpfTotalContribution, currency),
                            valueColor = InterestAmber
                        )
                        HeroMetricItem(
                            label = "Total Interest",
                            value = CurrencyFormatter.formatCompact(result.totalInterestEarned, currency),
                            valueColor = PrincipalEmerald
                        )
                    }
                }
            }
        }

        // 2. Inputs Card
        item(key = "epf_inputs") {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionAccentHeader(
                        title = "EPF Parameters",
                        badge = "${uiState.rules.employeePercent.toInt()}% + ${uiState.rules.employerEpfPercent}%",
                        icon = Icons.Outlined.Badge,
                        modifier = Modifier.weight(1f)
                    )
                    ResetToDefaultAction(
                        visible = uiState.hasCustomRate,
                        onClick = onResetRate,
                        testTag = "epf_reset_rate_btn"
                    )
                }
                uiState.epfRateItem?.let {
                    Spacer(Modifier.height(6.dp))
                    ValueSourceBadgesRow(item = it)
                }
                Spacer(Modifier.height(10.dp))

                AgeSliderRow(
                    label = stringResource(R.string.ret_current_age),
                    value = input.currentAge,
                    range = 18f..57f,
                    onValueChange = onUpdateCurrentAge,
                    testTag = "epf_current_age_slider"
                )
                AgeSliderRow(
                    label = stringResource(R.string.ret_retire_age),
                    value = input.retirementAge,
                    range = (input.currentAge + 1).toFloat()..65f,
                    onValueChange = onUpdateRetirementAge,
                    testTag = "epf_retire_age_slider"
                )

                Spacer(Modifier.height(8.dp))
                AmountField(
                    label = stringResource(R.string.epf_basic_da),
                    amount = input.monthlyBasicPlusDa,
                    currency = currency,
                    onAmountChange = onUpdateBasicDa,
                    modifier = Modifier.testTag("epf_basic_da_input")
                )
                Spacer(Modifier.height(10.dp))
                AmountField(
                    label = stringResource(R.string.epf_existing_bal),
                    amount = input.existingEpfBalance,
                    currency = currency,
                    onAmountChange = onUpdateExistingBalance,
                    modifier = Modifier.testTag("epf_existing_bal_input")
                )
                Spacer(Modifier.height(10.dp))
                AmountField(
                    label = stringResource(R.string.epf_vpf_extra),
                    amount = input.vpfExtraMonthly,
                    currency = currency,
                    onAmountChange = onUpdateVpf,
                    modifier = Modifier.testTag("epf_vpf_input")
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumericInputField(
                        label = stringResource(R.string.epf_salary_growth),
                        valueText = growthText,
                        onValueChange = { txt ->
                            growthText = txt
                            txt.toDoubleOrNull()?.let(onUpdateSalaryGrowth)
                        },
                        trailingBadge = "%",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("epf_growth_input")
                    )
                    NumericInputField(
                        label = stringResource(R.string.epf_interest_rate),
                        valueText = rateText,
                        onValueChange = { txt ->
                            rateText = txt
                            txt.toDoubleOrNull()?.let(onUpdateInterestRate)
                        },
                        trailingBadge = "%",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("epf_rate_input")
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.epf_wage_ceiling_toggle),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.epf_wage_ceiling_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = input.applyWageCeiling,
                        onCheckedChange = onToggleWageCeiling,
                        modifier = Modifier.testTag("epf_wage_ceiling_switch")
                    )
                }
            }
        }

        // 3. Monthly Split & Total Breakdown
        item(key = "epf_breakdown") {
            FintechCard {
                SectionAccentHeader(title = "Year 1 Monthly Split & Corpus Breakdown", badge = "EPF + EPS")
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Year 1 Employee EPF (+VPF)", CurrencyFormatter.format(result.firstMonthEmployeeEpf, currency))
                KeyValueRow("Year 1 Employer EPF Share", CurrencyFormatter.format(result.firstMonthEmployerEpf, currency))
                KeyValueRow("Year 1 Employer EPS Share", CurrencyFormatter.format(result.firstMonthEmployerEps, currency), valueColor = InfoBlue)
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                KeyValueRow("Total Employee Contribution", CurrencyFormatter.format(result.employeeTotalContribution, currency))
                KeyValueRow("Total Employer EPF Contribution", CurrencyFormatter.format(result.employerEpfTotalContribution, currency))
                KeyValueRow("Total EPF Interest Compounded", CurrencyFormatter.format(result.totalInterestEarned, currency), valueColor = SavingsGreen)
                KeyValueRow("Total Employer EPS (Separate Pension)", CurrencyFormatter.format(result.epsTotalContribution, currency), valueColor = InfoBlue)
            }
        }

        // 4. EPS-95 Note Card
        item(key = "epf_eps_note") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("epf_eps_note_card"),
                shape = RoundedCornerShape(16.dp),
                color = InfoBlue.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, InfoBlue.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = InfoBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.epf_eps_note_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = result.epsNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "epf_disclaimer") {
            DisclaimerBanner()
        }
    }
}

private fun formatEpfRate(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.2f", v).trimEnd('0').trimEnd('.')
