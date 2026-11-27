package com.jyotirmoyb.loan.features.tax.incometax

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.domain.CurrencyCode
import com.jyotirmoyb.loan.domain.CurrencyFormatter
import com.jyotirmoyb.loan.domain.IncomeTaxComparisonResult
import com.jyotirmoyb.loan.domain.RegimeTaxCalculation
import com.jyotirmoyb.loan.features.deposits.fd.KeyValueRow
import com.jyotirmoyb.loan.ui.components.FintechCard
import com.jyotirmoyb.loan.ui.components.SectionAccentHeader
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SavingsGreen

@Composable
internal fun IncomeTaxComparisonCard(
    fyCode: String,
    newRegime: RegimeTaxCalculation,
    oldRegime: RegimeTaxCalculation,
    currency: CurrencyCode
) {
    FintechCard(modifier = Modifier.testTag("income_tax_comparison_card")) {
        SectionAccentHeader(
            title = "New vs Old Regime ($fyCode)",
            badge = "Side-by-Side"
        )
        Spacer(Modifier.height(10.dp))

        ComparisonHeaderRow()
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        ComparisonRow("Gross Income", newRegime.grossTotalIncome, oldRegime.grossTotalIncome, currency)
        ComparisonRow("Standard Ded.", -newRegime.standardDeduction, -oldRegime.standardDeduction, currency)
        ComparisonRow("Other Deductions", -newRegime.otherDeductionsTotal, -oldRegime.otherDeductionsTotal, currency)
        ComparisonRow(stringResource(R.string.tax_taxable_income), newRegime.taxableIncome, oldRegime.taxableIncome, currency, bold = true)
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        ComparisonRow(stringResource(R.string.tax_slab_tax), newRegime.slabTax, oldRegime.slabTax, currency)
        if (newRegime.rebate87A > 0.0 || oldRegime.rebate87A > 0.0) {
            ComparisonRow(
                stringResource(R.string.tax_rebate_87a),
                -newRegime.rebate87A,
                -oldRegime.rebate87A,
                currency,
                highlightGreen = true
            )
        }
        if (newRegime.rebateMarginalRelief > 0.0 || oldRegime.rebateMarginalRelief > 0.0) {
            ComparisonRow(
                stringResource(R.string.tax_marginal_relief),
                -newRegime.rebateMarginalRelief,
                -oldRegime.rebateMarginalRelief,
                currency,
                highlightGreen = true
            )
        }
        if (newRegime.surchargePayable > 0.0 || oldRegime.surchargePayable > 0.0) {
            ComparisonRow(stringResource(R.string.tax_surcharge), newRegime.surchargePayable, oldRegime.surchargePayable, currency)
        }
        ComparisonRow(
            "${stringResource(R.string.tax_cess)} (${newRegime.cessPercent.toInt()}%)",
            newRegime.cessAmount,
            oldRegime.cessAmount,
            currency
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        ComparisonRow(stringResource(R.string.tax_payable), newRegime.totalTaxPayable, oldRegime.totalTaxPayable, currency, bold = true)
        ComparisonRow(stringResource(R.string.tax_monthly_tds), newRegime.monthlyTdsEstimate, oldRegime.monthlyTdsEstimate, currency)
    }
}

@Composable
internal fun IncomeTaxWhyCard(result: IncomeTaxComparisonResult) {
    FintechCard(modifier = Modifier.testTag("income_tax_why_card")) {
        SectionAccentHeader(
            title = stringResource(R.string.tax_why_title),
            badge = result.recommendedRegimeId,
            icon = Icons.Outlined.Lightbulb
        )
        Spacer(Modifier.height(10.dp))
        result.whyBullets.forEach { bullet ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = SavingsGreen,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = bullet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
internal fun IncomeTaxSlabBreakdownCard(
    fyCode: String,
    newRegime: RegimeTaxCalculation,
    oldRegime: RegimeTaxCalculation,
    currency: CurrencyCode,
    expanded: Boolean,
    onToggleSlabBreakdown: () -> Unit
) {
    FintechCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleSlabBreakdown)
                .testTag("tax_slab_breakdown_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionAccentHeader(
                title = stringResource(R.string.tax_slab_breakdown_title),
                badge = fyCode,
                icon = Icons.Outlined.TableChart,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SlabTableBlock(title = newRegime.displayName, calc = newRegime, currency = currency)
                HorizontalDivider()
                SlabTableBlock(title = oldRegime.displayName, calc = oldRegime, currency = currency)
            }
        }
    }
}

@Composable
internal fun RegimeHeroPill(title: String, taxPayable: String, monthlyTds: String, isWinner: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (isWinner) PrincipalEmerald.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, if (isWinner) PrincipalEmerald.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = if (isWinner) PrincipalEmerald else Color.White.copy(alpha = 0.72f),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = taxPayable,
                style = MonoSmallStyle,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.ui_txt_tds, monthlyTds),
                style = MaterialTheme.typography.labelSmall,
                color = InterestAmber
            )
        }
    }
}

@Composable
private fun ComparisonHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            stringResource(R.string.ui_txt_component),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1.3f)
        )
        Text(
            stringResource(R.string.ui_txt_new),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.ui_txt_old),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    newAmount: Double,
    oldAmount: Double,
    currency: CurrencyCode,
    bold: Boolean = false,
    highlightGreen: Boolean = false
) {
    val formatSigned: (Double) -> String = { v ->
        if (v < 0.0) {
            "-${CurrencyFormatter.formatCompact(-v, currency)}"
        } else {
            CurrencyFormatter.formatCompact(v, currency)
        }
    }
    val valueColor = if (highlightGreen) SavingsGreen else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1.3f)
        )
        Text(
            text = formatSigned(newAmount),
            style = MonoSmallStyle,
            color = valueColor,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatSigned(oldAmount),
            style = MonoSmallStyle,
            color = valueColor,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SlabTableBlock(title: String, calc: RegimeTaxCalculation, currency: CurrencyCode) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        calc.slabBreakdown.forEach { line ->
            KeyValueRow(
                label = "${line.rangeLabel} @ ${line.ratePercent.toInt()}%",
                value = CurrencyFormatter.format(line.taxAmount, currency)
            )
        }
    }
}
