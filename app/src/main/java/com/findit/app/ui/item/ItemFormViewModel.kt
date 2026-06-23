package com.findit.app.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findit.app.data.model.Location
import com.findit.app.data.repository.ItemRepository
import com.findit.app.data.repository.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ItemFormState(
    val name: String = "",
    val note: String = "",
    val tagInput: String = "",
    val tags: List<String> = emptyList(),
    val locationText: String = "",
    val selectedLocation: Location? = null,
    val locations: List<Location> = emptyList(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

class ItemFormViewModel(
    private val itemRepository: ItemRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ItemFormState())
    val state: StateFlow<ItemFormState> = _state.asStateFlow()
    private var editingItemId: Long? = null

    init {
        viewModelScope.launch {
            locationRepository.getCanonicalLocations().collect { locations ->
                _state.value = _state.value.copy(locations = locations)
            }
        }
    }

    fun loadItem(itemId: Long) {
        editingItemId = itemId
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val details = itemRepository.getItemById(itemId)
            if (details != null) {
                _state.value = _state.value.copy(
                    name = details.item.name,
                    note = details.item.note ?: "",
                    tags = details.tags.map { it.name },
                    locationText = details.locationText(),
                    selectedLocation = details.location,
                    isLoading = false
                )
            }
        }
    }

    fun onNameChange(name: String) { _state.value = _state.value.copy(name = name) }
    fun onNoteChange(note: String) { _state.value = _state.value.copy(note = note) }
    fun onTagInputChange(input: String) { _state.value = _state.value.copy(tagInput = input) }
    fun onLocationTextChange(text: String) { _state.value = _state.value.copy(locationText = text) }

    fun addTag() {
        val tag = _state.value.tagInput.trim()
        if (tag.isNotEmpty() && tag !in _state.value.tags) {
            _state.value = _state.value.copy(tags = _state.value.tags + tag, tagInput = "")
        }
    }

    fun removeTag(tag: String) {
        _state.value = _state.value.copy(tags = _state.value.tags - tag)
    }

    fun selectLocation(location: Location?) {
        _state.value = _state.value.copy(
            selectedLocation = location,
            locationText = location?.name ?: ""
        )
    }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.value = s.copy(error = "请输入物品名称")
            return
        }
        viewModelScope.launch {
            try {
                val locName = s.locationText.trim().ifBlank { null }
                if (editingItemId != null) {
                    val existing = itemRepository.getItemById(editingItemId!!)
                    if (existing != null) {
                        itemRepository.updateItem(
                            item = existing.item.copy(name = s.name, note = s.note.ifBlank { null }),
                            newTags = s.tags,
                            locationName = locName
                        )
                    }
                } else {
                    itemRepository.addItem(
                        name = s.name, note = s.note.ifBlank { null },
                        tags = s.tags, locationName = locName
                    )
                }
                _state.value = _state.value.copy(isSaved = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "保存失败: ${e.message}")
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
