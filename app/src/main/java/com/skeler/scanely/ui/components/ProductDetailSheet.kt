package com.skeler.scanely.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.skeler.scanely.core.lookup.LookupResult
import com.skeler.scanely.core.lookup.ProductCategory
import com.skeler.scanely.core.lookup.ProductInfo
import com.skeler.scanely.ui.components.product.BookContentSection
import com.skeler.scanely.ui.components.product.CosmeticsContentSection
import com.skeler.scanely.ui.components.product.FoodContentSection
import com.skeler.scanely.ui.components.product.MedicineContentSection

/**
 * Polymorphic product detail sheet that renders different layouts
 * based on the product category (Food, Book, Medicine, etc.)
 * 
 * Content sections are extracted to ui/components/product/ for SRP compliance:
 * - FoodContentSection.kt
 * - BookContentSection.kt
 * - MedicineContentSection.kt
 * - CosmeticsContentSection.kt
 * - SharedComponents.kt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailSheet(
    result: LookupResult?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when {
                isLoading -> LoadingContent()
                result is LookupResult.Found -> ProductContent(result.product)
                result is LookupResult.NotFound -> NotFoundContent(result.source)
                result is LookupResult.Error -> ErrorContent(result.exception.message)
                else -> NotFoundContent("Unknown")
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Looking up product...")
        }
    }
}

@Composable
private fun NotFoundContent(source: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Product Not Found",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Searched: $source",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Lookup Failed",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = message ?: "Unknown error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProductContent(product: ProductInfo) {
    ProductHeader(product)
    Spacer(modifier = Modifier.height(16.dp))
    SourceBadge(product.source, product.category)
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))
    
    // Delegate to category-specific extracted components
    when (product.category) {
        ProductCategory.FOOD, ProductCategory.PET_FOOD -> {
            product.foodData?.let { FoodContentSection(it) }
        }
        ProductCategory.BOOK -> {
            product.bookData?.let { BookContentSection(it) }
        }
        ProductCategory.MEDICINE -> {
            product.medicineData?.let { MedicineContentSection(it) }
        }
        ProductCategory.COSMETICS -> {
            product.cosmeticsData?.let { CosmeticsContentSection(it) }
        }
        ProductCategory.GENERIC -> {
            product.description?.let { 
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ProductHeader(product: ProductInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        if (product.imageUrl != null) {
            AsyncImage(
                model = product.imageUrl,
                contentDescription = product.name,
                modifier = Modifier
                    .size(100.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name ?: "Unknown Product",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            product.brand?.let { brand ->
                Text(
                    text = brand,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = product.barcode,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SourceBadge(source: String, category: ProductCategory) {
    val icon: ImageVector = when (category) {
        ProductCategory.FOOD -> Icons.Rounded.Restaurant
        ProductCategory.BOOK -> Icons.Rounded.Book
        ProductCategory.MEDICINE -> Icons.Rounded.LocalHospital
        ProductCategory.COSMETICS -> Icons.Rounded.Spa
        ProductCategory.PET_FOOD -> Icons.Rounded.Pets
        ProductCategory.GENERIC -> Icons.Rounded.Restaurant
    }
    
    AssistChip(
        onClick = { },
        label = { Text("via $source") },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }
    )
}
