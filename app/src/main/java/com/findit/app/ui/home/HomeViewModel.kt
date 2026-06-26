package com.findit.app.ui.home

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.findit.app.FindItApplication
import com.findit.app.data.local.AppDatabase
import com.findit.app.data.model.Item
import com.findit.app.data.model.ItemWithDetails
import com.findit.app.data.model.Location
import com.findit.app.data.model.Tag
import com.findit.app.data.repository.ItemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val itemRepository: ItemRepository
        get() = getApplication<FindItApplication>().itemRepository

    val searchQuery = MutableStateFlow("")
    val exportMessage = MutableStateFlow<String?>(null)
    val importRestartRequired = MutableStateFlow(false)
    private val _items = MutableStateFlow<List<ItemWithDetails>>(emptyList())
    val items: StateFlow<List<ItemWithDetails>> = _items
    val isInitialLoading = MutableStateFlow(true)
    val isShowingSnapshot = MutableStateFlow(false)

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val snapshot = loadHomeSnapshot(getApplication())
            if (snapshot.isNotEmpty()) {
                _items.value = snapshot
                isShowingSnapshot.value = true
                isInitialLoading.value = false
            }
        }

        viewModelScope.launch {
            // 让首页外壳有机会先完成首帧绘制，再初始化 Room 和执行查询。
            kotlinx.coroutines.delay(16)
            searchQuery
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        itemRepository.getAllItems()
                    } else {
                        itemRepository.searchItems(query.trim())
                    }
                }
                .onEach { latestItems ->
                    _items.value = latestItems
                    isShowingSnapshot.value = false
                    isInitialLoading.value = false
                    if (searchQuery.value.isBlank()) {
                        viewModelScope.launch(Dispatchers.Default) {
                            saveHomeSnapshot(getApplication(), latestItems)
                        }
                    }
                }
                .collect {}
        }

        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            if (isInitialLoading.value) {
                isInitialLoading.value = false
            }
        }
    }

    fun exportExcel() {
        val app = getApplication<FindItApplication>()
        viewModelScope.launch {
            try {
                val file = app.exportManager.exportToExcel()
                app.exportManager.shareFile(
                    file,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "导出 Excel"
                )
                exportMessage.value = "Excel 导出成功"
            } catch (e: Exception) {
                exportMessage.value = "导出失败: ${e.message}"
            }
        }
    }

    fun createDatabaseBackupFileName(): String {
        val app = getApplication<FindItApplication>()
        return app.exportManager.createDatabaseBackupFileName()
    }

    fun exportDatabase(destinationUri: Uri) {
        val app = getApplication<FindItApplication>()
        viewModelScope.launch {
            try {
                checkpointDatabase(app)
                app.exportManager.exportDatabaseToUri(destinationUri)
                exportMessage.value = "数据库备份成功"
            } catch (e: Exception) {
                exportMessage.value = "备份失败: ${e.message}"
            }
        }
    }

    fun deleteItem(item: ItemWithDetails) {
        viewModelScope.launch {
            try {
                itemRepository.deleteItem(item.item)
                exportMessage.value = "已删除「${item.item.name}」"
            } catch (e: Exception) {
                exportMessage.value = "删除失败: ${e.message}"
            }
        }
    }

    fun clearExportMessage() { exportMessage.value = null }

    fun clearImportRestartRequired() { importRestartRequired.value = false }

    fun importDatabase(uri: Uri) {
        val app = getApplication<FindItApplication>()
        viewModelScope.launch {
            try {
                val inputStream = app.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("无法打开选择的文件")
                val tempFile = File(app.cacheDir, "import_temp.db")
                inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                app.exportManager.importDatabase(tempFile) {
                    app.database.close()
                    AppDatabase.closeInstance()
                }
                tempFile.delete()
                importRestartRequired.value = true
            } catch (e: Exception) {
                exportMessage.value = "恢复失败: ${e.message}"
            }
        }
    }

    private suspend fun checkpointDatabase(app: FindItApplication) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                app.database.openHelper.writableDatabase
                    .query("PRAGMA wal_checkpoint(FULL)")
                    .close()
            } catch (_: Exception) {
                // 如果当前数据库未启用 WAL 或 checkpoint 失败，仍继续导出主数据库文件。
            }
        }
    }

    private fun loadHomeSnapshot(context: Context): List<ItemWithDetails> {
        val raw = context
            .getSharedPreferences(HOME_SNAPSHOT_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HOME_ITEMS_SNAPSHOT, null)
            ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val itemObject = array.getJSONObject(index)
                    val locationObject = itemObject.optJSONObject("location")
                    val tagsArray = itemObject.optJSONArray("tags") ?: JSONArray()
                    add(
                        ItemWithDetails(
                            item = Item(
                                id = itemObject.getLong("id"),
                                name = itemObject.getString("name"),
                                note = itemObject.optString("note").takeIf { it.isNotBlank() },
                                locationId = if (itemObject.isNull("locationId")) null else itemObject.getLong("locationId"),
                                createdAt = itemObject.optLong("createdAt", System.currentTimeMillis())
                            ),
                            tags = buildList {
                                for (tagIndex in 0 until tagsArray.length()) {
                                    val tagObject = tagsArray.getJSONObject(tagIndex)
                                    add(
                                        Tag(
                                            id = tagObject.optLong("id", 0),
                                            name = tagObject.getString("name")
                                        )
                                    )
                                }
                            },
                            location = locationObject?.let {
                                Location(
                                    id = it.optLong("id", 0),
                                    name = it.getString("name"),
                                    groupId = it.optLong("groupId", 0)
                                )
                            }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveHomeSnapshot(context: Context, items: List<ItemWithDetails>) {
        val array = JSONArray()
        items.forEach { itemWithDetails ->
            val itemObject = JSONObject()
                .put("id", itemWithDetails.item.id)
                .put("name", itemWithDetails.item.name)
                .put("note", itemWithDetails.item.note ?: "")
                .put("locationId", itemWithDetails.item.locationId ?: JSONObject.NULL)
                .put("createdAt", itemWithDetails.item.createdAt)

            val tagsArray = JSONArray()
            itemWithDetails.tags.forEach { tag ->
                tagsArray.put(
                    JSONObject()
                        .put("id", tag.id)
                        .put("name", tag.name)
                )
            }
            itemObject.put("tags", tagsArray)

            itemWithDetails.location?.let { location ->
                itemObject.put(
                    "location",
                    JSONObject()
                        .put("id", location.id)
                        .put("name", location.name)
                        .put("groupId", location.groupId)
                )
            }
            array.put(itemObject)
        }

        context
            .getSharedPreferences(HOME_SNAPSHOT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HOME_ITEMS_SNAPSHOT, array.toString())
            .apply()
    }

    companion object {
        private const val HOME_SNAPSHOT_PREFS = "findit_home_snapshot"
        private const val KEY_HOME_ITEMS_SNAPSHOT = "items_snapshot"
    }
}
