package com.jyotirmoyb.loan.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jyotirmoyb.loan.data.AppContainer
import com.jyotirmoyb.loan.features.articles.ArticlesScreen
import com.jyotirmoyb.loan.features.articles.ArticlesViewModel
import com.jyotirmoyb.loan.features.emi.EmiInputScreen
import com.jyotirmoyb.loan.features.emi.EmiViewModel
import com.jyotirmoyb.loan.features.emi.results.EmiResultsScreen
import com.jyotirmoyb.loan.features.hub.HubScreen
import com.jyotirmoyb.loan.features.hub.HubViewModel
import com.jyotirmoyb.loan.features.market.MarketScreen
import com.jyotirmoyb.loan.features.market.MarketViewModel
import com.jyotirmoyb.loan.features.mutualfund.sip.SipViewModel
import com.jyotirmoyb.loan.features.profile.ProfileScreen
import com.jyotirmoyb.loan.features.profile.ProfileViewModel
import com.jyotirmoyb.loan.ui.theme.LocalIsDarkTheme
import com.jyotirmoyb.loan.ui.theme.SageGreen
import com.jyotirmoyb.loan.ui.theme.SageGreenDark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanJBMainShell(
    profileViewModel: ProfileViewModel,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val coroutineScope = rememberCoroutineScope()

    val hubViewModel: HubViewModel = viewModel()
    val emiViewModel: EmiViewModel = viewModel()
    val sipViewModel: SipViewModel = viewModel()
    val taxPrefsRepo = remember(appContext) { AppContainer.providePreferencesRepository(appContext) }
    val taxRatesRepo = remember(appContext) { AppContainer.provideRatesRepository(appContext) }

    val hubState by hubViewModel.uiState.collectAsStateWithLifecycle()
    val emiState by emiViewModel.uiState.collectAsStateWithLifecycle()
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showMenuSheet by remember { mutableStateOf(false) }

    val showSnackbar: (String) -> Unit = remember(coroutineScope, snackbarHostState) {
        { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } }
    }

    LaunchedEffect(emiState.snackbarMessage) {
        emiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            emiViewModel.clearSnackbar()
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

    val isDark = LocalIsDarkTheme.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useNavRail = maxWidth >= 700.dp

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val backWave = Path().apply {
                moveTo(0f, h * 0.66f)
                cubicTo(w * 0.30f, h * 0.58f, w * 0.72f, h * 0.72f, w, h * 0.62f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                backWave,
                color = SageGreenDark.copy(alpha = 0.14f)
            )
            val frontWave = Path().apply {
                moveTo(0f, h * 0.73f)
                cubicTo(w * 0.28f, h * 0.65f, w * 0.68f, h * 0.80f, w, h * 0.70f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                frontWave,
                color = if (isDark) SageGreen.copy(alpha = 0.16f) else SageGreen.copy(alpha = 0.32f)
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
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
                        onInfoClick = { navController.navigate(AppRoute.AboutLegal.route) }
                    )
                }
            },
            bottomBar = {
                if (!useNavRail && isTopLevelTab) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
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
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
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
                                onEmiDayOfMonthChange = { emiViewModel.updateEmiDayOfMonth(it) },
                                onProRataToggle = { emiViewModel.toggleProRataFirstMonth(it) },
                                onResetRateDefault = { emiViewModel.resetRateToDefault() },
                                onApplyRepoScenario = { emiViewModel.applyRepoRateScenario(it) },
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
                                onSaveLoan = { nick, lender, rem, days, emiDay, nach ->
                                    emiViewModel.saveCurrentToMyLoans(nick, lender, rem, days, emiDay, nach)
                                },
                                onAddToCompare = { emiViewModel.addCurrentToCompare() },
                                onShowMessage = { emiViewModel.showMessage(it) }
                            )
                        }

                        composable(AppRoute.Articles.route) {
                            val articlesRepo = remember(appContext) { AppContainer.provideArticlesRepository(appContext) }
                            val articlesViewModel: ArticlesViewModel = viewModel(
                                factory = remember(articlesRepo) { ArticlesViewModel.Factory(articlesRepo) }
                            )
                            val articlesState by articlesViewModel.uiState.collectAsStateWithLifecycle()
                            LaunchedEffect(articlesState.snackbarMessage) {
                                articlesState.snackbarMessage?.let {
                                    showSnackbar(it)
                                    articlesViewModel.clearSnackbar()
                                }
                            }
                            ArticlesScreen(
                                uiState = articlesState,
                                onSearchChange = { articlesViewModel.updateSearchQuery(it) },
                                onSelectCategory = { articlesViewModel.selectCategory(it) },
                                onToggleBookmarkedOnly = { articlesViewModel.toggleBookmarkedOnly() },
                                onOpenArticle = { articlesViewModel.openArticle(it) },
                                onCloseArticle = { articlesViewModel.closeArticle() },
                                onToggleBookmark = { articlesViewModel.toggleBookmark(it) },
                                onRefreshRemote = { articlesViewModel.refreshRemoteArticles() },
                                buildShareText = { articlesViewModel.buildShareText(it) }
                            )
                        }

                        composable(AppRoute.Market.route) {
                            val marketRepo = remember(appContext) { AppContainer.provideMarketRepository(appContext) }
                            val marketViewModel: MarketViewModel = viewModel(
                                factory = remember(marketRepo) { MarketViewModel.Factory(marketRepo) }
                            )
                            val marketState by marketViewModel.uiState.collectAsStateWithLifecycle()
                            LaunchedEffect(marketState.snackbarMessage) {
                                marketState.snackbarMessage?.let {
                                    showSnackbar(it)
                                    marketViewModel.clearSnackbar()
                                }
                            }
                            MarketScreen(
                                uiState = marketState,
                                onSelectFilter = { marketViewModel.selectFilter(it) },
                                onOpenMetricDetail = { marketViewModel.openMetricDetail(it) },
                                onCloseMetricDetail = { marketViewModel.closeMetricDetail() },
                                onRefreshNow = { marketViewModel.refreshNow() },
                                onSaveManualMetric = { key, raw, onErr, onDone ->
                                    marketViewModel.saveManualMetric(key, raw, onErr, onDone)
                                },
                                onResetManualMetric = { marketViewModel.resetManualMetric(it) },
                                onSaveBankFd = { id, name, gen, sen, ten, onErr, onDone ->
                                    marketViewModel.saveBankFdRate(id, name, gen, sen, ten, onErr, onDone)
                                },
                                onDeleteBankFd = { id, name -> marketViewModel.deleteBankFdRate(id, name) },
                                onResetAllBankFds = { marketViewModel.resetAllBankFdRates() },
                                onOpenDataAndRates = { navController.navigate(AppRoute.DataAndRates.route) }
                            )
                        }

                        composable(AppRoute.Profile.route) {
                            ProfileScreen(
                                uiState = profileState,
                                serializeCatalog = { profileViewModel.serializeCatalog(it) },
                                createEncryptedBackupJson = { pwd -> profileViewModel.createEncryptedBackupJson(pwd) },
                                onSetLanguage = { profileViewModel.setLanguageCode(it) },
                                onSetRegion = { profileViewModel.setRegion(it) },
                                onSetCurrency = { profileViewModel.setCurrency(it) },
                                onSetThemeMode = { profileViewModel.setThemeMode(it) },
                                onSetMonthlyIncome = { profileViewModel.setMonthlyIncome(it) },
                                onSetTaxRegime = { profileViewModel.setPreferredTaxRegime(it) },
                                onSetUserAge = { profileViewModel.setUserAge(it) },
                                onSetRiskProfile = { profileViewModel.setRiskProfile(it) },
                                onSaveCatalogObject = { profileViewModel.saveCatalogObject(it) },
                                onSaveCatalogRawJson = { profileViewModel.saveCatalogRawJson(it) },
                                onResetCatalogDefault = { profileViewModel.resetCatalogToDefault() },
                                onRestoreBackupJson = { raw, pwd -> profileViewModel.restoreBackupJson(raw, pwd) },
                                onResetAllData = { profileViewModel.resetAllData() },
                                onShowMessage = { profileViewModel.showMessage(it) },
                                onOpenDataAndRates = { navController.navigate(AppRoute.DataAndRates.route) },
                                onOpenPrivacy = { navController.navigate(AppRoute.Privacy.route) },
                                onOpenSavedLoans = { navController.navigate(AppRoute.SavedLoans.route) },
                                onOpenLoanGuide = { navController.navigate(AppRoute.LoanGuide.route) },
                                onOpenCompare = { navController.navigate(AppRoute.LoanComparison.route) },
                                onReplayOnboarding = { profileViewModel.setOnboardingCompleted(false) }
                            )
                        }

                        addLoanAndDepositRoutes(
                            context = context,
                            navController = navController,
                            emiViewModel = emiViewModel,
                            catalog = profileState.catalog,
                            navigateToTab = ::navigateToTab,
                            onShowSnackbar = showSnackbar
                        )

                        addMutualFundAndTaxRoutes(
                            navController = navController,
                            sipViewModel = sipViewModel,
                            taxPrefsRepo = taxPrefsRepo,
                            taxRatesRepo = taxRatesRepo,
                            onShowSnackbar = showSnackbar
                        )

                        addRetirementAndPlannersRoutes(
                            context = context,
                            navController = navController,
                            profileViewModel = profileViewModel,
                            profileState = profileState,
                            taxPrefsRepo = taxPrefsRepo,
                            taxRatesRepo = taxRatesRepo,
                            onShowSnackbar = showSnackbar
                        )
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
}
