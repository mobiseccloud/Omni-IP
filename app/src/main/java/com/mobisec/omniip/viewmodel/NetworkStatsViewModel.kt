package com.mobisec.omniip.viewmodel

import android.app.Application
import android.net.TrafficStats
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class InterfaceStats(val name: String, val rxBytes: Long, val txBytes: Long, val rxSpeed: Long, val txSpeed: Long)

class NetworkStatsViewModel(application: Application) : AndroidViewModel(application) {
    private val _interfaces = MutableStateFlow<List<InterfaceStats>>(emptyList())
    val interfaces: StateFlow<List<InterfaceStats>> = _interfaces

    init {
        viewModelScope.launch(Dispatchers.IO) {
            var lastStats = readTrafficStats()
            while (isActive) {
                delay(1000)
                val currentStats = readTrafficStats()
                val updatedStats = mutableListOf<InterfaceStats>()

                for ((name, currentData) in currentStats) {
                    val lastData = lastStats[name]
                    if (lastData != null) {
                        val rxSpeed = currentData.first - lastData.first
                        val txSpeed = currentData.second - lastData.second
                        updatedStats.add(InterfaceStats(name, currentData.first, currentData.second, rxSpeed, txSpeed))
                    } else {
                        updatedStats.add(InterfaceStats(name, currentData.first, currentData.second, 0L, 0L))
                    }
                }

                _interfaces.value = updatedStats.sortedByDescending { it.rxBytes + it.txBytes }
                lastStats = currentStats
            }
        }
    }

    private fun readTrafficStats(): Map<String, Pair<Long, Long>> {
        val stats = mutableMapOf<String, Pair<Long, Long>>()
        
        try {
            val file = java.io.File("/proc/net/dev")
            if (file.exists() && file.canRead()) {
                val lines = file.readLines()
                for (line in lines) {
                    if (line.contains(":")) {
                        val parts = line.split(":").map { it.trim() }
                        if (parts.size == 2) {
                            val iface = parts[0]
                            val data = parts[1].split("\\s+".toRegex())
                            if (data.size >= 9) {
                                val rxBytes = data[0].toLongOrNull() ?: 0L
                                val txBytes = data[8].toLongOrNull() ?: 0L
                                if (rxBytes > 0 || txBytes > 0) {
                                    stats[iface] = Pair(rxBytes, txBytes)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NetworkStatsViewModel", "Error reading /proc/net/dev", e)
        }

        // Fallback to TrafficStats if /proc/net/dev fails or is empty
        if (stats.isEmpty()) {
            val mobileRx = TrafficStats.getMobileRxBytes()
            val mobileTx = TrafficStats.getMobileTxBytes()
            val totalRx = TrafficStats.getTotalRxBytes()
            val totalTx = TrafficStats.getTotalTxBytes()

            if (mobileRx != TrafficStats.UNSUPPORTED.toLong()) {
                stats["Mobile"] = Pair(mobileRx, mobileTx)
            }
            
            if (totalRx != TrafficStats.UNSUPPORTED.toLong()) {
                stats["Total"] = Pair(totalRx, totalTx)
                
                // Approximate WiFi
                val wifiRx = totalRx - (if (mobileRx != TrafficStats.UNSUPPORTED.toLong()) mobileRx else 0L)
                val wifiTx = totalTx - (if (mobileTx != TrafficStats.UNSUPPORTED.toLong()) mobileTx else 0L)
                if (wifiRx > 0 || wifiTx > 0) {
                    stats["WiFi (Approx)"] = Pair(wifiRx, wifiTx)
                }
            }
        }
        
        return stats
    }
}
