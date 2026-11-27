package com.jyotirmoyb.loan.features.retirement.planner

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.ShowChart
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.RetirementProjectionPoint
import com.jyotirmoyb.loan.features.deposits.fd.HeroMetricItem
import com.jyotirmoyb.loan.features.deposits.fd.KeyValueRow
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
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun RetirementPlannerScreen(
    uiState: RetirementPlannerUiState,
    onUpdateCurrentAge: (Int) -> Unit,
    onUpdateRetirementAge: (Int) -> Unit,
    onUpdateLifeExpectancy: (Int) -> Unit,
    onUpdateMonthlyExpenses: (Double) -> Unit,
    onUpdateExistingSavings: (Double) -> Unit,
    onUpdateCurrentSip: (Double) -> Unit,
    onUpdateInflation: (Double) -> Unit,
    onUpdatePreReturn: (Double) -> Unit,
    onUpdatePostReturn: (Double) -> Unit,
    onResetRates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val input = uiState.input
    val result = uiState.result
    val currency = uiState.currency

    var infText by remember { mutableStateOf(formatRate(input.inflationPercent)) }
    var preText by remember { mutableStateOf(formatRate(input.preRetirementReturnPercent)) }
    var postText by remember { mutableStateOf(formatRate(input.postRetirementReturnPercent)) }

    LaunchedEffect(input.inflationPercent) {
        if (infText.toDoubleOrNull()?.let { abs(it - input.inflationPercent) > 0.01 } != false) {
            infText = formatRate(input.inflationPercent)
        }
    }
    LaunchedEffect(input.preRetirementReturnPercent) {
        if (preText.toDoubleOrNull()?.let { abs(it - input.preRetirementReturnPercent) > 0.01 } != false) {
            preText = formatRate(input.preRetirementReturnPercent)
        }
    }
    LaunchedEffect(input.postRetirementReturnPercent) {
        if (postText.toDoubleOrNull()?.let { abs(it - input.postRetirementReturnPercent) > 0.01 } != false) {
            postText = formatRate(input.postRetirementReturnPercent)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("retirement_planner_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Corpus & Monthly SIP Needed Banner
        item(key = "ret_hero") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SlateNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("retirement_hero_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ret_corpus_needed_hero),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = CurrencyFormatter.formatCompact(result.requiredCorpusAtRetirement, currency),
                        style = MonoLargeStyle,
                        color = PrincipalEmerald
                    )
                    val sipSummaryText = if (result.additionalMonthlySipNeeded > 0.0) {
                        stringResource(
                            R.string.ui_ret_monthly_sip_extra,
                            CurrencyFormatter.format(result.monthlySipNeededTotal, currency),
                            CurrencyFormatter.format(result.additionalMonthlySipNeeded, currency)
                        )
                    } else {
                        stringResource(
                            R.string.ui_ret_monthly_sip_on_track,
                            CurrencyFormatter.format(result.monthlySipNeededTotal, currency)
                        )
                    }
                    Text(
                        text = sipSummaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HeroMetricItem(
                            label = "Projected",
                            value = CurrencyFormatter.formatCompact(result.projectedTotalCorpus, currency),
                            valueColor = Color.White
                        )
                        HeroMetricItem(
                            label = if (result.corpusShortfall > 0.0) "Shortfall" else "Surplus",
                            value = CurrencyFormatter.formatCompact(
                                if (result.corpusShortfall > 0.0) result.corpusShortfall else result.corpusSurplus,
                                currency
                            ),
                            valueColor = if (result.corpusShortfall > 0.0) InterestAmber else PrincipalEmerald
                        )
                        HeroMetricItem(
                            label = "Funded",
                            value = "${result.fundingRatioPercent.roundToInt()}%",
                            valueColor = if (result.fundingRatioPercent >= 100.0) PrincipalEmerald else InterestAmber
                        )
                    }
                }
            }
        }

        // 2. Shortfall & Corpus Growth Trajectory Chart
        if (result.isValid && result.projectionPoints.size >= 2) {
            item(key = "ret_shortfall_chart") {
                FintechCard(modifier = Modifier.testTag("retirement_shortfall_chart_card")) {
                    SectionAccentHeader(
                        title = stringResource(R.string.ret_shortfall_chart_title),
                        badge = "${result.yearsToRetirement} yrs",
                        icon = Icons.Outlined.ShowChart
                    )
                    Spacer(Modifier.height(10.dp))
                    RetirementShortfallChart(
                        points = result.projectionPoints,
                        currency = currency
                    )
                    Spacer(Modifier.height(10.dp))
                    KeyValueRow(
                        label = "Monthly Expense at Age ${input.retirementAge}",
                        value = CurrencyFormatter.format(result.monthlyExpenseAtRetirement, currency),
                        valueColor = InterestAmber
                    )
                    KeyValueRow(
                        label = "Real Post-Retirement Return",
                        value = "${result.realPostRetirementReturnPercent}% p.a.",
                        valueColor = InfoBlue
                    )
                    KeyValueRow(
                        label = "Retirement Duration",
                        value = "${result.retirementDurationYears} years (Age ${input.retirementAge}–${input.lifeExpectancy})"
                    )
                }
            }
        }

        // 3. Age Horizon Sliders
        item(key = "ret_ages_card") {
            FintechCard {
                SectionAccentHeader(
                    title = "Life Horizon",
                    badge = "Age ${input.currentAge} → ${input.retirementAge} → ${input.lifeExpectancy}",
                    icon = Icons.Outlined.BeachAccess
                )
                Spacer(Modifier.height(10.dp))

                AgeSliderRow(
                    label = stringResource(R.string.ret_current_age),
                    value = input.currentAge,
                    range = 18f..65f,
                    onValueChange = onUpdateCurrentAge,
                    testTag = "ret_current_age_slider"
                )
                AgeSliderRow(
                    label = stringResource(R.string.ret_retire_age),
                    value = input.retirementAge,
                    range = (input.currentAge + 1).toFloat()..75f,
                    onValueChange = onUpdateRetirementAge,
                    testTag = "ret_retire_age_slider"
                )
                AgeSliderRow(
                    label = stringResource(R.string.ret_life_exp),
                    value = input.lifeExpectancy,
                    range = (input.retirementAge + 5).coerceAtMost(99).toFloat()..100f,
                    onValueChange = onUpdateLifeExpectancy,
                    testTag = "ret_life_exp_slider"
                )
            }
        }

        // 4. Expenses, Savings & Return Assumptions
        item(key = "ret_financial_inputs") {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionAccentHeader(
                        title = "Expenses & Return Assumptions",
                        badge = "Data-Driven",
                        modifier = Modifier.weight(1f)
                    )
                    ResetToDefaultAction(
                        visible = uiState.hasCustomInflation || uiState.hasCustomPreReturn || uiState.hasCustomPostReturn,
                        onClick = onResetRates,
                        testTag = "ret_reset_rates_btn"
                    )
                }
                uiState.inflationItem?.let {
                    Spacer(Modifier.height(6.dp))
                    ValueSourceBadgesRow(item = it)
                }
                Spacer(Modifier.height(10.dp))

                AmountField(
                    label = stringResource(R.string.ret_monthly_expenses),
                    amount = input.currentMonthlyExpenses,
                    currency = currency,
                    onAmountChange = onUpdateMonthlyExpenses,
                    modifier = Modifier.testTag("ret_expenses_input")
                )
                Spacer(Modifier.height(10.dp))
                AmountField(
                    label = stringResource(R.string.ret_existing_savings),
                    amount = input.existingRetirementSavings,
                    currency = currency,
                    onAmountChange = onUpdateExistingSavings,
                    modifier = Modifier.testTag("ret_existing_savings_input")
                )
                Spacer(Modifier.height(10.dp))
                AmountField(
                    label = stringResource(R.string.ret_current_sip),
                    amount = input.currentMonthlySip,
                    currency = currency,
                    onAmountChange = onUpdateCurrentSip,
                    modifier = Modifier.testTag("ret_current_sip_input")
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumericInputField(
                        label = stringResource(R.string.ret_inflation_label),
                        valueText = infText,
                        onValueChange = { txt ->
                            infText = txt
                            txt.toDoubleOrNull()?.let(onUpdateInflation)
                        },
                        trailingBadge = "%",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ret_inflation_input")
                    )
                    NumericInputField(
                        label = stringResource(R.string.ret_pre_return_label),
                        valueText = preText,
                        onValueChange = { txt ->
                            preText = txt
                            txt.toDoubleOrNull()?.let(onUpdatePreReturn)
                        },
                        trailingBadge = "%",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ret_pre_return_input")
                    )
                }
                Spacer(Modifier.height(8.dp))
                NumericInputField(
                    label = stringResource(R.string.ret_post_return_label),
                    valueText = postText,
                    onValueChange = { txt ->
                        postText = txt
                        txt.toDoubleOrNull()?.let(onUpdatePostReturn)
                    },
                    trailingBadge = "%",
                    modifier = Modifier.testTag("ret_post_return_input")
                )
            }
        }

        // 5. Breakdown Summary Card
        item(key = "ret_breakdown") {
            FintechCard {
                SectionAccentHeader(title = "Corpus Breakdown", badge = "At Age ${input.retirementAge}")
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Projected from Existing Savings", CurrencyFormatter.format(result.projectedCorpusFromExisting, currency))
                KeyValueRow("Projected from Current SIP", CurrencyFormatter.format(result.projectedCorpusFromCurrentSip, currency))
                KeyValueRow(
                    "Total Projected Corpus",
                    CurrencyFormatter.format(result.projectedTotalCorpus, currency),
                    valueColor = SavingsGreen
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                KeyValueRow(
                    "Required Target Corpus",
                    CurrencyFormatter.format(result.requiredCorpusAtRetirement, currency),
                    valueColor = InfoBlue
                )
                if (result.corpusShortfall > 0.0) {
                    KeyValueRow(
                        "Projected Shortfall",
                        CurrencyFormatter.format(result.corpusShortfall, currency),
                        valueColor = InterestAmber
                    )
                    KeyValueRow(
                        "Additional Monthly SIP Needed",
                        CurrencyFormatter.format(result.additionalMonthlySipNeeded, currency),
                        valueColor = InterestAmber
                    )
                } else {
                    KeyValueRow(
                        "Projected Surplus",
                        CurrencyFormatter.format(result.corpusSurplus, currency),
                        valueColor = PrincipalEmerald
                    )
                }
            }
        }

        item(key = "ret_disclaimer") {
            DisclaimerBanner()
        }
    }
}

