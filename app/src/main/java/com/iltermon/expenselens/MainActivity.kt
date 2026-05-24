package com.iltermon.expenselens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iltermon.expenselens.data.ExpenseLensDatabase
import com.iltermon.expenselens.data.ExpenseLensRepository
import com.iltermon.expenselens.ui.ExpenseLensViewModel
import com.iltermon.expenselens.ui.MainScreen
import com.iltermon.expenselens.ui.theme.ExpenseLensTheme
import androidx.navigation.compose.rememberNavController
import com.iltermon.expenselens.ui.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = ExpenseLensDatabase.getDatabase(this)
        val repository = ExpenseLensRepository(database)

        setContent {
            ExpenseLensTheme {
                val viewModel: ExpenseLensViewModel = viewModel(
                    factory = ExpenseLensViewModel.factory(repository)
                )
                val navController = rememberNavController()
                AppNavigation(navController = navController, viewModel = viewModel)
            }
        }
    }
}