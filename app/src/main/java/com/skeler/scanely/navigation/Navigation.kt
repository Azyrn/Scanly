package com.skeler.scanely.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.skeler.scanely.core.animation.galleryPopEnter
import com.skeler.scanely.core.animation.galleryPopExit
import com.skeler.scanely.core.animation.gallerySlideEnter
import com.skeler.scanely.core.animation.gallerySlideExit
import com.skeler.scanely.core.common.LocalSettings
import com.skeler.scanely.history.presentation.screen.HistoryScreen
import com.skeler.scanely.settings.presentation.screen.LookAndFeelScreen
import com.skeler.scanely.settings.presentation.screen.SettingsScreen
import com.skeler.scanely.ui.ScanViewModel
import com.skeler.scanely.ui.screens.CameraScreen
import com.skeler.scanely.ui.screens.HomeScreen
import com.skeler.scanely.ui.screens.ResultsScreen
import com.skeler.scanely.ui.screens.BarcodeScannerScreen
import com.skeler.scanely.ui.screens.UnifiedResultsScreen

object Routes {
    const val HOME = "home"
    const val CAMERA = "camera"
    const val RESULTS = "results"
    const val UNIFIED_RESULTS = "unified_results"
    const val BARCODE_SCANNER = "barcode_scanner"
    const val SETTINGS = "settings"
    const val LOOK_AND_FEEL = "look_and_feel"
    const val HISTORY = "history"
}

@Composable
fun ScanelyNavigation(
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scanViewModel: ScanViewModel = hiltViewModel(activity)

    val ocrLanguages = LocalSettings.current.ocrLanguages

    LaunchedEffect(Unit, ocrLanguages) {
        scanViewModel.updateLanguages(ocrLanguages)
    }

    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            // Smooth fade+scale transitions (no jarring edge clipping)
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

            composable(Routes.HISTORY) {
                HistoryScreen()
            }

            composable(Routes.CAMERA) {
                CameraScreen()
            }

            composable(Routes.RESULTS) {
                ResultsScreen()
            }

            composable(Routes.BARCODE_SCANNER) {
                BarcodeScannerScreen()
            }

            composable(Routes.UNIFIED_RESULTS) {
                UnifiedResultsScreen()
            }
        }
    }
}