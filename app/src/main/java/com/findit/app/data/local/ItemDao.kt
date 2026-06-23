package com.findit.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.findit.app.data.model.Item
import com.findit.app.data.model.ItemTagCrossRef
import com.findit.app.data.model.ItemWithDetails
import com.findit.app.data.model.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Transaction
    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun getAllItems(): Flow<List<ItemWithDetails>>

    @Transaction
    @Query("""
        SELECT DISTINCT items.* FROM items 
        LEFT JOIN locations ON items.locationId = locations.id 
        LEFT JOIN item_tag_cross_ref ON items.id = item_tag_cross_ref.itemId 
        LEFT JOIN tags ON item_tag_cross_ref.tagId = tags.id 
        LEFT JOIN locations AS alias ON alias.groupId = locations.groupId 
        WHERE items.name LIKE '%' || :query || '%' 
        OR tags.name LIKE '%' || :query || '%' 
        OR locations.name LIKE '%' || :query || '%' 
        OR alias.name LIKE '%' || :query || '%'
        ORDER BY items.createdAt DESC
    """)
    fun searchItems(query: String): Flow<List<ItemWithDetails>>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItemById(id: Long): ItemWithDetails?

    @Query("SELECT * FROM items WHERE name = :name LIMIT 1")
    suspend fun findItemByName(name: String): Item?

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("UPDATE items SET locationId = :newLocationId WHERE locationId = :oldLocationId")
    suspend fun updateItemLocations(oldLocationId: Long, newLocationId: Long?)

    @Insert
    suspend fun insertTagCrossRef(crossRef: ItemTagCrossRef)

    @Query("DELETE FROM item_tag_cross_ref WHERE itemId = :itemId")
    suspend fun deleteTagCrossRefsForItem(itemId: Long)

    @Query("SELECT * FROM tags ORDER BY name")
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findTagByName(name: String): Tag?

    @Insert
    suspend fun insertTag(tag: Tag): Long
}
