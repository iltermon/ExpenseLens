package com.iltermon.expenselens.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.iltermon.expenselens.R
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val EXPENSES = "expenses"
    const val ANALYTICS = "analytics"
    const val INCOME = "income"
    const val ADD_EXPENSE = "add_expense"
    const val ADD_INCOME = "add_income"
    const val SETTINGS = "settings"
    const val EDIT_TRANSACTION = "edit_transaction"
    const val EDIT_TEMPLATE = "edit_template"
}

private data class BottomNavItem(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.EXPENSES, R.string.nav_expenses, Icons.Filled.Receipt),
    BottomNavItem(Routes.ANALYTICS, R.string.nav_analytics, Icons.Filled.BarChart),
    BottomNavItem(Routes.INCOME, R.string.nav_income, Icons.Filled.Payments),
    BottomNavItem(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: ExpenseLensViewModel,
    onChangeLanguage: (String) -> Unit
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabRoutes = bottomNavItems.map { it.route }
    val busy by viewModel.busy.collectAsState()

    Box(Modifier.fillMaxSize()) {
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
                            icon = { Icon(item.icon, contentDescription = stringResource(item.label)) },
                            label = { Text(stringResource(item.label)) }
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
                        onAddTransaction = { navController.navigate(Routes.ADD_EXPENSE) },
                        onEditTransaction = { id -> navController.navigate("${Routes.EDIT_TRANSACTION}/$id") },
                        onEditTemplate = { id -> navController.navigate("${Routes.EDIT_TEMPLATE}/$id") }
                    )
                }
                composable(Routes.ANALYTICS) {
                    AnalyticsScreen(viewModel = viewModel)
                }
                composable(Routes.INCOME) {
                    IncomeScreen(
                        viewModel = viewModel,
                        onAddIncome = { navController.navigate(Routes.ADD_INCOME) },
                        onEditTransaction = { id -> navController.navigate("${Routes.EDIT_TRANSACTION}/$id") },
                        onEditTemplate = { id -> navController.navigate("${Routes.EDIT_TEMPLATE}/$id") }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(viewModel = viewModel, onChangeLanguage = onChangeLanguage)
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
                composable(
                    route = "${Routes.EDIT_TRANSACTION}/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.IntType })
                ) { entry ->
                    val id = entry.arguments?.getInt("id") ?: return@composable
                    EditTransactionScreen(
                        viewModel = viewModel,
                        transactionId = id,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "${Routes.EDIT_TEMPLATE}/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.IntType })
                ) { entry ->
                    val id = entry.arguments?.getInt("id") ?: return@composable
                    EditTemplateScreen(
                        viewModel = viewModel,
                        templateId = id,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

        if (busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .pointerInput(Unit) {
                        // Swallow every gesture (incl. bottom-nav taps) until the write finishes.
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
