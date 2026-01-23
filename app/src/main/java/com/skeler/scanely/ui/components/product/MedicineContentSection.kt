package com.skeler.scanely.ui.components.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skeler.scanely.core.lookup.MedicineData

/**
 * Medicine-specific product detail content.
 * Displays generic name, active ingredients, dosage form, route, warnings, and indications.
 */
@Composable
fun MedicineContentSection(data: MedicineData) {
    var hasContent = false
    
    // Generic Name
    data.genericName?.takeIf { it.isNotBlank() }?.let { generic ->
        hasContent = true
        SectionHeader("Generic Name")
        Text(generic, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
    }
    
    // Manufacturer
    data.manufacturer?.takeIf { it.isNotBlank() }?.let { manufacturer ->
        hasContent = true
        SectionHeader("Manufacturer")
        Text(manufacturer, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
    }
    
    // Active Ingredients
    if (data.activeIngredients.isNotEmpty()) {
        hasContent = true
        SectionHeader("Active Ingredients")
        data.activeIngredients.forEach { ingredient ->
            Text("• $ingredient", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
    
    // Dosage Form & Route
    if (data.dosageForm != null || data.route != null) {
        hasContent = true
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            data.dosageForm?.let { InfoColumn("Dosage Form", it) }
            data.route?.let { InfoColumn("Route", it) }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
    
    // Recall Warning
    if (data.isRecalled) {
        hasContent = true
        WarningCard(listOf("⚠️ This product has been recalled by the FDA."))
        Spacer(modifier = Modifier.height(12.dp))
    }
    
    // Warnings
    if (data.warnings.isNotEmpty()) {
        hasContent = true
        WarningCard(data.warnings)
        Spacer(modifier = Modifier.height(12.dp))
    }
    
    // Contraindications
    if (data.contraindications.isNotEmpty()) {
        hasContent = true
        SectionHeader("Contraindications")
        data.contraindications.take(5).forEach { contra ->
            Text("• ${contra.take(150)}", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
    
    // Indications
    data.indications?.takeIf { it.isNotBlank() }?.let { indication ->
        hasContent = true
        SectionHeader("Indications")
        Text(
            text = indication.take(500),
            style = MaterialTheme.typography.bodySmall
        )
    }
    
    // FDA Approval Date
    data.fdaApprovalDate?.takeIf { it.isNotBlank() }?.let { date ->
        hasContent = true
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "FDA approved: $date",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    
    // Fallback if no data
    if (!hasContent) {
        Text(
            text = "No detailed information available for this medicine.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun WarningCard(warnings: List<String>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Warning,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Warnings",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
            warnings.take(3).forEach { warning ->
                Text(
                    text = warning.take(200),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
