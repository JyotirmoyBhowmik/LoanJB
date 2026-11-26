package com.jyotirmoyb.loan.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.jyotirmoyb.loan.data.UserPreferencesRepository
import com.jyotirmoyb.loan.data.remote.RatesRepository
import com.jyotirmoyb.loan.features.mutualfund.goal.GoalSipScreen
import com.jyotirmoyb.loan.features.mutualfund.goal.GoalSipViewModel
import com.jyotirmoyb.loan.features.mutualfund.lumpsum.LumpsumScreen
import com.jyotirmoyb.loan.features.mutualfund.lumpsum.LumpsumViewModel
import com.jyotirmoyb.loan.features.mutualfund.nav.LiveNavScreen
import com.jyotirmoyb.loan.features.mutualfund.nav.LiveNavViewModel
import com.jyotirmoyb.loan.features.mutualfund.sip.SipScreen
import com.jyotirmoyb.loan.features.mutualfund.sip.SipViewModel
import com.jyotirmoyb.loan.features.mutualfund.swp.SwpScreen
import com.jyotirmoyb.loan.features.mutualfund.swp.SwpViewModel
import com.jyotirmoyb.loan.features.mutualfund.tax.MfTaxGainsScreen
import com.jyotirmoyb.loan.features.mutualfund.tax.MfTaxGainsViewModel
import com.jyotirmoyb.loan.features.mutualfund.xirr.XirrScreen
import com.jyotirmoyb.loan.features.mutualfund.xirr.XirrViewModel
import com.jyotirmoyb.loan.features.tax.advancetax.AdvanceTaxScreen
import com.jyotirmoyb.loan.features.tax.advancetax.AdvanceTaxViewModel
import com.jyotirmoyb.loan.features.tax.gst.GstScreen
import com.jyotirmoyb.loan.features.tax.gst.GstViewModel
import com.jyotirmoyb.loan.features.tax.hra.HraScreen
import com.jyotirmoyb.loan.features.tax.hra.HraViewModel
import com.jyotirmoyb.loan.features.tax.incometax.IncomeTaxScreen
import com.jyotirmoyb.loan.features.tax.incometax.IncomeTaxViewModel

