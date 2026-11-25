package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.floatXDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "floatx_preferences"
)

class FloatXPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.floatXDataStore

    private object Keys {
        val SIDEBAR_ENABLED = booleanPreferencesKey("sidebar_enabled")
        val SIDEBAR_EDGE = stringPreferencesKey("sidebar_edge")
        val VERTICAL_BIAS = floatPreferencesKey("vertical_bias")
        val HORIZONTAL_OFFSET_DP = intPreferencesKey("sidebar_horizontal_offset_v7_dp")
        val HANDLE_HEIGHT_DP = intPreferencesKey("handle_height_dp")
        val HANDLE_WIDTH_DP = intPreferencesKey("handle_width_dp")
        val PANEL_WIDTH_DP = intPreferencesKey("panel_width_dp")
        val PANEL_HEIGHT_DP = intPreferencesKey("panel_height_dp")
        val OPACITY = floatPreferencesKey("opacity")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PINNED_PACKAGES = stringSetPreferencesKey("pinned_packages")
    }

    val configFlow: Flow<SidebarConfig> = dataStore.data.map { prefs ->
        SidebarConfig(
            enabled = prefs[Keys.SIDEBAR_ENABLED] ?: true,
            edge = prefs[Keys.SIDEBAR_EDGE]?.let { runCatching { SidebarEdge.valueOf(it) }.getOrNull() }
                ?: SidebarEdge.RIGHT,
            verticalBias = (prefs[Keys.VERTICAL_BIAS] ?: 0.45f).coerceIn(0.15f, 0.85f),
            horizontalOffsetDp = (prefs[Keys.HORIZONTAL_OFFSET_DP] ?: 4).coerceIn(0, 60),
            handleHeightDp = (prefs[Keys.HANDLE_HEIGHT_DP] ?: 96).coerceIn(48, 140),
            handleWidthDp = (prefs[Keys.HANDLE_WIDTH_DP] ?: 10).coerceIn(4, 14),
            panelWidthDp = (prefs[Keys.PANEL_WIDTH_DP] ?: 78).coerceIn(56, 140),
            panelHeightDp = (prefs[Keys.PANEL_HEIGHT_DP] ?: 340).coerceIn(180, 580),
            opacity = (prefs[Keys.OPACITY] ?: 0.96f).coerceIn(0.50f, 1f),
            accent = SidebarAccent.GRAY,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            pinnedPackages = prefs[Keys.PINNED_PACKAGES] ?: emptySet()
        )
    }

    suspend fun setSidebarEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SIDEBAR_ENABLED] = enabled }
    }

    suspend fun setEdge(edge: SidebarEdge) {
        dataStore.edit { it[Keys.SIDEBAR_EDGE] = edge.name }
    }

    suspend fun setVerticalBias(bias: Float) {
        dataStore.edit { it[Keys.VERTICAL_BIAS] = bias.coerceIn(0.15f, 0.85f) }
    }

    suspend fun setHorizontalOffsetDp(offsetDp: Int) {
        dataStore.edit { it[Keys.HORIZONTAL_OFFSET_DP] = offsetDp.coerceIn(0, 60) }
    }

    suspend fun setHandleHeightDp(heightDp: Int) {
        dataStore.edit { it[Keys.HANDLE_HEIGHT_DP] = heightDp.coerceIn(48, 140) }
    }

    suspend fun setHandleWidthDp(widthDp: Int) {
        dataStore.edit { it[Keys.HANDLE_WIDTH_DP] = widthDp.coerceIn(4, 14) }
    }

    suspend fun setPanelWidthDp(widthDp: Int) {
        dataStore.edit { it[Keys.PANEL_WIDTH_DP] = widthDp.coerceIn(56, 140) }
    }

    suspend fun setPanelHeightDp(heightDp: Int) {
        dataStore.edit { it[Keys.PANEL_HEIGHT_DP] = heightDp.coerceIn(180, 580) }
    }

    suspend fun setOpacity(opacity: Float) {
        dataStore.edit { it[Keys.OPACITY] = opacity.coerceIn(0.50f, 1f) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun togglePinnedPackage(packageName: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PINNED_PACKAGES] ?: emptySet()
            prefs[Keys.PINNED_PACKAGES] = if (packageName in current) {
                current - packageName
            } else {
                current + packageName
            }
        }
    }
}
