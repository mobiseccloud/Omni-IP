package com.mobisec.omniip.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobisec.omniip.core.NativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import androidx.lifecycle.SavedStateHandle

class DashboardViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {
    private val TERMINAL_OUTPUT_KEY = "terminal_output"
    val terminalOutput: StateFlow<String> = savedStateHandle.getStateFlow(TERMINAL_OUTPUT_KEY, "")

    private fun setTerminalOutput(output: String) {
        savedStateHandle[TERMINAL_OUTPUT_KEY] = output
    }

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting

    private val _showUpgradePrompt = MutableStateFlow(false)
    val showUpgradePrompt: StateFlow<Boolean> = _showUpgradePrompt

    private val _isFirewallActive = MutableStateFlow(false)
    val isFirewallActive: StateFlow<Boolean> = _isFirewallActive

    fun setFirewallActive(isActive: Boolean) {
        _isFirewallActive.value = isActive
    }

    fun triggerUpgradePrompt() {
        _showUpgradePrompt.value = true
    }

    fun dismissUpgradePrompt() {
        _showUpgradePrompt.value = false
    }

    fun executeAction(ip: String, action: String) {
        if (_isExecuting.value) return

        _isExecuting.value = true
        setTerminalOutput("")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val output = when (action) {
                    "PING" -> NativeEngine.executeRawPing(ip)
                    "TRACEROUTE" -> NativeEngine.executeTraceroute(ip)
                    "PORTSCAN" -> NativeEngine.executeNmapScan(ip)
                    "PORTSCAN_FAST" -> {
                        NativeEngine.executeNmapScan(ip)
                    }
                    "PORTSCAN_DEEP" -> {
                        NativeEngine.executeNmapScan("$ip -p- -A")
                    }
                    else -> "Unknown action."
                }
                setTerminalOutput(output)
            } catch (e: Exception) {
                setTerminalOutput("Error executing action: ${e.message}")
            } finally {
                _isExecuting.value = false
            }
        }
    }
}
