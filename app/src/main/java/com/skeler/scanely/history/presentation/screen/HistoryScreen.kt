@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.history.presentation.screen

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.skeler.scanely.core.presentation.components.svg.DynamicColorImageVectors
import com.skeler.scanely.core.presentation.components.svg.vectors.noSearchResult
import com.skeler.scanely.history.data.HistoryItem
import com.skeler.scanely.history.presentation.HistoryViewModel
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.ui.ScanViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scanViewModel: ScanViewModel = hiltViewModel(activity)
    val historyViewModel: HistoryViewModel = hiltViewModel()
    val navController = LocalNavController.current
    
    val historyItems by historyViewModel.historyItems.collectAsState()
    
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
                        text = "History",
                        maxLines = 1,
                        fontSize = fontSize,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.05.em
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (historyItems.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    modifier = Modifier.padding(bottom = 10.dp),
                    expanded = isFabExpanded,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear History"
                        )
                    },
                    text = {
                        Text("Clear All")
                    },
                    onClick = {
                        historyViewModel.clearHistory()
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(40.dp)
            ) {
                NoHistoryUi(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 65.dp)
                        .align(Alignment.Center)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .nestedScroll(topBarScrollBehavior.nestedScrollConnection),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(historyItems, key = { it.id }) { item ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                historyViewModel.deleteItem(item.id)
                                true
                            } else {
                                false
                            }
                        }
                    )
                    
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.errorContainer,
                                        MaterialTheme.shapes.medium
                                    )
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                    ) {
                        HistoryItemCard(
                            item = item,
                            onClick = {
                                // Navigate to results with saved text (no re-extraction)
                                scanViewModel.onImageSelected(Uri.parse(item.imageUri))
                                // Pass saved text via shared state
                                scanViewModel.setHistoryText(item.text)
                                navController.navigate(Routes.RESULTS)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryItemCard(
    modifier: Modifier = Modifier,
    item: HistoryItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = item.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(
                    Date(
                        item.timestamp
                    )
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoHistoryUi(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Image(
            imageVector = DynamicColorImageVectors.noSearchResult(),
            contentDescription = null,
        )

        Text(
            text = "No history yet",
            style = MaterialTheme.typography.bodyMediumEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        )
    }
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