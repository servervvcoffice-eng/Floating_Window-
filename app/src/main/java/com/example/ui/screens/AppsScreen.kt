package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppFilter
import com.example.data.InstalledAppInfo
import com.example.ui.FloatXUiState
import com.example.ui.components.AppIconImage
import com.example.ui.components.FrostedCard
import com.example.ui.components.SegmentedPillControl

@Composable
fun AppsScreen(
    uiState: FloatXUiState,
    getIconBitmap: (String) -> Bitmap?,
    onSearchChange: (String) -> Unit,
    onFilterChange: (AppFilter) -> Unit,
    onRefreshApps: () -> Unit,
    onTogglePin: (String) -> Unit,
    onLaunchFloating: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.all_apps_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${uiState.installedApps.size} apps · Tap Float to open in window",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 2.dp
                ) {
                    IconButton(
                        onClick = onRefreshApps,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("refresh_apps_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh Installed Apps",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Crisp iOS-style Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_apps_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchChange("") },
                            modifier = Modifier.testTag("clear_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apps_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Segmented Control
            SegmentedPillControl(
                items = AppFilter.entries,
                selectedItem = uiState.appFilter,
                labelProvider = { it.label },
                onSelect = onFilterChange,
                testTagPrefix = "app_filter"
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (uiState.isLoadingApps) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pinned to Sidebar horizontal quick dock
                    if (uiState.pinnedApps.isNotEmpty() && uiState.searchQuery.isBlank() && uiState.appFilter == AppFilter.ALL) {
                        item(key = "pinned_dock") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.pinned_apps_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(uiState.pinnedApps, key = { "pin_${it.packageName}" }) { app ->
                                        PinnedAppChip(
                                            app = app,
                                            getIconBitmap = getIconBitmap,
                                            onClick = { onLaunchFloating(app) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }

                    if (uiState.filteredApps.isEmpty()) {
                        item(key = "empty_apps") {
                            FrostedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Apps,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.empty_apps_search),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Try a different keyword or switch filters.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(uiState.filteredApps, key = { it.packageName }) { app ->
                            InstalledAppRowCard(
                                app = app,
                                isAlreadyOpen = false,
                                getIconBitmap = getIconBitmap,
                                onTogglePin = { onTogglePin(app.packageName) },
                                onLaunchFloating = { onLaunchFloating(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedAppChip(
    app: InstalledAppInfo,
    getIconBitmap: (String) -> Bitmap?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = Modifier.width(82.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppIconImage(
                packageName = app.packageName,
                contentDescription = app.label,
                getBitmap = getIconBitmap,
                modifier = Modifier.size(40.dp),
                cornerRadius = 12.dp
            )
            Text(
                text = app.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InstalledAppRowCard(
    app: InstalledAppInfo,
    isAlreadyOpen: Boolean,
    getIconBitmap: (String) -> Bitmap?,
    onTogglePin: () -> Unit,
    onLaunchFloating: () -> Unit
) {
    FrostedCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLaunchFloating() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconImage(
                packageName = app.packageName,
                contentDescription = app.label,
                getBitmap = getIconBitmap,
                modifier = Modifier.size(44.dp),
                cornerRadius = 13.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isAlreadyOpen) "Floating window open" else app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAlreadyOpen) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Solid Pin Toggle Button (crisp active vs inactive color)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (app.isPinned) Color(0xFF10B981)
                else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    if (app.isPinned) Color(0xFF059669)
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("pin_app_${app.packageName}")
                ) {
                    Icon(
                        imageVector = if (app.isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (app.isPinned) "Unpin from Sidebar" else "Pin to Sidebar",
                        tint = if (app.isPinned) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Launch Floating Pill Button
            Surface(
                onClick = onLaunchFloating,
                shape = RoundedCornerShape(14.dp),
                color = if (isAlreadyOpen) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("float_app_${app.packageName}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = if (isAlreadyOpen) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isAlreadyOpen) "Focus" else stringResource(R.string.launch_floating_action),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isAlreadyOpen) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
