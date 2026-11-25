package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.SidebarConfig
import com.example.data.SidebarEdge
import com.example.data.ThemeMode
import com.example.ui.RamTelemetry
import com.example.ui.components.FrostedCard
import com.example.ui.components.SegmentedPillControl
import com.example.ui.components.SolidToggleSwitch
import com.example.ui.theme.FloatXCyan
import com.example.ui.theme.FloatXIndigo
import com.example.ui.theme.WindowFreeformGreen
import kotlin.math.roundToInt

@Composable
fun CustomizeScreen(
    config: SidebarConfig,
    isServiceRunning: Boolean,
    isSidebarExpanded: Boolean,
    ramTelemetry: RamTelemetry,
    onBoostRam: () -> Unit,
    onToggleService: (Boolean) -> Unit,
    onToggleSidebarPanel: () -> Unit,
    onSetEdge: (SidebarEdge) -> Unit,
    onSetVerticalBias: (Float) -> Unit,
    onSetHorizontalOffset: (Int) -> Unit,
    onSetHandleHeight: (Int) -> Unit,
    onSetHandleWidth: (Int) -> Unit,
    onSetPanelWidth: (Int) -> Unit,
    onSetPanelHeight: (Int) -> Unit,
    onSetOpacity: (Float) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var draftHeight by remember(config.handleHeightDp) { mutableFloatStateOf(config.handleHeightDp.toFloat()) }
    var draftWidth by remember(config.handleWidthDp) { mutableFloatStateOf(config.handleWidthDp.toFloat()) }
    var draftBias by remember(config.verticalBias) { mutableFloatStateOf(config.verticalBias) }
    var draftHorizontalOffset by remember(config.horizontalOffsetDp) { mutableFloatStateOf(config.horizontalOffsetDp.toFloat()) }
    var draftPanelWidth by remember(config.panelWidthDp) { mutableFloatStateOf(config.panelWidthDp.toFloat()) }
    var draftPanelHeight by remember(config.panelHeightDp) { mutableFloatStateOf(config.panelHeightDp.toFloat()) }
    var draftOpacity by remember(config.opacity) { mutableFloatStateOf(config.opacity) }

    val appBudgetFraction = (ramTelemetry.totalAppMb / ramTelemetry.maxBudgetMb).coerceIn(0.08f, 0.96f)
    val animatedRamProgress by animateFloatAsState(
        targetValue = appBudgetFraction,
        animationSpec = tween(durationMillis = 350),
        label = "app_ram_progress"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Sidebar & Settings",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Ultra-low < 5 MB memory optimizer, sidebar position & app card size",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Solid Card: FloatX App RAM & Cache Usage ONLY (< 5.0 MB Ultra Optimizer)
            item {
                FrostedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(WindowFreeformGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Memory,
                                        contentDescription = "App Memory",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "App RAM & Cache",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Total Used: ${"%.2f".format(ramTelemetry.totalAppMb)} MB (Max 5.0 MB)",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = WindowFreeformGreen
                                    )
                                }
                            }

                            Button(
                                onClick = onBoostRam,
                                enabled = !ramTelemetry.isBoosting,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WindowFreeformGreen,
                                    contentColor = Color.White,
                                    disabledContainerColor = WindowFreeformGreen,
                                    disabledContentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.testTag("boost_ram_button")
                            ) {
                                if (ramTelemetry.isBoosting) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Cleaned",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Bolt,
                                        contentDescription = "Boost App RAM",
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Boost",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        // Solid Metric Boxes: ONLY FloatX App RAM & FloatX Cache Files
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(
                                        text = "FloatX App RAM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${"%.2f".format(ramTelemetry.appRamMb)} MB",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(
                                        text = "Cache Files",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${"%.2f".format(ramTelemetry.cacheMb)} MB",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = WindowFreeformGreen
                                    )
                                }
                            }
                        }

                        // Solid Progress Bar against the strict 5.0 MB cap
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Ultra-Low Memory Cap (< 5.0 MB)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${"%.2f".format(ramTelemetry.totalAppMb)} / 5.00 MB",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = WindowFreeformGreen
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedRamProgress)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(WindowFreeformGreen)
                                )
                            }
                        }
                    }
                }
            }

            // Placement & Visibility Solid Card
            item {
                FrostedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ViewSidebar,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Text(
                                text = "Floating Sidebar Position",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        TogglePreferenceRow(
                            title = "Show Floating Sidebar",
                            subtitle = "Keep the gray edge handle active over all apps",
                            checked = isServiceRunning,
                            onCheckedChange = onToggleService,
                            testTag = "customize_sidebar_toggle"
                        )

                        Text(
                            text = "Screen Edge",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        SegmentedPillControl(
                            items = SidebarEdge.entries,
                            selectedItem = config.edge,
                            labelProvider = { it.label },
                            onSelect = onSetEdge,
                            testTagPrefix = "sidebar_edge"
                        )

                        // Left / Right Horizontal Offset Slider
                        LabeledSlider(
                            label = if (config.edge == SidebarEdge.RIGHT)
                                "Horizontal Shift (Right ↔ Left)"
                            else
                                "Horizontal Shift (Left ↔ Right)",
                            valueText = "${draftHorizontalOffset.roundToInt()} dp",
                            value = draftHorizontalOffset,
                            valueRange = 0f..60f,
                            onValueChange = {
                                draftHorizontalOffset = it
                                onSetHorizontalOffset(it.roundToInt())
                            },
                            onValueChangeFinished = {
                                onSetHorizontalOffset(draftHorizontalOffset.roundToInt())
                            }
                        )

                        // Vertical Position Slider
                        LabeledSlider(
                            label = "Vertical Position (Top ↔ Bottom)",
                            valueText = "${(draftBias * 100).roundToInt()}%",
                            value = draftBias,
                            valueRange = 0.15f..0.85f,
                            onValueChange = {
                                draftBias = it
                                onSetVerticalBias(it)
                            },
                            onValueChangeFinished = {
                                onSetVerticalBias(draftBias)
                            }
                        )
                    }
                }
            }

            // Sidebar App List Card Width & Height Solid Card
            item {
                FrostedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(WindowFreeformGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AspectRatio,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Sidebar App Card Size",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Adjust app list card width & height",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onToggleSidebarPanel,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("preview_sidebar_card_button")
                            ) {
                                Icon(
                                    imageVector = if (isSidebarExpanded) Icons.Rounded.VisibilityOff
                                    else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isSidebarExpanded) "Hide" else "Preview",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        LabeledSlider(
                            label = "App List Card Width",
                            valueText = "${draftPanelWidth.roundToInt()} dp",
                            value = draftPanelWidth,
                            valueRange = 56f..140f,
                            onValueChange = {
                                draftPanelWidth = it
                                onSetPanelWidth(it.roundToInt())
                            },
                            onValueChangeFinished = {
                                onSetPanelWidth(draftPanelWidth.roundToInt())
                            }
                        )

                        LabeledSlider(
                            label = "App List Card Height",
                            valueText = "${draftPanelHeight.roundToInt()} dp",
                            value = draftPanelHeight,
                            valueRange = 180f..580f,
                            onValueChange = {
                                draftPanelHeight = it
                                onSetPanelHeight(it.roundToInt())
                            },
                            onValueChangeFinished = {
                                onSetPanelHeight(draftPanelHeight.roundToInt())
                            }
                        )
                    }
                }
            }

            // Handle Size & Opacity Solid Card
            item {
                FrostedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(FloatXIndigo),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Text(
                                text = "Handle Bar Size & Opacity",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        LabeledSlider(
                            label = "Handle Bar Length",
                            valueText = "${draftHeight.roundToInt()} dp",
                            value = draftHeight,
                            valueRange = 48f..140f,
                            onValueChange = { draftHeight = it },
                            onValueChangeFinished = { onSetHandleHeight(draftHeight.roundToInt()) }
                        )

                        LabeledSlider(
                            label = "Handle Bar Thickness",
                            valueText = "${draftWidth.roundToInt()} dp",
                            value = draftWidth,
                            valueRange = 4f..12f,
                            onValueChange = { draftWidth = it },
                            onValueChangeFinished = { onSetHandleWidth(draftWidth.roundToInt()) }
                        )

                        LabeledSlider(
                            label = "Sidebar Opacity",
                            valueText = "${(draftOpacity * 100).roundToInt()}%",
                            value = draftOpacity,
                            valueRange = 0.50f..1.0f,
                            onValueChange = { draftOpacity = it },
                            onValueChangeFinished = { onSetOpacity(draftOpacity) }
                        )
                    }
                }
            }

            // Theme Solid Card
            item {
                FrostedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(FloatXCyan),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Text(
                                text = "Appearance Theme",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        SegmentedPillControl(
                            items = ThemeMode.entries,
                            selectedItem = config.themeMode,
                            labelProvider = { it.label },
                            onSelect = onSetThemeMode,
                            testTagPrefix = "theme_mode"
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun TogglePreferenceRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SolidToggleSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
