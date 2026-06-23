package com.findit.app.data.repository

import com.findit.app.data.local.ItemDao
import com.findit.app.data.local.LocationDao
import com.findit.app.data.model.BatchOperation
import com.findit.app.data.model.Item
import com.findit.app.data.model.ItemTagCrossRef
import com.findit.app.data.model.ItemWithDetails
import com.findit.app.data.model.Location
import com.findit.app.data.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ItemRepository(
    private val itemDao: ItemDao,
    private val locationDao: LocationDao
) {
    fun getAllItems(): Flow<List<ItemWithDetails>> = itemDao.getAllItems()
    fun searchItems(query: String): Flow<List<ItemWithDetails>> = itemDao.searchItems(query)
    suspend fun getItemById(id: Long): ItemWithDetails? = itemDao.getItemById(id)
    suspend fun findItemByName(name: String): Item? = itemDao.findItemByName(name)
    fun getAllTags(): Flow<List<Tag>> = itemDao.getAllTags()
    suspend fun searchItemsOnce(query: String): List<ItemWithDetails> = searchItems(query).first()

    suspend fun addItem(
        name: String, note: String?, tags: List<String>, locationName: String?
    ): Long {
        val locationId = locationName?.let { getOrCreateLocation(it) }
        val itemId = itemDao.insert(
            Item(name = name, note = note, locationId = locationId)
        )
        for (tagName in tags) {
            val tag = itemDao.findTagByName(tagName) ?: run {
                val tagId = itemDao.insertTag(Tag(name = tagName))
                Tag(id = tagId, name = tagName)
            }
            itemDao.insertTagCrossRef(ItemTagCrossRef(itemId = itemId, tagId = tag.id))
        }
        return itemId
    }

    suspend fun updateItem(
        item: Item, newTags: List<String>?, locationName: String?
    ) {
        val locationId = locationName?.let { getOrCreateLocation(it) }
        val updated = item.copy(locationId = locationId ?: item.locationId)
        itemDao.update(updated)

        if (newTags != null) {
            itemDao.deleteTagCrossRefsForItem(item.id)
            for (tagName in newTags) {
                val tag = itemDao.findTagByName(tagName) ?: run {
                    val tagId = itemDao.insertTag(Tag(name = tagName))
                    Tag(id = tagId, name = tagName)
                }
                itemDao.insertTagCrossRef(ItemTagCrossRef(itemId = item.id, tagId = tag.id))
            }
        }
    }

    suspend fun deleteItem(item: Item) = itemDao.delete(item)

    private suspend fun getOrCreateLocation(name: String): Long {
        val existing = locationDao.findLocationByName(name)
        if (existing != null) return if (existing.id == existing.groupId) existing.id else existing.groupId
        return locationDao.insert(Location(name = name, groupId = 0)).also { newId ->
            // Set groupId to its own id (canonical)
            locationDao.getLocationById(newId)?.let { locationDao.update(it.copy(groupId = newId)) }
        }
    }
}

class LocationRepository(
    private val locationDao: LocationDao,
    private val itemDao: ItemDao
) {
    fun getCanonicalLocations(): Flow<List<Location>> = locationDao.getCanonicalLocations()

    suspend fun getLocationById(id: Long): Location? = locationDao.getLocationById(id)

    suspend fun addLocation(name: String): Long {
        val id = locationDao.insert(Location(name = name, groupId = 0))
        locationDao.getLocationById(id)?.let { locationDao.update(it.copy(groupId = id)) }
        return id
    }

    suspend fun deleteLocation(location: Location) {
        // If this is the canonical location of a group with aliases, promote the next one
        val group = locationDao.getLocationsInGroup(location.groupId)
        if (group.size > 1 && location.id == location.groupId) {
            val next = group.first { it.id != location.id }
            locationDao.updateGroupId(location.groupId, next.id)
            itemDao.updateItemLocations(location.id, next.id)
        } else {
            itemDao.updateItemLocations(location.id, null)
        }
        locationDao.delete(location)
    }

    suspend fun mergeLocations(sourceId: Long, targetId: Long) {
        val source = locationDao.getLocationById(sourceId) ?: return
        val target = locationDao.getLocationById(targetId) ?: return

        val sourceGroup = if (source.id == source.groupId) source.id else source.groupId
        val targetGroup = if (target.id == target.groupId) target.id else target.groupId
        if (sourceGroup == targetGroup) return

        // 1. Move all items referencing source location or its aliases to target location
        val sourceLocations = locationDao.getLocationsInGroup(sourceGroup)
        for (loc in sourceLocations) {
            itemDao.updateItemLocations(loc.id, targetId)
        }

        // 2. Delete all locations in source group, including source itself and aliases
        for (loc in sourceLocations) {
            locationDao.delete(loc)
        }
    }
}

