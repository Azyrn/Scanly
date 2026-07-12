@file:OptIn(ExperimentalMaterial3Api::class)

package com.skeler.scanely.ui.screens

import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.skeler.scanely.R
import com.skeler.scanely.core.image.ScanFilter
import com.skeler.scanely.core.pdf.ScanExporter
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.ui.viewmodel.DocumentScanViewModel
import com.skeler.scanely.ui.viewmodel.FilterPreview

@Composable
fun ScanReviewScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val navController = LocalNavController.current
    val vm: DocumentScanViewModel = hiltViewModel(activity)
    val state by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showExportSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }

    val pages = state.pages
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val currentPage = pagerState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { vm.buildPreviews(it) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    LaunchedEffect(state.exportedScan) {
        state.exportedScan?.let { exported ->
            if (!ScanExporter.openDocument(
                    context,
                    exported.uri,
                    exported.format.mimeType,
                    "Open ${exported.format.label} with…"
                )
            ) {
                snackbarHostState.showSnackbar(
                    "No app installed to open ${exported.format.label} files"
                )
            }
            vm.consumeExport()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Review scan", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = pageSummary(pages.size, currentPage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
            if (pages.isEmpty()) {
                EmptyPages(onBack = { navController.popBackStack() })
                return@Box
            }

            Column(Modifier.fillMaxSize()) {
                PageStage(
                    pagerState = pagerState,
                    pages = pages,
                    dimmed = state.isLoading,
                    onRotate = { vm.rotatePage(currentPage) },
                    onDelete = { pendingDelete = currentPage },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                FilterStrip(
                    previews = state.previews,
                    selected = state.filter,
                    onSelect = vm::setFilter
                )

                ExportBar(
                    enabled = !state.isExporting,
                    onClick = { showExportSheet = true }
                )
            }

            if (state.isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (showExportSheet) {
        ExportSheet(
            pageCount = pages.size,
            onPdf = { showExportSheet = false; vm.exportPdf() },
            onWord = { showExportSheet = false; vm.exportWord() },
            onImages = { showExportSheet = false; vm.saveImages() },
            onDismiss = { showExportSheet = false }
        )
    }

    pendingDelete?.let { index ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete page ${index + 1}?") },
            text = { Text("This page won't be included in your export. You can rescan it at any time.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        vm.deletePage(index)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Keep") }
            }
        )
    }
}

@Composable
private fun PageStage(
    pagerState: androidx.compose.foundation.pager.PagerState,
    pages: List<android.graphics.Bitmap>,
    dimmed: Boolean,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        val pageAlpha by animateFloatAsState(if (dimmed) 0.45f else 1f, label = "Page dim")

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            pageSpacing = 8.dp
        ) { index ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.alpha(pageAlpha),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.White,
                    shadowElevation = 8.dp
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

        AnimatedVisibility(
            visible = dimmed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        if (pages.size > 1) {
            PageCounter(
                current = pagerState.currentPage,
                total = pages.size,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 20.dp, top = 8.dp)
            )
        }

        PageTools(
            onRotate = onRotate,
            onDelete = onDelete,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun PageCounter(current: Int, total: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = "${current + 1} / $total",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PageTools(
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onRotate) {
                Icon(
                    Icons.Rounded.Rotate90DegreesCw,
                    contentDescription = "Rotate page",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete page",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun FilterStrip(
    previews: List<FilterPreview>,
    selected: ScanFilter,
    onSelect: (ScanFilter) -> Unit
) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            text = "Enhance",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, bottom = 10.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(previews, key = { _, preview -> preview.filter }) { _, preview ->
                FilterThumbnail(
                    preview = preview,
                    selected = preview.filter == selected,
                    onClick = { onSelect(preview.filter) }
                )
            }
        }
    }
}

@Composable
private fun FilterThumbnail(
    preview: FilterPreview,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp, label = "Thumb border")
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clip(MaterialTheme.shapes.small)
                .background(Color.White)
                .border(borderWidth, borderColor, MaterialTheme.shapes.small)
        ) {
            Image(
                bitmap = preview.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = preview.filter.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.heightIn(min = 32.dp)
        )
    }
}

@Composable
private fun ExportBar(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Text(
                    "Save or share",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "PDF, Word or images in your gallery",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExportSheet(
    pageCount: Int,
    onPdf: () -> Unit,
    onWord: () -> Unit,
    onImages: () -> Unit,
    onDismiss: () -> Unit
) {
    val pageLabel = "$pageCount page${if (pageCount > 1) "s" else ""}"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Save or share",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.height(20.dp))

            ExportOption(
                icon = R.drawable.ic_file_pdf,
                title = "PDF document",
                subtitle = "$pageLabel in one file · opens after saving",
                onClick = onPdf
            )
            Spacer(Modifier.height(8.dp))
            ExportOption(
                icon = R.drawable.ic_file_word,
                title = "Word document",
                subtitle = "$pageLabel as .docx, one scan per page",
                onClick = onWord
            )
            Spacer(Modifier.height(8.dp))
            ExportOption(
                icon = R.drawable.ic_action_gallery_stack,
                title = "Images to gallery",
                subtitle = "$pageLabel as JPEG in your Scanly album",
                onClick = onImages
            )
        }
    }
}

@Composable
private fun ExportOption(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyPages(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Nothing left to review",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Every page was deleted. Scan again to start over.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBack, shape = CircleShape) { Text("Back to scanner") }
    }
}

private fun pageSummary(count: Int, current: Int): String = when {
    count == 0 -> "No pages"
    count == 1 -> "1 page"
    else -> "Page ${current + 1} of $count"
}
