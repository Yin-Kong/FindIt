package com.findit.app

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.findit.app.data.repository.BatchRepository
import com.findit.app.data.repository.ItemRepository
import com.findit.app.data.repository.LocationRepository
import com.findit.app.ui.batch.BatchImportViewModel
import com.findit.app.ui.home.HomeViewModel
import com.findit.app.ui.item.ItemDetailViewModel
import com.findit.app.ui.item.ItemFormViewModel
import com.findit.app.ui.location.LocationViewModel

class FindItViewModelFactory(
    private val application: Application,
    private val itemRepository: ItemRepository,
    private val locationRepository: LocationRepository,
    private val batchRepository: BatchRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when (modelClass) {
            HomeViewModel::class.java -> HomeViewModel(application, itemRepository) as T
            ItemFormViewModel::class.java -> ItemFormViewModel(itemRepository, locationRepository) as T
            ItemDetailViewModel::class.java -> ItemDetailViewModel(itemRepository) as T
            LocationViewModel::class.java -> LocationViewModel(locationRepository) as T
            BatchImportViewModel::class.java -> BatchImportViewModel(batchRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
