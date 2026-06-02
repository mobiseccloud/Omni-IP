package com.mobisec.omniip.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobisec.omniip.db.Action
import com.mobisec.omniip.db.AppDatabase
import com.mobisec.omniip.db.FirewallRule
import com.mobisec.omniip.db.TargetType
import com.mobisec.omniip.model.ConnectionTelemetry
import com.mobisec.omniip.vpn.OmniVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TelemetryViewModel(application: Application) : AndroidViewModel(application) {
    private val _connections = MutableStateFlow<List<ConnectionTelemetry>>(emptyList())
    val connections: StateFlow<List<ConnectionTelemetry>> = _connections

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.firewallRuleDao()

    init {
        viewModelScope.launch {
            OmniVpnService.telemetryFlow.collect { telemetry: ConnectionTelemetry ->
                // Keep the last 100 connections
                _connections.value = (listOf(telemetry) + _connections.value).take(100)
            }
        }
    }

    fun addRule(targetType: TargetType, targetValue: String, action: Action) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertRule(FirewallRule(targetType = targetType, targetValue = targetValue, action = action))
        }
    }
}
