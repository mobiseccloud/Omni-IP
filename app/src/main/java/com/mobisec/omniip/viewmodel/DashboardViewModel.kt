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

import com.mobisec.omniip.core.SecurityPreferences

class DashboardViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {
    private val securityPreferences = SecurityPreferences(application)
    
    private val billingManager = com.mobisec.omniip.billing.BillingManager(application, androidx.lifecycle.viewModelScope)
    val isPersonalUnlocked = billingManager.isPersonalUnlocked
    val isEnterpriseUnlocked = billingManager.isEnterpriseUnlocked
    
    private val _showPinAuthDialog = MutableStateFlow(false)
    val showPinAuthDialog: StateFlow<Boolean> = _showPinAuthDialog

    private val _pinAuthError = MutableStateFlow(false)
    val pinAuthError: StateFlow<Boolean> = _pinAuthError

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

    fun requestStopFirewall() {
        if (securityPreferences.isPinLockEnabled()) {
            _showPinAuthDialog.value = true
        } else {
            broadcastStopVpn()
        }
    }

    fun submitTeardownPin(pin: String) {
        viewModelScope.launch {
            if (securityPreferences.verifyPin(pin)) {
                _showPinAuthDialog.value = false
                _pinAuthError.value = false
                broadcastStopVpn()
            } else {
                _pinAuthError.value = true
            }
        }
    }

    fun dismissPinAuthDialog() {
        _showPinAuthDialog.value = false
        _pinAuthError.value = false
    }

    private fun broadcastStopVpn() {
        val stopIntent = android.content.Intent(getApplication<Application>(), com.mobisec.omniip.vpn.OmniVpnService::class.java).apply {
            action = com.mobisec.omniip.vpn.OmniVpnService.ACTION_STOP_VPN
        }
        getApplication<Application>().startService(stopIntent)
        setFirewallActive(false)
    }

    fun executeAction(ip: String, action: String) {
        // Phase 8 Implementation: Gate premium actions
        if (action.contains("DEEP") && !(isPersonalUnlocked.value || isEnterpriseUnlocked.value)) {
            _showUpgradePrompt.value = true
            return
        }
        // ... proceed with existing _isExecuting logic        
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