class BatchRepository(private val itemRepository: ItemRepository) {
    suspend fun executeBatch(operations: List<BatchOperation>): BatchResult {
        var added = 0; var updated = 0; var deleted = 0
        val errors = mutableListOf<String>()
        val queryResults = mutableListOf<QueryResult>()

        for (op in operations) {
            when (op.action) {
                "add" -> for (bi in op.items) {
                    try {
                        itemRepository.addItem(bi.name, bi.note, bi.tags, bi.location)
                        added++
                    } catch (e: Exception) { errors.add("添加 '${bi.name}' 失败: ${e.message}") }
                }
                "update" -> for (bi in op.items) {
                    try {
                        val existing = itemRepository.findItemByName(bi.name)
                        if (existing != null) {
                            itemRepository.updateItem(
                                item = existing.copy(name = bi.newName ?: existing.name, note = bi.newNote ?: existing.note),
                                newTags = bi.newTags, locationName = bi.newLocation
                            )
                            updated++
                        } else { errors.add("未找到物品 '${bi.name}'，跳过更新") }
                    } catch (e: Exception) { errors.add("更新 '${bi.name}' 失败: ${e.message}") }
                }
                "delete" -> for (bi in op.items) {
                    try {
                        val existing = itemRepository.findItemByName(bi.name)
                        if (existing != null) { itemRepository.deleteItem(existing); deleted++ }
                        else { errors.add("未找到物品 '${bi.name}'，跳过删除") }
                    } catch (e: Exception) { errors.add("删除 '${bi.name}' 失败: ${e.message}") }
                }
                "query" -> for (bi in op.items) {
                    try {
                        val terms = bi.queryTerms()
                        if (terms.isEmpty()) {
                            errors.add("查询条件为空，跳过")
                        } else {
                            val matched = linkedMapOf<Long, ItemWithDetails>()
                            for (term in terms) {
                                itemRepository.searchItemsOnce(term).forEach { item ->
                                    matched[item.item.id] = item
                                }
                            }
                            queryResults.add(
                                QueryResult(
                                    query = terms.joinToString("、"),
                                    items = matched.values.toList()
                                )
                            )
                        }
                    } catch (e: Exception) { errors.add("查询失败: ${e.message}") }
                }
            }
        }
        return BatchResult(added, updated, deleted, queryResults, errors)
    }
}

private fun com.findit.app.data.model.BatchItem.queryTerms(): List<String> {
    return buildList {
        query?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        name.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        keywords.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
        tags.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
        location?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
    }.distinct()
}

data class QueryResult(
    val query: String,
    val items: List<ItemWithDetails>
)

data class BatchResult(
    val added: Int,
    val updated: Int,
    val deleted: Int,
    val queryResults: List<QueryResult> = emptyList(),
    val errors: List<String>
) {
    val isSuccess: Boolean get() = errors.isEmpty()
    val summary: String get() = buildString {
        if (added > 0) append("新增 $added 项")
        if (updated > 0) { if (isNotEmpty) append("，"); append("修改 $updated 项") }
        if (deleted > 0) { if (isNotEmpty) append("，"); append("删除 $deleted 项") }
        val queryCount = queryResults.sumOf { it.items.size }
        if (queryResults.isNotEmpty()) {
            if (isNotEmpty) append("，")
            append("查询 ${queryResults.size} 组，匹配 $queryCount 项")
        }
        if (errors.isNotEmpty()) { if (isNotEmpty) append("，"); append("${errors.size} 项失败") }
        if (isEmpty()) append("没有执行任何操作")
    }
    private val isNotEmpty: Boolean get() = (added + updated + deleted) > 0 || queryResults.isNotEmpty()
}
