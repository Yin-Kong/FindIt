package com.findit.app.ui.location

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.findit.app.LocalViewModelFactory
import com.findit.app.data.model.Location
import com.findit.app.ui.components.EmptyState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    viewModel: LocationViewModel = viewModel(factory = LocalViewModelFactory.current)
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Track positions of all cards for drop detection
    val cardPositions = remember { mutableMapOf<Long, Rect>() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var hoveringTargetId by remember { mutableStateOf<Long?>(null) }

    // Show snackbar when merge completes
    LaunchedEffect(state.mergeSuccessMessage) {
        state.mergeSuccessMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearMergeSuccessMessage()
        }
    }

    // Merge confirmation dialog
    if (state.showMergeDialog && state.mergeSource != null && state.mergeTarget != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelMerge,
            icon = { Icon(Icons.Default.Merge, contentDescription = null) },
            title = { Text("合并地点") },
            text = {
                Text("将 \"${state.mergeSource!!.name}\" 合并到 \"${state.mergeTarget!!.name}\"？\n合并后前者将被删除，所有相关物品会同步更新到目标地点。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmMerge()
                }) { Text("确认合并") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelMerge) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("地点管理") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text(
                    "长按拖动一个地点到另一个上可合并；也可点击合并按钮选择目标",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item {
                AddLocationRow(
                    value = state.newName,
                    onValueChange = viewModel::onNewNameChange,
                    onAdd = viewModel::addLocation
                )
            }

            if (state.locations.isEmpty()) {
                item { EmptyState("还没有地点，请添加") }
            }

            items(state.locations, key = { it.id }) { location ->
                val isSource = draggingId == location.id
                val isTarget = hoveringTargetId == location.id

                DraggableLocationCard(
                    location = location,
                    isSource = isSource,
                    isTarget = isTarget,
                    otherLocations = state.locations.filter { it.id != location.id },
                    cardPositions = cardPositions,
                    onRegisterPosition = { rect -> cardPositions[location.id] = rect },
                    onDragStart = {
                        draggingId = location.id
                        hoveringTargetId = null
                        viewModel.onDragStart(location)
                    },
                    onDragTargetChanged = { target ->
                        hoveringTargetId = target?.id
                    },
                    onDragEnd = {
                        draggingId = null
                        hoveringTargetId = null
                        viewModel.onDragEnd()
                    },
                    onDropDetected = { target ->
                        draggingId = null
                        hoveringTargetId = null
                        viewModel.onDropOnTarget(target)
                    },
                    onDelete = { viewModel.deleteLocation(location) },
                    onMergeClick = { target -> viewModel.requestMerge(location, target) }
                )
            }
        }
    }
}

