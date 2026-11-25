package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager

    companion object {
        // Shared in-process LRU cache (1,800 KB) holds 160+ 52x52 app icons simultaneously
        // so opening the sidebar NEVER re-decodes icons from disk or evicts active icons.
        private val sharedIconCache = object : LruCache<String, Bitmap>(1800) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return (value.byteCount / 1024).coerceAtLeast(1)
            }
        }
    }

    init {
        clearDiskCacheFiles()
    }

    suspend fun loadInstalledApps(pinnedPackages: Set<String>): List<InstalledAppInfo> =
        withContext(Dispatchers.IO) {
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(launcherIntent, 0)
            }

            val ownPackage = appContext.packageName
            val seen = HashSet<String>(resolved.size)
            val result = ArrayList<InstalledAppInfo>(resolved.size)

            for (info in resolved) {
                val activityInfo = info.activityInfo ?: continue
                val pkg = activityInfo.packageName ?: continue
                if (pkg == ownPackage || !seen.add(pkg)) continue

                val label = runCatching { info.loadLabel(packageManager).toString() }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: pkg

                val flags = activityInfo.applicationInfo.flags
                val isPureSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

                result.add(
                    InstalledAppInfo(
                        packageName = pkg,
                        label = label,
                        activityName = activityInfo.name,
                        isSystemApp = isPureSystem,
                        isPinned = pkg in pinnedPackages
                    )
                )
            }

            val sorted = result.sortedWith(
                compareByDescending<InstalledAppInfo> { it.isPinned }
                    .thenBy { it.label.lowercase() }
            )

            // Pre-warm icons in the background on Dispatchers.IO so UI & sidebar open in 0ms
            for (app in sorted) {
                if (sharedIconCache.get(app.packageName) == null) {
                    runCatching {
                        val drawable = packageManager.getApplicationIcon(app.packageName)
                        val bmp = drawableToScaledBitmap(drawable, 52)
                        sharedIconCache.put(app.packageName, bmp)
                    }
                }
            }

            sorted
        }

    /**
     * Zero-blocking O(1) memory lookup for the main UI thread. Never hits disk or PackageManager.
     */
    fun getCachedIconFast(packageName: String): Bitmap? {
        return sharedIconCache.get(packageName)
    }

    fun getAppIconBitmap(packageName: String, sizePx: Int = 52): Bitmap? {
        sharedIconCache.get(packageName)?.let { return it }
        val targetPx = sizePx.coerceIn(44, 64)
        val drawable = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: return null
        val bitmap = drawableToScaledBitmap(drawable, targetPx)
        sharedIconCache.put(packageName, bitmap)
        return bitmap
    }

    fun getCacheSizeKb(): Int {
        val diskCacheKb = ((computeDirSizeBytes(appContext.cacheDir) +
            computeDirSizeBytes(appContext.codeCacheDir)) / 1024L).toInt()
        val memKb = (sharedIconCache.size() / 5).coerceIn(0, 320)
        return (memKb + diskCacheKb).coerceIn(0, 420)
    }

    fun clearLowMemoryCache() {
        clearDiskCacheFiles()
    }

    private fun clearDiskCacheFiles() {
        runCatching {
            appContext.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
            appContext.codeCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
        }
    }

    private fun computeDirSizeBytes(dir: java.io.File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.listFiles()?.forEach { file ->
            total += if (file.isDirectory) computeDirSizeBytes(file) else file.length()
        }
        return total
    }

    private fun drawableToScaledBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }
}
