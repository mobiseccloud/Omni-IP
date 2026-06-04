package com.mobisec.omniip.viewmodel

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RouterSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val _gatewayIp = MutableStateFlow<String?>(null)
    val gatewayIp: StateFlow<String?> = _gatewayIp

    fun fetchGateway() {
        val wifiManager = getApplication<Application>().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo
        if (dhcpInfo != null && dhcpInfo.gateway != 0) {
            val ip = Formatter.formatIpAddress(dhcpInfo.gateway)
            _gatewayIp.value = ip
        } else {
            _gatewayIp.value = null
        }
    }
}
