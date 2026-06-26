package com.findit.app.ui.home

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.findit.app.LocalViewModelFactory
import com.findit.app.data.model.ItemWithDetails
import com.findit.app.ui.components.EmptyState
import com.findit.app.ui.components.ItemCard
import com.findit.app.ui.settings.ListRenderMode
import com.findit.app.ui.settings.getAutoRenderThreshold
import com.findit.app.ui.settings.getListRenderMode
import com.findit.app.ui.settings.isAutoHideChromeEnabled
import com.findit.app.ui.settings.isColorfulTagMarkersEnabled
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onItemClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onBatchImportClick: () -> Unit,
    onHelpClick: () -> Unit,
    chromeHidden: Boolean = false,
    onChromeHiddenChange: (Boolean) -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = LocalViewModelFactory.current)
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val items by viewModel.items.collectAsState()
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()
    val isShowingSnapshot by viewModel.isShowingSnapshot.collectAsState()
    val drawerItems = remember(items, isShowingSnapshot) {
        if (isShowingSnapshot) items else sortItemsForDrawer(items)
    }
    val indexTargets = remember(drawerItems, isShowingSnapshot) {
        if (isShowingSnapshot) {
            emptyMap()
        } else {
            drawerItems
                .mapIndexed { index, item -> itemIndexLetter(item.item.name) to index }
                .distinctBy { it.first }
                .toMap()
        }
    }
    val indexLetters = remember(indexTargets, isShowingSnapshot) {
        if (isShowingSnapshot) emptyList() else (('A'..'Z').map { it.toString() } + "#").filter { it in indexTargets }
    }
    val exportMessage by viewModel.exportMessage.collectAsState()
    val importRestartRequired by viewModel.importRestartRequired.collectAsState()
    var colorfulTagMarkers by remember { mutableStateOf(true) }
    var autoHideChrome by remember { mutableStateOf(false) }
    var listRenderMode by remember { mutableStateOf(ListRenderMode.Auto) }
    var autoRenderThreshold by remember { mutableStateOf(64) }
    val useNormalScroll = when (listRenderMode) {
        ListRenderMode.NormalScroll -> true
        ListRenderMode.Lazy -> false
        ListRenderMode.Auto -> drawerItems.size <= autoRenderThreshold
    }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var pendingDeleteItem by remember { mutableStateOf<ItemWithDetails?>(null) }
    var showDatabaseTransferDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val importDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.importDatabase(it) }
    }
    val exportDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.exportDatabase(it) }
    }
    val listState = rememberLazyListState()
    val scrollState = rememberScrollState()
    val itemHeights = remember(drawerItems) { mutableMapOf<Long, Int>() }
    val coroutineScope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var activeIndexLetter by remember { mutableStateOf<String?>(null) }
    val scrollAccumulator = remember { FloatArray(1) }
    val scrollConnection = remember(autoHideChrome, isSearching, chromeHidden) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!autoHideChrome || isSearching) return Offset.Zero
                val delta = available.y
                if (delta == 0f) return Offset.Zero

                if (!chromeHidden && delta < 0f) {
                    scrollAccumulator[0] = (scrollAccumulator[0] - delta).coerceAtMost(160f)
                    if (scrollAccumulator[0] > 96f) {
                        onChromeHiddenChange(true)
                        scrollAccumulator[0] = 0f
                    }
                } else if (chromeHidden && delta > 0f) {
                    scrollAccumulator[0] = (scrollAccumulator[0] + delta).coerceAtMost(160f)
                    if (scrollAccumulator[0] > 64f) {
                        onChromeHiddenChange(false)
                        scrollAccumulator[0] = 0f
                    }
                } else {
                    scrollAccumulator[0] = 0f
                }
                return Offset.Zero
            }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(context) {
        colorfulTagMarkers = isColorfulTagMarkersEnabled(context)
        autoHideChrome = isAutoHideChromeEnabled(context)
        listRenderMode = getListRenderMode(context)
        autoRenderThreshold = getAutoRenderThreshold(context)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                colorfulTagMarkers = isColorfulTagMarkersEnabled(context)
                autoHideChrome = isAutoHideChromeEnabled(context)
                listRenderMode = getListRenderMode(context)
                autoRenderThreshold = getAutoRenderThreshold(context)
                if (!isAutoHideChromeEnabled(context)) {
                    onChromeHiddenChange(false)
                    scrollAccumulator[0] = 0f
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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

    if (showDatabaseTransferDialog) {
        AlertDialog(
            onDismissRequest = { showDatabaseTransferDialog = false },
            title = { Text("数据管理", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showDatabaseTransferDialog = false
                            importDbLauncher.launch(arrayOf("*/*"))
                        }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "导入数据库",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "从 SQLite 备份恢复数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showDatabaseTransferDialog = false
                            exportDbLauncher.launch(viewModel.createDatabaseBackupFileName())
                        }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "导出数据库",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "备份 SQLite 文件到指定目录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showDatabaseTransferDialog = false
                            viewModel.exportExcel()
                        }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "导出 Excel",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "导出物品清单和地点信息为 Excel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDatabaseTransferDialog = false }
                ) { Text("关闭") }
            }
        )
    }

    if (importRestartRequired) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("数据库恢复成功") },
            text = { Text("备份文件已导入。为了让 Room 重新读取新的数据库文件，请立即重启应用。") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.clearImportRestartRequired()
                        restartApp(context)
                    }
                ) { Text("立即重启") }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSearching || !chromeHidden) {
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
                                        text = { Text("数据管理") },
                                        onClick = {
                                            showMenu = false
                                            showDatabaseTransferDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("使用说明") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null) },
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
                .then(if (autoHideChrome) Modifier.nestedScroll(scrollConnection) else Modifier)
        ) {
            if (drawerItems.isEmpty()) {
                EmptyState(
                    message = when {
                        isInitialLoading -> "正在读取物品…"
                        searchQuery.isNotEmpty() -> "没有找到匹配的物品"
                        else -> "还没有物品，点击右上角 + 添加"
                    },
                    modifier = Modifier.padding(top = 80.dp)
                )
            } else {
                if (useNormalScroll) {
                    val itemSpacingPx = with(density) { 12.dp.roundToPx() }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(start = 16.dp, top = 16.dp, end = 34.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        drawerItems.forEach { itemWithDetails ->
                            ItemCard(
                                item = itemWithDetails,
                                onClick = { onItemClick(itemWithDetails.item.id) },
                                onLongClick = { pendingDeleteItem = itemWithDetails },
                                colorfulTagMarkers = colorfulTagMarkers,
                                modifier = Modifier.onSizeChanged { size ->
                                    itemHeights[itemWithDetails.item.id] = size.height
                                }
                            )
                        }
                    }
                    AlphabetIndexBar(
                        letters = indexLetters,
                        activeLetter = activeIndexLetter,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onLetterTouched = { letter ->
                            activeIndexLetter = letter
                            val targetIndex = indexTargets[letter] ?: return@AlphabetIndexBar
                            val targetOffset = drawerItems
                                .take(targetIndex)
                                .sumOf { item -> itemHeights[item.item.id] ?: 0 } +
                                itemSpacingPx * targetIndex
                            scrollJob?.cancel()
                            scrollJob = coroutineScope.launch {
                                scrollState.scrollTo(targetOffset.coerceIn(0, scrollState.maxValue))
                            }
                        },
                        onTouchEnd = {
                            activeIndexLetter = null
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            top = 16.dp,
                            end = 34.dp,
                            bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = drawerItems,
                            key = { it.item.id },
                            contentType = { "item_card" }
                        ) { itemWithDetails ->
                            ItemCard(
                                item = itemWithDetails,
                                onClick = { onItemClick(itemWithDetails.item.id) },
                                onLongClick = { pendingDeleteItem = itemWithDetails },
                                colorfulTagMarkers = colorfulTagMarkers
                            )
                        }
                    }

                    AlphabetIndexBar(
                        letters = indexLetters,
                        activeLetter = activeIndexLetter,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onLetterTouched = { letter ->
                            activeIndexLetter = letter
                            val targetIndex = indexTargets[letter] ?: return@AlphabetIndexBar
                            scrollJob?.cancel()
                            scrollJob = coroutineScope.launch {
                                listState.scrollToItem(targetIndex)
                            }
                        },
                        onTouchEnd = {
                            activeIndexLetter = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlphabetIndexBar(
    letters: List<String>,
    activeLetter: String?,
    modifier: Modifier = Modifier,
    onLetterTouched: (String) -> Unit,
    onTouchEnd: () -> Unit
) {
    if (letters.isEmpty()) return

    var barSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .width(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                .padding(vertical = 8.dp)
                .onSizeChanged { barSize = it }
                .pointerInput(letters) {
                    fun touchLetter(y: Float) {
                        if (barSize.height <= 0) return
                        val index = ((y / barSize.height) * letters.size)
                            .toInt()
                            .coerceIn(0, letters.lastIndex)
                        onLetterTouched(letters[index])
                    }

                    detectDragGestures(
                        onDragStart = { offset -> touchLetter(offset.y) },
                        onDrag = { change, _ -> touchLetter(change.position.y) },
                        onDragEnd = onTouchEnd,
                        onDragCancel = onTouchEnd
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            letters.forEach { letter ->
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (letter == activeLetter) FontWeight.Bold else FontWeight.Medium,
                    color = if (letter == activeLetter) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        activeLetter?.let { letter ->
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = letter,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                )
            }
        }
    }
}

private fun restartApp(context: Context) {
    val restartIntent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        ?: return
    context.startActivity(restartIntent)
    Runtime.getRuntime().exit(0)
}
