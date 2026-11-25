package com.example.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.WindowFreeformGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ComposeIconCache {
    // Ultra-lightweight 24-entry UI cache so memory never balloons
    private val cache = LruCache<String, ImageBitmap>(24)
    fun get(pkg: String): ImageBitmap? = cache.get(pkg)
    fun put(pkg: String, bmp: ImageBitmap) {
        cache.put(pkg, bmp)
    }
    fun clear() {
        cache.evictAll()
    }
}

@Composable
fun FrostedCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    // 100% Solid card design with crisp solid border and elevation
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp
    ) {
        content()
    }
}

@Composable
fun SolidToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()
    val uncheckedTrack = if (dark) Color(0xFF2D3748) else Color(0xFFCBD5E1)
    val uncheckedThumb = if (dark) Color(0xFF94A3B8) else Color.White

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                tint = if (checked) WindowFreeformGreen else uncheckedTrack,
                modifier = Modifier.size(14.dp)
            )
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = WindowFreeformGreen,
            checkedBorderColor = WindowFreeformGreen,
            uncheckedThumbColor = uncheckedThumb,
            uncheckedTrackColor = uncheckedTrack,
            uncheckedBorderColor = uncheckedTrack
        ),
        modifier = modifier
    )
}

@Composable
fun AppIconImage(
    packageName: String,
    contentDescription: String,
    getBitmap: (String) -> Bitmap?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 13.dp
) {
    val cached = ComposeIconCache.get(packageName)
    val imageBitmap by produceState(initialValue = cached, key1 = packageName) {
        if (value == null) {
            val loaded = withContext(Dispatchers.IO) {
                getBitmap(packageName)?.asImageBitmap()
            }
            if (loaded != null) {
                ComposeIconCache.put(packageName, loaded)
                value = loaded
            }
        }
    }

    val currentBitmap = imageBitmap
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap,
            contentDescription = contentDescription,
            modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun <T> SegmentedPillControl(
    items: List<T>,
    selectedItem: T,
    labelProvider: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "segment"
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                val selected = item == selectedItem
                val bgColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "segment_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "segment_text"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .clickable { onSelect(item) }
                        .testTag("${testTagPrefix}_${labelProvider(item).lowercase().replace(" ", "_")}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
