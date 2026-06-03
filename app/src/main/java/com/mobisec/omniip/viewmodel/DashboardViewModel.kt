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

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val _terminalOutput = MutableStateFlow("")
    val terminalOutput: StateFlow<String> = _terminalOutput

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting

    private val _showUpgradePrompt = MutableStateFlow(false)
    val showUpgradePrompt: StateFlow<Boolean> = _showUpgradePrompt

    fun dismissUpgradePrompt() {
        _showUpgradePrompt.value = false
    }

    fun executeAction(ip: String, action: String) {
        if (_isExecuting.value) return

        if (action == "PORTSCAN_DEEP") {
            val isPremium = NativeEngine.isPremiumUnlockedNative()
            if (!isPremium) {
                _showUpgradePrompt.value = true
                return
            }
        }

        _isExecuting.value = true
        _terminalOutput.value = ""

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
                _terminalOutput.value = output
            } catch (e: Exception) {
                _terminalOutput.value = "Error executing action: ${e.message}"
            } finally {
                _isExecuting.value = false
            }
        }
    }
}
