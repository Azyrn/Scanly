@file:OptIn(ExperimentalMaterial3Api::class)

package com.skeler.scanely.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.skeler.scanely.core.image.ScanFilter
import com.skeler.scanely.core.pdf.ScanExporter
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.ui.viewmodel.DocumentScanViewModel

/**
 * Material 3 Expressive review surface for scanned pages: swipe through pages,
 * apply a quick enhancement filter, then export as a high-quality PDF or save
 * the images to the gallery.
 */
@Composable
fun ScanReviewScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val navController = LocalNavController.current
    val vm: DocumentScanViewModel = hiltViewModel(activity)
    val state by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface transient messages.
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    // Offer to open the PDF in a viewer as soon as it's ready.
    LaunchedEffect(state.exportedPdf) {
        state.exportedPdf?.let {
            if (!ScanExporter.openPdf(context, it)) {
                snackbarHostState.showSnackbar("No PDF viewer installed to open the file")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val count = state.pages.size
                    Text(
                        if (count > 0) "Review · $count page${if (count > 1) "s" else ""}"
                        else "Review",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Pages pager
                val pages = state.pages
                if (pages.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { pages.size })
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        pageSpacing = 12.dp
                    ) { index ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = Color.White,
                                shadowElevation = 6.dp
                            ) {
                                Image(
                                    bitmap = pages[index].asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (pages.size > 1) {
                        PageDots(
                            count = pages.size,
                            current = pagerState.currentPage,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 8.dp)
                        )
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "No pages",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Filter chips
                FilterRow(
                    selected = state.filter,
                    onSelect = vm::setFilter
                )

                // Export actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = vm::saveImages,
                        enabled = !state.isExporting && state.pages.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Save Images", fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = vm::exportPdf,
                        enabled = !state.isExporting && state.pages.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Export PDF", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Busy overlay
            if (state.isLoading || state.isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    selected: ScanFilter,
    onSelect: (ScanFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ScanFilter.entries) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}

@Composable
private fun PageDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val active = i == current
            Box(
                modifier = Modifier
                    .size(if (active) 8.dp else 6.dp)
                    .background(
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}
