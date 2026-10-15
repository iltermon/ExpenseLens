package com.iltermon.expenselens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.iltermon.expenselens.data.ExpenseLensDatabase
import com.iltermon.expenselens.data.ExpenseLensRepository
import com.iltermon.expenselens.ui.ExpenseLensViewModel
import com.iltermon.expenselens.ui.analytics.AnalyticsViewModel
import com.iltermon.expenselens.ui.dev.ImportViewModel
import com.iltermon.expenselens.ui.util.LocalCurrencySymbol
import com.iltermon.expenselens.ui.util.LocaleManager
import com.iltermon.expenselens.ui.theme.ExpenseLensTheme
import androidx.navigation.compose.rememberNavController
import com.iltermon.expenselens.ui.navigation.AppNavigation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // Created here rather than inside setContent so onResume can reach it; Compose screens receive
    // this same instance (it lives in the activity's ViewModelStore and survives rotation).
    private val repository by lazy { ExpenseLensRepository(ExpenseLensDatabase.getDatabase(this)) }
    private val viewModel: ExpenseLensViewModel by viewModels { ExpenseLensViewModel.factory(repository) }
    // Shares the main ViewModel's month selection; both live in the same ViewModelStore.
    private val analyticsViewModel: AnalyticsViewModel by viewModels {
        AnalyticsViewModel.factory(repository, viewModel.period)
    }
    private val importViewModel: ImportViewModel by viewModels { ImportViewModel.factory(repository) }

    // Apply the saved language to the whole activity before any UI is created.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ExpenseLensTheme {
                val currencySymbol by viewModel.currencySymbol.collectAsState()
                val navController = rememberNavController()

                // Changing the language restarts the app so it comes up cleanly in the new language.
                // Show a spinner over the app until the relaunch happens (no black flash thanks to the
                // window background).
                var restarting by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                CompositionLocalProvider(LocalCurrencySymbol provides currencySymbol) {
                    Box(Modifier.fillMaxSize()) {
                        AppNavigation(
                            navController = navController,
                            viewModel = viewModel,
                            analyticsViewModel = analyticsViewModel,
                            importViewModel = importViewModel,
                            onChangeLanguage = { tag ->
                                restarting = true
                                LocaleManager.setLanguageTag(this@MainActivity, tag)
                                scope.launch {
                                    // Let the spinner render before tearing the app down.
                                    delay(150)
                                    restartApp()
                                }
                            }
                        )

                        // Debug builds run as a separate app with their own database; this marker
                        // makes it obvious which one is on screen so real data isn't edited by mistake.
                        if (BuildConfig.DEBUG) {
                            Text(
                                text = "DEBUG",
                                color = Color.Red.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                            )
                        }

                        if (restarting) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    // App start, return from background, or a date change while away: record any auto-payments
    // that have fallen due since the last run.
    override fun onResume() {
        super.onResume()
        viewModel.refreshAutoPayments()
    }

    /** Relaunches the app from scratch so a new language takes effect everywhere. */
    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
        Runtime.getRuntime().exit(0)
    }
}
