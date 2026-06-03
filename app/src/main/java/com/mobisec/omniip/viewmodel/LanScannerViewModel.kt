package com.mobisec.omniip.viewmodel

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobisec.omniip.core.NativeEngine
import com.mobisec.omniip.model.DiscoveredDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader

import androidx.lifecycle.SavedStateHandle

class LanScannerViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {
    private val DISCOVERED_DEVICES_KEY = "discovered_devices"
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = savedStateHandle.getStateFlow(DISCOVERED_DEVICES_KEY, emptyList())

    private fun setDiscoveredDevices(devices: List<DiscoveredDevice>) {
        savedStateHandle[DISCOVERED_DEVICES_KEY] = devices
    }

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val vendorCache = mutableMapOf<String, String>()

    // We parse oui.txt once to an in-memory map or just use grep, but grep might be slow.
    // Let's optimize by loading prefixes we find from ARP into a fast lookup map.

    fun executeLanSweep() {
        if (_isScanning.value) return
        _isScanning.value = true
        setDiscoveredDevices(emptyList())

        viewModelScope.launch(Dispatchers.IO) {
            val wifiManager = getApplication<Application>().getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcpInfo = wifiManager.dhcpInfo
            val myIp = Formatter.formatIpAddress(dhcpInfo.ipAddress)
            val netmask = Formatter.formatIpAddress(dhcpInfo.netmask)

            // Let's compute a simple /24 subnet for now based on myIp.
            // A more robust way would calculate the actual subnet mask.
            val parts = myIp.split(".")
            val subnet = if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.0/24" else "192.168.1.0/24"

            // Execute rapid sweep using C++ engine via bridge
            NativeEngine.executeLanSweep(subnet)

            // The ping sweep updates the ARP cache. Read /proc/net/arp.
            val newDevices = mutableListOf<DiscoveredDevice>()

            try {
                BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                    // Skip header: "IP address       HW type     Flags       HW address            Mask     Device"
                    reader.readLine()
                    var line = reader.readLine()
                    while (line != null) {
                        val tokens = line.split("\\s+".toRegex())
                        if (tokens.size >= 4) {
                            val ip = tokens[0]
                            val mac = tokens[3].uppercase()

                            // Check valid MAC format and not 00:00:00:00:00:00
                            if (mac.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}".toRegex()) && mac != "00:00:00:00:00:00") {
                                val vendor = resolveVendorOptimized(mac)
                                newDevices.add(DiscoveredDevice(ip, mac, vendor))
                            }
                        }
                        line = reader.readLine()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            setDiscoveredDevices(newDevices)
            _isScanning.value = false
        }
    }

private var ouiMap: Map<String, String>? = null

    private suspend fun resolveVendorOptimized(mac: String): String {
        // e.g., MAC: 28:6F:B9:XX:XX:XX -> OUI: 28-6F-B9
        val oui = mac.substring(0, 8).replace(":", "-")

        if (vendorCache.containsKey(oui)) {
            return vendorCache[oui]!!
        }

        if (ouiMap == null) {
            val map = mutableMapOf<String, String>()
            try {
                val file = File(getApplication<Application>().filesDir, "oui.txt")
                if (!file.exists()) {
                    return "Unknown"
                }
                BufferedReader(FileReader(file)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.contains("(hex)")) {
                            val parts = line!!.split("(hex)")
                            if (parts.size > 1) {
                                map[parts[0].trim()] = parts[1].trim()
                            }
                        }
                    }
                }
                ouiMap = map
            } catch (e: java.io.FileNotFoundException) {
                return "Unknown"
            } catch (e: Exception) {
                e.printStackTrace()
                return "Unknown"
            }
        }

        val vendor = ouiMap?.get(oui) ?: "Unknown"
        vendorCache[oui] = vendor
        return vendor
    }
}
