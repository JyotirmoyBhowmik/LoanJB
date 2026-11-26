package com.jyotirmoyb.loan.navigation

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.data.remote.RatesRepository
import com.jyotirmoyb.loan.features.comingsoon.ComingSoonScreen
import com.jyotirmoyb.loan.features.datarates.DataRatesScreen
import com.jyotirmoyb.loan.features.datarates.DataRatesViewModel
import com.jyotirmoyb.loan.features.legal.AboutAndLegalScreen
import com.jyotirmoyb.loan.features.planners.goals.GoalPlannerScreen
import com.jyotirmoyb.loan.features.planners.goals.GoalPlannerViewModel
import com.jyotirmoyb.loan.features.planners.rules.FinancialRulesScreen
import com.jyotirmoyb.loan.features.planners.rules.FinancialRulesViewModel
import com.jyotirmoyb.loan.features.planners.wheretoinvest.WhereToInvestScreen
import com.jyotirmoyb.loan.features.planners.wheretoinvest.WhereToInvestViewModel
import com.jyotirmoyb.loan.features.profile.ProfileUiState
import com.jyotirmoyb.loan.features.profile.ProfileViewModel
import com.jyotirmoyb.loan.features.retirement.epf.EpfScreen
import com.jyotirmoyb.loan.features.retirement.epf.EpfViewModel
import com.jyotirmoyb.loan.features.retirement.govschemes.ApyScreen
import com.jyotirmoyb.loan.features.retirement.govschemes.GovPensionViewModel
import com.jyotirmoyb.loan.features.retirement.govschemes.PmsymScreen
import com.jyotirmoyb.loan.features.retirement.govschemes.PmvvySeniorSchemesScreen
import com.jyotirmoyb.loan.features.retirement.nps.NpsScreen
import com.jyotirmoyb.loan.features.retirement.nps.NpsViewModel
import com.jyotirmoyb.loan.features.retirement.planner.RetirementPlannerScreen
import com.jyotirmoyb.loan.features.retirement.planner.RetirementPlannerViewModel
import com.jyotirmoyb.loan.features.security.PrivacySecurityScreen
import com.jyotirmoyb.loan.util.GlobalErrorHandler

