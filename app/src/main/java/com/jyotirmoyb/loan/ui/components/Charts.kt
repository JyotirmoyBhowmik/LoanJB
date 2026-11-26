package com.jyotirmoyb.loan.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.YearlySummary
import com.jyotirmoyb.loan.ui.theme.FeesIndigo
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.viewmodel.CompareItemModel
import kotlin.math.max

@Composable
fun PrincipalInterestDonutChart(
    principal: Double,
    interest: Double,
    fees: Double,
    currency: CurrencyCode,
    modifier: Modifier = Modifier
) {
    val total = max(1.0, principal + interest + fees)
    val pFrac by animateFloatAsState(
        targetValue = (principal / total).toFloat(),
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "principalSweep"
    )
    val iFrac by animateFloatAsState(
        targetValue = (interest / total).toFloat(),
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "interestSweep"
    )
    val fFrac by animateFloatAsState(
        targetValue = (fees / total).toFloat(),
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "feesSweep"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(144.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 22.dp.toPx()
                val pad = strokeWidth / 2f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(pad, pad)

                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )

                var start = -90f
                val pSweep = pFrac * 360f
                if (pSweep > 0.5f) {
                    drawArc(
                        color = PrincipalEmerald,
                        startAngle = start,
                        sweepAngle = pSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    start += pSweep
                }
                val iSweep = iFrac * 360f
                if (iSweep > 0.5f) {
                    drawArc(
                        color = InterestAmber,
                        startAngle = start,
                        sweepAngle = iSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    start += iSweep
                }
                val fSweep = fFrac * 360f
                if (fSweep > 0.5f) {
                    drawArc(
                        color = FeesIndigo,
                        startAngle = start,
                        sweepAngle = fSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total Cost",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatCompact(principal + interest + fees, currency),
                    style = MonoMediumStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DonutLegendItem(
                color = PrincipalEmerald,
                label = "Principal",
                pct = "${String.format("%.1f", (principal / total) * 100)}%",
                amount = CurrencyFormatter.format(principal, currency)
            )
            DonutLegendItem(
                color = InterestAmber,
                label = "Total Interest",
                pct = "${String.format("%.1f", (interest / total) * 100)}%",
                amount = CurrencyFormatter.format(interest, currency)
            )
            if (fees > 0.0) {
                DonutLegendItem(
                    color = FeesIndigo,
                    label = "Fees & Insurance",
                    pct = "${String.format("%.1f", (fees / total) * 100)}%",
                    amount = CurrencyFormatter.format(fees, currency)
                )
            }
        }
    }
}

@Composable
private fun DonutLegendItem(color: Color, label: String, pct: String, amount: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(pct, style = MonoSmallStyle, color = color)
            }
            Text(amount, style = MonoMediumStyle, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun BalanceOverTimeLineChart(
    yearlySummaries: List<YearlySummary>,
    startingBalance: Double,
    currency: CurrencyCode,
    modifier: Modifier = Modifier
) {
    if (yearlySummaries.isEmpty()) return

    val points = buildList {
        add(0 to startingBalance)
        yearlySummaries.forEach { ys -> add(ys.yearIndex to ys.endingBalance) }
    }
    val maxVal = max(1.0, points.maxOf { it.second })
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = PrincipalEmerald
    val fillStart = PrincipalEmerald.copy(alpha = 0.28f)
    val fillEnd = PrincipalEmerald.copy(alpha = 0.02f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Balance Over Time",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Start: ${CurrencyFormatter.formatCompact(startingBalance, currency)} → ${CurrencyFormatter.format(0.0, currency)}",
                style = MonoSmallStyle,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
        ) {
            val leftPad = 8.dp.toPx()
            val rightPad = 8.dp.toPx()
            val topPad = 12.dp.toPx()
            val bottomPad = 14.dp.toPx()
            val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

            // Horizontal dashed-like guide lines
            for (step in 0..4) {
                val y = topPad + (chartH / 4f) * step
                drawLine(
                    color = gridColor,
                    start = Offset(leftPad, y),
                    end = Offset(size.width - rightPad, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (points.size >= 2) {
                val linePath = Path()
                val fillPath = Path()
                val coords = points.mapIndexed { idx, (_, bal) ->
                    val x = leftPad + (idx.toFloat() / (points.size - 1).toFloat()) * chartW
                    val y = topPad + chartH - ((bal / maxVal).toFloat().coerceIn(0f, 1f) * chartH)
                    Offset(x, y)
                }

                linePath.moveTo(coords.first().x, coords.first().y)
                fillPath.moveTo(coords.first().x, topPad + chartH)
                fillPath.lineTo(coords.first().x, coords.first().y)

                for (i in 1 until coords.size) {
                    val prev = coords[i - 1]
                    val curr = coords[i]
                    val midX = (prev.x + curr.x) / 2f
                    linePath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    fillPath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                }

                fillPath.lineTo(coords.last().x, topPad + chartH)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(fillStart, fillEnd),
                        startY = topPad,
                        endY = topPad + chartH
                    )
                )
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw dots on start, midpoint, and zero finish
                val highlightIndices = setOf(0, coords.size / 2, coords.lastIndex)
                highlightIndices.forEach { idx ->
                    val c = coords[idx]
                    drawCircle(color = Color.White, radius = 5.dp.toPx(), center = c)
                    drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = c)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Start", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (yearlySummaries.size >= 2) {
                Text(
                    "Year ${yearlySummaries.size / 2}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Year ${yearlySummaries.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CompareBarChart(
    items: List<CompareItemModel>,
    currency: CurrencyCode,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val maxCost = max(1.0, items.maxOf { it.result.effectiveTotalCost })
    val cheapestCost = items.minOf { it.result.effectiveTotalCost }
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendChip(PrincipalEmerald, "Principal")
            LegendChip(InterestAmber, "Interest")
            LegendChip(FeesIndigo, "Fees")
        }
        Spacer(Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(185.dp)
        ) {
            val topPad = 10.dp.toPx()
            val bottomPad = 10.dp.toPx()
            val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

            for (i in 0..3) {
                val y = topPad + (chartH / 3f) * i
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }

            val slotW = size.width / items.size
            val barW = (slotW * 0.48f).coerceAtMost(56.dp.toPx())

            items.forEachIndexed { idx, item ->
                val centerX = slotW * idx + slotW / 2f
                val left = centerX - barW / 2f

                val pH = ((item.result.totalPrincipal / maxCost).toFloat() * chartH).coerceAtLeast(2f)
                val iH = ((item.result.totalInterest / maxCost).toFloat() * chartH).coerceAtLeast(0f)
                val fH = ((item.result.totalFees / maxCost).toFloat() * chartH).coerceAtLeast(0f)

                var currentBottom = topPad + chartH

                // Principal (bottom segment)
                drawRoundRect(
                    color = PrincipalEmerald,
                    topLeft = Offset(left, currentBottom - pH),
                    size = Size(barW, pH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                currentBottom -= pH

                // Interest (middle segment)
                if (iH > 0.5f) {
                    drawRect(
                        color = InterestAmber,
                        topLeft = Offset(left, currentBottom - iH),
                        size = Size(barW, iH)
                    )
                    currentBottom -= iH
                }

                // Fees (top segment)
                if (fH > 0.5f) {
                    drawRoundRect(
                        color = FeesIndigo,
                        topLeft = Offset(left, currentBottom - fH),
                        size = Size(barW, fH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            items.forEach { item ->
                val isCheapest = item.result.effectiveTotalCost <= cheapestCost + 0.01
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCheapest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = CurrencyFormatter.formatCompact(item.result.effectiveTotalCost, currency),
                        style = MonoSmallStyle,
                        color = if (isCheapest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
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