@Composable
private fun DraggableLocationCard(
    location: Location,
    isSource: Boolean,
    isTarget: Boolean,
    otherLocations: List<Location>,
    cardPositions: Map<Long, Rect>,
    onRegisterPosition: (Rect) -> Unit,
    onDragStart: () -> Unit,
    onDragTargetChanged: (Location?) -> Unit,
    onDragEnd: () -> Unit,
    onDropDetected: (Location) -> Unit,
    onDelete: () -> Unit,
    onMergeClick: (Location) -> Unit
) {
    var offsetX by remember { mutableIntStateOf(0) }
    var offsetY by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartRect by remember { mutableStateOf<Rect?>(null) }
    var showMergeMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var lastHoveredTargetId by remember { mutableStateOf<Long?>(null) }
    val targetScale by animateFloatAsState(
        targetValue = if (isTarget) 1.04f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "targetScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset {
                IntOffset(
                    offsetX,
                    offsetY
                )
            }
            .zIndex(if (isDragging) 1f else 0f)
            .onGloballyPositioned { coords ->
                val topLeft = coords.positionInRoot()
                onRegisterPosition(
                    Rect(
                        left = topLeft.x,
                        top = topLeft.y,
                        right = topLeft.x + coords.size.width,
                        bottom = topLeft.y + coords.size.height
                    )
                )
            }
            .graphicsLayer {
                alpha = if (isDragging) 0.5f else 1f
                scaleX = if (isDragging) 1.03f else targetScale
                scaleY = if (isDragging) 1.03f else targetScale
                shadowElevation = if (isDragging) 16f else 0f
            }
            .pointerInput(location.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        isDragging = true
                        dragStartRect = cardPositions[location.id]
                        lastHoveredTargetId = null
                        onDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x.roundToInt()
                        offsetY += dragAmount.y.roundToInt()

                        val hovered = findBestDropTarget(
                            dragStartRect = dragStartRect,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            sourceId = location.id,
                            cardPositions = cardPositions,
                            otherLocations = otherLocations
                        )
                        onDragTargetChanged(hovered)
                        if (hovered?.id != null && hovered.id != lastHoveredTargetId) {
                            vibrateOnce(context)
                        }
                        lastHoveredTargetId = hovered?.id
                    },
                    onDragEnd = {
                        isDragging = false
                        val droppedOn = findBestDropTarget(
                            dragStartRect = dragStartRect,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            sourceId = location.id,
                            cardPositions = cardPositions,
                            otherLocations = otherLocations
                        )

                        if (droppedOn != null) {
                            onDropDetected(droppedOn)
                        }

                        offsetX = 0
                        offsetY = 0
                        dragStartRect = null
                        lastHoveredTargetId = null
                        onDragTargetChanged(null)
                        onDragEnd()
                    },
                    onDragCancel = {
                        isDragging = false
                        offsetX = 0
                        offsetY = 0
                        dragStartRect = null
                        lastHoveredTargetId = null
                        onDragTargetChanged(null)
                        onDragEnd()
                    }
                )
            }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = when {
                isTarget -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                isSource -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
                else -> null
            },
            colors = when {
                isSource -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                isTarget -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                else -> CardDefaults.cardColors()
            }
        ) {
            ListItem(
                headlineContent = {
                    Text(location.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                },
                supportingContent = {
                    when {
                        isSource -> Text(
                            "源地点：正在拖动，释放到目标地点上可合并",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        isTarget -> Text(
                            "目标地点：松手后会合并到这里",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                leadingContent = {
                    Icon(
                        Icons.Default.DragIndicator,
                        contentDescription = "长按拖动合并",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Row {
                        Box {
                            IconButton(onClick = { showMergeMenu = true }) {
                                Icon(
                                    Icons.Default.Merge,
                                    contentDescription = "合并到...",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (showMergeMenu && otherLocations.isNotEmpty()) {
                                androidx.compose.material3.DropdownMenu(
                                    expanded = showMergeMenu,
                                    onDismissRequest = { showMergeMenu = false }
                                ) {
                                    otherLocations.forEach { other ->
                                        DropdownMenuItem(
                                            text = { Text("合并到「${other.name}」") },
                                            onClick = {
                                                showMergeMenu = false
                                                onMergeClick(other)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    }
}

private fun findBestDropTarget(
    dragStartRect: Rect?,
    offsetX: Int,
    offsetY: Int,
    sourceId: Long,
    cardPositions: Map<Long, Rect>,
    otherLocations: List<Location>
): Location? {
    val startRect = dragStartRect ?: return null
    val draggedRect = Rect(
        left = startRect.left + offsetX,
        top = startRect.top + offsetY,
        right = startRect.right + offsetX,
        bottom = startRect.bottom + offsetY
    )
    val draggedArea = draggedRect.width * draggedRect.height
    val centerX = (draggedRect.left + draggedRect.right) / 2
    val centerY = (draggedRect.top + draggedRect.bottom) / 2

    val bestTargetId = cardPositions
        .filterKeys { it != sourceId }
        .mapNotNull { (id, targetRect) ->
            val overlapArea = intersectionArea(draggedRect, targetRect)
            val targetArea = targetRect.width * targetRect.height
            val minArea = min(draggedArea, targetArea)
            val centerInsideTarget = centerX in targetRect.left..targetRect.right &&
                centerY in targetRect.top..targetRect.bottom
            val hasEnoughOverlap = overlapArea >= minArea * 0.10f
            if (hasEnoughOverlap || centerInsideTarget) id to overlapArea else null
        }
        .maxByOrNull { it.second }
        ?.first

    return bestTargetId?.let { id -> otherLocations.find { it.id == id } }
}

private fun intersectionArea(first: Rect, second: Rect): Float {
    val left = max(first.left, second.left)
    val top = max(first.top, second.top)
    val right = min(first.right, second.right)
    val bottom = min(first.bottom, second.bottom)
    val width = max(0f, right - left)
    val height = max(0f, bottom - top)
    return width * height
}

private var lastVibrateTime = 0L

private fun vibrateOnce(context: Context) {
    val now = System.currentTimeMillis()
    if (now - lastVibrateTime < 300) return // Debounce: max once per 300ms
    lastVibrateTime = now

    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(30)
    }
}

@Composable
private fun AddLocationRow(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("新地点（如：书架、卧室柜子）", style = MaterialTheme.typography.bodySmall) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        IconButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = "添加")
        }
    }
}
