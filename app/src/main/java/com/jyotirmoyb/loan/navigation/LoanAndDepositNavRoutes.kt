package com.jyotirmoyb.loan.navigation

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.jyotirmoyb.loan.domain.FdMode
import com.jyotirmoyb.loan.domain.LoanCatalog
import com.jyotirmoyb.loan.features.bankstaff.BankStaffLoanScreen
import com.jyotirmoyb.loan.features.bankstaff.BankStaffViewModel
import com.jyotirmoyb.loan.features.deposits.compare.DepositComparisonScreen
import com.jyotirmoyb.loan.features.deposits.compare.DepositComparisonViewModel
import com.jyotirmoyb.loan.features.deposits.fd.FixedDepositScreen
import com.jyotirmoyb.loan.features.deposits.fd.FixedDepositViewModel
import com.jyotirmoyb.loan.features.deposits.rd.RecurringDepositScreen
import com.jyotirmoyb.loan.features.deposits.rd.RecurringDepositViewModel
import com.jyotirmoyb.loan.features.deposits.smallsavings.SmallSavingsScreen
import com.jyotirmoyb.loan.features.deposits.smallsavings.SmallSavingsViewModel
import com.jyotirmoyb.loan.features.emi.EmiViewModel
import com.jyotirmoyb.loan.features.loans.balancetransfer.BalanceTransferScreen
import com.jyotirmoyb.loan.features.loans.balancetransfer.BalanceTransferViewModel
import com.jyotirmoyb.loan.features.loans.comparison.LoanComparisonScreen
import com.jyotirmoyb.loan.features.loans.comparison.LoanComparisonViewModel
import com.jyotirmoyb.loan.features.loans.eligibility.EligibilityScreen
import com.jyotirmoyb.loan.features.loans.eligibility.EligibilityViewModel
import com.jyotirmoyb.loan.features.loans.partpayment.PartPaymentPlannerScreen
import com.jyotirmoyb.loan.features.loans.partpayment.PartPaymentPlannerViewModel
import com.jyotirmoyb.loan.features.loans.taxbenefit.LoanTaxBenefitScreen
import com.jyotirmoyb.loan.features.loans.taxbenefit.LoanTaxBenefitViewModel
import com.jyotirmoyb.loan.features.myloans.MyLoansViewModel
import com.jyotirmoyb.loan.ui.screens.ExploreScreen
import com.jyotirmoyb.loan.ui.screens.MyLoansScreen

