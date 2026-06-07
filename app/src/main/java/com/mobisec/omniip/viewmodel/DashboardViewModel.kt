package com.mobisec.omniip.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
    
    private val billingManager = com.mobisec.omniip.billing.BillingManager(application, viewModelScope)
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

    val isFirewallActive: StateFlow<Boolean> = com.mobisec.omniip.vpn.OmniVpnService.isServiceRunning

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _recordingTargetUid = MutableStateFlow<Int?>(null)
    val recordingTargetUid: StateFlow<Int?> = _recordingTargetUid

    fun setRecordingState(isRecording: Boolean, targetUid: Int?) {
        _isRecording.value = isRecording
        _recordingTargetUid.value = targetUid
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
    }

    /** F-01 fix: Both FAST and DEEP scans require the Kotlin-side premium check before
     * reaching the native layer. The native g_auth_state bitmask is now a second line
     * of defense, not the sole gate. */
    private fun isPremiumUnlocked(): Boolean {
        return isPersonalUnlocked.value || isEnterpriseUnlocked.value
    }

    // --- Battery Optimization Helpers ---

    enum class BatteryStatus { UNRESTRICTED, RESTRICTED }

    fun checkBatteryOptimizationStatus(): BatteryStatus {
        val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
        ) BatteryStatus.RESTRICTED else BatteryStatus.UNRESTRICTED
    }

    fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${getApplication<Application>().packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            getApplication<Application>().startActivity(intent)
        }
    }

    fun executeAction(ip: String, action: String) {
        // F-01 fix: Gate ALL premium scan types on the Kotlin layer before native bridge
        if ((action == "PORTSCAN_FAST" || action == "PORTSCAN_DEEP") && !isPremiumUnlocked()) {
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
