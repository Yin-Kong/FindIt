package com.findit.app.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.viewmodel.compose.viewModel
import com.findit.app.LocalViewModelFactory
import com.findit.app.data.model.ItemWithDetails
import com.findit.app.ui.components.EmptyState
import com.findit.app.ui.components.ItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onItemClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onBatchImportClick: () -> Unit,
    onHelpClick: () -> Unit,
    chromeCollapseFraction: Float = 0f,
    onChromeCollapseFractionChange: (Float) -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = LocalViewModelFactory.current)
) {
    val items by viewModel.items.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var pendingDeleteItem by remember { mutableStateOf<ItemWithDetails?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val effectiveCollapseFraction = if (isSearching) 0f else chromeCollapseFraction
    val topBarHeight by animateDpAsState(
        targetValue = 64.dp * (1f - effectiveCollapseFraction),
        label = "homeTopBarHeight"
    )
    val scrollConnection = remember(isSearching, chromeCollapseFraction) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isSearching) return Offset.Zero
                val delta = available.y
                if (delta == 0f) return Offset.Zero
                val next = (chromeCollapseFraction - delta / 320f).coerceIn(0f, 1f)
                if (next != chromeCollapseFraction) {
                    onChromeCollapseFractionChange(next)
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(exportMessage) {
        exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearExportMessage()
        }
    }

    pendingDeleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            title = { Text("删除物品") },
            text = { Text("确定要删除「${item.item.name}」吗？此操作无法撤销。") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.deleteItem(item)
                        pendingDeleteItem = null
                    }
                ) { Text("确认删除") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { pendingDeleteItem = null }
                ) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topBarHeight)
                    .clipToBounds()
            ) {
                if (isSearching) {
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    viewModel.searchQuery.value = it
                                },
                                placeholder = { Text("搜索物品、标签、位置...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                isSearching = false
                                searchQuery = ""
                                viewModel.searchQuery.value = ""
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        modifier = Modifier.graphicsLayer {
                            alpha = 1f - effectiveCollapseFraction
                            translationY = -64.dp.toPx() * effectiveCollapseFraction
                        },
                        title = { Text("findIt") },
                        actions = {
                            IconButton(onClick = { isSearching = true }) {
                                Icon(Icons.Default.Search, contentDescription = "搜索")
                            }
                            IconButton(onClick = onAddClick) {
                                Icon(Icons.Default.Add, contentDescription = "新增物品")
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("导出 Excel") },
                                        onClick = {
                                            showMenu = false
                                            viewModel.exportExcel()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("备份数据库") },
                                        onClick = {
                                            showMenu = false
                                            viewModel.exportDatabase()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("使用说明") },
                                        leadingIcon = { Icon(Icons.Default.Help, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            onHelpClick()
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f))
                    .clickable(onClick = onBatchImportClick)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "开始AI整理",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollConnection)
        ) {
            if (items.isEmpty()) {
                EmptyState(
                    message = if (searchQuery.isNotEmpty()) "没有找到匹配的物品" else "还没有物品，点击右上角 + 添加",
                    modifier = Modifier.padding(top = 80.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.item.id }) { itemWithDetails ->
                        ItemCard(
                            item = itemWithDetails,
                            onClick = { onItemClick(itemWithDetails.item.id) },
                            onLongClick = { pendingDeleteItem = itemWithDetails }
                        )
                    }
                }
            }
        }
    }
}
