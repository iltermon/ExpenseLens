package com.iltermon.expenselens.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

object Routes {
    const val EXPENSES = "expenses"
    const val ADD_TRANSACTION = "add_transaction"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: ExpenseLensViewModel
) {
    NavHost(navController = navController, startDestination = Routes.EXPENSES) {
        composable(Routes.EXPENSES) {
            ExpensesScreen(
                viewModel = viewModel,
                onAddTransaction = { navController.navigate(Routes.ADD_TRANSACTION) }
            )
        }
        composable(Routes.ADD_TRANSACTION) {
            AddTransactionScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}