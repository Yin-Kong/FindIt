package com.findit.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.findit.app.FindItApplication
import com.findit.app.data.model.ItemWithDetails
import com.findit.app.data.repository.ItemRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    application: Application,
    private val itemRepository: ItemRepository
) : AndroidViewModel(application) {

    val searchQuery = MutableStateFlow("")
    val exportMessage = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<ItemWithDetails>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                itemRepository.getAllItems()
            } else {
                itemRepository.searchItems(query.trim())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun exportExcel() {
        val app = getApplication<FindItApplication>()
        viewModelScope.launch {
            try {
                val file = app.exportManager.exportToExcel()
                app.exportManager.shareFile(
                    file,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "导出 Excel"
                )
                exportMessage.value = "Excel 导出成功"
            } catch (e: Exception) {
                exportMessage.value = "导出失败: ${e.message}"
            }
        }
    }

    fun exportDatabase() {
        val app = getApplication<FindItApplication>()
        viewModelScope.launch {
            try {
                val file = app.exportManager.exportDatabase()
                app.exportManager.shareFile(
                    file,
                    "application/octet-stream",
                    "备份数据库"
                )
                exportMessage.value = "数据库备份成功"
            } catch (e: Exception) {
                exportMessage.value = "备份失败: ${e.message}"
            }
        }
    }

    fun deleteItem(item: ItemWithDetails) {
        viewModelScope.launch {
            try {
                itemRepository.deleteItem(item.item)
                exportMessage.value = "已删除「${item.item.name}」"
            } catch (e: Exception) {
                exportMessage.value = "删除失败: ${e.message}"
            }
        }
    }

    fun clearExportMessage() { exportMessage.value = null }
}
