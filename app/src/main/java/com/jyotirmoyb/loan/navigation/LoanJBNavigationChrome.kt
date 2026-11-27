package com.jyotirmoyb.loan.navigation

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.ui.theme.LocalIsDarkTheme
import com.jyotirmoyb.loan.ui.theme.MintTopStartDark
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SageGreenDark
import com.jyotirmoyb.loan.ui.theme.WarmBeige

internal data class BottomTabItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

internal val BOTTOM_TABS = listOf(
    BottomTabItem(
        route = AppRoute.CalculatorHub.route,
        labelRes = R.string.nav_calculator,
        selectedIcon = Icons.Filled.Apps,
        unselectedIcon = Icons.Outlined.Apps,
        testTag = "nav_tab_calculator"
    ),
    BottomTabItem(
        route = AppRoute.EmiCalculator.route,
        labelRes = R.string.nav_emi,
        selectedIcon = Icons.Filled.AccountBalance,
        unselectedIcon = Icons.Outlined.AccountBalance,
        testTag = "nav_tab_emi"
    ),
    BottomTabItem(
        route = AppRoute.Articles.route,
        labelRes = R.string.nav_articles,
        selectedIcon = Icons.AutoMirrored.Outlined.Article,
        unselectedIcon = Icons.AutoMirrored.Outlined.Article,
        testTag = "nav_tab_articles"
    ),
    BottomTabItem(
        route = AppRoute.Market.route,
        labelRes = R.string.nav_market,
        selectedIcon = Icons.AutoMirrored.Outlined.ShowChart,
        unselectedIcon = Icons.AutoMirrored.Outlined.ShowChart,
        testTag = "nav_tab_market"
    ),
    BottomTabItem(
        route = AppRoute.Profile.route,
        labelRes = R.string.nav_profile,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        testTag = "nav_tab_profile"
    )
)

internal fun isCalculatorRoute(route: String): Boolean = route in setOf(
    AppRoute.EmiCalculator.route,
    AppRoute.EmiResults.route,
    AppRoute.LoanComparison.route,
    AppRoute.LoanEligibility.route,
    AppRoute.BalanceTransfer.route,
    AppRoute.LoanTaxBenefit.route,
    AppRoute.PartPaymentPlanner.route,
    AppRoute.BankStaffLoan.route,
    AppRoute.FixedDeposit.route,
    AppRoute.FdInterestPayout.route,
    AppRoute.RecurringDeposit.route,
    AppRoute.SmallSavings.route,
    AppRoute.DepositComparison.route,
    AppRoute.MfSip.route,
    AppRoute.MfLumpsum.route,
    AppRoute.MfSwp.route,
    AppRoute.MfGoal.route,
    AppRoute.MfXirr.route,
    AppRoute.MfTaxGains.route,
    AppRoute.MfLiveNav.route,
    AppRoute.IncomeTax.route,
    AppRoute.HraCalculator.route,
    AppRoute.GstCalculator.route,
    AppRoute.AdvanceTaxTds.route,
    AppRoute.RetirementPlanner.route,
    AppRoute.NpsCalculator.route,
    AppRoute.EpfCalculator.route,
    AppRoute.ApyCalculator.route,
    AppRoute.PmsymCalculator.route,
    AppRoute.PmvvyCalculator.route,
    AppRoute.GoalPlanner.route,
    AppRoute.FinancialRules.route,
    AppRoute.WhereToInvest.route
)

