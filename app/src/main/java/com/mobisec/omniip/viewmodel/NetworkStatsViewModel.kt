package com.mobisec.omniip.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.FileReader

data class InterfaceStats(val name: String, val rxBytes: Long, val txBytes: Long, val rxSpeed: Long, val txSpeed: Long)

class NetworkStatsViewModel(application: Application) : AndroidViewModel(application) {
    private val _interfaces = MutableStateFlow<List<InterfaceStats>>(emptyList())
    val interfaces: StateFlow<List<InterfaceStats>> = _interfaces

    init {
        viewModelScope.launch(Dispatchers.IO) {
            var lastStats = readProcNetDev()
            while (isActive) {
                delay(1000)
                val currentStats = readProcNetDev()
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

    private fun readProcNetDev(): Map<String, Pair<Long, Long>> {
        val stats = mutableMapOf<String, Pair<Long, Long>>()
        try {
            BufferedReader(FileReader("/proc/net/dev")).use { reader ->
                reader.readLine() // Header 1
                reader.readLine() // Header 2
                var line = reader.readLine()
                while (line != null) {
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 17) {
                        val name = parts[0].removeSuffix(":")
                        if (name != "lo") {
                            val rxBytes = parts[1].toLongOrNull() ?: 0L
                            val txBytes = parts[9].toLongOrNull() ?: 0L
                            stats[name] = Pair(rxBytes, txBytes)
                        }
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return stats
    }
}
