package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object FloatXWindowRegistry {

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _sidebarExpanded = MutableStateFlow(false)
    val sidebarExpanded: StateFlow<Boolean> = _sidebarExpanded.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
        if (!running) {
            _sidebarExpanded.value = false
        }
    }

    fun setSidebarExpanded(expanded: Boolean) {
        _sidebarExpanded.value = expanded
    }

    fun toggleSidebarExpanded() {
        _sidebarExpanded.update { !it }
    }

    fun postStatus(message: String?) {
        _statusBanner.value = message
    }
}
