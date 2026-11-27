package com.jyotirmoyb.loan.features.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.ui.theme.InterestAmber
import com.jyotirmoyb.loan.ui.theme.MonoMediumStyle
import com.jyotirmoyb.loan.ui.theme.MonoSmallStyle
import com.jyotirmoyb.loan.ui.theme.PrincipalEmerald
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SageGreenDark
import com.jyotirmoyb.loan.ui.theme.SlateNavy
import com.jyotirmoyb.loan.ui.theme.TealPrimary
import com.jyotirmoyb.loan.ui.theme.WarmBeige
import kotlinx.coroutines.launch

private data class OnboardingPageData(
    val titleRes: Int,
    val subtitleRes: Int,
    val badge: String,
    val icon: ImageVector,
    val stat1Label: String,
    val stat1Value: String,
    val stat2Label: String,
    val stat2Value: String,
    val highlightText: String
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val pages = listOf(
        OnboardingPageData(
            titleRes = R.string.onboarding_title_1,
            subtitleRes = R.string.onboarding_sub_1,
            badge = "EMI CALCULATOR",
            icon = Icons.Outlined.Calculate,
            stat1Label = "Loan Amount",
            stat1Value = "₹28,00,000",
            stat2Label = "Prepayment",
            stat2Value = "₹6,00,000 Once",
            highlightText = "Home • Personal • Auto • Education • Business • Gold"
        ),
        OnboardingPageData(
            titleRes = R.string.onboarding_title_2,
            subtitleRes = R.string.onboarding_sub_2,
            badge = "YEARLY & MONTHLY LEDGER",
            icon = Icons.Outlined.PieChart,
            stat1Label = "Principal",
            stat1Value = "₹28,00,000",
            stat2Label = "Total Interest",
            stat2Value = "₹6,07,948",
            highlightText = "YEAR 1 • Oct – Sep • Exact zero balance"
        ),
        OnboardingPageData(
            titleRes = R.string.onboarding_title_3,
            subtitleRes = R.string.onboarding_sub_3,
            badge = "FINANCIAL HUB",
            icon = Icons.Outlined.AccountBalance,
            stat1Label = "Loans & EMI",
            stat1Value = "Compare Offers",
            stat2Label = "Bank Staff",
            stat2Value = "Subsidized Rates",
            highlightText = "100% Offline • Indian Lakh/Crore Grouping"
        ),
        OnboardingPageData(
            titleRes = R.string.onboarding_title_4,
            subtitleRes = R.string.onboarding_sub_4,
            badge = "FUTURE READY",
            icon = Icons.Outlined.Savings,
            stat1Label = "Modular Hub",
            stat1Value = "20+ Tools",
            stat2Label = "Privacy",
            stat2Value = "No Login Needed",
            highlightText = "Save Loans • Track EMIs • Export PDF & CSV"
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WarmBeige)
            .testTag("onboarding_screen")
    ) {
        // Organic sage-green hill wave at the bottom (matching the reference screenshots!)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val backWave = Path().apply {
                moveTo(0f, h * 0.56f)
                cubicTo(w * 0.30f, h * 0.48f, w * 0.72f, h * 0.64f, w, h * 0.52f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(backWave, color = SageGreenDark.copy(alpha = 0.25f))

            val frontWave = Path().apply {
                moveTo(0f, h * 0.62f)
                cubicTo(w * 0.28f, h * 0.54f, w * 0.66f, h * 0.72f, w, h * 0.59f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(frontWave, color = SageGreen)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onFinished,
                    modifier = Modifier.testTag("onboarding_skip_btn")
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        color = SageGreenDark,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { index ->
                val page = pages[index]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(page.titleRes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = SageGreenDark,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(page.subtitleRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF5F5D55),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))

                    // Phone-card preview mockup
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = TealPrimary.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = page.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TealPrimary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(SageGreen.copy(alpha = 0.14f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = page.icon,
                                        contentDescription = null,
                                        tint = SageGreenDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFF3FBF9)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(page.stat1Label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF5D6C7D))
                                        Spacer(Modifier.height(4.dp))
                                        Text(page.stat1Value, style = MonoMediumStyle, color = PrincipalEmerald)
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFFFF7EE)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(page.stat2Label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF5D6C7D))
                                        Spacer(Modifier.height(4.dp))
                                        Text(page.stat2Value, style = MonoMediumStyle, color = InterestAmber)
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = SlateNavy
                            ) {
                                Text(
                                    text = page.highlightText,
                                    style = MonoSmallStyle,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom indicator dots + CTA button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(pages.size) { idx ->
                        val active = pagerState.currentPage == idx
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (active) 24.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (active) Color.White else Color.White.copy(alpha = 0.45f))
                        )
                    }
                }

                val isLast = pagerState.currentPage == pages.lastIndex
                Button(
                    onClick = {
                        if (isLast) {
                            onFinished()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = SageGreenDark
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("onboarding_next_btn")
                ) {
                    Text(
                        text = stringResource(if (isLast) R.string.onboarding_get_started else R.string.onboarding_next),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
