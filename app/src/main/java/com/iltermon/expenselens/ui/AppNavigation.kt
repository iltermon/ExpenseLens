package com.iltermon.expenselens.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

object Routes {
    const val EXPENSES = "expenses"
    const val ANALYTICS = "analytics"
    const val INCOME = "income"
    const val ADD_EXPENSE = "add_expense"
    const val ADD_INCOME = "add_income"
    const val SETTINGS = "settings"
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.EXPENSES, "Expenses", Icons.Filled.Receipt),
    BottomNavItem(Routes.ANALYTICS, "Analytics", Icons.Filled.BarChart),
    BottomNavItem(Routes.INCOME, "Income", Icons.Filled.Payments),
    BottomNavItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: ExpenseLensViewModel
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabRoutes = bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (currentRoute in tabRoutes) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // Apply only bottom padding so inner TopAppBars can still extend behind the status bar.
        // consumeWindowInsets tells nested Scaffolds the nav bar insets are already handled.
        val bottomPadding = innerPadding.calculateBottomPadding()
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding)
                .consumeWindowInsets(PaddingValues(bottom = bottomPadding))
        ) {
            NavHost(navController = navController, startDestination = Routes.EXPENSES) {
                composable(Routes.EXPENSES) {
                    ExpensesScreen(
                        viewModel = viewModel,
                        onAddTransaction = { navController.navigate(Routes.ADD_EXPENSE) }
                    )
                }
                composable(Routes.ANALYTICS) {
                    AnalyticsScreen(viewModel = viewModel)
                }
                composable(Routes.INCOME) {
                    IncomeScreen(
                        viewModel = viewModel,
                        onAddIncome = { navController.navigate(Routes.ADD_INCOME) }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(viewModel = viewModel)
                }
                composable(Routes.ADD_EXPENSE) {
                    AddExpenseScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.ADD_INCOME) {
                    AddIncomeScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
