package com.jyotirmoyb.loan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jyotirmoyb.loan.ui.screens.CalculatorScreen
import com.jyotirmoyb.loan.ui.screens.CompareScreen
import com.jyotirmoyb.loan.ui.screens.ExploreScreen
import com.jyotirmoyb.loan.ui.screens.MyLoansScreen
import com.jyotirmoyb.loan.ui.screens.SettingsScreen
import com.jyotirmoyb.loan.ui.theme.LoanJBTheme
import com.jyotirmoyb.loan.viewmodel.LoanJBViewModel
import com.jyotirmoyb.loan.viewmodel.MainTab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoanJBApp()
        }
    }
}

private data class NavDestination(
    val tab: MainTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

private val NAV_ITEMS = listOf(
    NavDestination(MainTab.EXPLORE, "Explore", Icons.Filled.Explore, Icons.Outlined.Explore, "nav_explore"),
    NavDestination(MainTab.CALCULATOR, "Calculator", Icons.Filled.Calculate, Icons.Outlined.Calculate, "nav_calculator"),
    NavDestination(MainTab.MY_LOANS, "My Loans", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "nav_my_loans"),
    NavDestination(MainTab.COMPARE, "Compare", Icons.Filled.CompareArrows, Icons.Outlined.CompareArrows, "nav_compare"),
    NavDestination(MainTab.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanJBApp(vm: LoanJBViewModel = viewModel()) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val activeTab by vm.activeTab.collectAsStateWithLifecycle()
    val calcInput by vm.calcInput.collectAsStateWithLifecycle()
    val calcResult by vm.calcResult.collectAsStateWithLifecycle()
    val trackedLoans by vm.trackedLoans.collectAsStateWithLifecycle()
    val portfolioMetrics by vm.portfolioMetrics.collectAsStateWithLifecycle()
    val compareItems by vm.compareItems.collectAsStateWithLifecycle()
    val selectedCompareIds by vm.selectedCompareIds.collectAsStateWithLifecycle()
    val snackbarMsg by vm.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMsg) {
        if (snackbarMsg != null) {
            snackbarHostState.showSnackbar(snackbarMsg!!)
            vm.clearSnackbar()
        }
    }

    val topTitle = when (activeTab) {
        MainTab.EXPLORE -> "LoanJB"
        MainTab.CALCULATOR -> "EMI Calculator"
        MainTab.MY_LOANS -> "My Loans"
        MainTab.COMPARE -> "Compare Loans"
        MainTab.SETTINGS -> "Settings"
    }

    LoanJBTheme(themeMode = prefs.themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 700.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = topTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = prefs.currency.symbol,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            NAV_ITEMS.forEach { item ->
                                val selected = activeTab == item.tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { vm.selectTab(item.tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            item.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier.testTag(item.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            NAV_ITEMS.forEach { item ->
                                val selected = activeTab == item.tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { vm.selectTab(item.tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.tag)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val contentMod = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxSize()

                        when (activeTab) {
                            MainTab.EXPLORE -> ExploreScreen(
                                catalog = catalog,
                                onUseInCalculator = { cat, variant ->
                                    vm.selectCategory(cat, preselectedVariant = variant, navigateToCalc = true)
                                },
                                modifier = contentMod
                            )
                            MainTab.CALCULATOR -> CalculatorScreen(
                                catalog = catalog,
                                input = calcInput,
                                result = calcResult,
                                currency = prefs.currency,
                                variantSummary = vm.buildVariantSummary(calcInput, catalog),
                                onSelectCategory = { cat -> vm.selectCategory(cat, navigateToCalc = false) },
                                onSelectVariant = { cat, gId, v -> vm.selectVariantInGroup(cat, gId, v) },
                                onUpdateInput = { transform -> vm.updateCalcInput(transform) },
                                onSaveLoan = { nick, lender, rem, days -> vm.saveCurrentToMyLoans(nick, lender, rem, days) },
                                onAddToCompare = { lbl -> vm.addCurrentToCompare(lbl) },
                                onShowMessage = { vm.showMessage(it) },
                                modifier = contentMod
                            )
                            MainTab.MY_LOANS -> MyLoansScreen(
                                loans = trackedLoans,
                                metrics = portfolioMetrics,
                                monthlyIncome = prefs.monthlyIncome,
                                currency = prefs.currency,
                                onMarkNextEmiPaid = { vm.markNextEmiPaid(it) },
                                onToggleEmiPaid = { loan, m -> vm.toggleEmiPaid(loan, m) },
                                onLogPrepayment = { loan, m, amt, redTen -> vm.logActualPrepayment(loan, m, amt, redTen) },
                                onLogRateChange = { loan, afterM, r -> vm.logActualRateChange(loan, afterM, r) },
                                onToggleReminder = { loan, en, days -> vm.toggleLoanReminder(loan, en, days) },
                                onToggleChecklist = { loan, idx -> vm.toggleChecklistItem(loan, idx) },
                                onAddToCompare = { vm.addLoanToCompare(it) },
                                onDeleteLoan = { vm.deleteLoan(it) },
                                onOpenCalculator = { vm.selectTab(MainTab.CALCULATOR) },
                                onShowMessage = { vm.showMessage(it) },
                                modifier = contentMod
                            )
                            MainTab.COMPARE -> CompareScreen(
                                allScenarios = compareItems,
                                selectedIds = selectedCompareIds,
                                currency = prefs.currency,
                                onToggleSelect = { vm.toggleCompareSelection(it) },
                                onDeleteScenario = { vm.deleteCompareScenario(it) },
                                onLoadPreset = { vm.addPresetComparisonSet(it) },
                                onOpenCalculator = { vm.selectTab(MainTab.CALCULATOR) },
                                modifier = contentMod
                            )
                            MainTab.SETTINGS -> SettingsScreen(
                                preferences = prefs,
                                catalog = catalog,
                                serializeCatalog = { vm.repository.serializeCatalog(it) },
                                createBackupJson = { vm.repository.createBackupJson() },
                                onSetCurrency = { vm.setCurrency(it) },
                                onSetThemeMode = { vm.setThemeMode(it) },
                                onSetMonthlyIncome = { vm.setMonthlyIncome(it) },
                                onSaveCatalogObject = { vm.saveCatalogObject(it) },
                                onSaveCatalogRawJson = { vm.saveCatalogRawJson(it) },
                                onResetCatalogDefault = { vm.resetCatalogToDefault() },
                                onRestoreBackupJson = { vm.restoreBackupJson(it) },
                                onResetAllData = { vm.resetAllData() },
                                onShowMessage = { vm.showMessage(it) },
                                modifier = contentMod
                            )
                        }
                    }
                }
            }
        }
    }
}
