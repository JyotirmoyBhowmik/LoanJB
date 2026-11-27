package com.jyotirmoyb.loan.features.loans.comparison

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.data.SavedLoanEntity
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.LoanCategory
import com.jyotirmoyb.loan.domain.LoanVariant
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SlateNavy

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
                            Text(stringResource(R.string.ui_txt_load_3_indian_home_loan_offers))
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
                                            text = stringResource(
                                                R.string.ui_txt_fmt_4ed101,
                                                stringResource(R.string.compare_cheapest_badge),
                                                winner.label
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF86EFAC)
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(
                                            R.string.ui_txt_saves_vs_highest_cost_offer,
                                            CurrencyFormatter.format(report.maxSavings, cur)
                                        ),
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
                                    stringResource(R.string.ui_txt_fmt_4ed101, saved.lender, saved.variantSummary),
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
                                    Text(
                                        stringResource(R.string.ui_txt_fmt_374485, cat.shortName, variant.name),
                                        fontWeight = FontWeight.Bold
                                    )
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
                                    text = stringResource(
                                        R.string.ui_txt_fmt_986d4f,
                                        CurrencyFormatter.formatCompact(cat.defaultAmountInr, cur)
                                    ),
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
