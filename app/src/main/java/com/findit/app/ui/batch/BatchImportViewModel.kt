package com.findit.app.ui.batch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findit.app.data.model.BatchJsonParser
import com.findit.app.data.model.BatchOperation
import com.findit.app.data.repository.BatchRepository
import com.findit.app.data.repository.BatchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BatchImportState(
    val jsonInput: String = "",
    val operations: List<BatchOperation>? = null,
    val clipboardOperations: List<BatchOperation>? = null,
    val showClipboardConfirmDialog: Boolean = false,
    val showEditor: Boolean = false,
    val parseError: String? = null,
    val result: BatchResult? = null,
    val isExecuting: Boolean = false
)

class BatchImportViewModel(
    private val batchRepository: BatchRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BatchImportState())
    val state: StateFlow<BatchImportState> = _state.asStateFlow()

    fun onJsonInputChange(input: String) {
        _state.value = _state.value.copy(jsonInput = input, operations = null, parseError = null, result = null)
    }

    fun tryLoadFromClipboard(input: String?) {
        val clipboardText = input?.trim().orEmpty()
        if (clipboardText.isEmpty()) {
            _state.value = _state.value.copy(
                showEditor = true,
                parseError = "剪切板没有可用的 JSON 内容"
            )
            return
        }

        try {
            val operations = BatchJsonParser.parse(clipboardText)
            if (operations.isEmpty()) {
                _state.value = _state.value.copy(
                    jsonInput = clipboardText,
                    showEditor = true,
                    parseError = "剪切板 JSON 中没有找到有效的操作（需要 add/update/delete/query 字段）"
                )
            } else {
                _state.value = _state.value.copy(
                    jsonInput = clipboardText,
                    clipboardOperations = operations,
                    showClipboardConfirmDialog = true,
                    showEditor = false,
                    parseError = null
                )
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                jsonInput = clipboardText,
                showEditor = true,
                parseError = "剪切板内容不是有效 JSON，可手动编辑后再执行"
            )
        }
    }

    fun executeClipboardBatch() {
        val operations = _state.value.clipboardOperations ?: return
        _state.value = _state.value.copy(
            operations = operations,
            showClipboardConfirmDialog = false,
            isExecuting = true
        )
        viewModelScope.launch {
            val result = batchRepository.executeBatch(operations)
            _state.value = _state.value.copy(result = result, isExecuting = false)
        }
    }

    fun executeJsonDirectly(input: String) {
        val jsonText = input.trim()
        if (jsonText.isEmpty()) {
            _state.value = _state.value.copy(showEditor = true, parseError = "URL 中没有 JSON 内容")
            return
        }
        try {
            val operations = BatchJsonParser.parse(jsonText)
            if (operations.isEmpty()) {
                _state.value = _state.value.copy(
                    jsonInput = jsonText,
                    showEditor = true,
                    parseError = "URL JSON 中没有找到有效的操作（需要 add/update/delete/query 字段）"
                )
                return
            }
            _state.value = _state.value.copy(
                jsonInput = jsonText,
                operations = operations,
                showClipboardConfirmDialog = false,
                showEditor = false,
                isExecuting = true,
                parseError = null,
                result = null
            )
            viewModelScope.launch {
                val result = batchRepository.executeBatch(operations)
                _state.value = _state.value.copy(result = result, isExecuting = false)
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                jsonInput = jsonText,
                showEditor = true,
                parseError = "URL JSON 解析失败: ${e.message}"
            )
        }
    }

    fun cancelClipboardBatchAndEdit() {
        _state.value = _state.value.copy(
            clipboardOperations = null,
            showClipboardConfirmDialog = false,
            showEditor = true,
            operations = null,
            result = null
        )
    }

    fun parseJson() {
        val input = _state.value.jsonInput.trim()
        if (input.isEmpty()) {
            _state.value = _state.value.copy(parseError = "请粘贴 JSON 内容")
            return
        }
        try {
            val operations = BatchJsonParser.parse(input)
            if (operations.isEmpty()) {
                _state.value = _state.value.copy(parseError = "JSON 中没有找到有效的操作（需要 add/update/delete/query 字段）")
            } else {
                _state.value = _state.value.copy(operations = operations, parseError = null)
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(parseError = "JSON 解析失败: ${e.message}")
        }
    }

    fun executeBatch() {
        val operations = _state.value.operations ?: return
        _state.value = _state.value.copy(isExecuting = true)
        viewModelScope.launch {
            val result = batchRepository.executeBatch(operations)
            _state.value = _state.value.copy(result = result, isExecuting = false)
        }
    }

    fun reset() {
        _state.value = BatchImportState(showEditor = true)
    }
}