internal fun NavGraphBuilder.addLoanAndDepositRoutes(
    context: Context,
    navController: NavHostController,
    emiViewModel: EmiViewModel,
    catalog: LoanCatalog,
    navigateToTab: (String) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    composable(AppRoute.LoanComparison.route) {
        val vm: LoanComparisonViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.snackbarMessage) {
            state.snackbarMessage?.let {
                onShowSnackbar(it)
                vm.clearSnackbar()
            }
        }
        LoanComparisonScreen(
            uiState = state,
            onToggleSelect = { vm.toggleSelection(it) },
            onDeleteScenario = { vm.deleteScenario(it) },
            onSeedFromSavedLoan = { vm.seedFromSavedLoan(it) },
            onSeedFromCatalogVariant = { cat, variant -> vm.seedFromCatalogVariant(cat, variant) },
            onLoadQuickPresets = { vm.addQuickIndianHomePresets() },
            onSharePdf = { vm.exportPdf(context) },
            onShareImage = { vm.exportImage(context) }
        )
    }

    composable(AppRoute.LoanEligibility.route) {
        val vm: EligibilityViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        EligibilityScreen(
            uiState = state,
            onSetReverseMode = { vm.setReverseMode(it) },
            onUpdateMonthlyIncome = { vm.updateMonthlyIncome(it) },
            onUpdateExistingEmis = { vm.updateExistingEmis(it) },
            onUpdateAge = { vm.updateAge(it) },
            onUpdateRetirementAge = { vm.updateRetirementAge(it) },
            onUpdateRate = { vm.updateRate(it) },
            onResetRate = { vm.resetRateToDefault() },
            onUpdateTenureYears = { vm.updateTenureYears(it) },
            onUpdateFoir = { vm.updateCustomFoir(it) },
            onResetFoir = { vm.resetFoirToDefault() },
            onSelectCibil = { vm.selectCibilBand(it) },
            onUpdateReverseBudget = { vm.updateReverseEmiBudget(it) }
        )
    }

    composable(AppRoute.BalanceTransfer.route) {
        val vm: BalanceTransferViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        BalanceTransferScreen(
            uiState = state,
            onUpdateInput = { vm.updateInput(it) },
            onUpdateNewRate = { vm.updateNewRate(it) },
            onResetNewRate = { vm.resetNewRateToDefault() },
            onResetGstRate = { vm.resetGstRateToDefault() }
        )
    }

    composable(AppRoute.LoanTaxBenefit.route) {
        val vm: LoanTaxBenefitViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.snackbarMessage) {
            state.snackbarMessage?.let {
                onShowSnackbar(it)
                vm.clearSnackbar()
            }
        }
        LoanTaxBenefitScreen(
            uiState = state,
            onUpdateInput = { vm.updateInput(it) },
            onResetCap = { vm.resetTaxCap(it) }
        )
    }

    composable(AppRoute.PartPaymentPlanner.route) {
        val vm: PartPaymentPlannerViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        PartPaymentPlannerScreen(
            uiState = state,
            onUpdatePrincipal = { vm.updatePrincipal(it) },
            onUpdateRate = { vm.updateRate(it) },
            onResetRate = { vm.resetRateToDefault() },
            onUpdateRemainingMonths = { vm.updateRemainingMonths(it) },
            onSetTargetYearMonth = { y, m -> vm.setTargetYearMonth(y, m) },
            onApplyYearsEarlier = { vm.applyYearsEarlierPreset(it) }
        )
    }

    composable(AppRoute.BankStaffLoan.route) {
        val vm: BankStaffViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.snackbarMessage) {
            state.snackbarMessage?.let {
                onShowSnackbar(it)
                vm.clearSnackbar()
            }
        }
        BankStaffLoanScreen(
            uiState = state,
            onBack = { navController.popBackStack() },
            onSelectPreset = { vm.selectPreset(it) },
            onUpdateAmount = { vm.updateAmount(it) },
            onUpdateStaffRate = { vm.updateStaffRate(it) },
            onUpdateMarketRate = { vm.updateMarketRate(it) },
            onUpdateTenureMonths = { vm.updateTenureMonths(it) },
            onViewFullSchedule = {
                val staffInput = vm.buildCurrentStaffInput()
                emiViewModel.updateInput { staffInput }
                navController.navigate(AppRoute.EmiResults.route)
            },
            onSaveStaffLoan = { vm.saveStaffLoan() },
            onCompareWithMarket = {
                vm.addBothToCompare()
                navController.navigate(AppRoute.LoanComparison.route)
            },
            onSaveTemplateOverride = { preset, r, a, t -> vm.saveTemplateOverride(preset, r, a, t) },
            onResetTemplateToDefault = { preset -> vm.resetTemplateToDefault(preset) }
        )
    }

    composable(AppRoute.SavedLoans.route) {
        val vm: MyLoansViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.snackbarMessage) {
            state.snackbarMessage?.let {
                onShowSnackbar(it)
                vm.clearSnackbar()
            }
        }
        MyLoansScreen(
            loans = state.loans,
            metrics = state.portfolioMetrics,
            monthlyIncome = state.monthlyIncome,
            currency = state.currency,
            onMarkNextEmiPaid = { vm.markNextEmiPaid(it) },
            onToggleEmiPaid = { loan, m -> vm.toggleEmiPaid(loan, m) },
            onLogPrepayment = { loan, m, amt, redTen -> vm.logActualPrepayment(loan, m, amt, redTen) },
            onLogRateChange = { loan, afterM, r -> vm.logActualRateChange(loan, afterM, r) },
            onToggleAutoDebitNach = { loan, en -> vm.toggleAutoDebitNach(loan, en) },
            onUpdateEmiDay = { loan, day -> vm.updateEmiDayOfMonth(loan, day) },
            onToggleReminder = { loan, en, days -> vm.toggleLoanReminder(loan, en, days) },
            onToggleChecklist = { loan, idx -> vm.toggleChecklistItem(loan, idx) },
            onAddToCompare = { vm.addLoanToCompare(it) },
            onDeleteLoan = { vm.deleteLoan(it) },
            onOpenCalculator = { navigateToTab(AppRoute.EmiCalculator.route) },
            onShowMessage = onShowSnackbar
        )
    }

    composable(AppRoute.LoanGuide.route) {
        ExploreScreen(
            catalog = catalog,
            onUseInCalculator = { cat, variant ->
                emiViewModel.selectCategory(cat, preselectedVariant = variant)
                navigateToTab(AppRoute.EmiCalculator.route)
            }
        )
    }

    composable(AppRoute.FixedDeposit.route) {
        val vm: FixedDepositViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { vm.setMode(FdMode.CUMULATIVE) }
        LaunchedEffect(state.snackbarMessage) {
            state.snackbarMessage?.let {
                onShowSnackbar(it)
                vm.clearSnackbar()
            }
        }
        FixedDepositScreen(
            uiState = state,
            onSetMode = { vm.setMode(it) },
            onUpdatePrincipal = { vm.updatePrincipal(it) },
            onUpdateRateText = { vm.updateRateText(it) },
            onResetRate = { vm.resetRateToDefault() },
            onSetSeniorCitizen = { vm.setSeniorCitizen(it) },
            onSetCompounding = { vm.setCompounding(it) },
            onSetPayoutFrequency = { vm.setPayoutFrequency(it) },
            onUpdateTenureMonths = { vm.updateTenureMonths(it) },
            onSetHasPan = { vm.setHasPan(it) },
            onSetTaxSlab = { vm.setTaxSlab(it) }
        )
    }

    composable(AppRoute.FdInterestPayout.route) {
        val vm: FixedDepositViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { vm.setMode(FdMode.PAYOUT) }
        LaunchedEffect(state.snackbarMessage) {
            state.snackbarMessage?.let {
                onShowSnackbar(it)
                vm.clearSnackbar()
            }
        }
        FixedDepositScreen(
            uiState = state,
            onSetMode = { vm.setMode(it) },
            onUpdatePrincipal = { vm.updatePrincipal(it) },
            onUpdateRateText = { vm.updateRateText(it) },
            onResetRate = { vm.resetRateToDefault() },
            onSetSeniorCitizen = { vm.setSeniorCitizen(it) },
            onSetCompounding = { vm.setCompounding(it) },
            onSetPayoutFrequency = { vm.setPayoutFrequency(it) },
            onUpdateTenureMonths = { vm.updateTenureMonths(it) },
            onSetHasPan = { vm.setHasPan(it) },
            onSetTaxSlab = { vm.setTaxSlab(it) }
        )
    }

    composable(AppRoute.RecurringDeposit.route) {
        val vm: RecurringDepositViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.snackbarMessage) {
            state.snackbarMessage?.let {
                onShowSnackbar(it)
                vm.clearSnackbar()
            }
        }
        RecurringDepositScreen(
            uiState = state,
            onUpdateMonthlyDeposit = { vm.updateMonthlyDeposit(it) },
            onUpdateRateText = { vm.updateRateText(it) },
            onResetRate = { vm.resetRateToDefault() },
            onSetSeniorCitizen = { vm.setSeniorCitizen(it) },
            onUpdateTenureMonths = { vm.updateTenureMonths(it) },
            onSetHasPan = { vm.setHasPan(it) },
            onSetTaxSlab = { vm.setTaxSlab(it) }
        )
    }

    composable(AppRoute.SmallSavings.route) {
        val vm: SmallSavingsViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.snackbarMessage) {
            state.snackbarMessage?.let {
                onShowSnackbar(it)
                vm.clearSnackbar()
            }
        }
        SmallSavingsScreen(
            uiState = state,
            onSelectScheme = { vm.selectScheme(it) },
            onUpdateContribution = { vm.updateContribution(it) },
            onSetPpfExtensionBlocks = { vm.setPpfExtensionBlocks(it) },
            onSetPpfContributeDuringExtension = { vm.setPpfContributeDuringExtension(it) },
            onSetPoMisJoint = { vm.setPoMisJoint(it) },
            onSetPoTdYears = { vm.setPoTdYears(it) },
            onOverrideActiveRate = { vm.overrideActiveRate(it) },
            onResetActiveRate = { vm.resetActiveRateToDefault() }
        )
    }

    composable(AppRoute.DepositComparison.route) {
        val vm: DepositComparisonViewModel = viewModel()
        val state by vm.uiState.collectAsStateWithLifecycle()
        DepositComparisonScreen(
            uiState = state,
            onUpdateMonthly = { vm.updateMonthly(it) },
            onUpdateHorizonYears = { vm.updateHorizonYears(it) },
            onUpdateTaxSlab = { vm.updateTaxSlab(it) },
            onUpdateFdRate = { vm.updateFdRate(it) },
            onUpdateRdRate = { vm.updateRdRate(it) },
            onUpdatePpfRate = { vm.updatePpfRate(it) },
            onUpdateDebtRate = { vm.updateDebtRate(it) },
            onResetRates = { vm.resetRatesToDefault() }
        )
    }
}
