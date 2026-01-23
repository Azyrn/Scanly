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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skeler.scanely.core.lookup.FoodData

/**
 * Food-specific product detail content.
 * Displays Nutri-Score, NOVA group, nutrition facts, and ingredients.
 */
@Composable
fun FoodContentSection(data: FoodData) {
    var hasContent = false
    
    // Nutri-Score and NOVA
    if (data.nutriScore != null || data.novaGroup != null) {
        hasContent = true
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            data.nutriScore?.let { score ->
                ScoreCard(
                    title = "Nutri-Score",
                    value = score.uppercase(),
                    color = nutriScoreColor(score)
                )
            }
            data.novaGroup?.let { nova ->
                ScoreCard(
                    title = "NOVA Group",
                    value = nova.toString(),
                    color = novaColor(nova)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
    
    // Nutrition facts
    val hasNutrition = listOfNotNull(
        data.calories, data.fat, data.carbs, 
        data.protein, data.sugar, data.salt, data.fiber
    ).isNotEmpty()
    
    if (hasNutrition) {
        hasContent = true
        SectionHeader("Nutrition Facts")
        NutritionGrid(data)
        Spacer(modifier = Modifier.height(16.dp))
    }
    
    // Serving size
    data.servingSize?.takeIf { it.isNotBlank() }?.let { serving ->
        hasContent = true
        Text(
            text = "Serving size: $serving",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
    
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
    }
    
    // Fallback if no data
    if (!hasContent) {
        Text(
            text = "No detailed nutrition information available for this product.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun NutritionGrid(data: FoodData) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        data.calories?.let { NutritionRow("Calories", it) }
        data.fat?.let { NutritionRow("Fat", it) }
        data.carbs?.let { NutritionRow("Carbs", it) }
        data.protein?.let { NutritionRow("Protein", it) }
        data.sugar?.let { NutritionRow("Sugar", it) }
        data.salt?.let { NutritionRow("Salt", it) }
        data.fiber?.let { NutritionRow("Fiber", it) }
    }
}

@Composable
private fun NutritionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
