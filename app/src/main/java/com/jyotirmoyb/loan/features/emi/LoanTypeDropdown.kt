package com.jyotirmoyb.loan.features.emi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.LoanCategory
import com.jyotirmoyb.loan.domain.LoanVariant
import com.jyotirmoyb.loan.ui.components.categoryIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanTypeDropdown(
    categories: List<LoanCategory>,
    selectedCategory: LoanCategory?,
    selectedVariantsByGroup: Map<String, String>,
    onSelectCategory: (LoanCategory) -> Unit,
    onSelectVariant: (LoanCategory, String, LoanVariant) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedCategory?.let { "${it.name} (EMI)" } ?: "EMI",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.emi_loan_type_label)) },
                leadingIcon = selectedCategory?.let { cat ->
                    {
                        Icon(
                            imageVector = categoryIcon(cat.icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .testTag("emi_loan_type_dropdown")
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                categories.forEach { cat ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = categoryIcon(cat.icon),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(cat.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "${String.format("%.1f", cat.rateRangeMin)}–${String.format("%.1f", cat.rateRangeMax)}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelectCategory(cat)
                        },
                        modifier = Modifier.testTag("emi_dropdown_item_${cat.id}")
                    )
                }
            }
        }

        // Compact catalog variant chips for the selected loan category
        selectedCategory?.let { cat ->
            val primaryGroups = cat.variantGroups.take(2)
            if (primaryGroups.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                primaryGroups.forEach { group ->
                    val activeVariantId = selectedVariantsByGroup[group.id]
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(group.variants, key = { it.id }) { variant ->
                            val selected = variant.id == activeVariantId
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectVariant(cat, group.id, variant) },
                                label = {
                                    Text(
                                        text = variant.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("emi_variant_chip_${variant.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}
