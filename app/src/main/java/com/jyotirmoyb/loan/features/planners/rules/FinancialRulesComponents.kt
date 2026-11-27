package com.jyotirmoyb.loan.features.planners.rules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.features.deposits.fd.KeyValueRow
import com.jyotirmoyb.loan.features.emi.AmountField
import com.jyotirmoyb.loan.features.retirement.planner.ChartLegendDot
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.ResetToDefaultAction
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.ValueSourceBadgesRow
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun InsuranceCoverSection(
    uiState: FinancialRulesUiState,
    currency: CurrencyCode,
    onUpdateAnnualIncome: (Double) -> Unit,
    onUpdateExistingLiabilities: (Double) -> Unit,
    onUpdateExistingTermCover: (Double) -> Unit,
    onUpdateTermMultiplier: (Double) -> Unit,
    onResetTermMultiplier: () -> Unit,
    onSetMetroCity: (Boolean) -> Unit,
    onUpdateFamilyMembers: (Int) -> Unit,
    onUpdateExistingHealthCover: (Double) -> Unit
) {
    val res = uiState.insuranceResult

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FintechCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionAccentHeader(title = "Insurance Cover Rules", badge = "${res.termMultiplierApplied.toInt()}x + Loans")
                ResetToDefaultAction(
                    visible = uiState.hasCustomTermMultiplier,
                    onClick = onResetTermMultiplier,
                    testTag = "rules_ins_reset_btn"
                )
            }
            uiState.termConfigItem?.let {
                Spacer(Modifier.height(4.dp))
                ValueSourceBadgesRow(item = it)
            }
            Spacer(Modifier.height(12.dp))

            CoverComparisonBar(
                title = "Term Life Cover",
                recommended = res.recommendedTermCover,
                existing = res.existingTermCover,
                gap = res.termCoverGap,
                currency = currency,
                barColor = InfoBlue,
                testTag = "rules_term_bar"
            )
            Spacer(Modifier.height(14.dp))
            CoverComparisonBar(
                title = "Family Health Cover",
                recommended = res.recommendedHealthCover,
                existing = res.existingHealthCover,
                gap = res.healthCoverGap,
                currency = currency,
                barColor = PrincipalEmerald,
                testTag = "rules_health_bar"
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                text = res.healthRuleNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FintechCard {
            SectionAccentHeader(title = "Income, Loans & Family", badge = "Rule of Thumb")
            Spacer(Modifier.height(10.dp))
            AmountField(
                amount = uiState.annualIncome,
                currency = currency,
                onAmountChange = onUpdateAnnualIncome,
                label = stringResource(R.string.rules_annual_income),
                modifier = Modifier.testTag("rules_ins_income_input")
            )
            Spacer(Modifier.height(10.dp))
            AmountField(
                amount = uiState.existingLiabilities,
                currency = currency,
                onAmountChange = onUpdateExistingLiabilities,
                label = stringResource(R.string.rules_liabilities),
                modifier = Modifier.testTag("rules_ins_liab_input")
            )
            Spacer(Modifier.height(10.dp))
            AmountField(
                amount = uiState.existingTermCover,
                currency = currency,
                onAmountChange = onUpdateExistingTermCover,
                label = stringResource(R.string.rules_existing_term),
                modifier = Modifier.testTag("rules_ins_term_input")
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.ui_txt_term_cover_multiplier),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(R.string.ui_txt_x_annual_income, uiState.termIncomeMultiplier.toInt()),
                    style = MonoSmallStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = uiState.termIncomeMultiplier.toFloat(),
                onValueChange = { onUpdateTermMultiplier(it.roundToInt().toDouble()) },
                valueRange = 8f..20f,
                steps = 11,
                modifier = Modifier.testTag("rules_ins_mult_slider")
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.ui_txt_metro_tier_1_city),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (uiState.isMetroCity) "Higher hospitalisation cost base" else "Non-metro base cover",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.isMetroCity,
                    onCheckedChange = onSetMetroCity,
                    modifier = Modifier.testTag("rules_ins_metro_switch")
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.ui_txt_family_members_covered),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(R.string.ui_txt_member_s, uiState.familyMembersCount),
                    style = MonoSmallStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = uiState.familyMembersCount.toFloat(),
                onValueChange = { onUpdateFamilyMembers(it.roundToInt()) },
                valueRange = 1f..8f,
                steps = 6,
                modifier = Modifier.testTag("rules_ins_family_slider")
            )
            AmountField(
                amount = uiState.existingHealthCover,
                currency = currency,
                onAmountChange = onUpdateExistingHealthCover,
                label = stringResource(R.string.rules_existing_health),
                modifier = Modifier.testTag("rules_ins_health_input")
            )
        }
    }
}

@Composable
internal fun CoverComparisonBar(
    title: String,
    recommended: Double,
    existing: Double,
    gap: Double,
    currency: CurrencyCode,
    barColor: Color,
    testTag: String
) {
    val maxVal = max(1.0, max(recommended, existing))
    val recFrac = (recommended / maxVal).toFloat().coerceIn(0.05f, 1f)
    val existFrac = (existing / maxVal).toFloat().coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = if (gap <= 0.0) "Adequate" else "Gap: ${CurrencyFormatter.formatCompact(gap, currency)}",
                style = MonoSmallStyle,
                color = if (gap <= 0.0) SavingsGreen else InterestAmber,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        KeyValueRow("Recommended Rule-of-Thumb", CurrencyFormatter.format(recommended, currency), barColor)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(recFrac)
                    .height(8.dp)
                    .background(barColor.copy(alpha = 0.45f))
            )
        }
        Spacer(Modifier.height(6.dp))
        KeyValueRow("Your Existing Cover", CurrencyFormatter.format(existing, currency))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(existFrac)
                    .height(8.dp)
                    .background(if (gap <= 0.0) SavingsGreen else InterestAmber)
            )
        }
    }
}

@Composable
internal fun SplitSliderRow(label: String, percent: Int, color: Color, onPercentChange: (Int) -> Unit, testTag: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChartLegendDot(color = color, label = label)
            Text(stringResource(R.string.ui_txt_fmt_361cec, percent), style = MonoSmallStyle, fontWeight = FontWeight.Bold, color = color)
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = { onPercentChange(it.roundToInt()) },
            valueRange = 0f..90f,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
internal fun BucketLegendRow(color: Color, title: String, monthly: String, annual: String) {
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
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(monthly, style = MonoSmallStyle, fontWeight = FontWeight.Bold)
            Text(annual, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DonutSplitChart(
    segments: List<Pair<Color, Float>>,
    centerTitle: String,
    centerSubtitle: String,
    modifier: Modifier = Modifier
) {
    val total = segments.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 18.dp.toPx()
            val inset = strokePx / 2f + 2.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx)
            )

            var startAngle = -90f
            segments.forEach { (color, value) ->
                if (value > 0f) {
                    val sweep = (value / total) * 360f
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = centerSubtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
