package com.jyotirmoyb.loan.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jyotirmoyb.loan.R
import com.jyotirmoyb.loan.features.CalculatorRegistry
import com.jyotirmoyb.loan.features.bankstaff.BankStaffLoanScreen
import com.jyotirmoyb.loan.features.bankstaff.BankStaffViewModel
import com.jyotirmoyb.loan.features.comingsoon.ComingSoonScreen
import com.jyotirmoyb.loan.features.compare.CompareViewModel
import com.jyotirmoyb.loan.features.emi.EmiInputScreen
import com.jyotirmoyb.loan.features.emi.EmiViewModel
import com.jyotirmoyb.loan.features.emi.results.EmiResultsScreen
import com.jyotirmoyb.loan.features.hub.HubScreen
import com.jyotirmoyb.loan.features.hub.HubViewModel
import com.jyotirmoyb.loan.features.myloans.MyLoansViewModel
import com.jyotirmoyb.loan.features.placeholder.PlaceholderTabScreen
import com.jyotirmoyb.loan.features.profile.ProfileScreen
import com.jyotirmoyb.loan.features.profile.ProfileViewModel
import com.jyotirmoyb.loan.ui.screens.CompareScreen
import com.jyotirmoyb.loan.ui.screens.ExploreScreen
import com.jyotirmoyb.loan.ui.screens.MyLoansScreen
import com.jyotirmoyb.loan.ui.theme.MintTopStartDark
import com.jyotirmoyb.loan.ui.theme.MintTopStartLight
import com.jyotirmoyb.loan.ui.theme.TealPrimary
import com.jyotirmoyb.loan.ui.theme.TealPrimaryDark

