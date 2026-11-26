package com.jyotirmoyb.loan.features.loans.balancetransfer

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.BalanceTransferAnalysisInput
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.TransferRecommendation
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.ResetToDefaultAction
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.DangerRed
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoLargeStyle
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy

@Composable
fun BalanceTransferScreen(
    uiState: BalanceTransferUiState,
    onUpdateInput: ((BalanceTransferAnalysisInput) -> BalanceTransferAnalysisInput) -> Unit,
    onUpdateNewRate: (Double) -> Unit,
    onResetNewRate: () -> Unit,
    onResetGstRate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cur = uiState.currency
    val inp = uiState.input
    val res = uiState.result

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("balance_transfer_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Result Hero Card
        if (res.isValid) {
            item(key = "bt_hero") {
                val savingsPositive = res.netLifetimeSavings > 0.0
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SlateNavy,
                    border = BorderStroke(1.dp, SageGreen.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bt_result_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.bt_net_savings),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFC7E2C6),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = CurrencyFormatter.format(res.netLifetimeSavings, cur),
                                    style = MonoLargeStyle,
                                    color = if (savingsPositive) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
                                )
                            }
                            Surface(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = stringResource(R.string.bt_breakeven),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFCBD5E1)
                                    )
                                    Text(
                                        text = res.breakEvenMonth?.let { "Month $it" } ?: "Never",
                                        style = MonoMediumStyle,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(stringResource(R.string.bt_monthly_savings), style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                                Text(
                                    text = "${CurrencyFormatter.format(res.monthlyEmiSavings, cur)}/mo",
                                    style = MonoMediumStyle,
                                    color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.bt_switching_cost), style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                                Text(
                                    text = CurrencyFormatter.format(res.totalSwitchingCost, cur),
                                    style = MonoMediumStyle,
                                    color = InterestAmber
                                )
                            }
                        }

                        if (inp.includeTopUp) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.16f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.bt_blended_emi),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFC7E2C6)
                                )
                                Text(
                                    text = CurrencyFormatter.format(res.blendedEmi, cur, true),
                                    style = MonoMediumStyle,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Recommendation Banner
            item(key = "bt_recommendation") {
                val accent = when (res.recommendation) {
                    TransferRecommendation.STRONGLY_RECOMMENDED -> SavingsGreen
                    TransferRecommendation.WORTH_CONSIDERING -> InterestAmber
                    TransferRecommendation.NOT_RECOMMENDED -> DangerRed
                }
                FintechCard(
                    containerColor = accent.copy(alpha = 0.09f),
                    borderColor = accent.copy(alpha = 0.45f),
                    modifier = Modifier.testTag("bt_recommendation_card")
                ) {
                    Text(
                        text = res.recommendationHeadline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = res.recommendationDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Proc. Fee: ${CurrencyFormatter.format(res.processingFeeAmount, cur)}", style = MonoSmallStyle)
                        Text("GST: ${CurrencyFormatter.format(res.gstOnFeeAmount, cur)}", style = MonoSmallStyle)
                        Text("Legal+FC: ${CurrencyFormatter.format(res.legalChargesAmount + res.foreclosureFeeAmount, cur)}", style = MonoSmallStyle)
                    }
                }
            }
        }

        // Current Loan Card
        item(key = "bt_current") {
            FintechCard {
                SectionAccentHeader(title = stringResource(R.string.bt_current_loan_section))
                Spacer(Modifier.height(12.dp))
                AmountField(
                    amount = inp.currentOutstanding,
                    currency = cur,
                    onAmountChange = { v -> onUpdateInput { it.copy(currentOutstanding = v) } },
                    label = stringResource(R.string.bt_outstanding)
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumericInputField(
                        label = stringResource(R.string.bt_current_rate),
                        valueText = inp.currentAnnualRatePercent.toString(),
                        onValueChange = { txt -> txt.toDoubleOrNull()?.let { r -> onUpdateInput { it.copy(currentAnnualRatePercent = r) } } },
                        modifier = Modifier.weight(1f)
                    )
                    NumericInputField(
                        label = stringResource(R.string.bt_remaining_months),
                        valueText = inp.remainingMonths.toString(),
                        onValueChange = { txt -> txt.toIntOrNull()?.let { m -> onUpdateInput { it.copy(remainingMonths = m.coerceIn(1, 420)) } } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // New Bank Offer & Costs Card
        item(key = "bt_new_offer") {
            FintechCard {
                SectionAccentHeader(title = stringResource(R.string.bt_new_offer_section))
                Spacer(Modifier.height(12.dp))
                NumericInputField(
                    label = stringResource(R.string.bt_new_rate),
                    valueText = inp.newAnnualRatePercent.toString(),
                    onValueChange = { txt -> txt.toDoubleOrNull()?.let(onUpdateNewRate) },
                    modifier = Modifier.testTag("bt_new_rate_input")
                )
                uiState.newRateSourceItem?.let { src ->
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ValueSourceBadgesRow(item = src, modifier = Modifier.weight(1f))
                        ResetToDefaultAction(
                            visible = kotlin.math.abs(inp.newAnnualRatePercent - (src.numericValue ?: 8.4)) > 0.01,
                            onClick = onResetNewRate,
                            testTag = "bt_new_rate_reset_btn"
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumericInputField(
                        label = stringResource(R.string.bt_proc_fee_pct),
                        valueText = inp.processingFeePercent.toString(),
                        onValueChange = { txt -> txt.toDoubleOrNull()?.let { v -> onUpdateInput { it.copy(processingFeePercent = v) } } },
                        modifier = Modifier.weight(1f)
                    )
                    NumericInputField(
                        label = stringResource(R.string.bt_proc_fee_flat),
                        valueText = inp.processingFeeFlat.toInt().toString(),
                        onValueChange = { txt -> txt.toDoubleOrNull()?.let { v -> onUpdateInput { it.copy(processingFeeFlat = v) } } },
                        prefix = cur.symbol,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumericInputField(
                        label = stringResource(R.string.bt_legal_charges),
                        valueText = inp.legalAndValuationCharges.toInt().toString(),
                        onValueChange = { txt -> txt.toDoubleOrNull()?.let { v -> onUpdateInput { it.copy(legalAndValuationCharges = v) } } },
                        prefix = cur.symbol,
                        modifier = Modifier.weight(1f)
                    )
                    NumericInputField(
                        label = stringResource(R.string.bt_foreclosure_pct),
                        valueText = inp.foreclosureFeePercent.toString(),
                        onValueChange = { txt -> txt.toDoubleOrNull()?.let { v -> onUpdateInput { it.copy(foreclosureFeePercent = v) } } },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${stringResource(R.string.bt_gst_toggle)} (${inp.gstRatePercent}%)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        uiState.gstSourceItem?.let { src ->
                            Spacer(Modifier.height(4.dp))
                            ValueSourceBadgesRow(item = src)
                        }
                    }
                    Switch(
                        checked = inp.gstOnFeesEnabled,
                        onCheckedChange = { checked -> onUpdateInput { it.copy(gstOnFeesEnabled = checked) } },
                        modifier = Modifier.testTag("bt_gst_switch")
                    )
                }
            }
        }

        // Optional Top-Up Card
        item(key = "bt_topup") {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.bt_topup_toggle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = inp.includeTopUp,
                        onCheckedChange = { checked -> onUpdateInput { it.copy(includeTopUp = checked) } },
                        modifier = Modifier.testTag("bt_topup_switch")
                    )
                }
                if (inp.includeTopUp) {
                    Spacer(Modifier.height(10.dp))
                    AmountField(
                        amount = inp.topUpAmount,
                        currency = cur,
                        onAmountChange = { v -> onUpdateInput { it.copy(topUpAmount = v) } },
                        label = stringResource(R.string.bt_topup_amount)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumericInputField(
                            label = stringResource(R.string.bt_topup_rate),
                            valueText = inp.topUpAnnualRatePercent.toString(),
                            onValueChange = { txt -> txt.toDoubleOrNull()?.let { r -> onUpdateInput { it.copy(topUpAnnualRatePercent = r) } } },
                            modifier = Modifier.weight(1f)
                        )
                        NumericInputField(
                            label = stringResource(R.string.bt_topup_tenure),
                            valueText = inp.topUpTenureMonths.toString(),
                            onValueChange = { txt -> txt.toIntOrNull()?.let { m -> onUpdateInput { it.copy(topUpTenureMonths = m.coerceIn(6, 360)) } } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item(key = "disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}
