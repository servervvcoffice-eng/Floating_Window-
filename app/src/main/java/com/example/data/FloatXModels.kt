package com.example.data

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AccentGray

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class SidebarEdge(val label: String) {
    LEFT("Left Edge"),
    RIGHT("Right Edge")
}

enum class SidebarAccent(
    val label: String,
    val color: Color,
    val argb: Int
) {
    GRAY("Gray", AccentGray, 0xFF8E8E93.toInt())
}

data class SidebarConfig(
    val enabled: Boolean = true,
    val edge: SidebarEdge = SidebarEdge.RIGHT,
    val verticalBias: Float = 0.45f,
    val horizontalOffsetDp: Int = 4,
    val handleHeightDp: Int = 96,
    val handleWidthDp: Int = 10,
    val panelWidthDp: Int = 78,
    val panelHeightDp: Int = 340,
    val opacity: Float = 0.96f,
    val accent: SidebarAccent = SidebarAccent.GRAY,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pinnedPackages: Set<String> = emptySet()
)

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val activityName: String,
    val isSystemApp: Boolean,
    val isPinned: Boolean = false
)

enum class AppFilter(val label: String) {
    ALL("All"),
    PINNED("Pinned"),
    USER("User")
}
