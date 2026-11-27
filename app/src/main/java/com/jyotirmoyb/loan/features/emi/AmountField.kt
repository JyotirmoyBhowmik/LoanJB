package com.jyotirmoyb.loan.features.emi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
fun AmountField(
    amount: Double,
    currency: CurrencyCode,
    onAmountChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "${stringResource(R.string.emi_amount_label)} (${currency.symbol})"
) {
    var textValue by remember { mutableStateOf(formatInputDigits(amount, currency)) }

    LaunchedEffect(amount, currency) {
        val parsed = textValue.replace(",", "").toDoubleOrNull() ?: 0.0
        if (abs(parsed - amount) > 0.5) {
            textValue = formatInputDigits(amount, currency)
        }
    }

    val compactLabel = remember(amount, currency) {
        CurrencyFormatter.formatShortLabel(amount, currency)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = textValue,
            onValueChange = { raw ->
                val digitsOnly = raw.filter { it.isDigit() }
                if (digitsOnly.length <= 12) {
                    val numeric = digitsOnly.toLongOrNull()
                    if (numeric != null) {
                        textValue = when (currency) {
                            CurrencyCode.INR, CurrencyCode.NPR -> CurrencyFormatter.formatIndianGrouping(numeric)
                            CurrencyCode.USD -> CurrencyFormatter.formatWesternGrouping(numeric)
                        }
                        onAmountChange(numeric.toDouble())
                    } else {
                        textValue = ""
                        onAmountChange(0.0)
                    }
                }
            },
            label = { Text(label) },
            prefix = {
                Text(
                    text = stringResource(R.string.ui_txt_fmt_4939b3, currency.symbol),
                    style = MonoMediumStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            trailingIcon = {
                Text(
                    text = compactLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 14.dp)
                )
            },
            textStyle = MonoMediumStyle,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emi_amount_input")
        )
    }
}

private fun formatInputDigits(amount: Double, currency: CurrencyCode): String {
    val whole = amount.roundToLong().coerceAtLeast(0L)
    if (whole == 0L) return ""
    return when (currency) {
        CurrencyCode.INR, CurrencyCode.NPR -> CurrencyFormatter.formatIndianGrouping(whole)
        CurrencyCode.USD -> CurrencyFormatter.formatWesternGrouping(whole)
    }
}