@Composable
internal fun LoanJBTopBar(
    currentRoute: String,
    isTopLevelTab: Boolean,
    currencySymbol: String,
    onMenuClick: () -> Unit,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val headerBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(MintTopStartDark, MaterialTheme.colorScheme.background)
        } else {
            listOf(WarmBeige, MaterialTheme.colorScheme.background)
        }
    )

    val title = when (currentRoute) {
        AppRoute.LoanComparison.route -> stringResource(R.string.compare_title)
        AppRoute.LoanEligibility.route -> stringResource(R.string.eligibility_title)
        AppRoute.BalanceTransfer.route -> stringResource(R.string.bt_title)
        AppRoute.LoanTaxBenefit.route -> stringResource(R.string.tax_title)
        AppRoute.PartPaymentPlanner.route -> stringResource(R.string.partpay_title)
        AppRoute.SavedLoans.route -> stringResource(R.string.hub_saved_loans_title)
        AppRoute.LoanGuide.route -> stringResource(R.string.hub_loan_guide_title)
        AppRoute.DataAndRates.route -> stringResource(R.string.data_rates_title)
        AppRoute.Privacy.route -> stringResource(R.string.privacy_security_title)
        AppRoute.AboutLegal.route -> "About, Terms & Sources"
        AppRoute.FixedDeposit.route -> stringResource(R.string.fd_title)
        AppRoute.FdInterestPayout.route -> stringResource(R.string.fd_payout_title)
        AppRoute.RecurringDeposit.route -> stringResource(R.string.rd_title)
        AppRoute.SmallSavings.route -> stringResource(R.string.ss_title)
        AppRoute.DepositComparison.route -> stringResource(R.string.dep_cmp_title)
        AppRoute.MfSip.route -> stringResource(R.string.mf_sip_title)
        AppRoute.MfLumpsum.route -> stringResource(R.string.mf_lumpsum_title)
        AppRoute.MfSwp.route -> stringResource(R.string.mf_swp_title)
        AppRoute.MfGoal.route -> stringResource(R.string.mf_goal_title)
        AppRoute.MfXirr.route -> stringResource(R.string.mf_xirr_title)
        AppRoute.MfTaxGains.route -> stringResource(R.string.mf_tax_title)
        AppRoute.MfLiveNav.route -> stringResource(R.string.mf_nav_title)
        AppRoute.IncomeTax.route -> stringResource(R.string.tax_suite_income_title)
        AppRoute.HraCalculator.route -> stringResource(R.string.tax_suite_hra_title)
        AppRoute.GstCalculator.route -> stringResource(R.string.tax_suite_gst_title)
        AppRoute.AdvanceTaxTds.route -> stringResource(R.string.tax_suite_advance_title)
        AppRoute.RetirementPlanner.route -> stringResource(R.string.ret_planner_title)
        AppRoute.NpsCalculator.route -> stringResource(R.string.ret_nps_title)
        AppRoute.EpfCalculator.route -> stringResource(R.string.ret_epf_title)
        AppRoute.ApyCalculator.route -> stringResource(R.string.ret_apy_title)
        AppRoute.PmsymCalculator.route -> stringResource(R.string.ret_pmsym_title)
        AppRoute.PmvvyCalculator.route -> stringResource(R.string.ret_pmvvy_title)
        AppRoute.GoalPlanner.route -> stringResource(R.string.planner_goals_title)
        AppRoute.FinancialRules.route -> stringResource(R.string.planner_rules_title)
        AppRoute.WhereToInvest.route -> stringResource(R.string.planner_where_title)
        AppRoute.Articles.route -> stringResource(R.string.nav_articles)
        AppRoute.Market.route -> stringResource(R.string.nav_market)
        AppRoute.Profile.route -> stringResource(R.string.nav_profile)
        else -> stringResource(R.string.app_bar_title)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBrush)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isTopLevelTab) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("topbar_menu_btn")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = stringResource(R.string.cd_open_menu),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("topbar_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) MaterialTheme.colorScheme.onSurface else SageGreenDark,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = currencySymbol,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = if (isCalculatorRoute(currentRoute)) {
                    "Educational estimate • Not a SEBI/tax financial advisor"
                } else {
                    stringResource(R.string.app_bar_subtitle)
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) MaterialTheme.colorScheme.primary else SageGreen
            )
        }

        IconButton(
            onClick = onInfoClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("topbar_info_btn")
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.cd_info),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (currentRoute == AppRoute.CalculatorHub.route || currentRoute == AppRoute.EmiCalculator.route) {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("topbar_search_btn")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.cd_search),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
internal fun WorkspaceDrawerSheet(savedLoansCount: Int, onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.ui_txt_loanjb_workspace),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.ui_txt_100_offline_indian_loan_planner_tr),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))

        DrawerSheetRow(
            icon = Icons.Outlined.AccountBalanceWallet,
            label = "Saved Loans ($savedLoansCount)",
            onClick = { onNavigate(AppRoute.SavedLoans.route) }
        )
        DrawerSheetRow(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            label = "Loan Types Guide",
            onClick = { onNavigate(AppRoute.LoanGuide.route) }
        )
        DrawerSheetRow(
            icon = Icons.Outlined.CompareArrows,
            label = "Loan Comparison",
            onClick = { onNavigate(AppRoute.LoanComparison.route) }
        )
        DrawerSheetRow(
            icon = Icons.Outlined.Badge,
            label = "Bank Staff Loan",
            onClick = { onNavigate(AppRoute.BankStaffLoan.route) }
        )
        DrawerSheetRow(
            icon = Icons.Outlined.Tune,
            label = stringResource(R.string.data_rates_title),
            onClick = { onNavigate(AppRoute.DataAndRates.route) }
        )
        DrawerSheetRow(
            icon = Icons.Outlined.Security,
            label = stringResource(R.string.privacy_security_title),
            onClick = { onNavigate(AppRoute.Privacy.route) }
        )
        DrawerSheetRow(
            icon = Icons.Outlined.Gavel,
            label = "About, Privacy Policy & Terms",
            onClick = { onNavigate(AppRoute.AboutLegal.route) }
        )
        DrawerSheetRow(
            icon = Icons.Outlined.Person,
            label = "Profile, Catalog & Backup",
            onClick = { onNavigate(AppRoute.Profile.route) }
        )
    }
}

@Composable
private fun DrawerSheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}
