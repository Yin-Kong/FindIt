package com.findit.app.data.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class ItemWithDetails(
    @Embedded val item: Item,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ItemTagCrossRef::class,
            parentColumn = "itemId",
            entityColumn = "tagId"
        )
    )
    val tags: List<Tag>,
    @Relation(
        parentColumn = "locationId",
        entityColumn = "id"
    )
    val location: Location?
) {
    fun locationText(): String = location?.name ?: ""
    fun tagsText(): String = tags.joinToString(", ") { it.name }
}
