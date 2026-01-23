package com.skeler.scanely.ui.components.product

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skeler.scanely.core.lookup.CosmeticsData

/**
 * Cosmetics-specific product detail content.
 * Displays ingredients, allergens, labels, and categories.
 */
@Composable
fun CosmeticsContentSection(data: CosmeticsData) {
    var hasContent = false
    
    // Ingredients
    data.ingredients?.takeIf { it.isNotBlank() }?.let { ingredients ->
        hasContent = true
        SectionHeader("Ingredients")
        Text(
            text = ingredients,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
    
    // Allergens
    if (data.allergens.isNotEmpty()) {
        hasContent = true
        SectionHeader("Allergens")
        Text(
            text = data.allergens.joinToString(", ") { 
                it.removePrefix("en:").replace("-", " ").replaceFirstChar { c -> c.uppercase() }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
    
    // Labels
    if (data.labels.isNotEmpty()) {
        hasContent = true
        SectionHeader("Labels")
        Text(
            text = data.labels.joinToString(", ") { 
                it.removePrefix("en:").replace("-", " ").replaceFirstChar { c -> c.uppercase() }
            },
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
    
    // Categories
    if (data.categories.isNotEmpty()) {
        hasContent = true
        SectionHeader("Categories")
        Text(
            text = data.categories.joinToString(", ") { 
                it.removePrefix("en:").replace("-", " ").replaceFirstChar { c -> c.uppercase() }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    
    // Fallback if no data available
    if (!hasContent) {
        Text(
            text = "No detailed information available for this product.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
