package com.jyotirmoyb.loan.navigation

sealed class AppRoute(val route: String) {
    // 5 Primary Bottom Navigation Tabs
    data object CalculatorHub : AppRoute("tab_calculator_hub")
    data object EmiCalculator : AppRoute("tab_emi_calculator")
    data object Articles : AppRoute("tab_articles")
    data object Market : AppRoute("tab_market")
    data object Profile : AppRoute("tab_profile")

    // Feature Destinations
    data object EmiResults : AppRoute("emi_results")
    data object LoanComparison : AppRoute("loan_comparison")
    data object BankStaffLoan : AppRoute("bank_staff_loan")
    data object SavedLoans : AppRoute("saved_loans")
    data object LoanGuide : AppRoute("loan_guide")

    data object ComingSoon : AppRoute("coming_soon/{moduleId}") {
        const val ARG_MODULE_ID = "moduleId"
        fun createRoute(moduleId: String): String = "coming_soon/$moduleId"
    }
}
