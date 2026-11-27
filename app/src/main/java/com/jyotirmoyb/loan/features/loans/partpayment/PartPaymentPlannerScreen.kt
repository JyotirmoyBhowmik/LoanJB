package com.jyotirmoyb.loan.features.loans.partpayment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.MoneyMath
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
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy

@Composable
fun PartPaymentPlannerScreen(
    uiState: PartPaymentPlannerUiState,
    onUpdatePrincipal: (Double) -> Unit,
    onUpdateRate: (Double) -> Unit,
    onResetRate: () -> Unit,
    onUpdateRemainingMonths: (Int) -> Unit,
    onSetTargetYearMonth: (Int, Int) -> Unit,
    onApplyYearsEarlier: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cur = uiState.currency
    val inp = uiState.input
    val res = uiState.result
    val targetDateDisplay = MoneyMath.formatDateDdMmmYyyy(inp.startDay, inp.targetPayoffMonth, inp.targetPayoffYear)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("part_payment_planner_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Result Card
        if (res.isValid) {
            item(key = "pp_hero") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SlateNavy,
                    border = BorderStroke(1.dp, SageGreen.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("partpay_result_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.partpay_extra_monthly),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFC7E2C6),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.ui_txt_mo_b3ffed, CurrencyFormatter.format(res.extraMonthlyNeeded, cur)),
                            style = MonoLargeStyle,
                            color = Color(0xFF86EFAC)
                        )
                        Text(
                            text = stringResource(
                                R.string.ui_txt_mo_base_emi,
                                stringResource(R.string.partpay_new_outlay),
                                CurrencyFormatter.format(res.totalMonthlyOutlay, cur),
                                CurrencyFormatter.format(res.currentMonthlyEmi, cur)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    stringResource(R.string.partpay_interest_saved),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFCBD5E1)
                                )
                                Text(
                                    text = CurrencyFormatter.formatCompact(res.totalInterestSaved, cur),
                                    style = MonoMediumStyle,
                                    color = Color(0xFF86EFAC)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    stringResource(R.string.partpay_months_saved),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFCBD5E1)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.ui_txt_mos_y_m,
                                        res.monthsSaved,
                                        res.monthsSaved / 12,
                                        res.monthsSaved % 12
                                    ),
                                    style = MonoMediumStyle,
                                    color = Color.White
                                )
                            }
                        }

                        Surface(
                            color = Color.White.copy(alpha = 0.09f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    stringResource(R.string.ui_txt_before, res.currentLoanFreeDate),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFCBD5E1)
                                )
                                Text(
                                    stringResource(R.string.ui_txt_now, res.newLoanFreeDate),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF86EFAC),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        } else if (res.validationErrors.isNotEmpty()) {
            item(key = "pp_errors") {
                FintechCard(borderColor = DangerRed) {
                    res.validationErrors.forEach { err ->
                        Text(err, color = DangerRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Inputs Card
        item(key = "pp_inputs") {
            FintechCard {
                SectionAccentHeader(title = stringResource(R.string.partpay_inputs_section))
                Spacer(Modifier.height(12.dp))

                AmountField(
                    amount = inp.principal,
                    currency = cur,
                    onAmountChange = onUpdatePrincipal
                )
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumericInputField(
                        label = stringResource(R.string.eligibility_rate),
                        valueText = inp.annualRatePercent.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let(onUpdateRate) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("partpay_rate_input")
                    )
                    NumericInputField(
                        label = stringResource(R.string.bt_remaining_months),
                        valueText = inp.remainingMonths.toString(),
                        onValueChange = { it.toIntOrNull()?.let(onUpdateRemainingMonths) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("partpay_months_input")
                    )
                }

                uiState.rateSourceItem?.let { src ->
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ValueSourceBadgesRow(item = src, modifier = Modifier.weight(1f))
                        ResetToDefaultAction(
                            visible = kotlin.math.abs(inp.annualRatePercent - (src.numericValue ?: 8.75)) > 0.01,
                            onClick = onResetRate,
                            testTag = "partpay_rate_reset_btn"
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.partpay_target_date_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                val prevMonth = if (inp.targetPayoffMonth == 1) 12 else inp.targetPayoffMonth - 1
                                val prevYear = if (inp.targetPayoffMonth == 1) inp.targetPayoffYear - 1 else inp.targetPayoffYear
                                onSetTargetYearMonth(prevYear, prevMonth)
                            },
                            modifier = Modifier.testTag("partpay_target_minus_btn")
                        ) {
                            Icon(Icons.Outlined.Remove, contentDescription = "Earlier month")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = targetDateDisplay,
                                style = MonoMediumStyle,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (res.isValid) "${res.targetMonths} months from start" else "Adjust earlier than current payoff",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                val nextMonth = if (inp.targetPayoffMonth == 12) 1 else inp.targetPayoffMonth + 1
                                val nextYear = if (inp.targetPayoffMonth == 12) inp.targetPayoffYear + 1 else inp.targetPayoffYear
                                onSetTargetYearMonth(nextYear, nextMonth)
                            },
                            modifier = Modifier.testTag("partpay_target_plus_btn")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Later month")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(2, 5, 8).forEach { yrs ->
                        AssistChip(
                            onClick = { onApplyYearsEarlier(yrs) },
                            label = { Text(stringResource(R.string.ui_txt_yrs_early, yrs)) },
                            modifier = Modifier.testTag("partpay_preset_${yrs}y")
                        )
                    }
                }
            }
        }

        // Balance Comparison Line Chart
        if (res.isValid && res.baselineYearlyBalances.size >= 2) {
            item(key = "pp_curve") {
                PartPaymentPayoffCurveCard(
                    baseline = res.baselineYearlyBalances,
                    planned = res.plannedYearlyBalances,
                    currency = cur
                )
            }
        }

        item(key = "disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PartPaymentPayoffCurveCard(baseline: List<Double>, planned: List<Double>, currency: CurrencyCode) {
    val maxBal = (baseline.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val maxLen = maxOf(baseline.size, planned.size).coerceAtLeast(2)

    FintechCard(modifier = Modifier.testTag("partpay_curve_card")) {
        SectionAccentHeader(title = stringResource(R.string.partpay_curve_title))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(modifier = Modifier.size(9.dp).background(InterestAmber, CircleShape))
                Text(stringResource(R.string.ui_txt_regular_schedule), style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(modifier = Modifier.size(9.dp).background(SavingsGreen, CircleShape))
                Text(stringResource(R.string.ui_txt_with_extra_monthly), style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp)
        ) {
            val padL = 12f
            val padR = 12f
            val padT = 10f
            val padB = 14f
            val chartW = size.width - padL - padR
            val chartH = size.height - padT - padB

            for (g in 0..3) {
                val y = padT + chartH * (g / 3f)
                drawLine(Color.Gray.copy(alpha = 0.2f), Offset(padL, y), Offset(size.width - padR, y), 1.5f)
            }

            fun drawCurve(pts: List<Double>, color: Color) {
                if (pts.size < 2) return
                val path = Path()
                pts.forEachIndexed { idx, bal ->
                    val x = padL + (idx.toFloat() / (maxLen - 1).coerceAtLeast(1)) * chartW
                    val y = padT + (1f - (bal / maxBal).toFloat().coerceIn(0f, 1f)) * chartH
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = color, style = Stroke(width = 5f, cap = StrokeCap.Round))
            }

            drawCurve(baseline, InterestAmber)
            drawCurve(planned, SavingsGreen)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.ui_txt_start, CurrencyFormatter.formatCompact(maxBal, currency)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.ui_txt_year_0_da5296, maxLen - 1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
