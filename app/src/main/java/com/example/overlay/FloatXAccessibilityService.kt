package com.example.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.graphics.Region
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * System-Level Accessibility Overlay Host (`TYPE_ACCESSIBILITY_OVERLAY`).
 *
 * Why this solves the HyperOS 3 / Android 16 "Back triggers inside apps" issue 100%:
 * - Normal `TYPE_APPLICATION_OVERLAY` sits at Window Layer 25, BELOW `SystemUI`'s
 *   `EdgeBackGestureHandler` monitor when an app with a back stack is in the foreground.
 * - `TYPE_ACCESSIBILITY_OVERLAY` (available only inside a bound [AccessibilityService])
 *   sits at Window Layer 31 — the exact same high-priority system layer used by system sidebars
 *   and assistive docks — AND supports Android 11+ `setGestureDetectionPassthroughRegion`,
 *   ensuring touch gestures on the FloatX sidebar handle are delivered directly to FloatX
 *   inside ANY app without triggering System Back.
 */
class FloatXAccessibilityService : AccessibilityService() {

    companion object {
        private val _activeInstance = MutableStateFlow<FloatXAccessibilityService?>(null)
        val activeInstance: StateFlow<FloatXAccessibilityService?> = _activeInstance.asStateFlow()

        fun isEnabledInSettings(context: Context): Boolean {
            if (_activeInstance.value != null) return true
            return runCatching {
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                enabledServices.contains(
                    "${context.packageName}/${FloatXAccessibilityService::class.java.name}",
                    ignoreCase = true
                ) || enabledServices.contains(
                    "${context.packageName}/.overlay.FloatXAccessibilityService",
                    ignoreCase = true
                )
            }.getOrDefault(false)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _activeInstance.value = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Minimal no-op: FloatX only uses this service for TYPE_ACCESSIBILITY_OVERLAY window hosting
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (_activeInstance.value === this) {
            _activeInstance.value = null
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (_activeInstance.value === this) {
            _activeInstance.value = null
        }
        super.onDestroy()
    }

    fun getAccessibilityWindowManager(): WindowManager {
        return getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    /**
     * On Android 11+ (API 30+), informs the system gesture detector that touches inside [rect]
     * on the screen edge should pass straight through to the FloatX sidebar window rather than
     * being intercepted by system edge navigation gestures.
     */
    fun applyEdgePassthroughRegion(rect: Rect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val region = Region(rect)
                setGestureDetectionPassthroughRegion(Display.DEFAULT_DISPLAY, region)
            }
        }
    }
}
