package com.example.overlay

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlin.math.roundToInt

sealed class FreeformLaunchResult {
    data class FreeformSuccess(val bounds: Rect) : FreeformLaunchResult()
    data object StandardFallback : FreeformLaunchResult()
    data class Failed(val reason: String) : FreeformLaunchResult()
}

data class DeviceEnvironmentInfo(
    val androidVersion: String,
    val apiLevel: Int,
    val manufacturer: String,
    val isHyperOsOrMiui: Boolean,
    val hyperOsLabel: String
)

object FreeformWindowLauncher {

    // Android WindowConfiguration.WINDOWING_MODE_FREEFORM = 5
    private const val WINDOWING_MODE_FREEFORM = 5

    fun getDeviceEnvironment(): DeviceEnvironmentInfo {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val miuiProp = readSystemProperty("ro.miui.ui.version.name")
        val hyperOsProp = readSystemProperty("ro.mi.os.version.name")
        val isXiaomi = manufacturer.equals("Xiaomi", ignoreCase = true) ||
            manufacturer.equals("Redmi", ignoreCase = true) ||
            manufacturer.equals("POCO", ignoreCase = true) ||
            miuiProp.isNotEmpty() ||
            hyperOsProp.isNotEmpty()

        val osLabel = when {
            hyperOsProp.isNotEmpty() -> "HyperOS $hyperOsProp"
            miuiProp.isNotEmpty() -> "HyperOS / MIUI $miuiProp"
            isXiaomi -> "Xiaomi HyperOS"
            else -> "Android ${Build.VERSION.RELEASE}"
        }

        return DeviceEnvironmentInfo(
            androidVersion = Build.VERSION.RELEASE ?: "16",
            apiLevel = Build.VERSION.SDK_INT,
            manufacturer = manufacturer.ifBlank { "Android" },
            isHyperOsOrMiui = isXiaomi,
            hyperOsLabel = osLabel
        )
    }

    @SuppressLint("BlockedPrivateApi", "DiscouragedPrivateApi", "PrivateApi")
    fun launchInFreeformWindow(
        context: Context,
        packageName: String,
        xDp: Float,
        yDp: Float,
        widthDp: Float,
        heightDp: Float
    ): FreeformLaunchResult {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
            ?: return FreeformLaunchResult.Failed("App cannot be launched directly")

        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        val minW = (220f * density).roundToInt().coerceAtMost((screenW - 32).coerceAtLeast(180))
        val minH = (240f * density).roundToInt().coerceAtMost((screenH - 96).coerceAtLeast(180))
        val desiredW = (widthDp * density).roundToInt().coerceIn(minW, (screenW - 24).coerceAtLeast(minW))
        val desiredH = (heightDp * density).roundToInt().coerceIn(minH, (screenH - 96).coerceAtLeast(minH))

        val left = (xDp * density).roundToInt().coerceIn(12, (screenW - desiredW - 12).coerceAtLeast(12))
        val top = (yDp * density).roundToInt().coerceIn(48, (screenH - desiredH - 48).coerceAtLeast(48))
        val bounds = Rect(left, top, left + desiredW, top + desiredH)

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        )

        val freeformSucceeded = runCatching {
            val options = ActivityOptions.makeBasic()
            options.launchBounds = bounds

            runCatching {
                val method = ActivityOptions::class.java.getMethod(
                    "setLaunchWindowingMode",
                    Int::class.javaPrimitiveType
                )
                method.invoke(options, WINDOWING_MODE_FREEFORM)
            }

            if (Build.VERSION.SDK_INT >= 36) {
                runCatching {
                    val modeField = ActivityOptions::class.java.getField(
                        "MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS"
                    )
                    val mode = modeField.getInt(null)
                    options.setPendingIntentBackgroundActivityStartMode(mode)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                @Suppress("DEPRECATION")
                runCatching {
                    options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
            }

            val bundle = options.toBundle()
            bundle.putInt("android.activity.windowingMode", WINDOWING_MODE_FREEFORM)
            bundle.putParcelable("android:activity.launchBounds", bounds)

            context.startActivity(launchIntent, bundle)
            true
        }.getOrDefault(false)

        if (freeformSucceeded) {
            return FreeformLaunchResult.FreeformSuccess(bounds)
        }

        return runCatching {
            val fallbackIntent = pm.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: return FreeformLaunchResult.Failed("Launch intent unavailable")
            context.startActivity(fallbackIntent)
            FreeformLaunchResult.StandardFallback
        }.getOrElse { err ->
            FreeformLaunchResult.Failed(err.localizedMessage ?: "Unable to open app")
        }
    }

    fun openOverlayPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openDeveloperOptions(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (!started) {
            openAppDetailsSettings(context)
        }
    }

    fun openAppDetailsSettings(context: Context) {
        val miuiPermIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openedMiui = runCatching {
            context.startActivity(miuiPermIntent)
            true
        }.getOrDefault(false)
        if (openedMiui) return

        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (!started) {
            openAppDetailsSettings(context)
        }
    }

    private fun readSystemProperty(key: String): String {
        return runCatching {
            val propClass = Class.forName("android.os.SystemProperties")
            val getMethod = propClass.getMethod("get", String::class.java, String::class.java)
            getMethod.invoke(null, key, "") as? String ?: ""
        }.getOrDefault("")
    }
}
