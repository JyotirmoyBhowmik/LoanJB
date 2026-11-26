package com.jyotirmoyb.loan.features.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.BankFdRateItem
import com.jyotirmoyb.loan.domain.MarketHistoryPoint
import com.jyotirmoyb.loan.domain.MarketMetricCardData
import com.jyotirmoyb.loan.domain.MarketSourceType
import com.jyotirmoyb.loan.domain.MarketStalenessEvaluator
import com.jyotirmoyb.loan.ui.theme.DangerRed
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.PurpleAccent
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SoftAmberTile
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun MarketSourceChip(sourceType: MarketSourceType, modifier: Modifier = Modifier) {
    val (bg, fg) = when (sourceType) {
        MarketSourceType.MANUAL -> PurpleAccent.copy(alpha = 0.14f) to PurpleAccent
        MarketSourceType.LIVE_API -> SavingsGreen.copy(alpha = 0.14f) to SavingsGreen
        MarketSourceType.ONLINE -> InfoBlue.copy(alpha = 0.14f) to InfoBlue
        MarketSourceType.BUILT_IN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = sourceType.chipLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun MarketStaleBadge(modifier: Modifier = Modifier) {
    Surface(
        color = SoftAmberTile,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.testTag("market_stale_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = stringResource(R.string.market_stale_badge),
                tint = InterestAmber,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = stringResource(R.string.market_stale_badge),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = InterestAmber
            )
        }
    }
}

@Composable
fun MarketDeltaChip(deltaAbs: Double?, deltaPercent: Double?, unit: String) {
    if (deltaAbs == null || deltaPercent == null) return
    val isUp = deltaAbs > 0.0001
    val isDown = deltaAbs < -0.0001
    val sign = if (isUp) "+" else ""
    val color = when {
        isUp -> SavingsGreen
        isDown -> DangerRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val absFormatted = if (unit.contains("%")) {
        "${BigDecimal.valueOf(deltaAbs).setScale(2, RoundingMode.HALF_UP).toPlainString()}%"
    } else {
        BigDecimal.valueOf(deltaAbs).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
    val pctFormatted = BigDecimal.valueOf(deltaPercent).setScale(2, RoundingMode.HALF_UP).toPlainString()
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "$sign$absFormatted ($sign$pctFormatted%)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun MarketHistoryLineChart(
    points: List<MarketHistoryPoint>,
    unit: String,
    accentColor: Color = TealPrimaryDark,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.market_no_history_yet),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val minVal = points.minOf { it.value }
    val maxVal = points.maxOf { it.value }
    val span = (maxVal - minVal).takeIf { it > 1e-6 } ?: (maxVal.coerceAtLeast(1.0) * 0.05)
    val paddedMin = minVal - span * 0.15
    val paddedMax = maxVal + span * 0.15
    val totalRange = (paddedMax - paddedMin).coerceAtLeast(1e-6)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "High: ${MarketStalenessEvaluator.formatMetricValue(maxVal, unit)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Low: ${MarketStalenessEvaluator.formatMetricValue(minVal, unit)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .testTag("market_history_chart")
        ) {
            val w = size.width
            val h = size.height
            val padX = 18f
            val padY = 16f
            val chartW = (w - padX * 2).coerceAtLeast(1f)
            val chartH = (h - padY * 2).coerceAtLeast(1f)

            // Horizontal reference grid lines
            for (step in 0..2) {
                val y = padY + (chartH * step / 2f)
                drawLine(
                    color = gridColor,
                    start = Offset(padX, y),
                    end = Offset(w - padX, y),
                    strokeWidth = 1.5f
                )
            }

            val coords = points.mapIndexed { idx, pt ->
                val x = if (points.size == 1) {
                    padX + chartW / 2f
                } else {
                    padX + (chartW * idx / (points.size - 1).toFloat())
                }
                val normY = ((pt.value - paddedMin) / totalRange).toFloat().coerceIn(0f, 1f)
                val y = padY + chartH * (1f - normY)
                Offset(x, y)
            }

            if (coords.size >= 2) {
                val fillPath = Path().apply {
                    moveTo(coords.first().x, padY + chartH)
                    coords.forEach { lineTo(it.x, it.y) }
                    lineTo(coords.last().x, padY + chartH)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.25f), accentColor.copy(alpha = 0.02f)),
                        startY = padY,
                        endY = padY + chartH
                    )
                )

                val linePath = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    coords.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = linePath,
                    color = accentColor,
                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            coords.forEach { c ->
                drawCircle(color = Color.White, radius = 8f, center = c)
                drawCircle(color = accentColor, radius = 5.5f, center = c)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = MarketStalenessEvaluator.formatTimestamp(points.first().recordedAtMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (points.size > 1) {
                Text(
                    text = MarketStalenessEvaluator.formatTimestamp(points.last().recordedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketMetricDetailSheet(
    card: MarketMetricCardData,
    history: List<MarketHistoryPoint>,
    staleLimitHours: Int,
    onDismiss: () -> Unit,
    onSaveManual: (String, (String) -> Unit, () -> Unit) -> Unit,
    onResetManual: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var manualInput by remember(card.key, card.value) {
        mutableStateOf(
            card.value?.let {
                BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()
            }.orEmpty()
        )
    }
    var validationError by remember(card.key) { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("market_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = card.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MarketSourceChip(sourceType = card.sourceType)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = MarketStalenessEvaluator.formatMetricValue(card.value, card.unit),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TealPrimaryDark
                )
                MarketDeltaChip(card.deltaAbs, card.deltaPercent, card.unit)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${stringResource(R.string.market_as_of)}: ${MarketStalenessEvaluator.formatTimestamp(card.asOfMillis)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (card.isStale) {
                    MarketStaleBadge()
                }
            }

            Text(
                text = "Source: ${card.sourceName} • Staleness threshold: ${staleLimitHours}h",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "${stringResource(R.string.market_cached_history)} (${history.size} pts)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            MarketHistoryLineChart(
                points = history.takeLast(20),
                unit = card.unit
            )

            if (history.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    history.takeLast(5).reversed().forEach { pt ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = MarketStalenessEvaluator.formatTimestamp(pt.recordedAtMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                MarketSourceChip(pt.sourceType)
                                Text(
                                    text = MarketStalenessEvaluator.formatMetricValue(pt.value, card.unit),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = stringResource(R.string.market_manual_entry),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = manualInput,
                onValueChange = {
                    manualInput = it
                    validationError = null
                },
                label = { Text("Enter value (${card.unit})") },
                isError = validationError != null,
                supportingText = {
                    validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("market_manual_value_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onSaveManual(manualInput, { err -> validationError = err }, onDismiss)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("market_save_manual_btn")
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.save_override))
                }
                if (card.hasManualOverride) {
                    OutlinedButton(
                        onClick = {
                            onResetManual()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("market_reset_manual_btn")
                    ) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.reset_default))
                    }
                }
            }
        }
    }
}

@Composable
fun EditBankFdDialog(
    initial: BankFdRateItem?,
    onDismiss: () -> Unit,
    onSave: (bankId: String?, name: String, general: String, senior: String, tenure: String, onErr: (String) -> Unit, onDone: () -> Unit) -> Unit
) {
    var name by remember(initial?.bankId) { mutableStateOf(initial?.bankName.orEmpty()) }
    var general by remember(initial?.bankId) { mutableStateOf(initial?.generalRate?.toString().orEmpty()) }
    var senior by remember(initial?.bankId) { mutableStateOf(initial?.seniorRate?.toString().orEmpty()) }
    var tenure by remember(initial?.bankId) { mutableStateOf(initial?.tenureLabel ?: "1–3 yrs") }
    var errorMsg by remember(initial?.bankId) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) stringResource(R.string.market_add_bank_fd) else stringResource(R.string.market_edit_bank_fd),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = { Text("Bank Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_fd_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = general,
                        onValueChange = { general = it; errorMsg = null },
                        label = { Text("General %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bank_fd_general_input")
                    )
                    OutlinedTextField(
                        value = senior,
                        onValueChange = { senior = it; errorMsg = null },
                        label = { Text("Senior %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bank_fd_senior_input")
                    )
                }
                OutlinedTextField(
                    value = tenure,
                    onValueChange = { tenure = it; errorMsg = null },
                    label = { Text("Peak Tenure Band (e.g. 400 days / 2–3 yrs)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bank_fd_tenure_input")
                )
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(initial?.bankId, name, general, senior, tenure, { errorMsg = it }, onDismiss)
                },
                modifier = Modifier.testTag("bank_fd_save_btn")
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
