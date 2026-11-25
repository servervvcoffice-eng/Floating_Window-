package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AppsScreen
import com.example.ui.screens.CustomizeScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.FloatXTheme

enum class FloatXTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    STUDIO("studio", "Home", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    APPS("apps", "Apps", Icons.Rounded.Apps, Icons.Outlined.Apps),
    CUSTOMIZE("customize", "Sidebar", Icons.Rounded.Tune, Icons.Outlined.Tune)
}

@Composable
fun FloatXApp(
    viewModel: FloatXViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableStateOf(FloatXTab.STUDIO) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    val handleToggleService: (Boolean) -> Unit = { enable ->
        if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewModel.toggleSidebarService(enable)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    BackHandler(enabled = currentTab != FloatXTab.STUDIO) {
        currentTab = FloatXTab.STUDIO
    }

    FloatXTheme(themeMode = uiState.config.themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            FloatXTab.entries.forEach { tab ->
                                val selected = tab == currentTab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding()
                        ) {
                            Spacer(modifier = Modifier.weight(0.5f))
                            FloatXTab.entries.forEach { tab ->
                                val selected = tab == currentTab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("nav_rail_${tab.route}")
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        Crossfade(
                            targetState = currentTab,
                            animationSpec = tween(durationMillis = 200),
                            label = "tab_crossfade"
                        ) { tab ->
                            when (tab) {
                                FloatXTab.STUDIO -> StudioScreen(
                                    uiState = uiState,
                                    onToggleService = handleToggleService,
                                    onToggleSidebarPanel = viewModel::toggleSidebarPanelVisible,
                                    onCycleTheme = viewModel::cycleThemeMode,
                                    onBrowseApps = { currentTab = FloatXTab.APPS },
                                    onGrantOverlayPermission = viewModel::openOverlayPermissionSettings,
                                    onOpenAccessibilitySettings = viewModel::openAccessibilitySettings,
                                    onOpenDeveloperOptions = viewModel::openDeveloperOptions,
                                    onOpenAppSettings = viewModel::openAppDetailsSettings
                                )

                                FloatXTab.APPS -> AppsScreen(
                                    uiState = uiState,
                                    getIconBitmap = viewModel::getAppIconBitmap,
                                    onSearchChange = viewModel::updateSearchQuery,
                                    onFilterChange = viewModel::setAppFilter,
                                    onRefreshApps = viewModel::refreshInstalledApps,
                                    onTogglePin = viewModel::togglePinApp,
                                    onLaunchFloating = { app -> viewModel.launchAppInFloatingWindow(app) }
                                )

                                FloatXTab.CUSTOMIZE -> CustomizeScreen(
                                    config = uiState.config,
                                    isServiceRunning = uiState.isServiceRunning,
                                    isSidebarExpanded = uiState.isSidebarExpanded,
                                    ramTelemetry = uiState.ramTelemetry,
                                    onBoostRam = viewModel::boostRam,
                                    onToggleService = handleToggleService,
                                    onToggleSidebarPanel = viewModel::toggleSidebarPanelVisible,
                                    onSetEdge = viewModel::setSidebarEdge,
                                    onSetVerticalBias = viewModel::setVerticalBias,
                                    onSetHorizontalOffset = viewModel::setHorizontalOffsetDp,
                                    onSetHandleHeight = viewModel::setHandleHeightDp,
                                    onSetHandleWidth = viewModel::setHandleWidthDp,
                                    onSetPanelWidth = viewModel::setPanelWidthDp,
                                    onSetPanelHeight = viewModel::setPanelHeightDp,
                                    onSetOpacity = viewModel::setOpacity,
                                    onSetThemeMode = viewModel::setThemeMode
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
