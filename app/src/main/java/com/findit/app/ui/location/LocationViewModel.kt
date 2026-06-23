package com.findit.app.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findit.app.data.model.Location
import com.findit.app.data.repository.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LocationState(
    val locations: List<Location> = emptyList(),
    val newName: String = "",
    val mergeSource: Location? = null,
    val mergeTarget: Location? = null,
    val showMergeDialog: Boolean = false,
    val mergeSuccessMessage: String? = null,
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
                _state.value = _state.value.copy(locations = locations)
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

    fun cancelMerge() {
        _state.value = _state.value.copy(
            mergeSource = null, mergeTarget = null, showMergeDialog = false
        )
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}
