package com.mobisec.omniip.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class IpCalculatorViewModel : ViewModel() {
    private val _networkAddress = MutableStateFlow("")
    val networkAddress: StateFlow<String> = _networkAddress

    private val _broadcastAddress = MutableStateFlow("")
    val broadcastAddress: StateFlow<String> = _broadcastAddress

    private val _totalHosts = MutableStateFlow("")
    val totalHosts: StateFlow<String> = _totalHosts

    fun calculate(ip: String, cidr: Int) {
        if (cidr !in 0..32) {
            _networkAddress.value = "Invalid CIDR"
            _broadcastAddress.value = ""
            _totalHosts.value = ""
            return
        }

        try {
            val ipParts = ip.split(".").map { it.toLong() }
            if (ipParts.size != 4 || ipParts.any { it !in 0..255 }) {
                _networkAddress.value = "Invalid IP"
                _broadcastAddress.value = ""
                _totalHosts.value = ""
                return
            }

            val ipLong = (ipParts[0] shl 24) or (ipParts[1] shl 16) or (ipParts[2] shl 8) or ipParts[3]
            val maskLong = if (cidr == 0) 0L else (-1L shl (32 - cidr)) and 0xFFFFFFFFL

            val networkLong = ipLong and maskLong
            val broadcastLong = networkLong or (maskLong.inv() and 0xFFFFFFFFL)

            _networkAddress.value = longToIp(networkLong)
            _broadcastAddress.value = longToIp(broadcastLong)

            val hosts = if (cidr >= 31) 0 else (1L shl (32 - cidr)) - 2
            _totalHosts.value = hosts.toString()
        } catch (e: Exception) {
            _networkAddress.value = "Error"
            _broadcastAddress.value = ""
            _totalHosts.value = ""
        }
    }

    private fun longToIp(ip: Long): String {
        return "${(ip ushr 24) and 0xFF}.${(ip ushr 16) and 0xFF}.${(ip ushr 8) and 0xFF}.${ip and 0xFF}"
    }
}
