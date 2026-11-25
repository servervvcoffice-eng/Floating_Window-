package com.example.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import com.example.data.InstalledAppInfo
import com.example.data.InstalledAppsRepository
import com.example.data.SidebarConfig
import com.example.data.SidebarEdge
import com.example.data.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayViewFactory(
    private val context: Context,
    private val appsRepository: InstalledAppsRepository
) {

    private val asyncIconScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastOpenTriggerUptimeMs: Long = 0L
    private var lastCollapseUptimeMs: Long = 0L

    fun notifyPanelCollapsed() {
        lastCollapseUptimeMs = SystemClock.uptimeMillis()
    }

    private fun fixedDensity(): Float {
        val stableDpi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            DisplayMetrics.DENSITY_DEVICE_STABLE
        } else {
            0
        }
        return if (stableDpi > 0) {
            stableDpi / 160f
        } else {
            context.resources.displayMetrics.density.coerceIn(1.5f, 2.75f)
        }
    }

    private fun dp(value: Float): Int =
        (value * fixedDensity()).roundToInt()

    private fun isDark(config: SidebarConfig): Boolean = when (config.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> {
            val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            uiMode == Configuration.UI_MODE_NIGHT_YES
        }
    }

    private fun solidPanelBg(dark: Boolean, opacity: Float, radiusDp: Float = 18f): GradientDrawable {
        val alpha = (opacity.coerceIn(0.85f, 1f) * 255).roundToInt().coerceIn(224, 255)
        val fill = if (dark) Color.argb(alpha, 32, 36, 44) else Color.argb(alpha, 238, 241, 246)
        val stroke = if (dark) Color.argb(105, 255, 255, 255) else Color.argb(75, 15, 23, 42)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            setStroke(dp(1.2f).coerceAtLeast(1), stroke)
        }
    }

    private fun pillBg(
        colorInt: Int,
        radiusDp: Float = 99f,
        strokeColor: Int = Color.TRANSPARENT
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(colorInt)
            if (strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1.3f).coerceAtLeast(1), strokeColor)
            }
        }
    }

    /**
     * Applies `setSystemGestureExclusionRects` across [root] covering [target]'s top..bottom
     * with an extra 8dp vertical buffer on both ends, capped at Android's 200dp per-edge limit.
     */
    fun excludeFromBackGesture(root: View, target: View = root) {
        if (root.width <= 0 || root.height <= 0) return
        val padPx = dp(8f)
        val rawTop = if (target === root) 0 else (target.top - padPx).coerceAtLeast(0)
        val rawBottom = if (target === root) root.height else (target.bottom + padPx).coerceAtMost(root.height)
        val maxH = dp(200f)
        val clampedBottom = if (rawBottom - rawTop > maxH) rawTop + maxH else rawBottom
        val rect = Rect(0, rawTop, root.width, clampedBottom.coerceAtLeast(rawTop + 1))
        val rects = listOf(rect)
        ViewCompat.setSystemGestureExclusionRects(root, rects)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            root.systemGestureExclusionRects = rects
        }
    }

    /**
     * HyperOS 3 & Android 16 Anti-Back Edge Handle:
     * Implements all 4 layers from Xiaomi MiMo Studio's HyperOS 3 Back-Gesture Conflict Fix:
     * 1. `root.doOnLayout` + `root.addOnLayoutChangeListener` calling `excludeFromBackGesture(root, handle)`
     *    with `handle.top - 8.dp` to `handle.bottom + 8.dp` (within Android's 200dp exclusion cap).
     * 2. `root.setOnApplyWindowInsetsListener` reading `insets.getInsets(WindowInsets.Type.systemGestures())`
     *    and reporting the live gesture inset to [onSystemGestureInsetUpdate] so the service can
     *    anchor the handle at `gestureInset + 4dp` outside HyperOS 3's back gesture zone when needed.
     * 3. `EdgeSwipeListener` supporting inward swipe (`downX - e.rawX > slop`), vertical slide,
     *    two-finger swipe (`pointerCount >= 2`), tap/long-press, and `ACTION_CANCEL` recovery
     *    (re-calling `excludeFromBackGesture` and resetting drag state).
     */
    @SuppressLint("ClickableViewAccessibility")
    fun createEdgeHandleView(
        config: SidebarConfig,
        onExpand: () -> Unit,
        onTouchGestureEnded: () -> Unit,
        onSystemGestureInsetUpdate: (Int) -> Unit = {}
    ): View {
        val barWidthPx = dp(config.handleWidthDp.toFloat()).coerceAtLeast(dp(6f))
        val barHeightPx = dp(config.handleHeightDp.toFloat()).coerceAtLeast(dp(56f)).coerceAtMost(dp(176f))

        // Narrow strip root container (26dp+ wide, handle height + 16dp vertical buffer)
        val touchW = (barWidthPx + dp(18f)).coerceAtLeast(dp(26f))
        val touchH = (barHeightPx + dp(16f)).coerceAtMost(dp(196f))

        val root = object : FrameLayout(context) {
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                setWillNotDraw(false)
                post {
                    getChildAt(0)?.let { child -> excludeFromBackGesture(this, child) }
                        ?: excludeFromBackGesture(this, this)
                }
            }

            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                getChildAt(0)?.let { child -> excludeFromBackGesture(this, child) }
                    ?: excludeFromBackGesture(this, this)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(touchW, touchH)
            alpha = config.opacity
            isClickable = true
            isFocusable = false
            isHapticFeedbackEnabled = true
        }

        val handleGravity = if (config.edge == SidebarEdge.LEFT) {
            Gravity.START or Gravity.CENTER_VERTICAL
        } else {
            Gravity.END or Gravity.CENTER_VERTICAL
        }

        val handle = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(barWidthPx, barHeightPx, handleGravity).apply {
                if (config.edge == SidebarEdge.LEFT) {
                    marginStart = dp(2f)
                } else {
                    marginEnd = dp(2f)
                }
            }
            background = pillBg(
                colorInt = Color.parseColor("#8A909B"),
                radiusDp = 99f,
                strokeColor = Color.argb(195, 255, 255, 255)
            )
            elevation = dp(4f).toFloat()
        }

        root.addView(handle)

        // ★ Primary Fix: apply exclusion rect on initial layout and on every layout change
        root.doOnLayout {
            excludeFromBackGesture(root, handle)
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            excludeFromBackGesture(root, handle)
        }

        // ★ HyperOS 3 Dynamic WindowInsets Listener: read WindowInsets.Type.systemGestures()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { _, insets ->
                val gestureInsets = insets.getInsets(WindowInsets.Type.systemGestures())
                val edgeGestureInsetPx = if (config.edge == SidebarEdge.LEFT) {
                    gestureInsets.left
                } else {
                    gestureInsets.right
                }
                if (edgeGestureInsetPx > 0) {
                    onSystemGestureInsetUpdate(edgeGestureInsetPx)
                }
                excludeFromBackGesture(root, handle)
                insets
            }
        }

        fun triggerOpenPanel(view: View) {
            val now = SystemClock.uptimeMillis()
            if (now - lastOpenTriggerUptimeMs < 110L) return
            if (now - lastCollapseUptimeMs < 150L) return
            lastOpenTriggerUptimeMs = now
            runCatching {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            onExpand()
        }

        val touchListener = object : View.OnTouchListener {
            private val slop = (ViewConfiguration.get(context).scaledTouchSlop * 0.45f)
                .coerceAtLeast(dp(3f).toFloat())
            private var downX = 0f
            private var downY = 0f
            private var dragging = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                // Fallback #3: Two-finger touch/swipe never conflicts with single-finger Back gesture
                if (e.pointerCount >= 2 && !dragging) {
                    dragging = true
                    triggerOpenPanel(v)
                    return true
                }

                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX
                        downY = e.rawY
                        dragging = false
                        excludeFromBackGesture(root, handle)
                        // Open immediately on touch down so the focusable panel takes mCurrentFocus
                        // before EdgeBackGestureHandler can dispatch Back to the foreground app
                        dragging = true
                        triggerOpenPanel(v)
                        return true
                    }

                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (!dragging) {
                            dragging = true
                            triggerOpenPanel(v)
                        }
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val inwardDx = if (config.edge == SidebarEdge.RIGHT) {
                            downX - e.rawX
                        } else {
                            e.rawX - downX
                        }
                        val dy = abs(e.rawY - downY)
                        if (!dragging && (inwardDx > slop || dy > slop)) {
                            dragging = true
                            triggerOpenPanel(v)
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!dragging) {
                            triggerOpenPanel(v)
                        }
                        dragging = false
                        onTouchGestureEnded()
                        return true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        // Back gesture attempted to steal touch: reset drag state, re-enforce
                        // exclusion rect, and ensure panel opens
                        dragging = false
                        excludeFromBackGesture(root, handle)
                        triggerOpenPanel(v)
                        onTouchGestureEnded()
                        return true
                    }
                }
                return false
            }
        }

        root.setOnTouchListener(touchListener)
        handle.setOnTouchListener(touchListener)

        return root
    }

    @SuppressLint("ClickableViewAccessibility")
    fun createSidebarPanelView(
        config: SidebarConfig,
        apps: List<InstalledAppInfo>,
        onAppClick: (InstalledAppInfo) -> Unit,
        onCollapse: () -> Unit
    ): View {
        val dark = isDark(config)
        val primaryText = if (dark) Color.parseColor("#F8FAFC") else Color.parseColor("#111827")
        val secondaryText = if (dark) Color.parseColor("#CBD5E1") else Color.parseColor("#475569")

        val cardWidthDp = config.panelWidthDp.coerceIn(56, 140)
        val cardHeightDp = config.panelHeightDp.coerceIn(180, 580)
        val iconSizeDp = (cardWidthDp * 0.54f).coerceIn(30f, 64f)
        val labelWidthDp = (cardWidthDp - 14).coerceIn(42, 124).toFloat()
        val labelTextSizeDp = (cardWidthDp * 0.12f).coerceIn(8.5f, 11.5f)

        val root = object : LinearLayout(context) {
            private var backCallback: Any? = null

            private fun interceptOrCollapseOnBack(): Boolean {
                val shownAt = (tag as? Long) ?: 0L
                val elapsed = SystemClock.uptimeMillis() - shownAt
                if (elapsed < 950L) {
                    // Swallow the Back event produced by the edge swipe that just opened the sidebar!
                    return true
                }
                onCollapse()
                return true
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                post { excludeFromBackGesture(this, this) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val cb = OnBackInvokedCallback {
                        interceptOrCollapseOnBack()
                    }
                    backCallback = cb
                    findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                        cb
                    )
                }
            }

            override fun onDetachedFromWindow() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val cb = backCallback as? OnBackInvokedCallback
                    if (cb != null) {
                        findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(cb)
                    }
                    backCallback = null
                }
                super.onDetachedFromWindow()
            }

            override fun dispatchKeyEventPreIme(event: KeyEvent?): Boolean {
                if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        return interceptOrCollapseOnBack()
                    }
                    return true
                }
                return super.dispatchKeyEventPreIme(event)
            }

            override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        return interceptOrCollapseOnBack()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = solidPanelBg(dark, config.opacity, 18f)
            setPadding(dp(6f), dp(10f), dp(6f), dp(10f))
            elevation = dp(14f).toFloat()
            isFocusable = true
            isFocusableInTouchMode = true
            tag = SystemClock.uptimeMillis()
            doOnLayout { excludeFromBackGesture(this, this) }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                excludeFromBackGesture(this, this)
            }
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    val shownAt = (view.tag as? Long) ?: 0L
                    if (SystemClock.uptimeMillis() - shownAt > 450L) {
                        onCollapse()
                    }
                    true
                } else {
                    false
                }
            }
        }

        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val appsColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        if (apps.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "Loading..."
                setTextColor(secondaryText)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(10f).toFloat())
                setPadding(dp(4f), dp(12f), dp(4f), dp(12f))
            }
            appsColumn.addView(emptyTv)
        } else {
            apps.forEach { app ->
                appsColumn.addView(
                    createAppIconCell(
                        label = app.label,
                        packageName = app.packageName,
                        textColor = primaryText,
                        iconSizeDp = iconSizeDp,
                        labelWidthDp = labelWidthDp,
                        labelTextSizeDp = labelTextSizeDp
                    ) {
                        onAppClick(app)
                    }
                )
            }
        }
        scrollView.addView(appsColumn)

        val maxScreenAllowedPx = (context.resources.displayMetrics.heightPixels - dp(120f)).coerceAtLeast(dp(160f))
        val targetScrollPx = dp((cardHeightDp - 52).coerceAtLeast(120).toFloat())
            .coerceIn(dp(120f), maxScreenAllowedPx)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                targetScrollPx
            )
        )

        val hideBtn = TextView(context).apply {
            text = "Close"
            setTextColor(primaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(10f).toFloat())
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            gravity = Gravity.CENTER
            background = pillBg(
                colorInt = if (dark) Color.parseColor("#334155") else Color.parseColor("#CBD5E1"),
                radiusDp = 10f
            )
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            setOnClickListener { onCollapse() }
        }
        root.addView(
            hideBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8f) }
        )

        return root
    }

    private fun createAppIconCell(
        label: String,
        packageName: String,
        textColor: Int,
        iconSizeDp: Float,
        labelWidthDp: Float,
        labelTextSizeDp: Float,
        onClick: () -> Unit
    ): View {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(3f), dp(5f), dp(3f), dp(6f))
            isClickable = true
            setOnClickListener { onClick() }
        }

        val iconHolder = FrameLayout(context).apply {
            val size = dp(iconSizeDp)
            layoutParams = LinearLayout.LayoutParams(size, size)
        }

        val iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            val cachedBmp = appsRepository.getCachedIconFast(packageName)
            if (cachedBmp != null) {
                setImageBitmap(cachedBmp)
            } else {
                setImageResource(android.R.drawable.sym_def_app_icon)
                asyncIconScope.launch {
                    val loaded = withContext(Dispatchers.IO) {
                        appsRepository.getAppIconBitmap(packageName, 52)
                    }
                    if (loaded != null) {
                        setImageBitmap(loaded)
                    }
                }
            }
        }
        iconHolder.addView(
            iconView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val labelView = TextView(context).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(labelTextSizeDp).toFloat())
            maxLines = 1
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(0, dp(3f), 0, 0)
        }

        cell.addView(iconHolder)
        cell.addView(
            labelView,
            LinearLayout.LayoutParams(dp(labelWidthDp), LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        return cell
    }
}
