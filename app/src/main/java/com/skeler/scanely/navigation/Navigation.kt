package com.skeler.scanely.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.skeler.scanely.core.animation.galleryPopEnter
import com.skeler.scanely.core.animation.galleryPopExit
import com.skeler.scanely.core.animation.gallerySlideEnter
import com.skeler.scanely.core.animation.gallerySlideExit
import com.skeler.scanely.core.lookup.LookupOrchestrator
import com.skeler.scanely.history.presentation.screen.HistoryScreen
import com.skeler.scanely.settings.presentation.screen.AiProvidersScreen
import com.skeler.scanely.settings.presentation.screen.LookAndFeelScreen
import com.skeler.scanely.settings.presentation.screen.SettingsScreen
import com.skeler.scanely.settings.presentation.screen.TextRecognitionScreen
import com.skeler.scanely.ui.screens.BarcodeScannerScreen
import com.skeler.scanely.ui.screens.DocumentCaptureScreen
import com.skeler.scanely.ui.screens.HomeScreen
import com.skeler.scanely.ui.screens.ResultsScreen
import com.skeler.scanely.ui.screens.ScanReviewScreen
import com.skeler.scanely.ui.screens.TextComposeScreen
import com.skeler.scanely.ui.screens.UnifiedResultsScreen
import dagger.hilt.android.EntryPointAccessors

object Routes {
    const val HOME = "home"
    const val DOCUMENT_CAPTURE = "document_capture"
    const val SCAN_REVIEW = "scan_review"
    const val TEXT_COMPOSE = "text_compose"
    const val RESULTS = "results"
    const val UNIFIED_RESULTS = "unified_results"
    const val BARCODE_SCANNER = "barcode_scanner"
    const val SETTINGS = "settings"
    const val LOOK_AND_FEEL = "look_and_feel"
    const val AI_PROVIDERS = "ai_providers"
    const val TEXT_RECOGNITION = "text_recognition"
    const val HISTORY = "history"
}

@Composable
fun ScanelyNavigation(
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current

    val lookupOrchestrator = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            LookupOrchestratorEntryPoint::class.java
        ).lookupOrchestrator()
    }

    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = { gallerySlideEnter() },
            exitTransition = { gallerySlideExit() },
            popEnterTransition = { galleryPopEnter() },
            popExitTransition = { galleryPopExit() }
        ) {
            composable(Routes.HOME) {
                HomeScreen()
            }

            composable(Routes.SETTINGS) {
                SettingsScreen()
            }

            composable(Routes.LOOK_AND_FEEL) {
                LookAndFeelScreen()
            }

            composable(Routes.AI_PROVIDERS) {
                AiProvidersScreen()
            }

            composable(Routes.TEXT_RECOGNITION) {
                TextRecognitionScreen()
            }

            composable(Routes.HISTORY) {
                HistoryScreen()
            }

            composable(Routes.DOCUMENT_CAPTURE) {
                DocumentCaptureScreen()
            }

            composable(Routes.SCAN_REVIEW) {
                ScanReviewScreen()
            }

            composable(Routes.TEXT_COMPOSE) {
                TextComposeScreen()
            }

            composable(Routes.RESULTS) {
                ResultsScreen()
            }

            composable(Routes.BARCODE_SCANNER) {
                BarcodeScannerScreen(lookupOrchestrator = lookupOrchestrator)
            }

            composable(Routes.UNIFIED_RESULTS) {
                UnifiedResultsScreen()
            }
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface LookupOrchestratorEntryPoint {
    fun lookupOrchestrator(): LookupOrchestrator
}
