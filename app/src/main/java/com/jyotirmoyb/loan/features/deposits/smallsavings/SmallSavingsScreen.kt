package com.jyotirmoyb.loan.features.deposits.smallsavings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.jyotirmoyb.loan.domain.SmallSavingsSchemeType
import com.jyotirmoyb.loan.features.deposits.fd.DepositYearWiseCard
import com.jyotirmoyb.loan.features.deposits.fd.HeroMetricItem
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.ResetToDefaultAction
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SlateNavy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmallSavingsScreen(
    uiState: SmallSavingsUiState,
    onSelectScheme: (SmallSavingsSchemeType) -> Unit,
    onUpdateContribution: (Double) -> Unit,
    onSetPpfExtensionBlocks: (Int) -> Unit,
    onSetPpfContributeDuringExtension: (Boolean) -> Unit,
    onSetPoMisJoint: (Boolean) -> Unit,
    onSetPoTdYears: (Int) -> Unit,
    onOverrideActiveRate: (Double) -> Unit,
    onResetActiveRate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val input = uiState.input
    val result = uiState.result
    val currency = uiState.currency
    var showYearlyTable by remember { mutableStateOf(true) }
    var editableRate by remember(uiState.activeRateKey, uiState.rateOverrideText) {
        mutableStateOf(uiState.rateOverrideText)
    }

    LaunchedEffect(uiState.rateOverrideText) {
        editableRate = uiState.rateOverrideText
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("small_savings_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Scheme Selector Chips
        item(key = "ss_scheme_selector") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("ss_scheme_chips")
            ) {
                items(SmallSavingsSchemeType.entries) { scheme ->
                    FilterChip(
                        selected = input.scheme == scheme,
                        onClick = { onSelectScheme(scheme) },
                        label = {
                            Text(
                                text = scheme.shortTitle,
                                fontWeight = if (input.scheme == scheme) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("ss_chip_${scheme.id}")
                    )
                }
            }
        }

        // 2. Hero Card with Notified Rate, Quarter Badge & Maturity/Payout
        item(key = "ss_hero_card") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ss_hero_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = input.scheme.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = PrincipalEmerald.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = input.scheme.taxBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = PrincipalEmerald,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = input.scheme.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f)
                    )

                    if (result.periodicPayoutLabel != null) {
                        Text(
                            text = result.periodicPayoutLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.70f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = CurrencyFormatter.format(result.periodicPayoutAmount, currency),
                            style = MonoLargeStyle,
                            color = Color.White
                        )
                        Text(
                            text = "Total value incl. interest: ${CurrencyFormatter.format(result.maturityAmount, currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrincipalEmerald
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.fd_maturity_amount),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.70f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = CurrencyFormatter.format(result.maturityAmount, currency),
                            style = MonoLargeStyle,
                            color = Color.White
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeroMetricItem(
                            label = stringResource(R.string.ss_total_invested),
                            value = CurrencyFormatter.formatCompact(result.totalInvested, currency),
                            valueColor = Color.White
                        )
                        HeroMetricItem(
                            label = stringResource(R.string.fd_total_interest),
                            value = CurrencyFormatter.formatCompact(result.totalInterest, currency),
                            valueColor = PrincipalEmerald
                        )
                        HeroMetricItem(
                            label = stringResource(R.string.fd_maturity_date),
                            value = result.maturityDateLabel,
                            valueColor = InterestAmber
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = result.tenureDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Surface(
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${CurrencyFormatter.formatPercent(result.appliedRatePercent)} • ${result.quarterLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Rates Change Quarterly & Source Chip Banner (Mandatory for every Small Savings view)
        item(key = "ss_quarterly_notice") {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = InfoBlue.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, InfoBlue.copy(alpha = 0.30f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ss_quarterly_note_banner")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            text = stringResource(R.string.ss_as_of_quarter, uiState.config.quarterLabel),
                            style = MaterialTheme.typography.labelLarge,
                            color = InfoBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = stringResource(R.string.ss_quarterly_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    uiState.activeRateItem?.let { item ->
                        ValueSourceBadgesRow(item = item)
                    }
                }
            }
        }

        // 4. Inputs & Scheme-Specific Controls Card
        item(key = "ss_inputs_card") {
            FintechCard {
                SectionAccentHeader(
                    title = input.scheme.shortTitle,
                    badge = "${CurrencyFormatter.formatPercent(result.appliedRatePercent)} p.a.",
                    icon = Icons.Outlined.AccountBalance
                )
                Spacer(Modifier.height(14.dp))

                val depositLabel = when (input.scheme) {
                    SmallSavingsSchemeType.PPF, SmallSavingsSchemeType.SSY ->
                        stringResource(R.string.ss_yearly_deposit)
                    SmallSavingsSchemeType.PO_RD ->
                        stringResource(R.string.ss_monthly_deposit)
                    else ->
                        stringResource(R.string.ss_lumpsum_deposit)
                }

                AmountField(
                    amount = input.contributionAmount,
                    currency = currency,
                    onAmountChange = onUpdateContribution,
                    label = depositLabel
                )

                // Cap warning banner if exceeded
                result.capWarning?.let { warning ->
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = InterestAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, InterestAmber.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = InterestAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Scheme-specific controls
                when (input.scheme) {
                    SmallSavingsSchemeType.PPF -> {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.ss_ppf_extension),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "15 Yrs", 1 to "+5 (20Y)", 2 to "+10 (25Y)", 3 to "+15 (30Y)").forEach { (blk, lbl) ->
                                FilterChip(
                                    selected = input.ppfExtensionBlocks == blk,
                                    onClick = { onSetPpfExtensionBlocks(blk) },
                                    label = { Text(lbl) },
                                    modifier = Modifier.testTag("ss_ppf_ext_$blk")
                                )
                            }
                        }
                        if (input.ppfExtensionBlocks > 0) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.ss_ppf_contribute_ext),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = input.ppfContributeDuringExtension,
                                    onCheckedChange = onSetPpfContributeDuringExtension,
                                    modifier = Modifier.testTag("ss_ppf_contrib_ext_switch")
                                )
                            }
                        }
                    }

                    SmallSavingsSchemeType.PO_MIS -> {
                        Spacer(Modifier.height(14.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !input.pomisJointAccount,
                                onClick = { onSetPoMisJoint(false) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                                modifier = Modifier.testTag("ss_pomis_single_btn")
                            ) {
                                Text("${stringResource(R.string.ss_pomis_single)} (₹9L)")
                            }
                            SegmentedButton(
                                selected = input.pomisJointAccount,
                                onClick = { onSetPoMisJoint(true) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                                modifier = Modifier.testTag("ss_pomis_joint_btn")
                            ) {
                                Text("${stringResource(R.string.ss_pomis_joint)} (₹15L)")
                            }
                        }
                    }

                    SmallSavingsSchemeType.PO_TD -> {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.ss_potd_tenure),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3, 5).forEach { yrs ->
                                val r = uiState.config.rateFor(SmallSavingsSchemeType.PO_TD, yrs)
                                FilterChip(
                                    selected = input.potdTenureYears == yrs,
                                    onClick = { onSetPoTdYears(yrs) },
                                    label = { Text("${yrs}Y (${CurrencyFormatter.formatPercent(r)})") },
                                    modifier = Modifier.testTag("ss_potd_yr_$yrs")
                                )
                            }
                        }
                    }

                    else -> Unit
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))

                // Editable Rate Override with Save & Reset to default
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumericInputField(
                        label = stringResource(R.string.ss_rate_override_label),
                        valueText = editableRate,
                        onValueChange = { editableRate = it },
                        trailingBadge = "%",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ss_rate_override_input")
                    )
                    TextButton(
                        onClick = {
                            editableRate.toDoubleOrNull()?.let(onOverrideActiveRate)
                        },
                        modifier = Modifier.testTag("ss_rate_apply_btn")
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
                ResetToDefaultAction(
                    visible = uiState.isRateOverridden,
                    onClick = onResetActiveRate,
                    testTag = "ss_rate_reset_btn"
                )
            }
        }

        // 5. Year-wise Table
        item(key = "ss_yearly_table") {
            DepositYearWiseCard(
                rows = result.yearlyRows,
                currency = currency,
                showDepositColumn = input.scheme == SmallSavingsSchemeType.PPF ||
                    input.scheme == SmallSavingsSchemeType.SSY ||
                    input.scheme == SmallSavingsSchemeType.PO_RD,
                expanded = showYearlyTable,
                onToggleExpanded = { showYearlyTable = !showYearlyTable }
            )
        }

        item(key = "ss_disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}
