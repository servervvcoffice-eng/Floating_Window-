package com.example.overlay

import android.os.Build
import android.view.WindowManager

/**
 * Configures safe, public WindowManager.LayoutParams properties for the FloatX edge sidebar
 * without setting privileged `privateFlags` (which trigger `SecurityException: Requires
 * INTERNAL_SYSTEM_WINDOW` in `WindowManagerService.addWindow` on Android 10–16).
 */
object MiuiSidebarWindowApi {

    fun applySidebarSystemFlags(params: WindowManager.LayoutParams, isExpandedPanel: Boolean) {
        params.title = if (isExpandedPanel) "FloatXSidebarPanel" else "FloatXSidebarHandle"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }
}
