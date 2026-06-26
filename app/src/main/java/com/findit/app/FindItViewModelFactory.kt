package com.findit.app

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.findit.app.ui.batch.BatchImportViewModel
import com.findit.app.ui.home.HomeViewModel
import com.findit.app.ui.item.ItemDetailViewModel
import com.findit.app.ui.item.ItemFormViewModel
import com.findit.app.ui.location.LocationViewModel

class FindItViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val app = application as FindItApplication
        return when (modelClass) {
            HomeViewModel::class.java -> HomeViewModel(application) as T
            ItemFormViewModel::class.java -> ItemFormViewModel(app.itemRepository, app.locationRepository) as T
            ItemDetailViewModel::class.java -> ItemDetailViewModel(app.itemRepository) as T
            LocationViewModel::class.java -> LocationViewModel(app.locationRepository) as T
            BatchImportViewModel::class.java -> BatchImportViewModel(app.batchRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
