package com.findit.app.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findit.app.data.model.ItemWithDetails
import com.findit.app.data.repository.ItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ItemDetailViewModel(
    private val itemRepository: ItemRepository
) : ViewModel() {

    private val _item = MutableStateFlow<ItemWithDetails?>(null)
    val item: StateFlow<ItemWithDetails?> = _item.asStateFlow()

    fun loadItem(itemId: Long) {
        viewModelScope.launch {
            _item.value = itemRepository.getItemById(itemId)
        }
    }

    fun deleteItem(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _item.value?.let {
                itemRepository.deleteItem(it.item)
                onDeleted()
            }
        }
    }
}
