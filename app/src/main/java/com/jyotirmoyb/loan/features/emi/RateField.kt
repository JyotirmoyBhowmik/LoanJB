package com.jyotirmoyb.loan.features.emi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.engine.LoanCalculationEngine
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import kotlin.math.abs

@Composable
fun RateField(
    annualRate: Double,
    minRate: Float = 1f,
    maxRate: Float = 24f,
    onRateChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var rateText by remember { mutableStateOf(formatRateText(annualRate)) }

    LaunchedEffect(annualRate) {
        val parsed = rateText.toDoubleOrNull()
        if (parsed == null || abs(parsed - annualRate) > 0.005) {
            rateText = formatRateText(annualRate)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = rateText,
            onValueChange = { raw ->
                val filtered = raw.filter { it.isDigit() || it == '.' }
                if (filtered.count { it == '.' } <= 1 && filtered.length <= 6) {
                    rateText = filtered
                    filtered.toDoubleOrNull()?.let { value ->
                        onRateChange(value.coerceIn(0.0, 40.0))
                    }
                }
            },
            label = { Text(stringResource(R.string.emi_rate_label)) },
            trailingIcon = {
                Text(
                    text = "%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 14.dp)
                )
            },
            textStyle = MonoMediumStyle,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emi_rate_input")
        )

        Slider(
            value = annualRate.toFloat().coerceIn(minRate, maxRate),
            onValueChange = { sliderVal ->
                val rounded = LoanCalculationEngine.roundPaisa((sliderVal * 4f).toInt() / 4.0)
                rateText = formatRateText(rounded)
                onRateChange(rounded)
            },
            valueRange = minRate..maxRate,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emi_rate_slider")
        )
    }
}

private fun formatRateText(rate: Double): String {
    return if (rate % 1.0 == 0.0) {
        String.format("%.1f", rate)
    } else {
        String.format("%.2f", rate).trimEnd('0').trimEnd('.')
    }
}
