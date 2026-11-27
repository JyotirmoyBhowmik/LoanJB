package com.jyotirmoyb.loan.features.loans.eligibility

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.AffordabilityZone
import com.jyotirmoyb.loan.domain.CibilBand
import com.jyotirmoyb.loan.domain.CurrencyFormatter
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
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EligibilityScreen(
    uiState: EligibilityUiState,
    onSetReverseMode: (Boolean) -> Unit,
    onUpdateMonthlyIncome: (Double) -> Unit,
    onUpdateExistingEmis: (Double) -> Unit,
    onUpdateAge: (Int) -> Unit,
    onUpdateRetirementAge: (Int) -> Unit,
    onUpdateRate: (Double) -> Unit,
    onResetRate: () -> Unit,
    onUpdateTenureYears: (Int) -> Unit,
    onUpdateFoir: (Double) -> Unit,
    onResetFoir: () -> Unit,
    onSelectCibil: (CibilBand) -> Unit,
    onUpdateReverseBudget: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val cur = uiState.currency
    val res = uiState.result

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("eligibility_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Forward / Reverse Segmented Switch
        item(key = "mode_toggle") {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("eligibility_mode_switch")
            ) {
                SegmentedButton(
                    selected = !uiState.isReverseMode,
                    onClick = { onSetReverseMode(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("eligibility_forward_btn")
                ) {
                    Text(stringResource(R.string.eligibility_mode_forward))
                }
                SegmentedButton(
                    selected = uiState.isReverseMode,
                    onClick = { onSetReverseMode(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("eligibility_reverse_btn")
                ) {
                    Text(stringResource(R.string.eligibility_mode_reverse))
                }
            }
        }

        // Result Hero Card
        if (res.isValid) {
            item(key = "hero_result") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SlateNavy,
                    border = BorderStroke(1.dp, SageGreen.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("eligibility_result_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.eligibility_max_loan),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFC7E2C6),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = CurrencyFormatter.format(res.maxLoanAmount, cur),
                            style = MonoLargeStyle,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(
                                R.string.ui_txt_yrs_668acc,
                                CurrencyFormatter.formatShortLabel(res.maxLoanAmount, cur),
                                res.appliedTenureMonths / 12,
                                CurrencyFormatter.formatPercent(res.effectiveAnnualRatePercent)
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
                                    stringResource(R.string.eligibility_max_emi),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFCBD5E1)
                                )
                                Text(
                                    text = CurrencyFormatter.format(res.maxEmi, cur),
                                    style = MonoMediumStyle,
                                    color = Color(0xFF86EFAC)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    stringResource(R.string.ui_txt_emi_income),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFCBD5E1)
                                )
                                Text(
                                    text = stringResource(R.string.ui_txt_fmt_361cec, res.totalEmiToIncomePercent),
                                    style = MonoMediumStyle,
                                    color = when (res.zone) {
                                        AffordabilityZone.COMFORTABLE -> Color(0xFF86EFAC)
                                        AffordabilityZone.STRETCH -> Color(0xFFFBBF24)
                                        AffordabilityZone.RISKY -> Color(0xFFFCA5A5)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Affordability Meter Card
            item(key = "meter_card") {
                AffordabilityMeterCard(
                    zone = res.zone,
                    ratioPercent = res.totalEmiToIncomePercent,
                    summary = res.zoneSummary
                )
            }
        } else if (res.validationErrors.isNotEmpty()) {
            item(key = "errors") {
                FintechCard(borderColor = DangerRed) {
                    res.validationErrors.forEach { err ->
                        Text(err, color = DangerRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Inputs Card
        item(key = "inputs") {
            FintechCard {
                SectionAccentHeader(
                    title = if (uiState.isReverseMode) {
                        stringResource(R.string.eligibility_mode_reverse)
                    } else {
                        stringResource(R.string.eligibility_mode_forward)
                    }
                )
                Spacer(Modifier.height(12.dp))

                if (uiState.isReverseMode) {
                    AmountField(
                        amount = uiState.reverseMonthlyEmiBudget,
                        currency = cur,
                        onAmountChange = onUpdateReverseBudget,
                        label = stringResource(R.string.eligibility_reverse_prompt)
                    )
                    Spacer(Modifier.height(10.dp))
                }

                AmountField(
                    amount = uiState.monthlyIncome,
                    currency = cur,
                    onAmountChange = onUpdateMonthlyIncome,
                    label = stringResource(R.string.eligibility_monthly_income)
                )
                Spacer(Modifier.height(10.dp))
                AmountField(
                    amount = uiState.existingEmis,
                    currency = cur,
                    onAmountChange = onUpdateExistingEmis,
                    label = stringResource(R.string.eligibility_existing_emis)
                )
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumericInputField(
                        label = stringResource(R.string.eligibility_rate),
                        valueText = uiState.annualRatePercent.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let(onUpdateRate) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("eligibility_rate_input")
                    )
                    NumericInputField(
                        label = stringResource(R.string.eligibility_tenure_years),
                        valueText = uiState.desiredTenureYears.toString(),
                        onValueChange = { it.toIntOrNull()?.let(onUpdateTenureYears) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("eligibility_tenure_input")
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
                            visible = kotlin.math.abs(uiState.annualRatePercent - (src.numericValue ?: 8.5)) > 0.01,
                            onClick = onResetRate,
                            testTag = "eligibility_rate_reset_btn"
                        )
                    }
                }

                if (!uiState.isReverseMode) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumericInputField(
                            label = stringResource(R.string.eligibility_age),
                            valueText = uiState.ageYears.toString(),
                            onValueChange = { it.toIntOrNull()?.let(onUpdateAge) },
                            modifier = Modifier.weight(1f)
                        )
                        NumericInputField(
                            label = stringResource(R.string.eligibility_retirement_age),
                            valueText = uiState.retirementAgeYears.toString(),
                            onValueChange = { it.toIntOrNull()?.let(onUpdateRetirementAge) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    NumericInputField(
                        label = stringResource(R.string.eligibility_foir_label),
                        valueText = uiState.activeFoirPercent.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let(onUpdateFoir) },
                        trailingBadge = "Band ${uiState.defaultFoirPercent.toInt()}%",
                        modifier = Modifier.testTag("eligibility_foir_input")
                    )
                    uiState.foirSourceItem?.let { src ->
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ValueSourceBadgesRow(item = src, modifier = Modifier.weight(1f))
                            ResetToDefaultAction(
                                visible = uiState.customFoirPercent != null,
                                onClick = onResetFoir,
                                testTag = "eligibility_foir_reset_btn"
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.eligibility_cibil_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CibilBand.entries.forEach { band ->
                        FilterChip(
                            selected = uiState.cibilBand == band,
                            onClick = { onSelectCibil(band) },
                            label = { Text(band.displayLabel) },
                            modifier = Modifier.testTag("cibil_${band.id}")
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

@Composable
private fun AffordabilityMeterCard(zone: AffordabilityZone, ratioPercent: Double, summary: String) {
    val zoneColor = when (zone) {
        AffordabilityZone.COMFORTABLE -> SavingsGreen
        AffordabilityZone.STRETCH -> InterestAmber
        AffordabilityZone.RISKY -> DangerRed
    }
    val zoneText = when (zone) {
        AffordabilityZone.COMFORTABLE -> stringResource(R.string.eligibility_zone_comfortable)
        AffordabilityZone.STRETCH -> stringResource(R.string.eligibility_zone_stretch)
        AffordabilityZone.RISKY -> stringResource(R.string.eligibility_zone_risky)
    }

    FintechCard(modifier = Modifier.testTag("affordability_meter_card")) {
        SectionAccentHeader(
            title = stringResource(R.string.eligibility_meter_title),
            badge = zoneText.uppercase(),
            accentColor = zoneColor
        )
        Spacer(Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
        ) {
            val arcDiameter = minOf(size.width * 0.62f, size.height * 1.75f)
            val topLeft = Offset((size.width - arcDiameter) / 2f, size.height - arcDiameter / 2f - 10f)
            val arcSize = Size(arcDiameter, arcDiameter)
            val stroke = Stroke(width = 24f, cap = StrokeCap.Butt)

            // 3 gauge zones: Comfortable (0..40% -> 180..252), Stretch (40..52% -> 252..300), Risky (52..100% -> 300..360)
            drawArc(SavingsGreen, 180f, 72f, false, topLeft, arcSize, style = stroke)
            drawArc(InterestAmber, 254f, 48f, false, topLeft, arcSize, style = stroke)
            drawArc(DangerRed, 304f, 56f, false, topLeft, arcSize, style = stroke)

            val clamped = ratioPercent.coerceIn(0.0, 80.0)
            val needleAngleDeg = 180.0 + (clamped / 80.0) * 180.0
            val rad = Math.toRadians(needleAngleDeg)
            val center = Offset(size.width / 2f, topLeft.y + arcDiameter / 2f)
            val needleLen = arcDiameter * 0.38f
            val tip = Offset(
                (center.x + needleLen * cos(rad)).toFloat(),
                (center.y + needleLen * sin(rad)).toFloat()
            )
            drawLine(color = zoneColor, start = center, end = tip, strokeWidth = 7f, cap = StrokeCap.Round)
            drawCircle(color = zoneColor, radius = 10f, center = center)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                stringResource(R.string.eligibility_zone_comfortable),
                style = MaterialTheme.typography.labelSmall,
                color = SavingsGreen,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.eligibility_zone_stretch),
                style = MaterialTheme.typography.labelSmall,
                color = InterestAmber,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.eligibility_zone_risky),
                style = MaterialTheme.typography.labelSmall,
                color = DangerRed,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