@Composable
private fun RetirementShortfallChart(points: List<RetirementProjectionPoint>, currency: CurrencyCode) {
    val maxVal = max(1.0, points.maxOf { max(it.projectedCorpus, it.targetTrajectoryCorpus) })
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val targetColor = InfoBlue
    val projectedColor = PrincipalEmerald
    val shortfallFill = InterestAmber.copy(alpha = 0.24f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChartLegendDot(targetColor, "Target Path")
            ChartLegendDot(projectedColor, "Your Projected")
            ChartLegendDot(InterestAmber, "Shortfall Gap")
        }
        Spacer(Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .testTag("retirement_shortfall_canvas")
        ) {
            val leftPad = 8.dp.toPx()
            val rightPad = 8.dp.toPx()
            val topPad = 12.dp.toPx()
            val bottomPad = 14.dp.toPx()
            val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

            for (i in 0..3) {
                val y = topPad + (chartH / 3f) * i
                drawLine(gridColor, Offset(leftPad, y), Offset(size.width - rightPad, y), 1.dp.toPx())
            }

            val n = points.size
            val targetCoords = points.mapIndexed { idx, pt ->
                val x = leftPad + (idx.toFloat() / (n - 1).toFloat()) * chartW
                val y = topPad + chartH - ((pt.targetTrajectoryCorpus / maxVal).toFloat().coerceIn(0f, 1f) * chartH)
                Offset(x, y)
            }
            val projCoords = points.mapIndexed { idx, pt ->
                val x = leftPad + (idx.toFloat() / (n - 1).toFloat()) * chartW
                val y = topPad + chartH - ((pt.projectedCorpus / maxVal).toFloat().coerceIn(0f, 1f) * chartH)
                Offset(x, y)
            }

            // Shaded shortfall area between Target Path and Projected Path
            val gapPath = Path().apply {
                moveTo(targetCoords.first().x, targetCoords.first().y)
                for (i in 1 until n) lineTo(targetCoords[i].x, targetCoords[i].y)
                for (i in (n - 1) downTo 0) lineTo(projCoords[i].x, projCoords[i].y)
                close()
            }
            drawPath(gapPath, color = shortfallFill)

            // Gradient under projected line
            val projFill = Path().apply {
                moveTo(projCoords.first().x, topPad + chartH)
                projCoords.forEach { lineTo(it.x, it.y) }
                lineTo(projCoords.last().x, topPad + chartH)
                close()
            }
            drawPath(
                path = projFill,
                brush = Brush.verticalGradient(
                    listOf(projectedColor.copy(alpha = 0.24f), projectedColor.copy(alpha = 0.02f)),
                    startY = topPad,
                    endY = topPad + chartH
                )
            )

            // Target Path (dashed)
            val targetPath = Path().apply {
                moveTo(targetCoords.first().x, targetCoords.first().y)
                for (i in 1 until n) lineTo(targetCoords[i].x, targetCoords[i].y)
            }
            drawPath(
                path = targetPath,
                color = targetColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            )

            // Projected Path (solid)
            val projPath = Path().apply {
                moveTo(projCoords.first().x, projCoords.first().y)
                for (i in 1 until n) lineTo(projCoords[i].x, projCoords[i].y)
            }
            drawPath(
                path = projPath,
                color = projectedColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Terminal markers
            drawCircle(Color.White, 5.dp.toPx(), targetCoords.last())
            drawCircle(targetColor, 3.5.dp.toPx(), targetCoords.last())
            drawCircle(Color.White, 5.dp.toPx(), projCoords.last())
            drawCircle(projectedColor, 3.5.dp.toPx(), projCoords.last())
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.ui_txt_age_904bce, points.first().age),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.ui_txt_target, CurrencyFormatter.formatCompact(points.last().targetTrajectoryCorpus, currency)),
                style = MonoSmallStyle,
                color = targetColor
            )
            Text(
                stringResource(R.string.ui_txt_age_904bce, points.last().age),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun AgeSliderRow(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Int) -> Unit,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                stringResource(R.string.ui_txt_yrs_4c2f7f, value),
                style = MonoSmallStyle,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.toFloat().coerceIn(range.start, range.endInclusive),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
internal fun ChartLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatRate(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.2f", v).trimEnd('0').trimEnd('.')
