package com.jyotirmoyb.loan.features.loans.comparison

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.SavedLoanEntity
import com.jyotirmoyb.loan.domain.ComparisonScenarioEvaluated
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.LoanCategory
import com.jyotirmoyb.loan.domain.LoanComparisonReport
import com.jyotirmoyb.loan.domain.LoanVariant
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.PurpleAccent
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SavingsGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy

private val SCENARIO_LINE_COLORS = listOf(SageGreen, InfoBlue, InterestAmber)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanComparisonScreen(
    uiState: LoanComparisonUiState,
    onToggleSelect: (Long) -> Unit,
    onDeleteScenario: (Long) -> Unit,
    onSeedFromSavedLoan: (SavedLoanEntity) -> Unit,
    onSeedFromCatalogVariant: (LoanCategory, LoanVariant) -> Unit,
    onLoadQuickPresets: () -> Unit,
    onSharePdf: () -> Unit,
    onShareImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSavedSheet by remember { mutableStateOf(false) }
    var showCatalogSheet by remember { mutableStateOf(false) }
    val cur = uiState.currency
    val report = uiState.report

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("loan_comparison_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Seed & Share action bar
        item(key = "seed_actions") {
            FintechCard {
                SectionAccentHeader(
                    title = stringResource(R.string.compare_title),
                    badge = "${report.scenarios.size} / 3"
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showSavedSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("compare_seed_saved_btn")
                    ) {
                        Icon(Icons.Outlined.BookmarkAdded, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.compare_seed_saved), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { showCatalogSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("compare_seed_catalog_btn")
                    ) {
                        Icon(Icons.Outlined.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.compare_seed_catalog), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (report.scenarios.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onShareImage,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("compare_share_image_btn")
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.compare_share_image))
                        }
                        OutlinedButton(
                            onClick = onSharePdf,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("compare_share_pdf_btn")
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.compare_share_pdf))
                        }
                    }
                }
            }
        }

        // Scenario selector chips
        if (uiState.allScenarios.isNotEmpty()) {
            item(key = "scenario_chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.allScenarios, key = { it.id }) { sc ->
                        val selected = sc.id in uiState.selectedIds
                        FilterChip(
                            selected = selected,
                            onClick = { onToggleSelect(sc.id) },
                            label = { Text(sc.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.cd_close),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onDeleteScenario(sc.id) }
                                )
                            },
                            modifier = Modifier.testTag("compare_chip_${sc.id}")
                        )
                    }
                }
            }
        }

        if (report.scenarios.isEmpty()) {
            item(key = "empty_compare") {
                FintechCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.compare_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.compare_empty_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onLoadQuickPresets,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("compare_load_presets_btn")
                        ) {
                            Text("Load 3 Indian Home Loan Offers")
                        }
                    }
                }
            }
        } else {
            // Cheapest Savings Winner Banner
            if (report.maxSavings > 0.0 && report.cheapestId != null) {
                val winner = report.scenarios.firstOrNull { it.isCheapest }
                if (winner != null) {
                    item(key = "winner_banner") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SlateNavy,
                            border = BorderStroke(1.dp, SageGreen.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("compare_winner_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.Star,
                                            contentDescription = null,
                                            tint = Color(0xFF86EFAC),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "${stringResource(R.string.compare_cheapest_badge)} • ${winner.label}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF86EFAC)
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Saves ${CurrencyFormatter.format(report.maxSavings, cur)} vs highest-cost offer",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Side-by-side Comparison Matrix Cards
            item(key = "side_by_side") {
                SideBySideComparisonMatrix(report = report, currency = cur)
            }

            // Grouped Bar Chart (Principal vs Interest vs Fees+GST)
            item(key = "bar_chart") {
                ComparisonCostBarChartCard(report = report, currency = cur)
            }

            // Balance Payoff Line Chart
            item(key = "line_chart") {
                ComparisonBalanceLineChartCard(report = report, currency = cur)
            }
        }

        item(key = "disclaimer") {
            DisclaimerBanner()
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showSavedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSavedSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.compare_select_saved_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (uiState.savedLoans.isEmpty()) {
                    Text(
                        text = stringResource(R.string.compare_no_saved_loans),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    uiState.savedLoans.forEach { saved ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSeedFromSavedLoan(saved)
                                    showSavedSheet = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(saved.nickname, fontWeight = FontWeight.Bold)
                                Text(
                                    "${saved.lender} • ${saved.variantSummary}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCatalogSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCatalogSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.compare_select_catalog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                uiState.catalog.categories.forEach { cat ->
                    val firstVariants = cat.variantGroups.firstOrNull()?.variants.orEmpty().take(3)
                    items(firstVariants, key = { "${cat.id}_${it.id}" }) { variant ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSeedFromCatalogVariant(cat, variant)
                                    showCatalogSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${cat.shortName} — ${variant.name}", fontWeight = FontWeight.Bold)
                                    Text(
                                        variant.bestFor,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${CurrencyFormatter.formatCompact(cat.defaultAmountInr, cur)}",
                                    style = MonoSmallStyle,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideBySideComparisonMatrix(
    report: LoanComparisonReport,
    currency: CurrencyCode
) {
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
                    color = if (s.isCheapest) SavingsGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
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
                            text = "${CurrencyFormatter.formatPercent(s.input.annualRate)} • ${CurrencyFormatter.formatCompact(s.input.principal, currency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()

                        MetricPair(stringResource(R.string.compare_metric_emi), CurrencyFormatter.format(s.result.monthlyEmi, currency, true))
                        MetricPair(stringResource(R.string.compare_metric_interest), CurrencyFormatter.formatCompact(s.result.totalInterest, currency), InterestAmber)
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
private fun MetricPair(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
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
private fun ComparisonCostBarChartCard(
    report: LoanComparisonReport,
    currency: CurrencyCode
) {
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
private fun ComparisonBalanceLineChartCard(
    report: LoanComparisonReport,
    currency: CurrencyCode
) {
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
            Text("Year 0 (${CurrencyFormatter.formatCompact(maxBal, currency)})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Year ${report.maxYears} (₹0)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
