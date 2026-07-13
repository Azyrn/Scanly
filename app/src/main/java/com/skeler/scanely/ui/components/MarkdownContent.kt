package com.skeler.scanely.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skeler.scanely.core.text.MarkdownParser
import com.skeler.scanely.core.text.MdBlock

/** Renders the AI's Markdown the way it read the page: headings, emphasis, lists, table grids. */
@Composable
fun MarkdownContent(
    text: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    val selectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    )

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        SelectionContainer {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                blocks.forEach { block -> MarkdownBlock(block) }
            }
        }
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock) {
    when (block) {
        is MdBlock.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }
            Text(
                text = inline(block.text),
                style = style.copy(textDirection = TextDirection.Content),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        is MdBlock.Paragraph -> Text(
            text = inline(block.lines.joinToString("\n")),
            style = bodyStyle(),
            color = MaterialTheme.colorScheme.onSurface
        )

        is MdBlock.Bullet -> Row(
            modifier = Modifier.padding(start = (block.indent * 6).dp),
            verticalAlignment = androidx.compose.ui.Alignment.Top
        ) {
            val marker = when (block.checked) {
                true -> "☑"
                false -> "☐"
                null -> "•"
            }
            Text(
                text = marker,
                style = bodyStyle(),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(20.dp)
            )
            Text(
                text = inline(block.text),
                style = bodyStyle(),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        is MdBlock.Numbered -> Row(
            modifier = Modifier.padding(start = (block.indent * 6).dp),
            verticalAlignment = androidx.compose.ui.Alignment.Top
        ) {
            Text(
                text = block.marker,
                style = bodyStyle(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(26.dp)
            )
            Text(
                text = inline(block.text),
                style = bodyStyle(),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        is MdBlock.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = inline(block.lines.joinToString("\n")),
                style = bodyStyle(),
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is MdBlock.Code -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = block.text,
                style = bodyStyle().copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        is MdBlock.Table -> MarkdownTable(block)

        MdBlock.Divider -> HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun MarkdownTable(table: MdBlock.Table) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val widths = remember(table) {
        table.header.indices.map { column ->
            val longest = (listOf(table.header) + table.rows)
                .maxOf { row -> row.getOrElse(column) { "" }.length }
            (longest * 9 + 24).coerceIn(64, 220).dp
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, outline, RoundedCornerShape(12.dp))
        ) {
            TableRow(
                cells = table.header,
                widths = widths,
                outline = outline,
                background = MaterialTheme.colorScheme.surfaceContainerHigh,
                bold = true
            )
            table.rows.forEachIndexed { index, row ->
                HorizontalDivider(thickness = 1.dp, color = outline)
                TableRow(
                    cells = row,
                    widths = widths,
                    outline = outline,
                    background = if (index % 2 == 0) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    bold = false
                )
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    widths: List<androidx.compose.ui.unit.Dp>,
    outline: Color,
    background: Color,
    bold: Boolean
) {
    Row(
        modifier = Modifier
            .background(background)
            .height(IntrinsicSize.Min)
    ) {
        widths.forEachIndexed { column, width ->
            if (column > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(outline)
                )
            }
            Text(
                text = inline(cells.getOrElse(column) { "" }),
                style = bodyStyle(),
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .width(width)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun bodyStyle() = MaterialTheme.typography.bodyLarge.copy(
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.01.sp,
    textDirection = TextDirection.Content
)

@Composable
private fun inline(text: String): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(text, codeBackground, linkColor) {
        buildAnnotatedString {
            MarkdownParser.parseInline(text).forEach { span ->
                val decorations = listOfNotNull(
                    TextDecoration.Underline.takeIf { span.underline || span.link != null },
                    TextDecoration.LineThrough.takeIf { span.strike }
                )
                withStyle(
                    SpanStyle(
                        fontWeight = if (span.bold) FontWeight.Bold else null,
                        fontStyle = if (span.italic) FontStyle.Italic else null,
                        fontFamily = if (span.code) FontFamily.Monospace else null,
                        background = if (span.code) codeBackground else Color.Unspecified,
                        color = if (span.link != null) linkColor else Color.Unspecified,
                        textDecoration = decorations.takeIf { it.isNotEmpty() }
                            ?.let { TextDecoration.combine(it) }
                    )
                ) {
                    append(span.text)
                }
            }
        }
    }
}
