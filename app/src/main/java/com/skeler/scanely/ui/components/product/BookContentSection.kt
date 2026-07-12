package com.skeler.scanely.ui.components.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skeler.scanely.core.lookup.BookData

@Composable
fun BookContentSection(data: BookData) {
    var hasContent = false

    if (data.authors.isNotEmpty()) {
        hasContent = true
        SectionHeader("Authors")
        Text(
            text = data.authors.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    val hasMetadata = data.publisher != null || data.publishedDate != null || data.pageCount != null
    if (hasMetadata) {
        hasContent = true
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            data.publisher?.let { InfoColumn("Publisher", it) }
            data.publishedDate?.let { InfoColumn("Published", it) }
            data.pageCount?.let { InfoColumn("Pages", it.toString()) }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    data.language?.takeIf { it.isNotBlank() }?.let { language ->
        hasContent = true
        InfoColumn("Language", language.uppercase())
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (data.categories.isNotEmpty()) {
        hasContent = true
        SectionHeader("Categories")
        Text(
            text = data.categories.joinToString(", "),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    Column {
        data.isbn13?.let { isbn ->
            hasContent = true
            Text(
                text = "ISBN-13: $isbn",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        data.isbn10?.let { isbn ->
            hasContent = true
            Text(
                text = "ISBN-10: $isbn",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (!hasContent) {
        Text(
            text = "No detailed information available for this book.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
