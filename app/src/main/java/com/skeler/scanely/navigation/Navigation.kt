package com.skeler.scanely.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.skeler.scanely.core.common.LocalSettings
import com.skeler.scanely.ocr.OcrResult
import com.skeler.scanely.settings.presentation.screen.SettingsScreen
import com.skeler.scanely.ui.ScanViewModel
import com.skeler.scanely.ui.screens.BarcodeScannerScreen
import com.skeler.scanely.ui.screens.CameraScreen
import com.skeler.scanely.history.presentation.screen.HistoryScreen
import com.skeler.scanely.ui.screens.HomeScreen
import com.skeler.scanely.ui.screens.ResultsScreen

object Routes {
    const val HOME = "home"
    const val CAMERA = "camera"
    const val RESULTS = "results"
    const val BARCODE_SCANNER = "barcode_scanner"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
}

@Composable
fun ScanelyNavigation(
    navController: NavHostController = rememberNavController(),
    scanViewModel: ScanViewModel = hiltViewModel()
) {
    val ocrLanguages = LocalSettings.current.ocrLanguages



    LaunchedEffect(Unit, ocrLanguages) {
        scanViewModel.updateLanguages(ocrLanguages)
    }

    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
        ) {
            composable(Routes.HOME) {
                HomeScreen()
            }

            composable(Routes.SETTINGS) {
                SettingsScreen()
            }

            composable(Routes.HISTORY) {
                HistoryScreen()
            }

            composable(Routes.CAMERA) {
                CameraScreen(
                    onImageCaptured = { uri ->
                        scanViewModel.onImageSelected(uri)
                        navController.navigate(Routes.RESULTS) {
                            popUpTo(Routes.HOME)
                        }
                    }
                )
            }

            composable(Routes.RESULTS) {
                ResultsScreen()
            }

            composable(Routes.BARCODE_SCANNER) {
                BarcodeScannerScreen(
                    onBarcodeScanned = { barcodeResult ->
                        // Map BarcodeResult to OcrResult
                        val ocrResult = OcrResult(
                            text = barcodeResult.displayValue,
                            confidence = 100,
                            languages = listOf("Barcode: ${barcodeResult.formatName}"),
                            processingTimeMs = 0
                        )
                        scanViewModel.onBarcodeScanned(ocrResult)
                        navController.navigate(Routes.RESULTS)
                    }
                )
            }
        }
    }
}