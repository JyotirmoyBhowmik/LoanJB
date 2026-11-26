package com.jyotirmoyb.loan.features.mutualfund

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.MfYearRow
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SavingsGreen

/**
 * Mandatory Mutual Fund market-risk note shown on every MF calculator screen.
 */
@Composable
fun MutualFundRiskNoticeBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mf_market_risk_banner"),
        shape = RoundedCornerShape(14.dp),
        color = InterestAmber.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, InterestAmber.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = InterestAmber,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.mf_market_risk_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Responsive Canvas growth chart plotting Total Invested vs Projected Corpus vs Inflation-Adjusted Value.
 */
@Composable
fun MfWealthGrowthChartCard(
    rows: List<MfYearRow>,
    currency: CurrencyCode,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty()) return
    val maxVal = rows.maxOf { it.closingValue }.coerceAtLeast(1.0)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    FintechCard(modifier = modifier.testTag("mf_wealth_growth_chart")) {
        SectionAccentHeader(
            title = stringResource(R.string.mf_growth_chart_title),
            badge = "${rows.size} yrs",
            icon = Icons.Outlined.ShowChart
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChartLegendDot(color = InfoBlue, label = stringResource(R.string.mf_total_invested))
            ChartLegendDot(color = SavingsGreen, label = "Corpus")
            ChartLegendDot(color = InterestAmber, label = "Real (Infl.-Adj.)")
        }

        Spacer(Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            val w = size.width
            val h = size.height
            val padY = 12f
            val plotH = (h - padY * 2f).coerceAtLeast(10f)

            // Horizontal reference lines
            for (i in 0..3) {
                val y = padY + plotH * (i / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            val count = rows.size
            val barGroupW = w / count.coerceAtLeast(1)
            val barW = (barGroupW * 0.56f).coerceIn(4f, 26f)

            val realPath = Path()
            rows.forEachIndexed { idx, row ->
                val cx = barGroupW * idx + barGroupW / 2f
                val totalH = ((row.closingValue / maxVal) * plotH).toFloat().coerceIn(2f, plotH)
                val investedH = ((row.totalInvestedToDate / maxVal) * plotH).toFloat().coerceIn(2f, totalH)

                // Full corpus bar (green returns top)
                drawRoundRect(
                    color = SavingsGreen,
                    topLeft = Offset(cx - barW / 2f, padY + plotH - totalH),
                    size = Size(barW, totalH),
                    cornerRadius = CornerRadius(5f, 5f)
                )
                // Invested base bar (blue)
                drawRoundRect(
                    color = InfoBlue,
                    topLeft = Offset(cx - barW / 2f, padY + plotH - investedH),
                    size = Size(barW, investedH),
                    cornerRadius = CornerRadius(4f, 4f)
                )

                val realY = padY + plotH - ((row.inflationAdjustedValue / maxVal) * plotH).toFloat().coerceIn(0f, plotH)
                if (idx == 0) realPath.moveTo(cx, realY) else realPath.lineTo(cx, realY)
            }

            if (rows.size > 1) {
                drawPath(
                    path = realPath,
                    color = InterestAmber,
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Yr 1 (${rows.first().fyLabel})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Peak ${CurrencyFormatter.formatCompact(maxVal, currency)}",
                style = MonoSmallStyle,
                color = SageGreen,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Yr ${rows.size} (${rows.last().fyLabel})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MfYearWiseTableCard(
    rows: List<MfYearRow>,
    currency: CurrencyCode,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    if (rows.isEmpty()) return
    FintechCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .testTag("mf_yearwise_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionAccentHeader(
                title = stringResource(R.string.mf_yearwise_title),
                badge = "${rows.size} yrs",
                icon = Icons.Outlined.CalendarToday,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("Yr", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.75f))
                    Text("Invested", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                    Text("Corpus", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                    Text("Real Value", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                }
                Spacer(Modifier.height(4.dp))
                rows.forEachIndexed { idx, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Y${row.yearIndex}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(0.75f)
                        )
                        Text(
                            text = CurrencyFormatter.formatCompact(row.totalInvestedToDate, currency),
                            style = MonoSmallStyle,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = CurrencyFormatter.formatCompact(row.closingValue, currency),
                            style = MonoSmallStyle,
                            color = SavingsGreen,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = CurrencyFormatter.formatCompact(row.inflationAdjustedValue, currency),
                            style = MonoSmallStyle,
                            color = InterestAmber,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                    if (idx < rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
