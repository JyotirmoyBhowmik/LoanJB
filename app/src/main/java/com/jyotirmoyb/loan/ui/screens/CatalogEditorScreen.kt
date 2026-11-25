package com.jyotirmoyb.loan.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.engine.LoanCatalog
import com.jyotirmoyb.loan.engine.LoanCategory
import com.jyotirmoyb.loan.engine.LoanVariant
import com.jyotirmoyb.loan.engine.VariantGroup
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.NumericInputField
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle

@Composable
fun CatalogEditorScreen(
    catalog: LoanCatalog,
    catalogJson: String,
    onBack: () -> Unit,
    onSaveCatalogObject: (LoanCatalog) -> Unit,
    onSaveCatalogRawJson: (String) -> Unit,
    onResetDefault: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var editorTab by remember { mutableIntStateOf(0) } // 0 = Visual Editor, 1 = Raw JSON
    var workingCatalog by remember(catalog) { mutableStateOf(catalog) }
    var rawJsonText by remember(catalogJson) { mutableStateOf(catalogJson) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var addVariantTargetCategoryIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("catalog_editor_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Settings")
                }
                Text("Edit Loan Catalog", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onResetDefault) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset Seed")
            }
        }

        // Visual vs Raw JSON toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            listOf("Visual Editor", "Raw JSON").forEachIndexed { idx, label ->
                val sel = editorTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (sel) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { editorTab = idx }
                        .padding(vertical = 8.dp)
                        .testTag("catalog_tab_${idx}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (editorTab == 0) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(workingCatalog.categories, key = { _, c -> c.id }) { catIdx, cat ->
                    FintechCard {
                        SectionAccentHeader(
                            title = cat.name,
                            badge = "${cat.variantGroups.sumOf { it.variants.size }} variants"
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumericInputField(
                                label = "Min Rate %",
                                valueText = cat.rateRangeMin.toString(),
                                onValueChange = { t ->
                                    val v = t.toDoubleOrNull() ?: cat.rateRangeMin
                                    workingCatalog = workingCatalog.copy(
                                        categories = workingCatalog.categories.mapIndexed { i, c -> if (i == catIdx) c.copy(rateRangeMin = v) else c }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            NumericInputField(
                                label = "Max Rate %",
                                valueText = cat.rateRangeMax.toString(),
                                onValueChange = { t ->
                                    val v = t.toDoubleOrNull() ?: cat.rateRangeMax
                                    workingCatalog = workingCatalog.copy(
                                        categories = workingCatalog.categories.mapIndexed { i, c -> if (i == catIdx) c.copy(rateRangeMax = v) else c }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumericInputField(
                                label = "Min Tenure (m)",
                                valueText = cat.tenureMinMonths.toString(),
                                onValueChange = { t ->
                                    val v = t.toIntOrNull() ?: cat.tenureMinMonths
                                    workingCatalog = workingCatalog.copy(
                                        categories = workingCatalog.categories.mapIndexed { i, c -> if (i == catIdx) c.copy(tenureMinMonths = v) else c }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            NumericInputField(
                                label = "Max Tenure (m)",
                                valueText = cat.tenureMaxMonths.toString(),
                                onValueChange = { t ->
                                    val v = t.toIntOrNull() ?: cat.tenureMaxMonths
                                    workingCatalog = workingCatalog.copy(
                                        categories = workingCatalog.categories.mapIndexed { i, c -> if (i == catIdx) c.copy(tenureMaxMonths = v) else c }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            NumericInputField(
                                label = "Max LTV %",
                                valueText = cat.ltvMaxPercent.toInt().toString(),
                                onValueChange = { t ->
                                    val v = t.toDoubleOrNull() ?: cat.ltvMaxPercent
                                    workingCatalog = workingCatalog.copy(
                                        categories = workingCatalog.categories.mapIndexed { i, c -> if (i == catIdx) c.copy(ltvMaxPercent = v) else c }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { addVariantTargetCategoryIndex = catIdx },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Custom Variant to ${cat.shortName}")
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { showAddCategoryDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add New Loan Category")
                    }
                }
            }

            Surface(
                tonalElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Button(
                    onClick = { onSaveCatalogObject(workingCatalog) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("catalog_save_visual_btn")
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Catalog Changes")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Edit the complete catalog.json configuration directly. Changes persist offline in Room.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = rawJsonText,
                    onValueChange = { rawJsonText = it },
                    textStyle = MonoSmallStyle,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("catalog_raw_json_input")
                )
                Button(
                    onClick = { onSaveCatalogRawJson(rawJsonText) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("catalog_save_json_btn")
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Validate & Save JSON")
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        var name by remember { mutableStateOf("") }
        var minRate by remember { mutableStateOf("9.0") }
        var maxRate by remember { mutableStateOf("14.0") }

        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Add Loan Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Category Name") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericInputField("Min Rate %", minRate, { minRate = it }, modifier = Modifier.weight(1f))
                        NumericInputField("Max Rate %", maxRate, { maxRate = it }, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cleanName = name.trim()
                    if (cleanName.isNotEmpty()) {
                        val id = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "_")
                        val newCat = LoanCategory(
                            id = id,
                            name = cleanName,
                            shortName = cleanName.take(10),
                            description = "Custom loan category added via LoanJB catalog editor.",
                            rateRangeMin = minRate.toDoubleOrNull() ?: 9.0,
                            rateRangeMax = maxRate.toDoubleOrNull() ?: 14.0,
                            variantGroups = listOf(
                                VariantGroup(
                                    id = "default_group",
                                    name = "Repayment Type",
                                    variants = listOf(
                                        LoanVariant(
                                            id = "${id}_standard",
                                            name = "Standard Fixed EMI",
                                            howItWorks = "Equal monthly payments over the selected tenure.",
                                            bestFor = "Predictable repayment planning.",
                                            ruleFlag = "RATE_FIXED",
                                            rateRangeMin = minRate.toDoubleOrNull() ?: 9.0,
                                            rateRangeMax = maxRate.toDoubleOrNull() ?: 14.0
                                        )
                                    )
                                )
                            )
                        )
                        workingCatalog = workingCatalog.copy(categories = workingCatalog.categories + newCat)
                    }
                    showAddCategoryDialog = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddCategoryDialog = false }) { Text("Cancel") } }
        )
    }

    addVariantTargetCategoryIndex?.let { idx ->
        val targetCat = workingCatalog.categories.getOrNull(idx)
        if (targetCat != null) {
            var vName by remember { mutableStateOf("") }
            var vHow by remember { mutableStateOf("") }
            var vBest by remember { mutableStateOf("") }
            var vFlag by remember { mutableStateOf("EMI_STANDARD") }

            AlertDialog(
                onDismissRequest = { addVariantTargetCategoryIndex = null },
                title = { Text("Add Variant to ${targetCat.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = vName, onValueChange = { vName = it }, label = { Text("Variant Name") }, singleLine = true)
                        OutlinedTextField(value = vHow, onValueChange = { vHow = it }, label = { Text("How it works") })
                        OutlinedTextField(value = vBest, onValueChange = { vBest = it }, label = { Text("Best for") })
                        OutlinedTextField(value = vFlag, onValueChange = { vFlag = it }, label = { Text("Rule Flag (e.g. RATE_FLOATING, EMI_STEP_UP)") }, singleLine = true)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val clean = vName.trim()
                        if (clean.isNotEmpty() && targetCat.variantGroups.isNotEmpty()) {
                            val newVar = LoanVariant(
                                id = "${targetCat.id}_${clean.lowercase().replace(Regex("[^a-z0-9]+"), "_")}",
                                name = clean,
                                howItWorks = vHow.ifBlank { "Custom variant rule." },
                                bestFor = vBest.ifBlank { "Custom borrower profile." },
                                ruleFlag = vFlag.trim().ifBlank { "EMI_STANDARD" },
                                rateRangeMin = targetCat.rateRangeMin,
                                rateRangeMax = targetCat.rateRangeMax
                            )
                            val updatedGroups = targetCat.variantGroups.mapIndexed { gi, g ->
                                if (gi == 0) g.copy(variants = g.variants + newVar) else g
                            }
                            workingCatalog = workingCatalog.copy(
                                categories = workingCatalog.categories.mapIndexed { ci, c ->
                                    if (ci == idx) c.copy(variantGroups = updatedGroups) else c
                                }
                            )
                        }
                        addVariantTargetCategoryIndex = null
                    }) { Text("Add Variant") }
                },
                dismissButton = { TextButton(onClick = { addVariantTargetCategoryIndex = null }) { Text("Cancel") } }
            )
        }
    }
}
