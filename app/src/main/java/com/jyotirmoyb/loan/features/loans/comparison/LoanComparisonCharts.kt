package com.jyotirmoyb.loan.features.loans.comparison

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.LoanComparisonReport
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.PurpleAccent
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SavingsGreen

internal val SCENARIO_LINE_COLORS = listOf(SageGreen, InfoBlue, InterestAmber)

@Composable
internal fun SideBySideComparisonMatrix(report: LoanComparisonReport, currency: CurrencyCode) {
    val scenarios = report.scenarios
    FintechCard(modifier = Modifier.testTag("compare_matrix_card")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            scenarios.forEachIndexed { idx, s ->
                val accent = SCENARIO_LINE_COLORS[idx % SCENARIO_LINE_COLORS.size]
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (s.isCheapest) {
                        SavingsGreen.copy(
                            alpha = 0.08f
                        )
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    border = BorderStroke(
                        width = if (s.isCheapest) 2.dp else 1.dp,
                        color = if (s.isCheapest) SavingsGreen else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .width(172.dp)
                        .testTag("compare_card_${s.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(accent, CircleShape)
                            )
                            if (s.isCheapest) {
                                Surface(
                                    color = SavingsGreen,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.compare_cheapest_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = s.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                R.string.ui_txt_fmt_4ed101,
                                CurrencyFormatter.formatPercent(s.input.annualRate),
                                CurrencyFormatter.formatCompact(s.input.principal, currency)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()

                        MetricPair(
                            stringResource(R.string.compare_metric_emi),
                            CurrencyFormatter.format(s.result.monthlyEmi, currency, true)
                        )
                        MetricPair(
                            stringResource(R.string.compare_metric_interest),
                            CurrencyFormatter.formatCompact(s.result.totalInterest, currency),
                            InterestAmber
                        )
                        MetricPair(stringResource(R.string.compare_metric_fees_gst), CurrencyFormatter.format(s.result.totalFees, currency))
                        MetricPair(
                            stringResource(R.string.compare_metric_total_cost),
                            CurrencyFormatter.formatCompact(s.result.effectiveTotalCost, currency),
                            if (s.isCheapest) SavingsGreen else MaterialTheme.colorScheme.onSurface
                        )
                        MetricPair(
                            stringResource(R.string.compare_metric_tenure),
                            "${s.result.actualTenureMonths} mos (${s.result.actualTenureMonths / 12}y ${s.result.actualTenureMonths % 12}m)"
                        )
                        MetricPair(
                            stringResource(R.string.compare_metric_loan_free),
                            s.result.loanFreeDateLabel.ifBlank { "-" }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPair(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoSmallStyle,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
internal fun ComparisonCostBarChartCard(report: LoanComparisonReport, currency: CurrencyCode) {
    val scenarios = report.scenarios
    val maxCost = scenarios.maxOfOrNull { it.result.effectiveTotalCost }?.coerceAtLeast(1.0) ?: 1.0

    FintechCard(modifier = Modifier.testTag("compare_bar_chart")) {
        SectionAccentHeader(title = stringResource(R.string.compare_bar_chart_title))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(PrincipalEmerald, "Principal")
            LegendDot(InterestAmber, "Interest")
            LegendDot(PurpleAccent, "Fees + GST")
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            scenarios.forEach { s ->
                val pFrac = (s.result.totalPrincipal / maxCost).toFloat().coerceIn(0f, 1f)
                val iFrac = (s.result.totalInterest / maxCost).toFloat().coerceIn(0f, 1f)
                val fFrac = (s.result.totalFees / maxCost).toFloat().coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = s.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = CurrencyFormatter.formatCompact(s.result.effectiveTotalCost, currency),
                            style = MonoSmallStyle,
                            fontWeight = FontWeight.Bold,
                            color = if (s.isCheapest) SavingsGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        drawRoundRect(
                            color = Color.LightGray.copy(alpha = 0.25f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        val pW = w * pFrac
                        val iW = w * iFrac
                        val fW = w * fFrac
                        if (pW > 0f) {
                            drawRoundRect(
                                color = PrincipalEmerald,
                                topLeft = Offset(0f, 0f),
                                size = Size(pW, h),
                                cornerRadius = CornerRadius(8f, 8f)
                            )
                        }
                        if (iW > 0f) {
                            drawRect(
                                color = InterestAmber,
                                topLeft = Offset(pW, 0f),
                                size = Size(iW, h)
                            )
                        }
                        if (fW > 0f) {
                            drawRoundRect(
                                color = PurpleAccent,
                                topLeft = Offset(pW + iW, 0f),
                                size = Size(fW.coerceAtLeast(4f), h),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ComparisonBalanceLineChartCard(report: LoanComparisonReport, currency: CurrencyCode) {
    val scenarios = report.scenarios
    val maxBal = scenarios.flatMap { it.yearlyBalances }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val maxLen = scenarios.maxOfOrNull { it.yearlyBalances.size }?.coerceAtLeast(2) ?: 2

    FintechCard(modifier = Modifier.testTag("compare_line_chart")) {
        SectionAccentHeader(title = stringResource(R.string.compare_line_chart_title))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            scenarios.forEachIndexed { idx, s ->
                LegendDot(SCENARIO_LINE_COLORS[idx % SCENARIO_LINE_COLORS.size], s.label)
            }
        }
        Spacer(Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            val padL = 12f
            val padR = 12f
            val padT = 12f
            val padB = 16f
            val chartW = size.width - padL - padR
            val chartH = size.height - padT - padB

            // Horizontal gridlines
            for (g in 0..3) {
                val y = padT + chartH * (g / 3f)
                drawLine(
                    color = Color.Gray.copy(alpha = 0.2f),
                    start = Offset(padL, y),
                    end = Offset(size.width - padR, y),
                    strokeWidth = 1.5f
                )
            }

            scenarios.forEachIndexed { idx, s ->
                val pts = s.yearlyBalances
                if (pts.size >= 2) {
                    val path = Path()
                    pts.forEachIndexed { pIdx, bal ->
                        val x = padL + (pIdx.toFloat() / (maxLen - 1).coerceAtLeast(1)) * chartW
                        val y = padT + (1f - (bal / maxBal).toFloat().coerceIn(0f, 1f)) * chartH
                        if (pIdx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = SCENARIO_LINE_COLORS[idx % SCENARIO_LINE_COLORS.size],
                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.ui_txt_year_0, CurrencyFormatter.formatCompact(maxBal, currency)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.ui_txt_year_0_da5296, report.maxYears),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(9.dp).background(color, CircleShape))
        Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
