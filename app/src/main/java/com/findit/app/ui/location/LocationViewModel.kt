package com.findit.app.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findit.app.data.model.Location
import com.findit.app.data.repository.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TagAnalysisItem(
    val name: String,
    val count: Int,
    val percentage: Float
)

data class TagAnalysisResult(
    val selectedLocationCount: Int,
    val itemCount: Int,
    val tagTotalCount: Int,
    val tags: List<TagAnalysisItem>
)

data class LocationState(
    val locations: List<Location> = emptyList(),
    val newName: String = "",
    val mergeSource: Location? = null,
    val mergeTarget: Location? = null,
    val showMergeDialog: Boolean = false,
    val mergeSuccessMessage: String? = null,
    val isAnalysisMode: Boolean = false,
    val selectedLocationIds: Set<Long> = emptySet(),
    val showTagAnalysisDialog: Boolean = false,
    val tagAnalysisPage: Int = 0,
    val tagAnalysisResult: TagAnalysisResult? = null,
    val error: String? = null
)

class LocationViewModel(
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LocationState())
    val state: StateFlow<LocationState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            locationRepository.getCanonicalLocations().collect { locations ->
                _state.value = _state.value.copy(
                    locations = locations,
                    selectedLocationIds = _state.value.selectedLocationIds
                        .filter { selectedId -> locations.any { it.id == selectedId } }
                        .toSet()
                )
            }
        }
    }

    fun onNewNameChange(name: String) { _state.value = _state.value.copy(newName = name) }

    fun addLocation() {
        val name = _state.value.newName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            locationRepository.addLocation(name)
            _state.value = _state.value.copy(newName = "")
        }
    }

    fun deleteLocation(location: Location) {
        viewModelScope.launch { locationRepository.deleteLocation(location) }
    }

    fun onDragStart(source: Location) {
        _state.value = _state.value.copy(mergeSource = source)
    }

    fun onDragEnd() {
        // Only clear mergeSource if the dialog isn't showing (i.e., no valid drop target was found)
        if (!_state.value.showMergeDialog) {
            _state.value = _state.value.copy(mergeSource = null)
        }
    }

    fun onDropOnTarget(target: Location) {
        val source = _state.value.mergeSource ?: return
        if (source.id == target.id) {
            _state.value = _state.value.copy(mergeSource = null)
            return
        }
        _state.value = _state.value.copy(
            mergeSource = source,
            mergeTarget = target,
            showMergeDialog = true
        )
    }

    fun requestMerge(source: Location, target: Location) {
        if (source.id == target.id) return
        _state.value = _state.value.copy(
            mergeSource = source,
            mergeTarget = target,
            showMergeDialog = true
        )
    }

    fun confirmMerge() {
        val source = _state.value.mergeSource ?: return
        val target = _state.value.mergeTarget ?: return
        viewModelScope.launch {
            locationRepository.mergeLocations(source.id, target.id)
            _state.value = _state.value.copy(
                mergeSource = null,
                mergeTarget = null,
                showMergeDialog = false,
                mergeSuccessMessage = "已将「${source.name}」合并到「${target.name}」"
            )
        }
    }

    fun clearMergeSuccessMessage() {
        _state.value = _state.value.copy(mergeSuccessMessage = null)
    }

    fun enterAnalysisMode() {
        _state.value = _state.value.copy(
            isAnalysisMode = true,
            selectedLocationIds = emptySet(),
            showTagAnalysisDialog = false,
            tagAnalysisResult = null,
            tagAnalysisPage = 0
        )
    }

    fun cancelAnalysisMode() {
        _state.value = _state.value.copy(
            isAnalysisMode = false,
            selectedLocationIds = emptySet(),
            showTagAnalysisDialog = false,
            tagAnalysisResult = null,
            tagAnalysisPage = 0
        )
    }

    fun toggleLocationSelection(location: Location) {
        val selected = _state.value.selectedLocationIds
        _state.value = _state.value.copy(
            selectedLocationIds = if (location.id in selected) {
                selected - location.id
            } else {
                selected + location.id
            },
            error = null
        )
    }

    fun analyzeSelectedLocations() {
        val selectedIds = _state.value.selectedLocationIds
        if (selectedIds.isEmpty()) {
            _state.value = _state.value.copy(error = "请至少选择一个地点")
            return
        }

        viewModelScope.launch {
            // Load items on demand instead of keeping them in memory
            val allItems = locationRepository.getAllItems().first()
            val selectedItems = allItems.filter { item ->
                item.location?.id in selectedIds
            }
            val tagCounts = selectedItems
                .flatMap { item -> item.tags.map { it.name.trim() }.filter { it.isNotEmpty() } }
                .groupingBy { it }
                .eachCount()
            val tagTotalCount = tagCounts.values.sum()
            val tags = tagCounts
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { (name, count) ->
                    TagAnalysisItem(
                        name = name,
                        count = count,
                        percentage = if (tagTotalCount == 0) 0f else count.toFloat() / tagTotalCount
                    )
                }

            _state.value = _state.value.copy(
                showTagAnalysisDialog = true,
                tagAnalysisPage = 0,
                tagAnalysisResult = TagAnalysisResult(
                    selectedLocationCount = selectedIds.size,
                    itemCount = selectedItems.size,
                    tagTotalCount = tagTotalCount,
                    tags = tags
                ),
                error = null
            )
        }
    }

    fun showNextTagAnalysisPage() {
        _state.value = _state.value.copy(tagAnalysisPage = 1)
    }

    fun showFirstTagAnalysisPage() {
        _state.value = _state.value.copy(tagAnalysisPage = 0)
    }

    fun closeTagAnalysisDialog() {
        _state.value = _state.value.copy(showTagAnalysisDialog = false)
    }

    fun cancelMerge() {
        _state.value = _state.value.copy(
            mergeSource = null, mergeTarget = null, showMergeDialog = false
        )
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
