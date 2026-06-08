package com.mobisec.omniip.viewmodel

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mobisec.omniip.model.DiscoveredDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class LanScannerViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {
    private val DISCOVERED_DEVICES_KEY = "discovered_devices"
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = savedStateHandle.getStateFlow(DISCOVERED_DEVICES_KEY, emptyList())

    private fun setDiscoveredDevices(devices: List<DiscoveredDevice>) {
        savedStateHandle[DISCOVERED_DEVICES_KEY] = devices
    }

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scanFeedback = MutableStateFlow<String?>(null)
    val scanFeedback: StateFlow<String?> = _scanFeedback

    private val vendorCache = mutableMapOf<String, String>()

    fun executeLanSweep() {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanFeedback.value = null
        setDiscoveredDevices(emptyList())

        viewModelScope.launch(Dispatchers.IO) {
            val wifiManager = getApplication<Application>().getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcpInfo = wifiManager.dhcpInfo
            val myIp = Formatter.formatIpAddress(dhcpInfo.ipAddress)
            
            val parts = myIp.split(".")
            val baseIp = if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}" else "192.168.1"
            
            val activeIps = ConcurrentHashMap<String, String>()
            
            coroutineScope {
                val jobs = (1..254).map { i ->
                    async(Dispatchers.IO) {
                        val ip = "$baseIp.$i"
                        try {
                            val inetAddress = InetAddress.getByName(ip)
                            if (inetAddress.isReachable(500)) {
                                activeIps[ip] = inetAddress.canonicalHostName ?: "Unknown"
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("LanScannerViewModel", "Error fetching hostname for IP", e)
                        }
                        Unit
                    }
                }
                jobs.awaitAll()
            }

            val macMap = getMacAddresses()
            val newDevices = mutableListOf<DiscoveredDevice>()
            
            var rootUsed = false
            var fallbackMessage = "Showing active IPs & Hostnames."
            
            if (macMap.isEmpty() && activeIps.isNotEmpty()) {
                fallbackMessage += " MAC/Vendor discovery is restricted on Android 10+ without Root."
            } else if (macMap.isNotEmpty()) {
                rootUsed = true
            }

            for ((ip, hostname) in activeIps) {
                val mac = macMap[ip]
                val formattedMac = mac?.uppercase() ?: "Restricted"
                
                var vendor = "Unknown"
                if (formattedMac != "Restricted" && formattedMac != "00:00:00:00:00:00" && formattedMac != "02:00:00:00:00:00") {
                    vendor = resolveVendorOptimized(formattedMac)
                }
                
                newDevices.add(DiscoveredDevice(ip, formattedMac, vendor, hostname))
            }

            setDiscoveredDevices(newDevices)
            _scanFeedback.value = if (!rootUsed && activeIps.isNotEmpty()) fallbackMessage else null
            _isScanning.value = false
        }
    }

    private fun getMacAddresses(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            // Attempt standard read (fails on API 29+)
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine() // Header
                var line = reader.readLine()
                var foundAny = false
                while (line != null) {
                    val tokens = line.split("\\s+".toRegex())
                    if (tokens.size >= 4) {
                        val ip = tokens[0]
                        val mac = tokens[3]
                        if (mac != "00:00:00:00:00:00" && mac != "02:00:00:00:00:00") {
                            map[ip] = mac
                            foundAny = true
                        }
                    }
                    line = reader.readLine()
                }
                if (foundAny) return map
            }
        } catch (e: Exception) {
            android.util.Log.e("LanScannerViewModel", "Error reading ARP cache", e)
        }

        // Fallback to Shizuku/su
        try {
            var process: Process? = null
            if (rikka.shizuku.Shizuku.pingBinder()) {
                if (rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    try {
                        val clazz = Class.forName("rikka.shizuku.Shizuku")
                        val method = clazz.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                        method.isAccessible = true
                        process = method.invoke(null, arrayOf("sh", "-c", "ip neigh"), null, null) as Process
                    } catch (e: Exception) {
                        android.util.Log.e("LanScannerViewModel", "Shizuku method invocation failed", e)
                    }
                } else {
                    rikka.shizuku.Shizuku.requestPermission(0)
                    return map // Return early while waiting for permission
                }
            }
            
            if (process == null) {
                // Graceful fallback to su if Shizuku isn't available
                process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ip neigh"))
            }

            val reader = BufferedReader(InputStreamReader(process!!.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.split("\\s+".toRegex())
                // e.g. "192.168.1.5 dev wlan0 lladdr 12:34:56:78:90:ab REACHABLE"
                if (parts.size >= 5 && parts[3] == "lladdr") {
                    val ip = parts[0]
                    val mac = parts[4]
                    if (mac != "00:00:00:00:00:00" && mac != "02:00:00:00:00:00") {
                        map[ip] = mac
                    }
                }
            }
            reader.close()
            process!!.waitFor()
        } catch (e: Exception) { 
            android.util.Log.e("LanScannerViewModel", "Error reading Shizuku ip neigh output", e)
        }

        return map
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
                android.util.Log.e("LanScannerViewModel", "Error parsing OUI file", e)
                return "Unknown"
            }
        }

        val vendor = ouiMap?.get(oui) ?: "Unknown"
        vendorCache[oui] = vendor
        return vendor
    }
}
