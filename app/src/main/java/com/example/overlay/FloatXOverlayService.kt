package com.example.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.FloatXPreferencesRepository
import com.example.data.FloatXWindowRegistry
import com.example.data.InstalledAppInfo
import com.example.data.InstalledAppsRepository
import com.example.data.SidebarConfig
import com.example.data.SidebarEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class FloatXOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "floatx_overlay_engine"
        private const val NOTIFICATION_ID = 7401

        const val ACTION_START = "com.floatx.window.ACTION_START"
        const val ACTION_STOP = "com.floatx.window.ACTION_STOP"
        const val ACTION_TOGGLE_SIDEBAR = "com.floatx.window.ACTION_TOGGLE_SIDEBAR"

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, FloatXOverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatXOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.stopService(intent) }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var appWindowManager: WindowManager
    private var activeWindowManager: WindowManager? = null
    private var usingAccessibilityOverlay: Boolean = false

    private lateinit var prefsRepository: FloatXPreferencesRepository
    private lateinit var appsRepository: InstalledAppsRepository
    private lateinit var viewFactory: OverlayViewFactory

    private var currentConfig: SidebarConfig = SidebarConfig(enabled = true)
    private var lastLoadedPinnedSet: Set<String>? = null
    private var installedApps: List<InstalledAppInfo> = emptyList()

    private var edgeHandleView: View? = null
    private var edgeHandleParams: WindowManager.LayoutParams? = null

    private var sidebarPanelView: View? = null
    private var sidebarPanelParams: WindowManager.LayoutParams? = null
    private var lastPanelBuildSignature: String = ""

    override fun onCreate() {
        super.onCreate()
        appWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        activeWindowManager = appWindowManager
        prefsRepository = FloatXPreferencesRepository(applicationContext)
        appsRepository = InstalledAppsRepository(applicationContext)
        viewFactory = OverlayViewFactory(applicationContext, appsRepository)

        createNotificationChannel()
        startForegroundSafely()
        FloatXWindowRegistry.setServiceRunning(true)

        observeAccessibilityServiceHost()
        observePreferences()
        observeSidebarExpansion()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_STOP -> {
                FloatXWindowRegistry.setSidebarExpanded(false)
                serviceScope.launch { prefsRepository.setSidebarEnabled(false) }
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_TOGGLE_SIDEBAR -> {
                val currentlyOpen = sidebarPanelView?.visibility == View.VISIBLE &&
                    sidebarPanelView?.isAttachedToWindow == true
                FloatXWindowRegistry.setSidebarExpanded(!currentlyOpen)
            }

            else -> {
                if (edgeHandleView == null || edgeHandleView?.isAttachedToWindow == false) {
                    attachOrUpdateEdgeHandle()
                }
            }
        }

        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        lastPanelBuildSignature = ""
        if (edgeHandleView != null) {
            attachOrUpdateEdgeHandle()
        }
        if (FloatXWindowRegistry.sidebarExpanded.value) {
            showSidebarPanel(forceRebuild = true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onLowMemory() {
        super.onLowMemory()
        appsRepository.clearLowMemoryCache()
    }

    override fun onDestroy() {
        removeViewSafe(edgeHandleView)
        edgeHandleView = null
        edgeHandleParams = null
        removeViewSafe(sidebarPanelView)
        sidebarPanelView = null
        sidebarPanelParams = null
        FloatXWindowRegistry.setSidebarExpanded(false)
        FloatXWindowRegistry.setServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatXOverlayService::class.java).apply { action = ACTION_TOGGLE_SIDEBAR },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, FloatXOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("Edge sidebar active")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.notification_action_toggle_sidebar), toggleIntent)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .build()
    }

    private fun panelSignature(config: SidebarConfig, appCount: Int): String {
        return "${config.edge}_${config.panelWidthDp}_${config.panelHeightDp}_${config.opacity}_${config.themeMode}_${config.pinnedPackages.hashCode()}_${appCount}_$usingAccessibilityOverlay"
    }

    /**
     * Automatically upgrades the sidebar window layer to `TYPE_ACCESSIBILITY_OVERLAY` (System Layer 31)
     * whenever [FloatXAccessibilityService] is connected, or falls back to `TYPE_APPLICATION_OVERLAY`
     * with [MiuiSidebarWindowApi] flags.
     */
    private fun observeAccessibilityServiceHost() {
        serviceScope.launch {
            FloatXAccessibilityService.activeInstance.collectLatest { a11yService ->
                val newWm = a11yService?.getAccessibilityWindowManager() ?: appWindowManager
                val newUsingA11y = a11yService != null
                if (newWm !== activeWindowManager || newUsingA11y != usingAccessibilityOverlay) {
                    removeViewSafe(edgeHandleView)
                    edgeHandleView = null
                    edgeHandleParams = null
                    removeViewSafe(sidebarPanelView)
                    sidebarPanelView = null
                    sidebarPanelParams = null

                    activeWindowManager = newWm
                    usingAccessibilityOverlay = newUsingA11y
                    lastPanelBuildSignature = ""

                    if (currentConfig.enabled) {
                        attachOrUpdateEdgeHandle()
                        ensurePrebuiltSidebarPanel(forceRebuild = true)
                        if (FloatXWindowRegistry.sidebarExpanded.value) {
                            showSidebarPanel(forceRebuild = false)
                        }
                    }
                }
            }
        }
    }

    private fun observePreferences() {
        serviceScope.launch {
            prefsRepository.configFlow.collectLatest { config ->
                if (!config.enabled) {
                    stopSelf()
                    return@collectLatest
                }

                val prev = currentConfig
                currentConfig = config

                if (installedApps.isEmpty() || lastLoadedPinnedSet != config.pinnedPackages) {
                    lastLoadedPinnedSet = config.pinnedPackages
                    installedApps = withContext(Dispatchers.IO) {
                        appsRepository.loadInstalledApps(config.pinnedPackages)
                    }
                }

                val onlyPositionChanged = edgeHandleView != null &&
                    edgeHandleView?.isAttachedToWindow == true &&
                    edgeHandleParams != null &&
                    prev.edge == config.edge &&
                    prev.handleHeightDp == config.handleHeightDp &&
                    prev.handleWidthDp == config.handleWidthDp &&
                    prev.opacity == config.opacity &&
                    prev.themeMode == config.themeMode

                if (onlyPositionChanged) {
                    val params = edgeHandleParams!!
                    val screenH = resources.displayMetrics.heightPixels
                    params.x = computeEffectiveHandleXOffsetPx()
                    params.y = (screenH * config.verticalBias).roundToInt()
                        .coerceIn(dpToPx(48f), (screenH - dpToPx(140f)).coerceAtLeast(dpToPx(48f)))
                    updateViewLayoutSafe(edgeHandleView, params)
                    updateAccessibilityPassthroughRegion(params.y)
                } else {
                    attachOrUpdateEdgeHandle()
                }

                ensurePrebuiltSidebarPanel()

                if (FloatXWindowRegistry.sidebarExpanded.value) {
                    showSidebarPanel(forceRebuild = false)
                }
            }
        }
    }

    private fun observeSidebarExpansion() {
        serviceScope.launch {
            FloatXWindowRegistry.sidebarExpanded.collectLatest { expanded ->
                if (expanded) {
                    showSidebarPanel(forceRebuild = false)
                } else {
                    hideSidebarPanel()
                }
            }
        }
    }

    private fun overlayWindowType(): Int {
        if (usingAccessibilityOverlay && FloatXAccessibilityService.activeInstance.value != null) {
            return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun dpToPx(dp: Float): Int {
        val stableDpi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.util.DisplayMetrics.DENSITY_DEVICE_STABLE
        } else {
            0
        }
        val density = if (stableDpi > 0) {
            stableDpi / 160f
        } else {
            resources.displayMetrics.density.coerceIn(1.5f, 2.75f)
        }
        return (dp * density).roundToInt()
    }

    private fun updateAccessibilityPassthroughRegion(yPos: Int) {
        val a11yService = FloatXAccessibilityService.activeInstance.value ?: return
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val stripWidthPx = dpToPx(42f)
        val stripHeightPx = dpToPx((currentConfig.handleHeightDp + 36).coerceAtMost(200).toFloat())
        val top = (yPos - dpToPx(18f)).coerceAtLeast(0)
        val bottom = (top + stripHeightPx).coerceAtMost(screenH)
        val rect = if (currentConfig.edge == SidebarEdge.LEFT) {
            Rect(0, top, stripWidthPx, bottom)
        } else {
            Rect((screenW - stripWidthPx).coerceAtLeast(0), top, screenW, bottom)
        }
        a11yService.applyEdgePassthroughRegion(rect)
    }

    private var detectedSystemGestureInsetPx: Int = 0

    private fun computeEffectiveHandleXOffsetPx(): Int {
        return dpToPx(currentConfig.horizontalOffsetDp.coerceIn(0, 60).toFloat())
    }

    private fun attachOrUpdateEdgeHandle() {
        removeViewSafe(edgeHandleView)
        edgeHandleView = null
        edgeHandleParams = null

        val screenH = resources.displayMetrics.heightPixels
        val yPos = (screenH * currentConfig.verticalBias).roundToInt()
            .coerceIn(dpToPx(48f), (screenH - dpToPx(140f)).coerceAtLeast(dpToPx(48f)))
        val xOffsetPx = computeEffectiveHandleXOffsetPx()

        val barWidthPx = dpToPx(currentConfig.handleWidthDp.coerceIn(4, 14).toFloat()).coerceAtLeast(dpToPx(6f))
        val barHeightPx = dpToPx(currentConfig.handleHeightDp.coerceIn(48, 140).toFloat()).coerceAtLeast(dpToPx(56f))
        val windowW = (barWidthPx + dpToPx(18f)).coerceAtLeast(dpToPx(26f))
        val windowH = (barHeightPx + dpToPx(16f)).coerceAtMost(dpToPx(196f))

        val params = WindowManager.LayoutParams(
            windowW,
            windowH,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (currentConfig.edge == SidebarEdge.LEFT) Gravity.START else Gravity.END) or Gravity.TOP
            x = xOffsetPx
            y = yPos
            MiuiSidebarWindowApi.applySidebarSystemFlags(this, isExpandedPanel = false)
        }

        val handleView = viewFactory.createEdgeHandleView(
            config = currentConfig,
            onExpand = {
                edgeHandleView?.alpha = 0f
                showSidebarPanel(forceRebuild = false)
                FloatXWindowRegistry.setSidebarExpanded(true)
            },
            onTouchGestureEnded = {
                if (FloatXWindowRegistry.sidebarExpanded.value && sidebarPanelView?.visibility == View.VISIBLE) {
                    edgeHandleView?.visibility = View.GONE
                } else {
                    edgeHandleView?.visibility = View.VISIBLE
                    edgeHandleView?.alpha = currentConfig.opacity.coerceIn(0.6f, 1f)
                }
            },
            onSystemGestureInsetUpdate = { gestureInsetPx ->
                if (gestureInsetPx > 0) {
                    detectedSystemGestureInsetPx = gestureInsetPx
                }
            }
        )

        if (FloatXWindowRegistry.sidebarExpanded.value && sidebarPanelView?.visibility == View.VISIBLE) {
            handleView.visibility = View.GONE
        }

        edgeHandleParams = params
        edgeHandleView = handleView
        addViewSafe(handleView, params)
        updateAccessibilityPassthroughRegion(yPos)
    }

    private fun computePanelLayoutParams(isFocusablePanel: Boolean): WindowManager.LayoutParams {
        val screenH = resources.displayMetrics.heightPixels
        val panelWidthPx = dpToPx(currentConfig.panelWidthDp.coerceIn(56, 140).toFloat())
        val panelEstHeightPx = dpToPx(currentConfig.panelHeightDp.coerceIn(180, 580).toFloat())
            .coerceAtMost((screenH - dpToPx(80f)).coerceAtLeast(dpToPx(180f)))
        val yPos = (screenH * currentConfig.verticalBias - panelEstHeightPx / 2f).roundToInt()
            .coerceIn(dpToPx(32f), (screenH - panelEstHeightPx - dpToPx(32f)).coerceAtLeast(dpToPx(32f)))
        val xOffsetPx = dpToPx((currentConfig.horizontalOffsetDp.coerceIn(0, 60) + 4).toFloat())

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        val flags = if (isFocusablePanel) {
            baseFlags
        } else {
            baseFlags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        return WindowManager.LayoutParams(
            panelWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (currentConfig.edge == SidebarEdge.LEFT) Gravity.START else Gravity.END) or Gravity.TOP
            x = xOffsetPx
            y = yPos
            MiuiSidebarWindowApi.applySidebarSystemFlags(this, isExpandedPanel = true)
        }
    }

    private fun ensurePrebuiltSidebarPanel(forceRebuild: Boolean = false) {
        val sig = panelSignature(currentConfig, installedApps.size)
        val needsRebuild = forceRebuild ||
            sidebarPanelView == null ||
            sidebarPanelView?.isAttachedToWindow == false ||
            sig != lastPanelBuildSignature

        val isCurrentlyVisible = sidebarPanelView?.visibility == View.VISIBLE &&
            FloatXWindowRegistry.sidebarExpanded.value
        val params = computePanelLayoutParams(isFocusablePanel = isCurrentlyVisible)
        sidebarPanelParams = params

        if (!needsRebuild) {
            updateViewLayoutSafe(sidebarPanelView, params)
            return
        }

        removeViewSafe(sidebarPanelView)

        val panel = viewFactory.createSidebarPanelView(
            config = currentConfig,
            apps = installedApps,
            onAppClick = { app ->
                viewFactory.notifyPanelCollapsed()
                hideSidebarPanel()
                FloatXWindowRegistry.setSidebarExpanded(false)
                FreeformWindowLauncher.launchInFreeformWindow(
                    context = this,
                    packageName = app.packageName,
                    xDp = 24f,
                    yDp = 80f,
                    widthDp = 300f,
                    heightDp = 420f
                )
            },
            onCollapse = {
                viewFactory.notifyPanelCollapsed()
                hideSidebarPanel()
                FloatXWindowRegistry.setSidebarExpanded(false)
            }
        ).apply {
            visibility = if (isCurrentlyVisible) View.VISIBLE else View.GONE
        }

        lastPanelBuildSignature = sig
        sidebarPanelView = panel
        addViewSafe(panel, params)
    }

    private fun showSidebarPanel(forceRebuild: Boolean = false) {
        ensurePrebuiltSidebarPanel(forceRebuild = forceRebuild)
        val panel = sidebarPanelView ?: return
        panel.tag = SystemClock.uptimeMillis()
        if (panel.visibility != View.VISIBLE) {
            panel.visibility = View.VISIBLE
        }
        val focusableParams = computePanelLayoutParams(isFocusablePanel = true)
        sidebarPanelParams = focusableParams
        updateViewLayoutSafe(panel, focusableParams)
        viewFactory.excludeFromBackGesture(panel, panel)
        panel.requestFocus()
    }

    private fun hideSidebarPanel() {
        viewFactory.notifyPanelCollapsed()
        val panel = sidebarPanelView
        if (panel != null) {
            val hiddenParams = computePanelLayoutParams(isFocusablePanel = false)
            sidebarPanelParams = hiddenParams
            updateViewLayoutSafe(panel, hiddenParams)
            panel.visibility = View.GONE
        }
        if (edgeHandleView == null || edgeHandleView?.isAttachedToWindow == false) {
            if (currentConfig.enabled) {
                attachOrUpdateEdgeHandle()
            }
        } else {
            edgeHandleView?.visibility = View.VISIBLE
            edgeHandleView?.alpha = currentConfig.opacity
        }
    }

    private fun currentWm(): WindowManager = activeWindowManager ?: appWindowManager

    private fun addViewSafe(view: View, params: WindowManager.LayoutParams) {
        val added = runCatching {
            currentWm().addView(view, params)
            true
        }.getOrDefault(false)
        if (!added && currentWm() !== appWindowManager) {
            // Fallback to standard application overlay if accessibility token detached
            activeWindowManager = appWindowManager
            usingAccessibilityOverlay = false
            params.type = overlayWindowType()
            runCatching { appWindowManager.addView(view, params) }
        }
    }

    private fun updateViewLayoutSafe(view: View?, params: WindowManager.LayoutParams) {
        if (view != null && view.isAttachedToWindow) {
            runCatching {
                currentWm().updateViewLayout(view, params)
                viewFactory.excludeFromBackGesture(view, view)
            }
        }
    }

    private fun removeViewSafe(view: View?) {
        if (view != null) {
            val removed = runCatching {
                currentWm().removeViewImmediate(view)
                true
            }.getOrDefault(false)
            if (!removed) {
                runCatching { appWindowManager.removeViewImmediate(view) }
            }
        }
    }
}
