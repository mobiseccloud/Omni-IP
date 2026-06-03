package com.mobisec.omniip.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

class IpConverterViewModel : ViewModel() {
    private val _ipv4 = MutableStateFlow("")
    val ipv4: StateFlow<String> = _ipv4

    private val _ipv6 = MutableStateFlow("")
    val ipv6: StateFlow<String> = _ipv6

    private val _hex = MutableStateFlow("")
    val hex: StateFlow<String> = _hex

    private val _decimal = MutableStateFlow("")
    val decimal: StateFlow<String> = _decimal

    fun convertFromIpv4(ip: String) {
        _ipv4.value = ip
        try {
            val addr = InetAddress.getByName(ip)
            if (addr is Inet4Address) {
                val bytes = addr.address
                val dec = (bytes[0].toLong() and 0xFFL shl 24) or
                          (bytes[1].toLong() and 0xFFL shl 16) or
                          (bytes[2].toLong() and 0xFFL shl 8) or
                          (bytes[3].toLong() and 0xFFL)
                _decimal.value = dec.toString()
                _hex.value = String.format("%08X", dec)
                _ipv6.value = "::ffff:$ip"
            }
        } catch (e: Exception) {
            _decimal.value = "Invalid"
            _hex.value = "Invalid"
            _ipv6.value = "Invalid"
        }
    }

    fun convertFromDecimal(decStr: String) {
        _decimal.value = decStr
        try {
            val dec = decStr.toLong()
            val ip = "${(dec ushr 24) and 0xFF}.${(dec ushr 16) and 0xFF}.${(dec ushr 8) and 0xFF}.${dec and 0xFF}"
            _ipv4.value = ip
            _hex.value = String.format("%08X", dec)
            _ipv6.value = "::ffff:$ip"
        } catch (e: Exception) {
            _ipv4.value = "Invalid"
            _hex.value = "Invalid"
            _ipv6.value = "Invalid"
        }
    }

    fun convertFromHex(hexStr: String) {
        _hex.value = hexStr
        try {
            val dec = hexStr.toLong(16)
            convertFromDecimal(dec.toString())
            _decimal.value = dec.toString()
        } catch (e: Exception) {
            _ipv4.value = "Invalid"
            _decimal.value = "Invalid"
            _ipv6.value = "Invalid"
        }
    }
}
