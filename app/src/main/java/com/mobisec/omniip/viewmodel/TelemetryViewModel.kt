package com.mobisec.omniip.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobisec.omniip.db.Action
import com.mobisec.omniip.db.AppDatabase
import com.mobisec.omniip.db.FirewallRule
import com.mobisec.omniip.db.TargetType
import com.mobisec.omniip.model.AppConnectionSummary
import com.mobisec.omniip.model.ConnectionDetails
import com.mobisec.omniip.model.ConnectionDirection
import com.mobisec.omniip.model.ConnectionTelemetry
import com.mobisec.omniip.vpn.OmniVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TelemetryViewModel(application: Application) : AndroidViewModel(application) {
    private val _appSummaries = MutableStateFlow<List<AppConnectionSummary>>(emptyList())
    val appSummaries: StateFlow<List<AppConnectionSummary>> = _appSummaries

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.firewallRuleDao()

    private val connectionStateMap = mutableMapOf<Int, AppConnectionSummary>()

    init {
        viewModelScope.launch {
            OmniVpnService.telemetryFlow.collect { telemetry: ConnectionTelemetry ->
                val uid = telemetry.uid
                val signature = "${telemetry.uid}:${telemetry.destIp}:${telemetry.destPort}:${telemetry.protocol}"

                val currentSummary = connectionStateMap[uid] ?: AppConnectionSummary(
                    uid = uid,
                    appName = telemetry.appName,
                    packageName = telemetry.packageName,
                    appIcon = telemetry.appIcon,
                    totalTx = 0,
                    totalRx = 0,
                    totalBlocked = 0,
                    activeConnections = emptyList()
                )

                val currentConnections = currentSummary.activeConnections.toMutableList()
                val existingIndex = currentConnections.indexOfFirst { it.signature == signature }

                var totalTx = currentSummary.totalTx
                var totalRx = currentSummary.totalRx
                var totalBlocked = currentSummary.totalBlocked

                if (telemetry.isBlocked) {
                    totalBlocked += 1
                } else if (telemetry.direction == ConnectionDirection.OUTBOUND) {
                    totalTx += 1
                } else if (telemetry.direction == ConnectionDirection.INBOUND) {
                    totalRx += 1
                }

                if (existingIndex >= 0) {
                    val existing = currentConnections[existingIndex]
                    var tx = existing.bytesTx
                    var rx = existing.bytesRx
                    if (telemetry.direction == ConnectionDirection.OUTBOUND) {
                        tx += 1
                    } else if (telemetry.direction == ConnectionDirection.INBOUND) {
                        rx += 1
                    }

                    currentConnections[existingIndex] = existing.copy(
                        bytesTx = tx,
                        bytesRx = rx,
                        packetCount = existing.packetCount + 1,
                        isBlocked = telemetry.isBlocked,
                        lastActive = System.currentTimeMillis()
                    )
                } else {
                    var tx = 0L
                    var rx = 0L
                    if (telemetry.direction == ConnectionDirection.OUTBOUND) {
                        tx = 1L
                    } else if (telemetry.direction == ConnectionDirection.INBOUND) {
                        rx = 1L
                    }
                    currentConnections.add(
                        ConnectionDetails(
                            signature = signature,
                            protocol = telemetry.protocol,
                            destIp = telemetry.destIp,
                            destPort = telemetry.destPort,
                            isBlocked = telemetry.isBlocked,
                            direction = telemetry.direction,
                            bytesTx = tx,
                            bytesRx = rx,
                            packetCount = 1,
                            lastActive = System.currentTimeMillis(),
                            countryCode = telemetry.countryCode,
                            city = telemetry.city
                        )
                    )
                }

                val newSummary = currentSummary.copy(
                    totalTx = totalTx,
                    totalRx = totalRx,
                    totalBlocked = totalBlocked,
                    activeConnections = currentConnections.sortedByDescending { it.lastActive }
                )

                connectionStateMap[uid] = newSummary

                _appSummaries.value = connectionStateMap.values.toList().sortedByDescending { it.activeConnections.maxOfOrNull { conn -> conn.lastActive } ?: 0L }
            }
        }
    }

    fun addRule(targetType: TargetType, targetValue: String, action: Action) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertRule(FirewallRule(targetType = targetType, targetValue = targetValue, action = action))
        }
    }
}
