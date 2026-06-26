package com.findit.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.findit.app.data.model.ItemWithDetails

private val TagMarkerColors = listOf(
    Color(0xFFE57373),
    Color(0xFFF06292),
    Color(0xFFBA68C8),
    Color(0xFF9575CD),
    Color(0xFF7986CB),
    Color(0xFF64B5F6),
    Color(0xFF4FC3F7),
    Color(0xFF4DD0E1),
    Color(0xFF4DB6AC),
    Color(0xFF81C784),
    Color(0xFFAED581),
    Color(0xFFDCE775),
    Color(0xFFFFD54F),
    Color(0xFFFFB74D),
    Color(0xFFFF8A65),
    Color(0xFFA1887F),
    Color(0xFF90A4AE)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemCard(
    item: ItemWithDetails,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    colorfulTagMarkers: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val location = item.locationText()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.item.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (location.isNotEmpty()) {
                    LocationLabel(location = location)
                }
            }

            if (item.tags.isNotEmpty()) {
                val visibleTags = item.tags.take(5)
                val overflowCount = item.tags.size - visibleTags.size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleTags.forEachIndexed { index, tag ->
                        TagMarkerLabel(
                            text = tag.name,
                            color = if (colorfulTagMarkers) {
                                tagMarkerColor(tag.name)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.weight(1f),
                            alignment = when (index) {
                                0 -> Alignment.CenterStart
                                visibleTags.lastIndex -> Alignment.CenterEnd
                                else -> Alignment.Center
                            }
                        )
                    }
                    if (overflowCount > 0) {
                        TagMarkerLabel(
                            text = "+$overflowCount",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            alignment = Alignment.CenterEnd
                        )
                    }
                }
            }

            if (!item.item.note.isNullOrBlank()) {
                Text(
                    text = "note：${item.item.note}",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

        }
    }
}

@Composable
private fun TagMarkerLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.CenterStart
) {
    Box(
        modifier = modifier,
        contentAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.5f))
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LocationLabel(location: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 4.dp)
                .size(14.dp)
        )
        Text(
            text = location,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun tagMarkerColor(tagName: String): Color {
    val index = ((tagName.hashCode() % TagMarkerColors.size) + TagMarkerColors.size) % TagMarkerColors.size
    return TagMarkerColors[index]
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.Inventory2,
            contentDescription = null,
            modifier = Modifier.padding(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
