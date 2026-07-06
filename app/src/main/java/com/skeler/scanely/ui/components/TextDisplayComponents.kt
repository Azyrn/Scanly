@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skeler.scanely.core.ai.AiStage

/**
 * Document-style readable text tuned for long passages of extracted text.
 *
 * Readability rationale:
 * - 17sp reading size with a generous 28sp line height (~1.65x) reduces
 *   crowding on dense OCR output.
 * - onSurface gives the strongest text-on-background contrast in the scheme.
 * - Paragraphs (split on blank lines) are spaced apart so the eye can track
 *   where one block ends and the next begins — real typographic hierarchy
 *   instead of an undifferentiated wall of text.
 * - RTL-aware via TextDirection.Content for mixed-language documents.
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

    // Split into paragraphs on blank lines; keep the whole text as one block
    // when it has no clear paragraph breaks.
    val paragraphs = remember(text) {
        text.trim()
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(text.trim()) }
    }

    val readingStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 17.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.015.sp,
        textDirection = TextDirection.Content
    )

    CompositionLocalProvider(LocalTextSelectionColors provides customSelectionColors) {
        SelectionContainer {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph,
                        style = readingStyle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Editable twin of [ReadableTextContent] for correcting imperfect OCR. Keeps the
 * exact reading typography (17sp / 28sp, onSurface) so switching into edit mode
 * doesn't reflow or restyle the text; only the caret and a subtle field
 * background appear. Edits are staged locally — the caller commits them on Save.
 */
@Composable
fun EditableReadableText(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val readingStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 17.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.015.sp,
        textDirection = TextDirection.Content,
        color = MaterialTheme.colorScheme.onSurface
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = readingStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    )
}

/**
 * Processing indicator with stage progress, an optional live preview of the
 * text streaming in, a rate-limit/retry note, and a Cancel action.
 *
 * All stage parameters are optional so non-AI callers (plain OCR) can keep
 * using the simple spinner form.
 */
@Composable
fun ProcessingContent(
    currentFile: Int = 0,
    totalFiles: Int = 0,
    stage: AiStage? = null,
    stageMessage: String? = null,
    streamingText: String? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LoadingIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = when (stage) {
                AiStage.PREPARING -> "Preparing…"
                AiStage.UPLOADING -> "Uploading…"
                AiStage.PROCESSING -> "Processing…"
                AiStage.GENERATING -> "Generating…"
                AiStage.COMPLETE -> "Complete"
                null -> if (totalFiles > 1) {
                    "Processing file $currentFile of $totalFiles..."
                } else {
                    "Extracting text..."
                }
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (stage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            val stageProgress by animateFloatAsState(
                targetValue = (stage.ordinal + 1) / 5f,
                animationSpec = tween(300),
                label = "StageProgress"
            )
            LinearProgressIndicator(
                progress = { stageProgress },
                modifier = Modifier
                    .width(220.dp)
                    .clip(RoundedCornerShape(50))
            )
        }

        if (totalFiles > 1 && stage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "File $currentFile of $totalFiles",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            // Retry/fallback notes ("OpenRouter is busy — retrying in 3 s")
            // replace the generic hint the moment there is something to say.
            text = stageMessage ?: "This may take a few moments",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (stageMessage != null) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        // Live tail of the streamed text, so long extractions show visible
        // progress instead of an opaque spinner.
        if (!streamingText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = streamingText.takeLast(400).trimStart(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (onCancel != null) {
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
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
        LoadingIndicator(modifier = Modifier.size(48.dp))
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
