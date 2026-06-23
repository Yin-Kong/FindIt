package com.findit.app.ui.batch

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.findit.app.LocalViewModelFactory
import com.findit.app.data.model.BatchOperation
import com.findit.app.data.repository.QueryResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchImportScreen(
    onNavigateBack: () -> Unit,
    initialJson: String? = null,
    onInitialJsonConsumed: () -> Unit = {},
    viewModel: BatchImportViewModel = viewModel(factory = LocalViewModelFactory.current)
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (!initialJson.isNullOrBlank()) {
            viewModel.executeJsonDirectly(initialJson)
            onInitialJsonConsumed()
        } else {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            val clipboardText = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(context)?.toString()
            } else {
                null
            }
            viewModel.tryLoadFromClipboard(clipboardText)
        }
    }

    if (state.showClipboardConfirmDialog && state.clipboardOperations != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelClipboardBatchAndEdit,
            title = { Text("发现剪切板 JSON") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("已从剪切板读取到可执行的批量操作，是否现在执行？")
                    ClipboardOperationSummary(state.clipboardOperations!!)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::executeClipboardBatch,
                    enabled = !state.isExecuting
                ) { Text("开始执行") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelClipboardBatchAndEdit) { Text("取消并编辑") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI整理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Result display
            state.result?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.isSuccess)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (result.isSuccess) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = if (result.isSuccess) "执行成功" else "部分失败",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Text(result.summary, style = MaterialTheme.typography.bodyMedium)
                        if (result.queryResults.isNotEmpty()) {
                            result.queryResults.forEach { queryResult ->
                                QueryResultCard(queryResult)
                            }
                        }
                        if (result.errors.isNotEmpty()) {
                            result.errors.forEach { error ->
                                Text(
                                    "• $error",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Button(
                            onClick = {
                                viewModel.reset()
                                onNavigateBack()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("完成") }
                    }
                }
                return@Column
            }

            // Preview display
            state.operations?.let { operations ->
                Text("预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                operations.forEach { op ->
                    OperationPreviewCard(op)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = viewModel::reset,
                        modifier = Modifier.weight(1f)
                    ) { Text("重新编辑") }
                    Button(
                        onClick = viewModel::executeBatch,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isExecuting
                    ) {
                        if (state.isExecuting) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        }
                        Text("确认执行")
                    }
                }
                return@Column
            }

            if (!state.showEditor) {
                Text(
                    "正在检查剪切板内容...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            // JSON input
            Text("粘贴 JSON", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "将 JSON 格式的批量操作内容粘贴到下方，支持添加、查询、修改、删除操作。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = state.jsonInput,
                onValueChange = viewModel::onJsonInputChange,
                placeholder = { Text("{\n  \"add\": [...],\n  \"query\": [...],\n  \"update\": [...],\n  \"delete\": [...]\n}") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                minLines = 10
            )

            // Parse error
            state.parseError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // Parse button
            Button(
                onClick = viewModel::parseJson,
                modifier = Modifier.fillMaxWidth()
            ) { Text("开始AI整理") }
        }
    }
}

@Composable
private fun ClipboardOperationSummary(operations: List<BatchOperation>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        operations.forEach { op ->
            val label = when (op.action) {
                "add" -> "新增"
                "update" -> "修改"
                "delete" -> "删除"
                "query" -> "查询"
                else -> op.action
            }
            Text(
                "$label ${op.items.size} 项",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            op.items.take(3).forEach { item ->
                Text("• ${item.query ?: item.name.ifBlank { "查询" }}", style = MaterialTheme.typography.bodySmall)
            }
            if (op.items.size > 3) {
                Text("• 还有 ${op.items.size - 3} 项...", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun OperationPreviewCard(op: BatchOperation) {
    val actionLabel = when (op.action) {
        "add" -> "新增"
        "update" -> "修改"
        "delete" -> "删除"
        "query" -> "查询"
        else -> op.action
    }
    val color = when (op.action) {
        "add" -> MaterialTheme.colorScheme.tertiaryContainer
        "update" -> MaterialTheme.colorScheme.secondaryContainer
        "delete" -> MaterialTheme.colorScheme.errorContainer
        "query" -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "$actionLabel ${op.items.size} 项",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            op.items.forEach { item ->
                val details = buildString {
                    append(item.query ?: item.name.ifBlank { "查询" })
                    if (item.keywords.isNotEmpty()) append(" 关键词: ${item.keywords.joinToString(", ")}")
                    if (item.tags.isNotEmpty()) append(" [${item.tags.joinToString(", ")}]")
                    if (item.location != null) append(" → ${item.location}")
                    if (item.newName != null) append(" → 改为: ${item.newName}")
                    if (item.newLocation != null) append(" → 移至: ${item.newLocation}")
                }
                Text("• $details", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun QueryResultCard(queryResult: QueryResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "查询：${queryResult.query}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (queryResult.items.isEmpty()) {
                Text("没有找到匹配的物品", style = MaterialTheme.typography.bodySmall)
            } else {
                queryResult.items.forEach { item ->
                    val location = item.locationText().takeIf { it.isNotBlank() } ?: "未设置地点"
                    val tags = item.tagsText().takeIf { it.isNotBlank() } ?: "无标签"
                    Text(
                        "• ${item.item.name} → $location [$tags]",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