internal fun NavGraphBuilder.addMutualFundAndTaxRoutes(
    navController: NavHostController,
    sipViewModel: SipViewModel,
    taxPrefsRepo: UserPreferencesRepository,
    taxRatesRepo: RatesRepository,
    onShowSnackbar: (String) -> Unit
) {
    composable(AppRoute.MfSip.route) {
        val sipState by sipViewModel.uiState.collectAsStateWithLifecycle()
        SipScreen(
            uiState = sipState,
            onUpdateMonthlySip = { sipViewModel.updateMonthlySip(it) },
            onUpdateReturnRate = { sipViewModel.updateReturnRateText(it) },
            onResetReturn = { sipViewModel.resetReturnToDefault() },
            onUpdateStepUp = { sipViewModel.updateStepUpText(it) },
            onUpdateInflation = { sipViewModel.updateInflationText(it) },
            onUpdateTenureYears = { sipViewModel.updateTenureYears(it) }
        )
    }

    composable(AppRoute.MfLumpsum.route) {
        val vm: LumpsumViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LumpsumScreen(
            uiState = state,
            onUpdatePrincipal = { vm.updatePrincipal(it) },
            onUpdateReturnRate = { vm.updateReturnRate(it) },
            onResetReturn = { vm.resetReturn() },
            onUpdateInflation = { vm.updateInflation(it) },
            onUpdateTenureYears = { vm.updateTenureYears(it) }
        )
    }

    composable(AppRoute.MfSwp.route) {
        val vm: SwpViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        SwpScreen(
            uiState = state,
            onUpdateCorpus = { vm.updateCorpus(it) },
            onUpdateWithdrawal = { vm.updateMonthlyWithdrawal(it) },
            onUpdateReturnRate = { vm.updateReturnRate(it) },
            onUpdateStepUp = { vm.updateStepUp(it) },
            onUpdateTenureYears = { vm.updateTenureYears(it) },
            onApplySustainable = { vm.applySustainableWithdrawal() }
        )
    }

    composable(AppRoute.MfGoal.route) {
        val vm: GoalSipViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        GoalSipScreen(
            uiState = state,
            onUpdateTarget = { vm.updateTargetCorpus(it) },
            onUpdateYears = { vm.updateYears(it) },
            onToggleInflationAdjust = { vm.setAdjustForInflation(it) },
            onUpdateReturnRate = { vm.updateReturnRate(it) },
            onUpdateInflation = { vm.updateInflation(it) },
            onUpdateStepUp = { vm.updateStepUp(it) }
        )
    }

    composable(AppRoute.MfXirr.route) {
        val vm: XirrViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.snackbarMessage) {
            state.snackbarMessage?.let {
                onShowSnackbar(it)
                vm.clearSnackbar()
            }
        }
        XirrScreen(
            uiState = state,
            onAddEntry = { d, amt, note -> vm.addEntry(d, amt, note) },
            onRemoveEntry = { vm.removeEntry(it) },
            onImportBulk = { vm.importBulkText(it) },
            onLoadSample = { vm.loadSample() }
        )
    }

    composable(AppRoute.MfTaxGains.route) {
        val vm: MfTaxGainsViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        MfTaxGainsScreen(
            uiState = state,
            onSelectCategory = { vm.selectCategory(it) },
            onUpdateInvested = { vm.updateInvested(it) },
            onUpdateRedemption = { vm.updateRedemption(it) },
            onUpdateHoldingMonths = { vm.updateHoldingMonths(it) },
            onSelectSlab = { vm.selectTaxSlab(it) }
        )
    }

    composable(AppRoute.MfLiveNav.route) {
        val vm: LiveNavViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.snackbarMessage) {
            state.snackbarMessage?.let {
                onShowSnackbar(it)
                vm.clearSnackbar()
            }
        }
        LiveNavScreen(
            uiState = state,
            onSearchQueryChange = { vm.updateSearchQuery(it) },
            onSetFavouritesOnly = { vm.setShowFavouritesOnly(it) },
            onToggleFavourite = { vm.toggleFavourite(it) },
            onSyncNow = { vm.syncNow() },
            onCalculateWithFund = { fundName, navInfo, cagr ->
                sipViewModel.applyFundCagrPreset(fundName, navInfo, cagr)
                navController.navigate(AppRoute.MfSip.route)
            }
        )
    }

    composable(AppRoute.IncomeTax.route) {
        val vm: IncomeTaxViewModel = viewModel(
            factory = remember(taxPrefsRepo, taxRatesRepo) { IncomeTaxViewModel.Factory(taxPrefsRepo, taxRatesRepo) }
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        IncomeTaxScreen(
            uiState = state,
            onSelectFy = { vm.selectFy(it) },
            onUpdateGrossSalary = { vm.updateGrossSalary(it) },
            onUpdateOtherIncome = { vm.updateOtherIncome(it) },
            onToggleSalaried = { vm.toggleSalaried(it) },
            onSelectAgeGroup = { vm.selectAgeGroup(it) },
            onToggleParentsSenior = { vm.toggleParentsSenior(it) },
            onUpdateDeduction = { id, amt -> vm.updateDeduction(id, amt) },
            onSetDeductionFilter = { vm.setDeductionFilter(it) },
            onResetDeductions = { vm.resetDeductions() },
            onToggleSlabBreakdown = { vm.toggleSlabBreakdown() },
            onCheckUpdates = { vm.checkForUpdatedTaxRules() }
        )
    }

    composable(AppRoute.HraCalculator.route) {
        val vm: HraViewModel = viewModel(
            factory = remember(taxPrefsRepo, taxRatesRepo) { HraViewModel.Factory(taxPrefsRepo, taxRatesRepo) }
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        HraScreen(
            uiState = state,
            onSetMonthlyMode = { vm.setMonthlyInputMode(it) },
            onUpdateBasicDa = { vm.updateBasicPlusDa(it) },
            onUpdateHraReceived = { vm.updateHraReceived(it) },
            onUpdateRentPaid = { vm.updateRentPaid(it) },
            onSetMetroCity = { vm.setMetroCity(it) },
            onSelectSlab = { vm.selectMarginalSlab(it) },
            onCheckUpdates = { vm.checkForUpdatedTaxRules() }
        )
    }

    composable(AppRoute.GstCalculator.route) {
        val vm: GstViewModel = viewModel(
            factory = remember(taxPrefsRepo, taxRatesRepo) { GstViewModel.Factory(taxPrefsRepo, taxRatesRepo) }
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        GstScreen(
            uiState = state,
            onUpdateAmount = { vm.updateAmount(it) },
            onUpdateRate = { vm.updateRate(it) },
            onSelectMode = { vm.selectMode(it) },
            onSelectSupplyType = { vm.selectSupplyType(it) },
            onCheckUpdates = { vm.checkForUpdatedTaxRules() }
        )
    }

    composable(AppRoute.AdvanceTaxTds.route) {
        val vm: AdvanceTaxViewModel = viewModel(
            factory = remember(taxPrefsRepo, taxRatesRepo) { AdvanceTaxViewModel.Factory(taxPrefsRepo, taxRatesRepo) }
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        AdvanceTaxScreen(
            uiState = state,
            onSelectTab = { vm.selectTab(it) },
            onSelectFy = { vm.selectFy(it) },
            onUpdateEstimatedAnnualTax = { vm.updateEstimatedAnnualTax(it) },
            onUpdateTdsDeducted = { vm.updateTdsDeducted(it) },
            onTogglePresumptive = { vm.togglePresumptive(it) },
            onToggleSeniorExempt = { vm.toggleSeniorExempt(it) },
            onUpdateFdInterest = { vm.updateFdInterest(it) },
            onToggleFdSenior = { vm.toggleFdSenior(it) },
            onToggleFdHasPan = { vm.toggleFdHasPan(it) },
            onUpdateSalaryAnnualTax = { vm.updateSalaryAnnualTax(it) },
            onUpdateSalaryTdsDeducted = { vm.updateSalaryTdsDeducted(it) },
            onUpdateSalaryRemainingMonths = { vm.updateSalaryRemainingMonths(it) },
            onOpenIncomeTax = { navController.navigate(AppRoute.IncomeTax.route) },
            onOpenMfCapitalGains = { navController.navigate(AppRoute.MfTaxGains.route) },
            onCheckUpdates = { vm.checkForUpdatedTaxRules() }
        )
    }
}
