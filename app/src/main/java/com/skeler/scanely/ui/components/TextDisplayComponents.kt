package com.skeler.scanely.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Document-style readable text with refined typography.
 * 
 * ULTRATHINK Rationale:
 * - Line height 1.6x improves readability for dense extracted text
 * - Letter spacing 0.025sp enhances character distinction
 * - bodyMedium (16sp) chosen for document-density (Google Docs aesthetic)
 * - RTL-aware via TextDirection.Content for mixed-language documents
 */
@Composable
fun ReadableTextContent(
    text: String,
    modifier: Modifier = Modifier
) {
    val customSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    )

    CompositionLocalProvider(LocalTextSelectionColors provides customSelectionColors) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 26.sp,
                    letterSpacing = 0.025.sp,
                    textDirection = TextDirection.Content
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp)
            )
        }
    }
}

/**
 * Processing indicator with optional multi-file progress.
 */
@Composable
fun ProcessingContent(
    currentFile: Int = 0,
    totalFiles: Int = 0,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (totalFiles > 1) {
                "Processing file $currentFile of $totalFiles..."
            } else {
                "Extracting text..."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (totalFiles > 1) {
                "Processing ${totalFiles} files"
            } else {
                "This may take a few moments"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * Translating indicator.
 */
@Composable
fun TranslatingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Translating...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Empty state for no extracted text.
 */
@Composable
fun EmptyResultContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No text extracted",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Try selecting a different image",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}
