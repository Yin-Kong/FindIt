package com.findit.app.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.findit.app.data.local.ItemDao
import com.findit.app.data.local.LocationDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportManager(
    private val context: Context,
    private val itemDao: ItemDao,
    private val locationDao: LocationDao
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    suspend fun exportToExcel(): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(exportDir, "findit_items_$timestamp.xlsx")

        val items = itemDao.getAllItems().first()
        val locations = locationDao.getCanonicalLocations().first()

        val writer = SimpleXlsxWriter()

        // Sheet 1: Items
        val itemRows = mutableListOf<List<SimpleXlsxWriter.Cell>>()
        itemRows.add(listOf(
            SimpleXlsxWriter.Cell("名称", isBold = true),
            SimpleXlsxWriter.Cell("标签", isBold = true),
            SimpleXlsxWriter.Cell("位置", isBold = true),
            SimpleXlsxWriter.Cell("备注", isBold = true),
            SimpleXlsxWriter.Cell("添加时间", isBold = true)
        ))
        for (item in items) {
            itemRows.add(listOf(
                SimpleXlsxWriter.Cell(item.item.name),
                SimpleXlsxWriter.Cell(item.tagsText()),
                SimpleXlsxWriter.Cell(item.locationText()),
                SimpleXlsxWriter.Cell(item.item.note ?: ""),
                SimpleXlsxWriter.Cell(dateFormat.format(Date(item.item.createdAt)))
            ))
        }
        writer.addSheet("物品清单", itemRows)

        // Sheet 2: Locations
        val locRows = mutableListOf<List<SimpleXlsxWriter.Cell>>()
        locRows.add(listOf(
            SimpleXlsxWriter.Cell("地点名称", isBold = true),
            SimpleXlsxWriter.Cell("关联别名", isBold = true)
        ))
        for (loc in locations) {
            val aliases = locationDao.getLocationsInGroup(loc.groupId)
                .filter { it.id != loc.id }
                .joinToString(", ") { it.name }
            locRows.add(listOf(
                SimpleXlsxWriter.Cell(loc.name),
                SimpleXlsxWriter.Cell(aliases)
            ))
        }
        writer.addSheet("地点", locRows)

        writer.write(file)
        file
    }

    suspend fun exportDatabase(): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.filesDir, "db_backups").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(backupDir, "findit_backup_$timestamp.db")

        val dbFile = context.getDatabasePath("findit_database")
        if (dbFile.exists()) {
            dbFile.copyTo(file, overwrite = true)
        } else {
            throw IllegalStateException("数据库文件不存在")
        }
        file
    }

    fun shareFile(file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
