package com.mobisec.omniip.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobisec.omniip.model.ConnectionTelemetry
import com.mobisec.omniip.vpn.OmniVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TelemetryViewModel : ViewModel() {
    private val _connections = MutableStateFlow<List<ConnectionTelemetry>>(emptyList())
    val connections: StateFlow<List<ConnectionTelemetry>> = _connections

    init {
        viewModelScope.launch {
            OmniVpnService.telemetryFlow.collect { telemetry: ConnectionTelemetry ->
                // Keep the last 100 connections
                _connections.value = (listOf(telemetry) + _connections.value).take(100)
            }
        }
    }
}
