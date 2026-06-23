package com.findit.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String? = null,
    val locationId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
