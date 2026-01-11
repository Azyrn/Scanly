package com.skeler.scanely.history.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.history.data.HistoryItem
import com.skeler.scanely.history.data.HistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for History screen.
 * Uses Hilt-injected HistoryManager for consistent data access.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyManager: HistoryManager
) : ViewModel() {

    private val _historyItems = MutableStateFlow<List<HistoryItem>>(emptyList())
    val historyItems: StateFlow<List<HistoryItem>> = _historyItems.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _historyItems.value = historyManager.getHistory()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyManager.clearHistory()
            _historyItems.value = emptyList()
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            historyManager.deleteItem(id)
            loadHistory()
        }
    }
}
