package com.iltermon.expenselens.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.unit.dp
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
    const val SETTINGS_ACCOUNTS = "settings_accounts"
    const val SETTINGS_CATEGORIES = "settings_categories"
    const val SETTINGS_COUNTERPARTIES = "settings_counterparties"
    const val SETTINGS_TEMPLATES = "settings_templates"
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
        NavHost(
            navController = navController,
            startDestination = Routes.EXPENSES,
            modifier = Modifier.fillMaxSize(),
            // Plain crossfade between screens. Combined with the floating nav bar below (which overlays
            // rather than takes layout space), the content height is identical on every route, so
            // navigation never reflows the screen.
            enterTransition = { fadeIn(animationSpec = tween(180)) },
            exitTransition = { fadeOut(animationSpec = tween(180)) },
            popEnterTransition = { fadeIn(animationSpec = tween(180)) },
            popExitTransition = { fadeOut(animationSpec = tween(180)) }
        ) {
            // Tab destinations are wrapped in TabContent so their content clears the floating nav bar.
            composable(Routes.EXPENSES) {
                TabContent {
                    ExpensesScreen(
                        viewModel = viewModel,
                        onAddTransaction = { navController.navigate(Routes.ADD_EXPENSE) },
                        onEditTransaction = { id -> navController.navigate("${Routes.EDIT_TRANSACTION}/$id") },
                        onEditTemplate = { id -> navController.navigate("${Routes.EDIT_TEMPLATE}/$id") }
                    )
                }
            }
            composable(Routes.ANALYTICS) {
                TabContent { AnalyticsScreen(viewModel = viewModel) }
            }
            composable(Routes.INCOME) {
                TabContent {
                    IncomeScreen(
                        viewModel = viewModel,
                        onAddIncome = { navController.navigate(Routes.ADD_INCOME) },
                        onEditTransaction = { id -> navController.navigate("${Routes.EDIT_TRANSACTION}/$id") },
                        onEditTemplate = { id -> navController.navigate("${Routes.EDIT_TEMPLATE}/$id") }
                    )
                }
            }
            composable(Routes.SETTINGS) {
                TabContent {
                    SettingsScreen(
                        viewModel = viewModel,
                        onChangeLanguage = onChangeLanguage,
                        onOpenAccounts = { navController.navigate(Routes.SETTINGS_ACCOUNTS) },
                        onOpenCategories = { navController.navigate(Routes.SETTINGS_CATEGORIES) },
                        onOpenCounterparties = { navController.navigate(Routes.SETTINGS_COUNTERPARTIES) },
                        onOpenTemplates = { navController.navigate(Routes.SETTINGS_TEMPLATES) }
                    )
                }
            }
            // Non-tab destinations fill the full height (no nav bar shown for them).
            composable(Routes.SETTINGS_ACCOUNTS) {
                AccountsScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS_CATEGORIES) {
                CategoriesScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS_COUNTERPARTIES) {
                CounterpartiesScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS_TEMPLATES) {
                TemplatesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onEditTemplate = { id -> navController.navigate("${Routes.EDIT_TEMPLATE}/$id") }
                )
            }
            composable(Routes.ADD_EXPENSE) {
                AddExpenseScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
            }
            composable(Routes.ADD_INCOME) {
                AddIncomeScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
            }
            composable(
                route = "${Routes.EDIT_TRANSACTION}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: return@composable
                EditTransactionScreen(viewModel = viewModel, transactionId = id, onNavigateBack = { navController.popBackStack() })
            }
            composable(
                route = "${Routes.EDIT_TEMPLATE}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: return@composable
                EditTemplateScreen(viewModel = viewModel, templateId = id, onNavigateBack = { navController.popBackStack() })
            }
        }

        // Floating bottom nav bar: it overlays the NavHost instead of occupying a Scaffold bottomBar,
        // so the content height is the same on tab and non-tab routes — opening Add/Edit no longer
        // reflows/jumps the screen. It just slides out of view when leaving a tab.
        AnimatedVisibility(
            visible = currentRoute in tabRoutes,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(animationSpec = tween(180)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(animationSpec = tween(180)) { it } + fadeOut(tween(180))
        ) {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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

/**
 * Wraps a tab destination so its content sits above the floating bottom nav bar (its 80.dp height) and
 * the system navigation inset — and consumes that inset so a nested Scaffold doesn't apply it again.
 * Non-tab destinations skip this and use the full height (no bar is shown for them).
 */
@Composable
private fun TabContent(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) { content() }
}
