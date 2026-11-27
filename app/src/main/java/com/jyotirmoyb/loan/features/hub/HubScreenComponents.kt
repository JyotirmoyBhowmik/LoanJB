package com.jyotirmoyb.loan.features.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Elderly
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.features.CalculatorModule
import com.jyotirmoyb.loan.ui.theme.InfoBlue
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.LocalIsDarkTheme
import com.jyotirmoyb.loan.ui.theme.PurpleAccent
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SageGreenDark
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark

@Composable
internal fun HubStaleDataBanner(onClick: () -> Unit) {
    val isDark = LocalIsDarkTheme.current
    val bgColor = if (isDark) Color(0xFF3B2806) else Color(0xFFFFFBEB)
    val borderColor = if (isDark) Color(0xFF92400E) else Color(0xFFFCD34D)
    val fgColor = if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("hub_stale_data_banner"),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.hub_stale_data_banner),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = fgColor,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

internal fun sectionHeaderIcon(iconKey: String): ImageVector = when (iconKey) {
    "featured" -> Icons.Outlined.Stars
    "loans" -> Icons.Outlined.AccountBalance
    "deposits" -> Icons.Outlined.Savings
    "mutual" -> Icons.AutoMirrored.Outlined.TrendingUp
    "tax" -> Icons.Outlined.ReceiptLong
    "pension" -> Icons.Outlined.Elderly
    else -> Icons.Outlined.AccountBalance
}

internal fun moduleIcon(icon: String): ImageVector = when (icon) {
    "target" -> Icons.Outlined.TrackChanges
    "umbrella" -> Icons.Outlined.BeachAccess
    "sparkles" -> Icons.Outlined.AutoAwesome
    "lightbulb" -> Icons.Outlined.Lightbulb
    "compare" -> Icons.Outlined.CompareArrows
    "eligibility" -> Icons.Outlined.Speed
    "balance_transfer" -> Icons.Outlined.SwapHoriz
    "tax_benefit" -> Icons.Outlined.ReceiptLong
    "part_payment" -> Icons.Outlined.EventAvailable
    "staff" -> Icons.Outlined.Badge
    "small_savings" -> Icons.Outlined.AccountBalance
    "deposit_compare" -> Icons.Outlined.CompareArrows
    "lumpsum" -> Icons.Outlined.Savings
    "pension" -> Icons.Outlined.Elderly
    else -> Icons.Outlined.AccountBalance
}

internal fun moduleTintColor(module: CalculatorModule): Color = when (module.icon) {
    "target" -> SageGreen
    "umbrella" -> PurpleAccent
    "sparkles" -> InfoBlue
    "lightbulb" -> InterestAmber
    "compare" -> InfoBlue
    "eligibility" -> SageGreenDark
    "balance_transfer" -> TealPrimaryDark
    "tax_benefit" -> InterestAmber
    "part_payment" -> SageGreen
    "small_savings" -> SageGreenDark
    "deposit_compare" -> InfoBlue
    "xirr" -> PurpleAccent
    "live_nav" -> SageGreen
    "lumpsum" -> InterestAmber
    "tax" -> InterestAmber
    "hra" -> SageGreenDark
    "gst" -> InfoBlue
    "advance_tax" -> PurpleAccent
    "rp" -> PurpleAccent
    "nps" -> InfoBlue
    "epf" -> SageGreenDark
    "pension" -> PurpleAccent
    else -> TealPrimaryDark
}