internal fun NavGraphBuilder.addRetirementAndPlannersRoutes(
    context: Context,
    navController: NavHostController,
    profileViewModel: ProfileViewModel,
    profileState: ProfileUiState,
    taxPrefsRepo: UserPreferencesRepository,
    taxRatesRepo: RatesRepository,
    onShowSnackbar: (String) -> Unit
) {
    composable(AppRoute.Privacy.route) {
        PrivacySecurityScreen(
            appLockEnabled = profileState.preferences.appLockEnabled,
            flagSecureEnabled = profileState.preferences.flagSecureEnabled,
            hasBiometricAvailable = profileState.hasBiometricAvailable,
            hasPinConfigured = profileState.hasPinConfigured,
            onToggleAppLock = { profileViewModel.setAppLockEnabled(it) },
            onSetPin = { profileViewModel.setAppPin(it) },
            onClearPin = { profileViewModel.clearAppPin() },
            onToggleFlagSecure = { profileViewModel.setFlagSecureEnabled(it) },
            onShowMessage = { profileViewModel.showMessage(it) }
        )
    }

    composable(AppRoute.AboutLegal.route) {
        var lastCrash by remember { mutableStateOf(GlobalErrorHandler.getLastCrashSummary(context)) }
        AboutAndLegalScreen(
            lastCrashDiagnostic = lastCrash,
            onClearCrashDiagnostic = {
                GlobalErrorHandler.clearLastCrashSummary(context)
                lastCrash = null
            },
            onOpenDataAndRates = { navController.navigate(AppRoute.DataAndRates.route) },
            onOpenPrivacySecurity = { navController.navigate(AppRoute.Privacy.route) }
        )
    }

    composable(AppRoute.DataAndRates.route) {
        val vm: DataRatesViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.snackbarMessage) {
            state.snackbarMessage?.let {
                onShowSnackbar(it)
                vm.clearSnackbar()
            }
        }
        DataRatesScreen(
            uiState = state,
            onSelectCategory = { vm.selectCategory(it) },
            onToggleOnlineData = { vm.setUseOnlineData(it) },
            onRefreshNow = { vm.refreshNow() },
            onSaveOverride = { item, text, onErr, onDone -> vm.saveOverride(item, text, onErr, onDone) },
            onResetItem = { vm.resetToDefault(it) },
            onResetAllOverrides = { vm.resetAllOverrides() },
            exportOverridesJson = { vm.exportOverridesJson() },
            onImportOverridesJson = { vm.importOverridesJson(it) },
            onShowMessage = { vm.showMessage(it) },
            onSetMarketStaleHours = { vm.setMarketStaleHours(it) },
            onSaveApiKey = { slot, key -> vm.saveApiKey(slot, key) },
            onClearApiKey = { slot -> vm.clearApiKey(slot) }
        )
    }

    composable(AppRoute.RetirementPlanner.route) {
        val vm: RetirementPlannerViewModel = viewModel(
            factory = remember(taxPrefsRepo, taxRatesRepo) { RetirementPlannerViewModel.Factory(taxPrefsRepo, taxRatesRepo) }
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        RetirementPlannerScreen(
            uiState = state,
            onUpdateCurrentAge = { vm.updateCurrentAge(it) },
            onUpdateRetirementAge = { vm.updateRetirementAge(it) },
            onUpdateLifeExpectancy = { vm.updateLifeExpectancy(it) },
            onUpdateMonthlyExpenses = { vm.updateMonthlyExpenses(it) },
            onUpdateExistingSavings = { vm.updateExistingSavings(it) },
            onUpdateCurrentSip = { vm.updateCurrentSip(it) },
            onUpdateInflation = { vm.updateInflation(it) },
            onUpdatePreReturn = { vm.updatePreReturn(it) },
            onUpdatePostReturn = { vm.updatePostReturn(it) },
            onResetRates = { vm.resetRatesToDefault() }
        )
    }

    composable(AppRoute.NpsCalculator.route) {
        val vm: NpsViewModel = viewModel(
            factory = remember(taxPrefsRepo, taxRatesRepo) { NpsViewModel.Factory(taxPrefsRepo, taxRatesRepo) }
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        NpsScreen(
            uiState = state,
            onUpdateCurrentAge = { vm.updateCurrentAge(it) },
            onUpdateExitAge = { vm.updateExitAge(it) },
            onUpdateMonthlyContribution = { vm.updateMonthlyContribution(it) },
            onUpdateExistingCorpus = { vm.updateExistingCorpus(it) },
            onUpdateStepUp = { vm.updateStepUp(it) },
            onUpdateExpectedReturn = { vm.updateExpectedReturn(it) },
            onUpdateAnnuityAllocation = { vm.updateAnnuityAllocation(it) },
            onUpdateAnnuityRate = { vm.updateAnnuityRate(it) },
            onResetDefaults = { vm.resetDefaults() }
        )
    }

    composable(AppRoute.EpfCalculator.route) {
        val vm: EpfViewModel = viewModel(
            factory = remember(taxPrefsRepo, taxRatesRepo) { EpfViewModel.Factory(taxPrefsRepo, taxRatesRepo) }
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        EpfScreen(
            uiState = state,
            onUpdateCurrentAge = { vm.updateCurrentAge(it) },
            onUpdateRetirementAge = { vm.updateRetirementAge(it) },
            onUpdateBasicDa = { vm.updateBasicDa(it) },
            onUpdateExistingBalance = { vm.updateExistingBalance(it) },
            onUpdateVpf = { vm.updateVpf(it) },
            onUpdateSalaryGrowth = { vm.updateSalaryGrowth(it) },
            onToggleWageCeiling = { vm.toggleWageCeiling(it) },
            onUpdateInterestRate = { vm.updateInterestRate(it) },
            onResetRate = { vm.resetRateToDefault() }
        )
    }

    composable(AppRoute.ApyCalculator.route) {
        val vm: GovPensionViewModel = viewModel(
            factory = remember(taxPrefsRepo, taxRatesRepo) { GovPensionViewModel.Factory(taxPrefsRepo, taxRatesRepo) }
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        ApyScreen(
            uiState = state,
            onUpdateAge = { vm.updateApyAge(it) },
            onSelectSlab = { vm.selectApySlab(it) }
        )
    }

    composable(AppRoute.PmsymCalculator.route) {
        val vm: GovPensionViewModel = viewModel(
            factory = remember(taxPrefsRepo, taxRatesRepo) { GovPensionViewModel.Factory(taxPrefsRepo, taxRatesRepo) }
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        PmsymScreen(
            uiState = state,
            onUpdateAge = { vm.updatePmsymAge(it) },
            onUpdateIncome = { vm.updatePmsymIncome(it) }
        )
    }

    composable(AppRoute.PmvvyCalculator.route) {
        val vm: GovPensionViewModel = viewModel(
            factory = remember(taxPrefsRepo, taxRatesRepo) { GovPensionViewModel.Factory(taxPrefsRepo, taxRatesRepo) }
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        PmvvySeniorSchemesScreen(
            uiState = state,
            onSelectScheme = { vm.selectSeniorScheme(it) },
            onUpdateInvestment = { vm.updateSeniorInvestment(it) }
        )
    }

    composable(AppRoute.GoalPlanner.route) {
        val vm: GoalPlannerViewModel = viewModel(
            factory = remember(taxPrefsRepo) { GoalPlannerViewModel.Factory(taxPrefsRepo) }
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        GoalPlannerScreen(
            uiState = state,
            onAddGoal = { vm.addGoal(it) },
            onUpdateGoal = { vm.updateGoal(it) },
            onToggleGoal = { vm.toggleGoal(it) },
            onRemoveGoal = { vm.removeGoal(it) },
            onToggleExpand = { vm.toggleExpand(it) },
            onResetGoals = { vm.resetToStarterGoals() }
        )
    }

    composable(AppRoute.FinancialRules.route) {
        val vm: FinancialRulesViewModel = viewModel(
            factory = remember(taxPrefsRepo, taxRatesRepo) { FinancialRulesViewModel.Factory(taxPrefsRepo, taxRatesRepo) }
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        FinancialRulesScreen(
            uiState = state,
            onSelectTab = { vm.selectTab(it) },
            onUpdateMonthlyIncome = { vm.updateMonthlyIncome(it) },
            onUpdateBudgetSplit = { n, w, s -> vm.updateBudgetSplit(n, w, s) },
            onResetBudgetSplit = { vm.resetBudgetSplitToDefault() },
            onUpdateEssentialExpenses = { vm.updateEssentialExpenses(it) },
            onUpdateTargetMonths = { vm.updateTargetMonths(it) },
            onResetEmergencyMonths = { vm.resetEmergencyMonthsToDefault() },
            onUpdateCurrentLiquidSavings = { vm.updateCurrentLiquidSavings(it) },
            onUpdateMonthlyEmergencyAlloc = { vm.updateMonthlyEmergencyAllocation(it) },
            onUpdateAnnualIncome = { vm.updateAnnualIncome(it) },
            onUpdateExistingLiabilities = { vm.updateExistingLiabilities(it) },
            onUpdateExistingTermCover = { vm.updateExistingTermCover(it) },
            onUpdateTermMultiplier = { vm.updateTermMultiplier(it) },
            onResetTermMultiplier = { vm.resetTermMultiplierToDefault() },
            onSetMetroCity = { vm.setMetroCity(it) },
            onUpdateFamilyMembers = { vm.updateFamilyMembersCount(it) },
            onUpdateExistingHealthCover = { vm.updateExistingHealthCover(it) }
        )
    }

    composable(AppRoute.WhereToInvest.route) {
        val vm: WhereToInvestViewModel = viewModel(
            factory = remember(taxPrefsRepo, taxRatesRepo) { WhereToInvestViewModel.Factory(taxPrefsRepo, taxRatesRepo) }
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        WhereToInvestScreen(
            uiState = state,
            onUpdateAge = { vm.updateAge(it) },
            onSelectAnswer = { qId, score -> vm.selectAnswer(qId, score) },
            onOverrideProfile = { vm.overrideProfile(it) },
            onUpdateInvestableAmount = { vm.updateInvestableAmount(it) },
            onResetQuestionnaire = { vm.resetQuestionnaire() }
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
