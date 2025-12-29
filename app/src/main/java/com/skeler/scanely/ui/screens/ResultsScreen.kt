@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.screens

import android.content.ClipData
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.skeler.scanely.ocr.OcrResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Material 3 Results Screen
 * - Selectable Text Container
 * - Copy Button only (Share removed)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    imageUri: Uri?,
    ocrResult: OcrResult?,
    isProcessing: Boolean,
    progressMessage: String = "",
    pdfThumbnail: Bitmap? = null,
    onNavigateBack: () -> Unit = {},
    onCopyText: (String) -> Unit = {}
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showContent by remember { mutableStateOf(false) }
    val topBarScrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    var lastScrollOffset by remember { mutableIntStateOf(0) }
    val isFabExpanded by remember {
        derivedStateOf {
            listState.shouldFabExpand(lastScrollOffset) {
                lastScrollOffset = it
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                scrollBehavior = topBarScrollBehavior,
                title = {
                    val collapsedFraction = topBarScrollBehavior.state.collapsedFraction
                    val expandedFontSize = 33.sp
                    val collapsedFontSize = 20.sp

                    val fontSize = lerp(expandedFontSize, collapsedFontSize, collapsedFraction)
                    Text(
                        modifier = Modifier.basicMarquee(),
                        text = "Results",
                        maxLines = 1,
                        fontSize = fontSize,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.05.em
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isProcessing && ocrResult != null && ocrResult.text.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    modifier = Modifier.padding(bottom = 10.dp),
                    expanded = isFabExpanded,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy"
                        )
                    },
                    text = {
                        Text("Copy")
                    },
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(
                                    ClipData.newPlainText(
                                        "OCR result",
                                        AnnotatedString(ocrResult.text)
                                    )
                                )
                            )
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT)
                                .show()
                        }
                        onCopyText(ocrResult.text)
                    }
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .nestedScroll(topBarScrollBehavior.nestedScrollConnection),
            state = listState

        ) {
            // Image Preview Card
            item {
                AnimatedVisibility(
                    visible = showContent && (imageUri != null || pdfThumbnail != null),
                    enter = fadeIn(tween(300)) + slideInVertically(
                        initialOffsetY = { it / 4 }
                    )
                ) {
                    ImagePreviewCard(imageUri = imageUri, pdfThumbnail = pdfThumbnail)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Results Card
            item {
                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn(tween(400, delayMillis = 150)) + slideInVertically(
                        initialOffsetY = { it / 4 }
                    )
                ) {
                    ResultsCard(
                        ocrResult = ocrResult,
                        isProcessing = isProcessing,
                        progressMessage = progressMessage
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(75.dp)) }
        }
    }
}

@Composable
private fun ImagePreviewCard(imageUri: Uri?, pdfThumbnail: Bitmap? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.large
    ) {
        when {
            // Show PDF thumbnail if available
            pdfThumbnail != null -> {
                Image(
                    bitmap = pdfThumbnail.asImageBitmap(),
                    contentDescription = "PDF preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Crop
                )
            }
            // Show image via Coil
            imageUri != null -> {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Captured image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun ResultsCard(
    ocrResult: OcrResult?,
    isProcessing: Boolean,
    progressMessage: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            if (!isProcessing) {
                Text(
                    text = "Extracted Text",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isProcessing -> {
                    ProcessingContent(progressMessage)
                }

                ocrResult != null && ocrResult.text.isNotEmpty() -> {
                    ExtractedTextContent(ocrResult = ocrResult)
                }

                ocrResult != null && ocrResult.text.isEmpty() -> {
                    EmptyResultContent()
                }

                else -> {
                    WaitingContent()
                }
            }
        }
    }
}

@Composable
private fun ProcessingContent(progressMessage: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularWavyProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = progressMessage.ifEmpty { "Extracting text..." },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This may take a few moments",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun ExtractedTextContent(ocrResult: OcrResult) {
    val hasArabic = ocrResult.languages.contains("ara") ||
            ocrResult.text.any { it.code in 0x0600..0x06FF }

    val textDirection = if (hasArabic) TextDirection.Rtl else TextDirection.Ltr

    // Custom selection colors for better visibility
    val customSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    )

    Column {
        CompositionLocalProvider(
            LocalTextSelectionColors provides customSelectionColors
        ) {
            SelectionContainer {
                Text(
                    text = ocrResult.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = textDirection,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5f
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = if (hasArabic) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Metadata row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Confidence: ${ocrResult.confidence}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "Time: ${ocrResult.processingTimeMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun EmptyResultContent() {
    Text(
        text = "No text detected in this image.\nTry with a clearer image or different lighting.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    )
}

@Composable
private fun WaitingContent() {
    Text(
        text = "Select an image to extract text.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    )
}

private fun LazyListState.shouldFabExpand(
    lastScrollOffset: Int,
    onScrollOffsetChanged: (Int) -> Unit
): Boolean {
    val currentOffset = firstVisibleItemIndex * 1000 + firstVisibleItemScrollOffset
    val isScrollingUp = currentOffset < lastScrollOffset
    val isScrollingDown = currentOffset > lastScrollOffset
    onScrollOffsetChanged(currentOffset)

    val atTop = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    val lastVisible = visible.lastOrNull()

    val fitsOnScreen = if (info.totalItemsCount == 0 || lastVisible == null) {
        true
    } else {
        val allItemsVisible = info.totalItemsCount == visible.size
        val lastBottom = lastVisible.offset + lastVisible.size
        allItemsVisible && lastBottom <= info.viewportEndOffset
    }

    return when {
        atTop -> true
        fitsOnScreen -> true
        isScrollingUp -> true
        isScrollingDown -> false
        else -> false
    }
}