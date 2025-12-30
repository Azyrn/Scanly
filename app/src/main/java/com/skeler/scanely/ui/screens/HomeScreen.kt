package com.skeler.scanely.ui.screens

/**
 * Clean & Simple HomeScreen
 * - Center: 3 Main Actions (Camera, Gallery, PDF)
 * - Top: App Bar with Settings
 * - Bottom: History Link
 * - Settings: Theme Selection + Developer Info
 */
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.ui.ScanViewModel
import com.skeler.scanely.ui.components.GalleryPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    scanViewModel: ScanViewModel = hiltViewModel()
) {
    val navController = LocalNavController.current

    val launchGalleryPicker = GalleryPicker { uri ->
        if (uri != null) {
            scanViewModel.onImageSelected(uri)
            navController.navigate(Routes.RESULTS)
        }
    }

    // PDF Picker
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                scanViewModel.onPdfSelected(uri)
                navController.navigate(Routes.RESULTS)
            }
        }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Scanly",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ... (rest of Home content same as before) ...
            Text(
                text = "What would you like to scan?",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(48.dp))

            MainActionButton(
                icon = Icons.Outlined.CameraAlt,
                title = "Capture Photo",
                subtitle = "Scan text using camera",
                onClick = { navController.navigate(Routes.CAMERA) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            MainActionButton(
                icon = Icons.Outlined.PhotoLibrary,
                title = "From Gallery",
                subtitle = "Import image file",
                onClick = { launchGalleryPicker() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            MainActionButton(
                icon = Icons.Outlined.PictureAsPdf,
                title = "Extract PDF",
                subtitle = "Import PDF document",
                onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            MainActionButton(
                icon = Icons.Default.QrCodeScanner,
                title = "Scan Barcode/QR",
                subtitle = "Scan QR, Barcodes & More",
                onClick = { navController.navigate(Routes.BARCODE_SCANNER) }
            )

            Spacer(modifier = Modifier.height(48.dp))

            // History Link
            FilledTonalButton(
                onClick = { navController.navigate(Routes.HISTORY) },
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("View Previous Extracts")
            }
        }
    }
}

// Local map removed, using OcrHelper.SUPPORTED_LANGUAGES_MAP


@Composable
private fun MainActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(20.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
