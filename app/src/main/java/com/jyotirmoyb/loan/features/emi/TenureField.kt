package com.jyotirmoyb.loan.features.emi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import kotlin.math.roundToInt

@Composable
fun TenureField(
    tenureMonths: Int,
    inYears: Boolean,
    onTenureMonthsChange: (Int) -> Unit,
    onUnitChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var displayText by remember {
        mutableStateOf(
            if (inYears) formatYears(tenureMonths) else tenureMonths.toString()
        )
    }

    LaunchedEffect(tenureMonths, inYears) {
        val currentMonths = if (inYears) {
            ((displayText.toDoubleOrNull() ?: 0.0) * 12.0).roundToInt()
        } else {
            displayText.toIntOrNull() ?: 0
        }
        if (currentMonths != tenureMonths) {
            displayText = if (inYears) formatYears(tenureMonths) else tenureMonths.toString()
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = { raw ->
                val filtered = if (inYears) {
                    raw.filter { it.isDigit() || it == '.' }
                } else {
                    raw.filter { it.isDigit() }
                }
                if (filtered.count { it == '.' } <= 1 && filtered.length <= 5) {
                    displayText = filtered
                    val months = if (inYears) {
                        ((filtered.toDoubleOrNull() ?: 0.0) * 12.0).roundToInt()
                    } else {
                        filtered.toIntOrNull() ?: 0
                    }
                    if (months > 0) {
                        onTenureMonthsChange(months.coerceIn(1, 360))
                    }
                }
            },
            label = { Text(stringResource(R.string.emi_tenure_label)) },
            textStyle = MonoMediumStyle,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (inYears) KeyboardType.Decimal else KeyboardType.Number
            ),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .weight(1.1f)
                .testTag("emi_tenure_input")
        )

        Spacer(Modifier.width(2.dp))

        TenureTogglePill(
            label = stringResource(R.string.emi_tenure_year),
            selected = inYears,
            onClick = { onUnitChange(true) },
            modifier = Modifier
                .weight(0.65f)
                .testTag("emi_tenure_unit_year")
        )
        TenureTogglePill(
            label = stringResource(R.string.emi_tenure_month),
            selected = !inYears,
            onClick = { onUnitChange(false) },
            modifier = Modifier
                .weight(0.65f)
                .testTag("emi_tenure_unit_month")
        )
    }
}

@Composable
private fun TenureTogglePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

private fun formatYears(months: Int): String {
    val yrs = months / 12.0
    return if (months % 12 == 0) {
        (months / 12).toString()
    } else {
        String.format("%.1f", yrs)
    }
}
