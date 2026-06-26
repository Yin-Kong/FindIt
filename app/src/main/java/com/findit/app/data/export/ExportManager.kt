package com.findit.app.data.export

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
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
    private val databaseName = "findit_database"

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

    fun createDatabaseBackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "findit_backup_$timestamp.db"
    }

    suspend fun exportDatabase(): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(backupDir, createDatabaseBackupFileName())

        val dbFile = context.getDatabasePath(databaseName)
        if (dbFile.exists()) {
            dbFile.copyTo(file, overwrite = true)
        } else {
            throw IllegalStateException("数据库文件不存在")
        }
        file
    }

    suspend fun exportDatabaseToUri(destinationUri: Uri) = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(databaseName)
        if (!dbFile.exists()) {
            throw IllegalStateException("数据库文件不存在")
        }

        val outputStream = context.contentResolver.openOutputStream(destinationUri, "wt")
            ?: throw IllegalStateException("无法写入选择的位置")
        outputStream.use { output ->
            dbFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }
    }

    suspend fun importDatabase(
        sourceFile: File,
        beforeReplace: () -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) {
            throw IllegalStateException("选择的文件不存在")
        }
        if (!sourceFile.canRead()) {
            throw IllegalStateException("无法读取选择的文件")
        }

        validateFindItDatabase(sourceFile)

        val dbFile = context.getDatabasePath(databaseName)
        if (!dbFile.exists()) {
            throw IllegalStateException("当前数据库文件不存在")
        }

        // 导入前保留一份当前数据库，防止用户误选文件后完全无法回退。
        val backupDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val preImportBackup = File(backupDir, "findit_pre_import_backup_$timestamp.db")
        dbFile.copyTo(preImportBackup, overwrite = true)

        beforeReplace()

        // SQLite 可能存在 WAL/SHM 辅助文件。导入主库后必须清理旧辅助文件，
        // 否则应用重启后可能继续读取旧 WAL，导致“备份文件完好但导入不生效”。
        listOf(
            dbFile,
            File("${dbFile.absolutePath}-wal"),
            File("${dbFile.absolutePath}-shm")
        ).forEach { file ->
            if (file.exists() && !file.delete()) {
                throw IllegalStateException("无法替换旧数据库文件: ${file.name}")
            }
        }
        sourceFile.copyTo(dbFile, overwrite = true)
    }

    private fun validateFindItDatabase(sourceFile: File) {
        try {
            SQLiteDatabase.openDatabase(
                sourceFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                val requiredTables = setOf("items", "tags", "locations", "item_tag_cross_ref")
                val existingTables = mutableSetOf<String>()
                db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table'",
                    emptyArray()
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        existingTables.add(cursor.getString(0))
                    }
                }
                val missingTables = requiredTables - existingTables
                if (missingTables.isNotEmpty()) {
                    throw IllegalStateException("备份文件缺少必要数据表: ${missingTables.joinToString("、")}")
                }
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("选择的文件不是有效的 FindIt SQLite 备份")
        }
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
