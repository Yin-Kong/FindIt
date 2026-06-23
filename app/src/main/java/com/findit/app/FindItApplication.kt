package com.findit.app

import android.app.Application
import com.findit.app.data.export.ExportManager
import com.findit.app.data.local.AppDatabase
import com.findit.app.data.repository.BatchRepository
import com.findit.app.data.repository.ItemRepository
import com.findit.app.data.repository.LocationRepository

class FindItApplication : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val itemRepository by lazy { ItemRepository(database.itemDao(), database.locationDao()) }
    val locationRepository by lazy { LocationRepository(database.locationDao(), database.itemDao()) }
    val batchRepository by lazy { BatchRepository(itemRepository) }
    val exportManager by lazy { ExportManager(this, database.itemDao(), database.locationDao()) }
}
