package com.findit.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.findit.app.data.model.Location
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Query("SELECT * FROM locations WHERE id = groupId ORDER BY name")
    fun getCanonicalLocations(): Flow<List<Location>>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getLocationById(id: Long): Location?

    @Query("SELECT * FROM locations WHERE name = :name LIMIT 1")
    suspend fun findLocationByName(name: String): Location?

    @Query("SELECT * FROM locations WHERE groupId = :groupId ORDER BY id")
    suspend fun getLocationsInGroup(groupId: Long): List<Location>

    @Insert
    suspend fun insert(location: Location): Long

    @Update
    suspend fun update(location: Location)

    @Delete
    suspend fun delete(location: Location)

    @Query("UPDATE locations SET groupId = :newGroupId WHERE groupId = :oldGroupId")
    suspend fun updateGroupId(oldGroupId: Long, newGroupId: Long)
}
