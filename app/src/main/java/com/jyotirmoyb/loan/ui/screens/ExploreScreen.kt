package com.jyotirmoyb.loan.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.LoanCatalog
import com.jyotirmoyb.loan.domain.LoanCategory
import com.jyotirmoyb.loan.domain.LoanVariant
import com.jyotirmoyb.loan.ui.components.DisclaimerBanner
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.components.categoryIcon
import com.jyotirmoyb.loan.ui.components.parseAccentColor
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.SlateNavy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(catalog: LoanCatalog, onUseInCalculator: (LoanCategory, LoanVariant?) -> Unit, modifier: Modifier = Modifier) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<LoanCategory?>(null) }
    var inspectedVariant by remember { mutableStateOf<Pair<LoanCategory, LoanVariant>?>(null) }

    if (selectedCategory != null) {
        BackHandler { selectedCategory = null }
        CategoryDetailView(
            category = selectedCategory!!,
            onBack = { selectedCategory = null },
            onInspectVariant = { variant -> inspectedVariant = selectedCategory!! to variant },
            onQuickCalculate = { onUseInCalculator(selectedCategory!!, null) },
            modifier = modifier
        )
    } else {
        val filteredCategories = remember(catalog, searchQuery) {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) {
                catalog.categories
            } else {
                catalog.categories.filter { cat ->
                    cat.name.lowercase().contains(q) ||
                        cat.description.lowercase().contains(q) ||
                        cat.variantGroups.any { g ->
                            g.name.lowercase().contains(q) ||
                                g.variants.any { v ->
                                    v.name.lowercase().contains(q) ||
                                        v.howItWorks.lowercase().contains(q) ||
                                        v.bestFor.lowercase().contains(q)
                                }
                        }
                }
            }
        }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ExploreHeroBanner(
                    categoryCount = catalog.categories.size,
                    variantCount = catalog.categories.sumOf { c -> c.variantGroups.sumOf { it.variants.size } },
                    onStartCalc = {
                        catalog.categories.firstOrNull()?.let { onUseInCalculator(it, null) }
                    }
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.ui_txt_search_home_ev_balloon_offset_gold)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search loans") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explore_search_input")
                )
            }

            items(filteredCategories, key = { it.id }) { category ->
                ExploreCategoryBentoCard(
                    category = category,
                    onOpenCategory = { selectedCategory = category },
                    onSelectVariant = { variant -> inspectedVariant = category to variant }
                )
            }

            item {
                DisclaimerBanner()
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    inspectedVariant?.let { (cat, variant) ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { inspectedVariant = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            VariantDetailSheetContent(
                category = cat,
                variant = variant,
                onUseInCalculator = {
                    inspectedVariant = null
                    onUseInCalculator(cat, variant)
                }
            )
        }
    }
}

@Composable
private fun ExploreHeroBanner(categoryCount: Int, variantCount: Int, onStartCalc: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(SlateNavy)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_explore_hero_1795633223033),
            contentDescription = "LoanJB financial planning banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xE60B2A30),
                            Color(0x990F3B3E),
                            Color(0x220F3B3E)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ui_txt_categories_variants_offline, categoryCount, variantCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB8F5EA),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ui_txt_know_your_emi_own_your_plan),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = onStartCalc,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0A5C52)
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("explore_hero_calculate_btn")
            ) {
                Icon(Icons.Outlined.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ui_txt_open_calculator), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ExploreCategoryBentoCard(category: LoanCategory, onOpenCategory: () -> Unit, onSelectVariant: (LoanVariant) -> Unit) {
    val accent = parseAccentColor(category.accentHex, MaterialTheme.colorScheme.primary)
    val previewVariants = remember(category) {
        category.variantGroups.flatMap { it.variants }.take(4)
    }
    val totalVariants = remember(category) {
        category.variantGroups.sumOf { it.variants.size }
    }

    FintechCard(
        modifier = Modifier.testTag("category_card_${category.id}")
    ) {
        SectionAccentHeader(
            title = category.name,
            badge = "${String.format("%.1f", category.rateRangeMin)}-${String.format("%.1f", category.rateRangeMax)}%",
            accentColor = accent,
            icon = categoryIcon(category.icon)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = category.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))

        // 2-column grid of featured variants (inspired by clean fintech bento cards)
        val rows = previewVariants.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { rowPair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowPair.forEach { variant ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectVariant(variant) }
                                .testTag("variant_tile_${variant.id}"),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = variant.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(
                                        R.string.ui_txt_fmt_bc5ff1,
                                        String.format("%.1f", variant.rateRangeMin),
                                        String.format("%.1f", variant.rateRangeMax),
                                        variant.typicalTenureLabel
                                    ),
                                    style = MonoSmallStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = accent
                                )
                            }
                        }
                    }
                    if (rowPair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpenCategory() }
                .padding(vertical = 6.dp, horizontal = 4.dp)
                .testTag("explore_all_${category.id}"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.ui_txt_explore_all_variants_across_group, totalVariants, category.variantGroups.size),
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Explore ${category.name}",
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
