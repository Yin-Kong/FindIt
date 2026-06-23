package com.findit.app.data.model

import org.json.JSONArray
import org.json.JSONObject

data class BatchOperation(
    val action: String, // "add", "update", "delete"
    val items: List<BatchItem>
)

data class BatchItem(
    val name: String = "",
    val query: String? = null,
    val keywords: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val location: String? = null,
    val note: String? = null,
    val newName: String? = null,
    val newTags: List<String>? = null,
    val newLocation: String? = null,
    val newNote: String? = null
)

data class BatchPreview(
    val addCount: Int,
    val updateCount: Int,
    val deleteCount: Int,
    val queryCount: Int,
    val operations: List<BatchOperation>
)

object BatchJsonParser {
    fun parse(jsonString: String): List<BatchOperation> {
        val json = JSONObject(jsonString)
        val operations = mutableListOf<BatchOperation>()

        (json.optJSONArray("add"))?.let { arr ->
            operations.add(BatchOperation("add", parseItems(arr)))
        }
        (json.optJSONArray("update"))?.let { arr ->
            operations.add(BatchOperation("update", parseItems(arr)))
        }
        (json.optJSONArray("delete"))?.let { arr ->
            operations.add(BatchOperation("delete", parseItems(arr)))
        }
        (json.optJSONArray("query"))?.let { arr ->
            operations.add(BatchOperation("query", parseQueryItems(arr)))
        }

        return operations
    }

    private fun parseItems(array: JSONArray): List<BatchItem> {
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            BatchItem(
                name = obj.getString("name"),
                tags = parseStringList(obj.optJSONArray("tags")),
                location = obj.nullableString("location"),
                note = obj.nullableString("note"),
                newName = obj.nullableString("newName"),
                newTags = obj.optJSONArray("newTags")?.let { parseStringList(it) },
                newLocation = obj.nullableString("newLocation"),
                newNote = obj.nullableString("newNote")
            )
        }
    }

    private fun parseQueryItems(array: JSONArray): List<BatchItem> {
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            BatchItem(
                name = obj.nullableString("name").orEmpty(),
                query = obj.nullableString("query")
                    ?: obj.nullableString("keyword")
                    ?: obj.nullableString("name"),
                keywords = parseStringList(obj.optJSONArray("keywords")),
                tags = parseStringList(obj.optJSONArray("tags")),
                location = obj.nullableString("location"),
                note = obj.nullableString("note")
            )
        }
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun JSONObject.nullableString(key: String): String? {
        return if (has(key) && !isNull(key)) getString(key) else null
    }
}