private data class BottomTabItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val BOTTOM_TABS = listOf(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanJBMainShell(
    profileViewModel: ProfileViewModel,
    navController: NavHostController = rememberNavController()
) {
    val hubViewModel: HubViewModel = viewModel()
    val emiViewModel: EmiViewModel = viewModel()
    val myLoansViewModel: MyLoansViewModel = viewModel()
    val compareViewModel: CompareViewModel = viewModel()
    val bankStaffViewModel: BankStaffViewModel = viewModel()

    val hubState by hubViewModel.uiState.collectAsStateWithLifecycle()
    val emiState by emiViewModel.uiState.collectAsStateWithLifecycle()
    val myLoansState by myLoansViewModel.uiState.collectAsStateWithLifecycle()
    val compareState by compareViewModel.uiState.collectAsStateWithLifecycle()
    val bankStaffState by bankStaffViewModel.uiState.collectAsStateWithLifecycle()
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Observe snackbars across feature ViewModels
    LaunchedEffect(emiState.snackbarMessage) {
        emiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            emiViewModel.clearSnackbar()
        }
    }
    LaunchedEffect(myLoansState.snackbarMessage) {
        myLoansState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            myLoansViewModel.clearSnackbar()
        }
    }
    LaunchedEffect(compareState.snackbarMessage) {
        compareState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            compareViewModel.clearSnackbar()
        }
    }
    LaunchedEffect(bankStaffState.snackbarMessage) {
        bankStaffState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            bankStaffViewModel.clearSnackbar()
        }
    }
    LaunchedEffect(profileState.snackbarMessage) {
        profileState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            profileViewModel.clearSnackbar()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoute.CalculatorHub.route
    val isTopLevelTab = BOTTOM_TABS.any { it.route == currentRoute }
    val showShellTopBar = currentRoute != AppRoute.EmiResults.route &&
        currentRoute != AppRoute.BankStaffLoan.route &&
        !currentRoute.startsWith("coming_soon")

    fun navigateToTab(tabRoute: String) {
        navController.navigate(tabRoute) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (showShellTopBar) {
                    LoanJBTopBar(
                        currentRoute = currentRoute,
                        isTopLevelTab = isTopLevelTab,
                        currencySymbol = profileState.preferences.currency.symbol,
                        onMenuClick = { showMenuSheet = true },
                        onBackClick = { navController.popBackStack() },
                        onSearchClick = {
                            if (currentRoute != AppRoute.CalculatorHub.route) {
                                navigateToTab(AppRoute.CalculatorHub.route)
                            }
                            hubViewModel.toggleSearch()
                        },
                        onInfoClick = { showAboutDialog = true }
                    )
                }
            },
            bottomBar = {
                if (!useNavRail && isTopLevelTab) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        BOTTOM_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = TealPrimaryDark,
                                    selectedTextColor = TealPrimaryDark,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(tab.testTag)
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
                if (useNavRail && isTopLevelTab) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        BOTTOM_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tab.testTag)
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

                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.CalculatorHub.route,
                        modifier = contentMod
                    ) {
                        composable(AppRoute.CalculatorHub.route) {
                            HubScreen(
                                uiState = hubState,
                                onSearchQueryChange = { hubViewModel.updateSearchQuery(it) },
                                onClearSearch = { hubViewModel.clearSearch() },
                                onModuleClick = { mod ->
                                    if (mod.route == AppRoute.EmiCalculator.route) {
                                        navigateToTab(AppRoute.EmiCalculator.route)
                                    } else {
                                        navController.navigate(mod.route)
                                    }
                                },
                                onOpenSavedLoans = { navController.navigate(AppRoute.SavedLoans.route) },
                                onOpenLoanGuide = { navController.navigate(AppRoute.LoanGuide.route) }
                            )
                        }

                        composable(AppRoute.EmiCalculator.route) {
                            EmiInputScreen(
                                uiState = emiState,
                                onSelectCategory = { emiViewModel.selectCategory(it) },
                                onSelectVariant = { cat, gId, v -> emiViewModel.selectVariantInGroup(cat, gId, v) },
                                onUpdateInput = { emiViewModel.updateInput(it) },
                                onSetTenureUnit = { emiViewModel.setTenureUnit(it) },
                                onStartDateToday = { emiViewModel.setStartDateToday() },
                                onStartDateSelected = { y, m, d -> emiViewModel.setStartDate(y, m, d) },
                                onSelectEmiMode = { emiViewModel.selectEmiModeFlag(it) },
                                onToggleMoratorium = { emiViewModel.toggleMoratorium(it) },
                                onCalculateClick = { navController.navigate(AppRoute.EmiResults.route) }
                            )
                        }

                        composable(AppRoute.EmiResults.route) {
                            EmiResultsScreen(
                                uiState = emiState,
                                shareSummaryText = { emiViewModel.buildShareSummaryText() },
                                onBack = { navController.popBackStack() },
                                onSaveLoan = { nick, lender, rem, days ->
                                    emiViewModel.saveCurrentToMyLoans(nick, lender, rem, days)
                                },
                                onAddToCompare = { emiViewModel.addCurrentToCompare() },
                                onShowMessage = { emiViewModel.showMessage(it) }
                            )
                        }

                        composable(AppRoute.Articles.route) {
                            PlaceholderTabScreen(
                                title = stringResource(R.string.articles_title),
                                subtitle = stringResource(R.string.articles_subtitle),
                                icon = Icons.AutoMirrored.Outlined.Article,
                                tag = "articles_placeholder_screen",
                                onOpenEmi = { navigateToTab(AppRoute.EmiCalculator.route) }
                            )
                        }

                        composable(AppRoute.Market.route) {
                            PlaceholderTabScreen(
                                title = stringResource(R.string.market_title),
                                subtitle = stringResource(R.string.market_subtitle),
                                icon = Icons.AutoMirrored.Outlined.ShowChart,
                                tag = "market_placeholder_screen",
                                onOpenEmi = { navigateToTab(AppRoute.EmiCalculator.route) }
                            )
                        }

                        composable(AppRoute.Profile.route) {
                            ProfileScreen(
                                uiState = profileState,
                                serializeCatalog = { profileViewModel.serializeCatalog(it) },
                                createBackupJson = { profileViewModel.createBackupJson() },
                                onSetCurrency = { profileViewModel.setCurrency(it) },
                                onSetThemeMode = { profileViewModel.setThemeMode(it) },
                                onSetMonthlyIncome = { profileViewModel.setMonthlyIncome(it) },
                                onSaveCatalogObject = { profileViewModel.saveCatalogObject(it) },
                                onSaveCatalogRawJson = { profileViewModel.saveCatalogRawJson(it) },
                                onResetCatalogDefault = { profileViewModel.resetCatalogToDefault() },
                                onRestoreBackupJson = { profileViewModel.restoreBackupJson(it) },
                                onResetAllData = { profileViewModel.resetAllData() },
                                onShowMessage = { profileViewModel.showMessage(it) },
                                onOpenSavedLoans = { navController.navigate(AppRoute.SavedLoans.route) },
                                onOpenLoanGuide = { navController.navigate(AppRoute.LoanGuide.route) },
                                onOpenCompare = { navController.navigate(AppRoute.LoanComparison.route) },
                                onReplayOnboarding = { profileViewModel.setOnboardingCompleted(false) }
                            )
                        }

                        composable(AppRoute.LoanComparison.route) {
                            CompareScreen(
                                allScenarios = compareState.items,
                                selectedIds = compareState.selectedIds,
                                currency = compareState.currency,
                                onToggleSelect = { compareViewModel.toggleCompareSelection(it) },
                                onDeleteScenario = { compareViewModel.deleteCompareScenario(it) },
                                onLoadPreset = { compareViewModel.addPresetComparisonSet(it) },
                                onOpenCalculator = { navigateToTab(AppRoute.EmiCalculator.route) }
                            )
                        }

                        composable(AppRoute.BankStaffLoan.route) {
                            BankStaffLoanScreen(
                                uiState = bankStaffState,
                                onBack = { navController.popBackStack() },
                                onSelectPreset = { bankStaffViewModel.selectPreset(it) },
                                onUpdateAmount = { bankStaffViewModel.updateAmount(it) },
                                onUpdateStaffRate = { bankStaffViewModel.updateStaffRate(it) },
                                onUpdateMarketRate = { bankStaffViewModel.updateMarketRate(it) },
                                onUpdateTenureMonths = { bankStaffViewModel.updateTenureMonths(it) },
                                onViewFullSchedule = {
                                    val staffInput = bankStaffViewModel.buildCurrentStaffInput()
                                    emiViewModel.updateInput { staffInput }
                                    navController.navigate(AppRoute.EmiResults.route)
                                },
                                onSaveStaffLoan = { bankStaffViewModel.saveStaffLoan() },
                                onCompareWithMarket = {
                                    bankStaffViewModel.addBothToCompare()
                                    navController.navigate(AppRoute.LoanComparison.route)
                                }
                            )
                        }

                        composable(AppRoute.SavedLoans.route) {
                            MyLoansScreen(
                                loans = myLoansState.loans,
                                metrics = myLoansState.portfolioMetrics,
                                monthlyIncome = myLoansState.monthlyIncome,
                                currency = myLoansState.currency,
                                onMarkNextEmiPaid = { myLoansViewModel.markNextEmiPaid(it) },
                                onToggleEmiPaid = { loan, m -> myLoansViewModel.toggleEmiPaid(loan, m) },
                                onLogPrepayment = { loan, m, amt, redTen ->
                                    myLoansViewModel.logActualPrepayment(loan, m, amt, redTen)
                                },
                                onLogRateChange = { loan, afterM, r ->
                                    myLoansViewModel.logActualRateChange(loan, afterM, r)
                                },
                                onToggleReminder = { loan, en, days ->
                                    myLoansViewModel.toggleLoanReminder(loan, en, days)
                                },
                                onToggleChecklist = { loan, idx ->
                                    myLoansViewModel.toggleChecklistItem(loan, idx)
                                },
                                onAddToCompare = { myLoansViewModel.addLoanToCompare(it) },
                                onDeleteLoan = { myLoansViewModel.deleteLoan(it) },
                                onOpenCalculator = { navigateToTab(AppRoute.EmiCalculator.route) },
                                onShowMessage = { profileViewModel.showMessage(it) }
                            )
                        }

                        composable(AppRoute.LoanGuide.route) {
                            ExploreScreen(
                                catalog = profileState.catalog,
                                onUseInCalculator = { cat, variant ->
                                    emiViewModel.selectCategory(cat, preselectedVariant = variant)
                                    navigateToTab(AppRoute.EmiCalculator.route)
                                }
                            )
                        }

                        composable(
                            route = AppRoute.ComingSoon.route,
                            arguments = listOf(navArgument(AppRoute.ComingSoon.ARG_MODULE_ID) { type = NavType.StringType })
                        ) { entry ->
                            val modId = entry.arguments?.getString(AppRoute.ComingSoon.ARG_MODULE_ID).orEmpty()
                            ComingSoonScreen(
                                moduleId = modId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMenuSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMenuSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            WorkspaceDrawerSheet(
                savedLoansCount = hubState.savedLoansCount,
                onNavigate = { route ->
                    showMenuSheet = false
                    if (BOTTOM_TABS.any { it.route == route }) {
                        navigateToTab(route)
                    } else {
                        navController.navigate(route)
                    }
                }
            )
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About LoanJB", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "LoanJB is a 100% offline Indian loan planner, EMI calculator, prepayment analyzer, and loan tracker. All calculations and saved loans stay privately on your device.\n\n" +
                        stringResource(R.string.disclaimer_text)
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun LoanJBTopBar(
    currentRoute: String,
    isTopLevelTab: Boolean,
    currencySymbol: String,
    onMenuClick: () -> Unit,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val headerBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(MintTopStartDark, MaterialTheme.colorScheme.background)
        } else {
            listOf(MintTopStartLight, MaterialTheme.colorScheme.background)
        }
    )

    val title = when (currentRoute) {
        AppRoute.LoanComparison.route -> "Loan Comparison"
        AppRoute.SavedLoans.route -> stringResource(R.string.hub_saved_loans_title)
        AppRoute.LoanGuide.route -> stringResource(R.string.hub_loan_guide_title)
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
                modifier = Modifier.testTag("topbar_menu_btn")
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
                modifier = Modifier.testTag("topbar_back_btn")
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = currencySymbol,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.app_bar_subtitle),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = TealPrimary
            )
        }

        IconButton(
            onClick = onInfoClick,
            modifier = Modifier.testTag("topbar_info_btn")
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
                modifier = Modifier.testTag("topbar_search_btn")
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
private fun WorkspaceDrawerSheet(
    savedLoansCount: Int,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "LoanJB Workspace",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "100% offline Indian loan planner & tracker",
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
            icon = Icons.Outlined.Person,
            label = "Profile, Catalog & Backup",
            onClick = { onNavigate(AppRoute.Profile.route) }
        )
    }
}

@Composable
private fun DrawerSheetRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TealPrimaryDark,
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
